package com.example.expensify.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.designsystem.components.MoneyField
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.accounts.domain.AccountType

@Composable
fun AccountFormRoute(
    onDone: () -> Unit,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AccountFormEvent.Saved -> onDone()
            }
        }
    }
    AccountFormScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onBalanceChange = viewModel::onBalanceChange,
        onCreditLimitChange = viewModel::onCreditLimitChange,
        onIncludeInTotalChange = viewModel::onIncludeInTotalChange,
        onSave = viewModel::save,
        onClose = onDone,
    )
}

/** Full screen: the keyboard is up most of the time and Save stays pinned at the bottom. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountFormScreen(
    state: AccountFormUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onBalanceChange: (String) -> Unit,
    onCreditLimitChange: (String) -> Unit,
    onIncludeInTotalChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val liability = state.type.isLiability
    val balanceLabel = when {
        !liability -> R.string.account_form_opening_balance
        state.isEdit -> R.string.account_form_opening_amount_owed
        else -> R.string.account_form_amount_owed
    }
    val balanceHint = when {
        state.isEdit -> R.string.account_form_balance_hint_edit
        liability -> R.string.account_form_balance_hint_liability
        else -> R.string.account_form_balance_hint_new
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.account_form_edit_title else R.string.account_form_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = onSave,
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                state.saving -> R.string.action_saving
                                state.isEdit -> R.string.action_save
                                else -> R.string.account_form_submit_add
                            },
                        ),
                    )
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.account_form_name),
                placeholder = stringResource(R.string.account_form_name_placeholder),
                error = state.nameError?.asString(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )

            SectionLabel(stringResource(R.string.account_form_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(stringResource(type.labelRes())) },
                        leadingIcon = { Icon(IconMap.of(type.defaultIcon), contentDescription = null, Modifier.height(18.dp)) },
                    )
                }
            }

            MoneyField(
                value = state.balanceText,
                onValueChange = onBalanceChange,
                label = stringResource(balanceLabel),
                hint = stringResource(balanceHint),
                error = state.balanceError?.asString(),
                imeAction = if (state.type == AccountType.CreditCard) ImeAction.Next else ImeAction.Done,
            )

            if (state.type == AccountType.CreditCard) {
                MoneyField(
                    value = state.creditLimitText,
                    onValueChange = onCreditLimitChange,
                    label = stringResource(R.string.account_form_credit_limit),
                    hint = stringResource(R.string.account_form_credit_limit_hint),
                    error = state.creditLimitError?.asString(),
                    imeAction = ImeAction.Done,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.account_form_include_in_total),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.includeInTotal, onCheckedChange = onIncludeInTotalChange)
            }

            state.error?.let { Banner(it.asString(), BannerTone.Error) }
        }
    }
}

@PreviewLightDark
@Composable
private fun AccountFormPreview() {
    ExpensifyTheme {
        AccountFormScreen(
            state = AccountFormUiState(name = "Visa card", type = AccountType.CreditCard, balanceText = "42000", creditLimitText = "50000"),
            onNameChange = {}, onTypeChange = {}, onBalanceChange = {}, onCreditLimitChange = {},
            onIncludeInTotalChange = {}, onSave = {}, onClose = {},
        )
    }
}
