package com.example.expensify.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Meter
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.meterToneOf
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules

/**
 * One account on the Accounts page (req ACC-04): icon, name, type and
 * balance, credit use for cards, chips, and a menu with the account's
 * actions. Tapping the card opens the account page.
 */
@Composable
fun AccountCard(
    account: Account,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onReconcile: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance = AccountRules.balanceView(account.type, account.currentBalance)
    val used = AccountRules.utilizationView(account)
    var menuOpen by remember { mutableStateOf(false) }

    AppCard(modifier = modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = IconMap.of(account.icon), color = dataColor(account.color), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(account.type.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.More, contentDescription = stringResource(R.string.account_actions_for, account.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (account.archived) {
                        MenuItem(R.string.account_restore, AppIcons.Restore) { menuOpen = false; onRestore() }
                    } else {
                        MenuItem(R.string.action_edit, AppIcons.Edit) { menuOpen = false; onEdit() }
                        MenuItem(R.string.account_reconcile, AppIcons.Reconcile) { menuOpen = false; onReconcile() }
                        MenuItem(R.string.account_archive, AppIcons.Archive) { menuOpen = false; onArchive() }
                    }
                    MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(balance.caption.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(
            money = balance.amount,
            style = MaterialTheme.typography.headlineSmall,
            color = if (balance.negative) ExpensifyTheme.colors.expense else Color.Unspecified,
            textAlign = TextAlign.Start,
        )

        if (used != null) {
            Spacer(Modifier.height(12.dp))
            Meter(
                fraction = (used.raw / 100).toFloat(),
                tone = meterToneOf(used.raw),
                label = stringResource(R.string.account_credit_used),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.account_credit_used_of,
                    used.percent,
                    MoneyFormat.full(account.creditLimit ?: com.example.expensify.core.money.Money.ZERO),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (account.archived || !account.includeInTotal) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (account.archived) TagChip(stringResource(R.string.account_chip_archived))
                if (!account.includeInTotal) TagChip(stringResource(R.string.account_chip_not_in_total))
            }
        }
    }
}

@Composable
private fun MenuItem(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}
