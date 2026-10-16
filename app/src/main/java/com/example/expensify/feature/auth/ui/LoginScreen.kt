package com.example.expensify.feature.auth.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.PasswordField
import com.example.expensify.designsystem.theme.ExpensifyTheme

@Composable
fun LoginRoute(onSignup: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onSignup = onSignup,
    )
}

/** Username or email, and password (req AUTH-05), with the way to sign up. Calm and centred; the keyboard pushes it up, never over it. */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSignup: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_subtitle),
    ) {
        AppTextField(
            value = state.usernameOrEmail,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.login_username_or_email),
            placeholder = stringResource(R.string.login_username_or_email_placeholder),
            error = state.usernameError?.asString(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.login_password),
            placeholder = stringResource(R.string.login_password_placeholder),
            error = state.passwordError?.asString(),
            enabled = !state.submitting,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )

        state.error?.let { Banner(it.asString(), BannerTone.Error) }

        Spacer(Modifier.height(4.dp))
        AuthSubmitButton(
            label = stringResource(if (state.submitting) R.string.login_submitting else R.string.login_submit),
            working = state.submitting,
            onClick = onSubmit,
        )
        AuthSwitchLink(
            question = stringResource(R.string.login_new_here),
            action = stringResource(R.string.login_create_account),
            onClick = onSignup,
        )
    }
}

@PreviewLightDark
@Composable
private fun LoginPreview() {
    ExpensifyTheme {
        LoginScreen(
            state = LoginUiState(usernameOrEmail = "sita.r", error = plain("Wrong username, email or password.")),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onSignup = {},
        )
    }
}
