package com.example.expensify.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.PasswordField
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val currentError: UiText? = null,
    val newError: UiText? = null,
    val error: UiText? = null,
    val saving: Boolean = false,
)

sealed interface ChangePasswordEvent {
    data object Changed : ChangePasswordEvent
}

/** Change password (req AUTH-12): the current one proves it's the user, then the new one replaces it. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val messages: Messages,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    fun onCurrentChange(value: String) = _state.update { it.copy(currentPassword = value, currentError = null, error = null) }
    fun onNewChange(value: String) = _state.update { it.copy(newPassword = value, newError = null, error = null) }

    fun submit() {
        val current = _state.value
        if (current.saving) return
        val currentError = if (current.currentPassword.isEmpty()) res(R.string.settings_error_current_password) else null
        val newError = when (SettingsRules.validateNewPassword(current.newPassword, current.currentPassword)) {
            SettingsRules.PasswordError.TooShort -> res(R.string.settings_error_password_short, SettingsRules.MIN_PASSWORD_LENGTH)
            SettingsRules.PasswordError.SameAsCurrent -> res(R.string.settings_error_password_same)
            null -> null
        }
        if (currentError != null || newError != null) {
            _state.update { it.copy(currentError = currentError, newError = newError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                auth.changePassword(current.currentPassword, current.newPassword)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppError.WrongPassword) {
                _state.update { it.copy(saving = false, currentError = plain(error.message.orEmpty())) }
                return@launch
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
                return@launch
            }
            messages.show(res(R.string.settings_password_changed))
            _events.trySend(ChangePasswordEvent.Changed)
        }
    }
}

@Composable
fun ChangePasswordRoute(onDone: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ChangePasswordEvent.Changed -> onDone()
            }
        }
    }
    ChangePasswordScreen(
        state = state,
        onCurrentChange = viewModel::onCurrentChange,
        onNewChange = viewModel::onNewChange,
        onSubmit = viewModel::submit,
        onClose = onDone,
    )
}

@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    FormScaffold(
        title = stringResource(R.string.settings_change_password),
        actionLabel = stringResource(if (state.saving) R.string.action_saving else R.string.settings_change_password),
        actionEnabled = !state.saving,
        onAction = onSubmit,
        onClose = onClose,
    ) {
        Text(
            stringResource(R.string.settings_password_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(
            value = state.currentPassword,
            onValueChange = onCurrentChange,
            label = stringResource(R.string.settings_current_password),
            error = state.currentError?.asString(),
            enabled = !state.saving,
        )
        PasswordField(
            value = state.newPassword,
            onValueChange = onNewChange,
            label = stringResource(R.string.settings_new_password),
            hint = stringResource(R.string.settings_new_password_hint, SettingsRules.MIN_PASSWORD_LENGTH),
            error = state.newError?.asString(),
            imeAction = ImeAction.Done,
            enabled = !state.saving,
        )
        state.error?.let { Banner(it.asString(), BannerTone.Error) }
    }
}

@PreviewLightDark
@Composable
private fun ChangePasswordPreview() {
    ExpensifyTheme {
        ChangePasswordScreen(state = ChangePasswordUiState(currentPassword = "secret", newPassword = "sh"), onCurrentChange = {}, onNewChange = {}, onSubmit = {}, onClose = {})
    }
}
