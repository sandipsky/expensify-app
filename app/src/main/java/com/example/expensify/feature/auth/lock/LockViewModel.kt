package com.example.expensify.feature.auth.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The lock screen over the signed-in shell: runs the phone's check, reports
 * why it did not pass, and offers Log out for someone who cannot pass it.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLock: AppLock,
    private val auth: AuthRepository,
    private val messages: Messages,
    val biometrics: Biometrics,
) : ViewModel() {

    /** Counts up each time the lock engages, so the screen asks once more each time. */
    val generation: StateFlow<Int> = appLock.generation

    private val _note = MutableStateFlow<UiText?>(null)

    /** Why the last check did not pass, under the Unlock button. */
    val note: StateFlow<UiText?> = _note.asStateFlow()

    fun finished(outcome: Biometrics.Outcome) {
        when (outcome) {
            Biometrics.Outcome.Success -> {
                _note.value = null
                appLock.unlock()
            }
            Biometrics.Outcome.Cancelled -> Unit
            Biometrics.Outcome.LockedOut -> _note.value = res(R.string.lock_locked_out)
            Biometrics.Outcome.Failed -> _note.value = res(R.string.lock_failed)
        }
    }

    /** The phone has no screen lock any more, so nothing can be checked: the lock stands aside and says so. */
    fun skip() {
        appLock.unlock()
        messages.show(res(R.string.lock_skipped))
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
