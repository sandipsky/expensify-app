package com.example.expensify.feature.settings.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.settings.domain.SettingsRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormUiState(
    /** The profile has been read into the fields. */
    val loaded: Boolean = false,
    val name: String = "",
    val email: String = "",
    /** Needed only when the email changes: the login only changes once it proves it's the user. */
    val password: String = "",
    val originalName: String = "",
    val currentEmail: String = "",
    val pendingEmail: String? = null,
    val nameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    /** A refused save, shown above the button. */
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    val emailEdited: Boolean get() = SettingsRules.emailKey(email) != SettingsRules.emailKey(currentEmail)
    val dirty: Boolean get() = name.trim() != originalName || emailEdited
}

sealed interface ProfileFormEvent {
    data object Saved : ProfileFormEvent
}

/**
 * Settings › Edit profile (req AUTH-11): the full name and email. A new
 * email gets a verification link, and the login switches once it's opened;
 * the password proves it's the user asking.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val messages: Messages,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormUiState())
    val state: StateFlow<ProfileFormUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileFormEvent>(Channel.BUFFERED)
    val events: Flow<ProfileFormEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = auth.profile().filterNotNull().first()
            _state.update {
                it.copy(
                    loaded = true,
                    name = profile.name,
                    email = profile.email,
                    originalName = profile.name,
                    currentEmail = profile.email,
                    pendingEmail = profile.pendingEmail,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun save() {
        val current = _state.value
        if (!current.loaded || current.saving) return
        val nameError = when (SettingsRules.validateName(current.name)) {
            SettingsRules.NameError.Required -> res(R.string.settings_error_name_required)
            SettingsRules.NameError.TooLong -> res(R.string.settings_error_name_long)
            null -> null
        }
        val emailError = when (SettingsRules.validateEmail(current.email)) {
            SettingsRules.EmailError.Required -> res(R.string.settings_error_email_required)
            SettingsRules.EmailError.Invalid -> res(R.string.settings_error_email_invalid)
            null -> null
        }
        val passwordError = if (current.emailEdited && current.password.isEmpty()) res(R.string.settings_error_password_for_email) else null
        if (nameError != null || emailError != null || passwordError != null) {
            _state.update { it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError) }
            return
        }
        val nameEdited = current.name.trim() != current.originalName
        val newEmail = if (current.emailEdited) current.email.trim() else null
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                if (nameEdited) auth.updateName(current.name)
                if (newEmail != null) auth.changeEmail(newEmail, current.password)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppError.WrongPassword) {
                _state.update { it.copy(saving = false, passwordError = plain(error.message.orEmpty())) }
                return@launch
            } catch (error: AppError.Taken) {
                _state.update { it.copy(saving = false, emailError = plain(error.message.orEmpty())) }
                return@launch
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = res(R.string.settings_could_not_save_profile, ErrorMessages.of(error))) }
                return@launch
            }
            messages.show(if (newEmail != null) res(R.string.settings_check_inbox, newEmail) else res(R.string.settings_profile_saved))
            _events.trySend(ProfileFormEvent.Saved)
        }
    }
}

@Composable
fun ProfileFormRoute(onDone: () -> Unit, viewModel: ProfileFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileFormEvent.Saved -> onDone()
            }
        }
    }
    ProfileFormScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSave = viewModel::save,
        onClose = onDone,
    )
}

@Composable
fun ProfileFormScreen(
    state: ProfileFormUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    FormScaffold(
        title = stringResource(R.string.settings_edit_profile),
        actionLabel = stringResource(if (state.saving) R.string.action_saving else R.string.settings_save_changes),
        actionEnabled = state.loaded && state.dirty && !state.saving,
        onAction = onSave,
        onClose = onClose,
    ) {
        if (!state.loaded) {
            Skeleton(56.dp)
            Skeleton(56.dp)
            return@FormScaffold
        }
        AppTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.settings_full_name),
            placeholder = stringResource(R.string.settings_full_name_placeholder),
            error = state.nameError?.asString(),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.settings_email),
            placeholder = stringResource(R.string.settings_email_placeholder),
            error = state.emailError?.asString(),
            hint = if (state.pendingEmail != null && !state.emailEdited) stringResource(R.string.settings_pending_email, state.pendingEmail) else null,
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (state.emailEdited) ImeAction.Next else ImeAction.Done),
        )
        if (state.emailEdited) {
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.settings_your_password),
                hint = stringResource(R.string.settings_email_link_hint),
                error = state.passwordError?.asString(),
                imeAction = ImeAction.Done,
                enabled = !state.saving,
            )
        }
        state.error?.let { Banner(it.asString(), BannerTone.Error) }
        Text(
            stringResource(R.string.settings_username_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfileFormPreview() {
    ExpensifyTheme {
        ProfileFormScreen(
            state = ProfileFormUiState(loaded = true, name = "Sita Rai", email = "sita.rai@example.com", originalName = "Sita Rai", currentEmail = "sita@example.com"),
            onNameChange = {}, onEmailChange = {}, onPasswordChange = {}, onSave = {}, onClose = {},
        )
    }
}
