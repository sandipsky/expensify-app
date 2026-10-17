package com.example.expensify.feature.settings.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.device.PhotoThumbnails
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.domain.PreferencesPatch
import com.example.expensify.feature.auth.domain.Profile
import com.example.expensify.feature.auth.domain.ThemePref
import com.example.expensify.feature.auth.lock.AppLock
import com.example.expensify.feature.auth.lock.Biometrics
import com.example.expensify.feature.settings.domain.SettingsRules
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Delete all transactions?" confirmation, with the count it names. */
data class DeleteAllDialog(val count: Int, val busy: Boolean = false)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Error(val message: UiText) : SettingsUiState
    data class Content(
        val profile: Profile,
        /** A photo is being read or saved; its buttons wait. */
        val photoBusy: Boolean,
        val deleteAll: DeleteAllDialog?,
        /** Settings › Security: whether the app lock is on for this login. */
        val appLock: Boolean,
    ) : SettingsUiState
}

/**
 * `/settings` (req SET-01…SET-04): the profile and password, the theme,
 * which notifications to get, and deleting data. The profile is live, so a
 * preference saved here or on the web shows at once; a refused save reports
 * and the listener puts the old value back.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val messages: Messages,
    private val thumbnails: PhotoThumbnails,
    private val appLock: AppLock,
    /** For the screen to run the phone's prompt; it holds no activity. */
    val biometrics: Biometrics,
) : ViewModel() {

    private val photoBusy = MutableStateFlow(false)
    private val deleteAll = MutableStateFlow<DeleteAllDialog?>(null)

    init {
        // Picks up an email changed through its link, and repairs a login without a profile (req §9.3).
        viewModelScope.launch { runCatching { auth.syncLogin() } }
    }

    val state: StateFlow<SettingsUiState> = combine(auth.profile(), photoBusy, deleteAll, appLock.enabled) { profile, busy, dialog, lock ->
        if (profile == null) SettingsUiState.Loading else SettingsUiState.Content(profile, busy, dialog, appLock = lock?.on == true) as SettingsUiState
    }
        .catch { emit(SettingsUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    // Appearance (SET-02)

    fun setTheme(theme: ThemePref) {
        val current = (state.value as? SettingsUiState.Content)?.profile?.preferences?.theme
        if (theme == current) return
        save(PreferencesPatch(theme = theme), R.string.settings_could_not_change_theme)
    }

    // Notifications (SET-03)

    fun setDailyReminder(on: Boolean) = save(PreferencesPatch(dailyReminder = on), R.string.settings_could_not_save)

    fun setReminderTime(time: String) {
        if (!SettingsRules.isTimeOfDay(time)) return
        save(PreferencesPatch(reminderTime = time), R.string.settings_could_not_save)
    }

    fun setBudgetAlerts(on: Boolean) = save(PreferencesPatch(budgetAlerts = on), R.string.settings_could_not_save)

    fun setBillReminders(on: Boolean) = save(PreferencesPatch(billReminders = on), R.string.settings_could_not_save)

    // Security: the app lock, kept on this phone (feature/auth/lock). The phone has just confirmed it is them.

    fun setAppLock(on: Boolean) {
        viewModelScope.launch {
            appLock.setEnabled(on)
            messages.show(res(if (on) R.string.settings_app_lock_on else R.string.settings_app_lock_off))
        }
    }

    /** The prompt was cancelled or did not pass: the switch stays as it was. */
    fun appLockNotChanged() = messages.show(res(R.string.settings_app_lock_not_changed))

    /** What the system answered when asked to allow notifications. */
    fun notifyPermission(granted: Boolean) {
        messages.show(res(if (granted) R.string.settings_notifications_allowed else R.string.settings_notifications_stay_off))
    }

    private fun save(patch: PreferencesPatch, @StringRes failure: Int) {
        viewModelScope.launch {
            try {
                auth.updatePreferences(patch)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }

    // Photo

    /** Scales the picked image down to a small square and saves it at once. */
    fun pickPhoto(uri: Uri) {
        viewModelScope.launch {
            photoBusy.value = true
            try {
                val photoUrl = thumbnails.squareJpegDataUrl(uri, SettingsRules.PHOTO_EDGE, SettingsRules.MAX_PHOTO_URL_LENGTH)
                if (photoUrl == null) {
                    messages.show(res(R.string.settings_photo_unreadable))
                } else {
                    auth.updatePhoto(photoUrl)
                    messages.show(res(R.string.settings_photo_updated))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(R.string.settings_could_not_save_photo, ErrorMessages.of(error)))
            } finally {
                photoBusy.value = false
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            photoBusy.value = true
            try {
                auth.updatePhoto(null)
                messages.show(res(R.string.settings_photo_removed))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(R.string.settings_could_not_remove_photo, ErrorMessages.of(error)))
            } finally {
                photoBusy.value = false
            }
        }
    }

    // Data and privacy (SET-04)

    /** Counts the entries first, so the question says how many go. */
    fun requestDeleteAll() {
        viewModelScope.launch {
            val count = try {
                transactions.count()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(R.string.settings_could_not_count, ErrorMessages.of(error)))
                return@launch
            }
            if (count == 0) messages.show(res(R.string.settings_no_transactions_to_delete)) else deleteAll.value = DeleteAllDialog(count)
        }
    }

    fun confirmDeleteAll() {
        val dialog = deleteAll.value ?: return
        if (dialog.busy) return
        viewModelScope.launch {
            deleteAll.value = dialog.copy(busy = true)
            try {
                val deleted = transactions.deleteAll()
                deleteAll.value = null
                messages.show(UiText.Plural(R.plurals.settings_deleted_transactions, deleted, listOf(deleted)))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Reported and kept open for another try; the batches already committed stay deleted.
                deleteAll.update { it?.copy(busy = false) }
                messages.show(res(R.string.settings_could_not_delete_transactions, ErrorMessages.of(error)))
            }
        }
    }

    fun dismissDeleteAll() {
        if (deleteAll.value?.busy != true) deleteAll.value = null
    }
}
