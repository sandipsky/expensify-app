package com.example.expensify.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val usernameOrEmail: String = "",
    val password: String = "",
    val usernameError: UiText? = null,
    val passwordError: UiText? = null,
    /** A refused login, shown above the button. */
    val error: UiText? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(usernameOrEmail = value, usernameError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, error = null) }
    }

    /** On success nothing happens here: the session flow switches the app to the signed-in shell. */
    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val usernameError = if (current.usernameOrEmail.isBlank()) res(R.string.login_enter_username) else null
        val passwordError = if (current.password.isEmpty()) res(R.string.login_enter_password) else null
        if (usernameError != null || passwordError != null) {
            _state.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                authRepository.login(current.usernameOrEmail, current.password)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(submitting = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}
