package com.example.expensify.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.designsystem.components.MoneyField
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.accounts.domain.AccountRules.BalanceCaption

/** The archive warning, the delete confirmation and the reconcile sheet, whichever is open. */
@Composable
fun AccountDialogsHost(
    dialogs: AccountDialogs,
    onConfirmArchive: () -> Unit,
    onConfirmDelete: () -> Unit,
    onReconcileInput: (String) -> Unit,
    onSaveReconcile: () -> Unit,
    onDismiss: () -> Unit,
) {
    dialogs.archiveTarget?.let { account ->
        val view = AccountRules.balanceView(account.type, account.currentBalance)
        val amount = MoneyFormat.full(view.amount)
        val balance = if (view.caption == BalanceCaption.Owed) stringResource(R.string.archive_dialog_balance_owed, amount) else amount
        ConfirmDialog(
            title = stringResource(R.string.archive_dialog_title, account.name),
            message = stringResource(R.string.archive_dialog_message, balance),
            confirmText = stringResource(R.string.account_archive),
            onConfirm = onConfirmArchive,
            onDismiss = onDismiss,
            busy = dialogs.busy,
        )
    }
    dialogs.deleteTarget?.let { account ->
        ConfirmDialog(
            title = stringResource(R.string.delete_dialog_title, account.name),
            message = stringResource(R.string.delete_dialog_message),
            confirmText = stringResource(R.string.delete_dialog_confirm),
            onConfirm = onConfirmDelete,
            onDismiss = onDismiss,
            destructive = true,
            busy = dialogs.busy,
        )
    }
    dialogs.reconcileTarget?.let { account ->
        ReconcileSheet(
            account = account,
            input = dialogs.reconcileInput,
            busy = dialogs.busy,
            onInput = onReconcileInput,
            onSave = onSaveReconcile,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Reconcile: the user types the real balance and the current balance moves to
 * it (req ACC-06). Cards and loans enter the amount owed, stored negative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReconcileSheet(
    account: Account,
    input: String,
    busy: Boolean,
    onInput: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val typed = Money.parse(input)
    val difference = typed?.let { AccountRules.flipForLiability(account.type, it) - account.currentBalance }
    val view = AccountRules.balanceView(account.type, account.currentBalance)
    val inApp = when (view.caption) {
        BalanceCaption.Owed -> stringResource(R.string.reconcile_in_app_owed, MoneyFormat.full(view.amount))
        BalanceCaption.InCredit -> stringResource(R.string.reconcile_in_app_in_credit, MoneyFormat.full(view.amount))
        BalanceCaption.Balance -> MoneyFormat.full(view.amount)
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.reconcile_title, account.name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.reconcile_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                value = input,
                onValueChange = onInput,
                label = stringResource(if (account.type.isLiability) R.string.reconcile_actual_owed else R.string.reconcile_actual_balance),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { if (typed != null && !busy) onSave() }),
            )
            AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                SummaryRow(label = stringResource(R.string.reconcile_in_app)) {
                    Text(inApp, style = MaterialTheme.typography.bodyLarge)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                SummaryRow(label = stringResource(R.string.reconcile_adjustment)) {
                    when {
                        difference == null -> Text("—", style = MaterialTheme.typography.bodyLarge)
                        difference.isZero -> Text(
                            stringResource(R.string.reconcile_adjustment_none),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        else -> AmountText(
                            money = difference,
                            kind = if (difference.isPositive) AmountKind.Income else AmountKind.Expense,
                            sign = Sign.ExceptZero,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = onSave, enabled = typed != null && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (busy) R.string.action_saving else R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}
