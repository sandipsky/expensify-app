package com.example.expensify.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.domain.SignupRules

@Composable
fun SignupRoute(onLogin: () -> Unit, viewModel: SignupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignupScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onLogin = onLogin,
    )
}

/**
 * Sign up (req AUTH-01): name, username, email and a password of at least 8
 * characters with the strength checklist. The new account logs straight in,
 * with no verification step.
 */
@Composable
fun SignupScreen(
    state: SignupUiState,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLogin: () -> Unit,
) {
    AuthScaffold(
        title = stringResource(R.string.signup_title),
        subtitle = stringResource(R.string.signup_subtitle),
        onBack = onLogin,
    ) {
        AppTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.signup_name),
            placeholder = stringResource(R.string.signup_name_placeholder),
            error = state.nameError?.asString(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        AppTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = stringResource(R.string.signup_username),
            placeholder = stringResource(R.string.signup_username_placeholder),
            hint = stringResource(R.string.signup_username_hint),
            error = state.usernameError?.asString(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.signup_email),
            placeholder = stringResource(R.string.signup_email_placeholder),
            error = state.emailError?.asString(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.signup_password),
            placeholder = stringResource(R.string.signup_password_placeholder, SignupRules.MIN_PASSWORD_LENGTH),
            error = state.passwordError?.asString(),
            enabled = !state.submitting,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        PasswordChecklist(state.passwordChecks)

        state.error?.let { Banner(it.asString(), BannerTone.Error) }

        Spacer(Modifier.height(4.dp))
        AuthSubmitButton(
            label = stringResource(if (state.submitting) R.string.signup_submitting else R.string.signup_submit),
            working = state.submitting,
            onClick = onSubmit,
        )
        Text(
            stringResource(R.string.signup_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AuthSwitchLink(
            question = stringResource(R.string.signup_have_account),
            action = stringResource(R.string.signup_log_in),
            onClick = onLogin,
        )
    }
}

/** What a strong password has, as the web's password input lists it; a hint, only the length is enforced. */
@Composable
private fun PasswordChecklist(checks: SignupRules.PasswordChecks) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PasswordRule(checks.length, stringResource(R.string.signup_rule_length, SignupRules.MIN_PASSWORD_LENGTH))
        PasswordRule(checks.mixedCase, stringResource(R.string.signup_rule_case))
        PasswordRule(checks.special, stringResource(R.string.signup_rule_special))
        PasswordRule(checks.number, stringResource(R.string.signup_rule_number))
    }
}

@Composable
private fun PasswordRule(met: Boolean, text: String) {
    // Icon and colour change together, so the state never rests on colour alone (NFR-06).
    val tint = if (met) ExpensifyTheme.colors.income else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (met) AppIcons.RuleMet else AppIcons.RuleUnmet,
            contentDescription = stringResource(if (met) R.string.signup_rule_met else R.string.signup_rule_unmet),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@PreviewLightDark
@Composable
private fun SignupPreview() {
    ExpensifyTheme {
        SignupScreen(
            state = SignupUiState(
                name = "Sita Rai",
                username = "sita.r",
                email = "sita@example.com",
                password = "Secret1",
                usernameError = plain("That username is taken. Try another one."),
                passwordChecks = SignupRules.passwordChecks("Secret1"),
            ),
            onNameChange = {},
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onLogin = {},
        )
    }
}
