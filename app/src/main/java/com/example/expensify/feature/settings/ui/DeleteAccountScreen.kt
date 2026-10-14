package com.example.expensify.feature.settings.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.settings.domain.SettingsRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val phrase: String = "",
    val password: String = "",
    val passwordError: UiText? = null,
    val error: UiText? = null,
    val deleting: Boolean = false,
) {
    val ready: Boolean get() = SettingsRules.canDelete(phrase, password) && !deleting
}

/**
 * The last step before deleting the account (req AUTH-13, US-09): the user
 * types DELETE, so it can't happen by a slip, and their password, so only
 * they can. Once the login is gone the session ends and the login screen
 * shows, with a note that says so.
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val deviceState: DeviceState,
    private val messages: Messages,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    fun onPhraseChange(value: String) = _state.update { it.copy(phrase = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.ready) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            try {
                auth.deleteAccount(current.password)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppError.WrongPassword) {
                _state.update { it.copy(deleting = false, passwordError = plain(error.message.orEmpty())) }
                return@launch
            } catch (error: Exception) {
                _state.update { it.copy(deleting = false, error = plain(ErrorMessages.of(error))) }
                return@launch
            }
            // This phone forgets what belonged to the login; the session flow sends the app to Log in.
            deviceState.clearUserState()
            messages.show(res(R.string.settings_account_deleted))
        }
    }
}

@Composable
fun DeleteAccountRoute(onClose: () -> Unit, viewModel: DeleteAccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeleteAccountScreen(
        state = state,
        onPhraseChange = viewModel::onPhraseChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onClose = onClose,
    )
}

@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onPhraseChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    FormScaffold(
        title = stringResource(R.string.settings_delete_account_question),
        actionLabel = stringResource(if (state.deleting) R.string.settings_deleting else R.string.settings_delete_account_action),
        actionEnabled = state.ready,
        onAction = onSubmit,
        onClose = { if (!state.deleting) onClose() },
        destructive = true,
    ) {
        Text(
            stringResource(R.string.settings_delete_account_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextField(
            value = state.phrase,
            onValueChange = onPhraseChange,
            label = stringResource(R.string.settings_type_to_confirm, SettingsRules.DELETE_PHRASE),
            enabled = !state.deleting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.settings_your_password),
            error = state.passwordError?.asString(),
            imeAction = ImeAction.Done,
            enabled = !state.deleting,
        )
        state.error?.let { Banner(it.asString(), BannerTone.Error) }
    }
}

@PreviewLightDark
@Composable
private fun DeleteAccountPreview() {
    ExpensifyTheme {
        DeleteAccountScreen(state = DeleteAccountUiState(phrase = "DELETE"), onPhraseChange = {}, onPasswordChange = {}, onSubmit = {}, onClose = {})
    }
}
