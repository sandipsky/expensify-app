package com.example.expensify.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.lock.AppLock
import com.example.expensify.feature.auth.domain.SignupRules
import com.example.expensify.feature.auth.domain.Usernames
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignupUiState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: UiText? = null,
    val usernameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    /** The checklist under the password; a hint, only the length is enforced (req AUTH-01). */
    val passwordChecks: SignupRules.PasswordChecks = SignupRules.PasswordChecks(),
    /** A refused signup that isn't about one field, shown above the button. */
    val error: UiText? = null,
    val submitting: Boolean = false,
)

/**
 * Sign up (req AUTH-01…AUTH-04, US-08): name, username, email and a password
 * of at least 8 characters. The new account logs straight in; on success the
 * session flow switches the app to the signed-in shell, nothing happens here.
 */
@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appLock: AppLock,
) : ViewModel() {

    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, error = null) }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, usernameError = null, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordError = null, passwordChecks = SignupRules.passwordChecks(value), error = null)
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return

        val nameError = when (SignupRules.validateName(current.name)) {
            SignupRules.NameError.Required -> res(R.string.signup_error_name_required)
            SignupRules.NameError.TooLong -> res(R.string.signup_error_name_long, SignupRules.MAX_NAME_LENGTH)
            null -> null
        }
        val usernameError = when (SignupRules.validateUsername(current.username)) {
            SignupRules.UsernameError.Required -> res(R.string.signup_error_username_required)
            SignupRules.UsernameError.TooShort -> res(R.string.signup_error_username_short, Usernames.MIN_LENGTH)
            SignupRules.UsernameError.TooLong -> res(R.string.signup_error_username_long, Usernames.MAX_LENGTH)
            SignupRules.UsernameError.InvalidCharacters -> res(R.string.signup_error_username_chars)
            null -> null
        }
        val emailError = when (SignupRules.validateEmail(current.email)) {
            SignupRules.EmailError.Required -> res(R.string.signup_error_email_required)
            SignupRules.EmailError.Invalid -> res(R.string.signup_error_email_invalid)
            null -> null
        }
        val passwordError = when (SignupRules.validatePassword(current.password)) {
            SignupRules.PasswordError.TooShort -> res(R.string.signup_error_password_short, SignupRules.MIN_PASSWORD_LENGTH)
            null -> null
        }
        if (nameError != null || usernameError != null || emailError != null || passwordError != null) {
            _state.update {
                it.copy(nameError = nameError, usernameError = usernameError, emailError = emailError, passwordError = passwordError)
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                // A new login is proof enough: the app lock (Settings › Security) does not ask right after.
                appLock.unlock()
                authRepository.register(
                    name = current.name,
                    username = current.username,
                    email = current.email,
                    password = current.password,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppError.Taken) {
                // A taken username or email shows on its field (req AUTH-03).
                val message = plain(error.message.orEmpty())
                _state.update {
                    when (error.field) {
                        AppError.Taken.Field.Username -> it.copy(submitting = false, usernameError = message)
                        AppError.Taken.Field.Email -> it.copy(submitting = false, emailError = message)
                        AppError.Taken.Field.Name -> it.copy(submitting = false, error = message)
                    }
                }
            } catch (error: Exception) {
                _state.update { it.copy(submitting = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}
