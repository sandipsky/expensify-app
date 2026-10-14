package com.example.expensify.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.money.Money
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.DayHeader
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.EntryRow
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Meter
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.StatTile
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.meterToneOf
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules

@Composable
fun AccountDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddEntry: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AccountDetailEvent.Left -> onBack()
            }
        }
    }
    AccountDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(it.id) },
        onAddEntry = { onAddEntry(it.id) },
        onOpenEntry = onOpenEntry,
        onReconcile = viewModel::openReconcile,
        onArchive = viewModel::requestArchive,
        onRestore = viewModel::restore,
        onDelete = viewModel::requestDelete,
    )
    AccountDialogsHost(
        dialogs = dialogs,
        onConfirmArchive = viewModel::confirmArchive,
        onConfirmDelete = viewModel::confirmDelete,
        onReconcileInput = viewModel::onReconcileInput,
        onSaveReconcile = viewModel::saveReconcile,
        onDismiss = viewModel::dismissDialogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: (Account) -> Unit,
    onAddEntry: (Account) -> Unit,
    onOpenEntry: (String) -> Unit,
    onReconcile: (Account) -> Unit,
    onArchive: (Account) -> Unit,
    onRestore: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val account = (state as? AccountDetailUiState.Content)?.account
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.account_back_to_accounts))
                    }
                },
                actions = {
                    if (account != null) {
                        if (account.archived) {
                            IconButton(onClick = { onRestore(account) }) {
                                Icon(AppIcons.Restore, contentDescription = stringResource(R.string.account_restore))
                            }
                        } else {
                            IconButton(onClick = { onReconcile(account) }) {
                                Icon(AppIcons.Reconcile, contentDescription = stringResource(R.string.account_reconcile))
                            }
                            IconButton(onClick = { onEdit(account) }) {
                                Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!account.archived) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.account_archive)) },
                                        leadingIcon = { Icon(AppIcons.Archive, contentDescription = null) },
                                        onClick = { menuOpen = false; onArchive(account) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; onDelete(account) },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            // Add an entry preset to this account (CLAUDE.md, Account page).
            if (account != null && !account.archived) {
                ExtendedFloatingActionButton(
                    onClick = { onAddEntry(account) },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.tx_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            AccountDetailUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(64.dp)
                Skeleton(104.dp)
                Skeleton(240.dp)
            }
            AccountDetailUiState.NotFound -> Column(Modifier.padding(padding).padding(16.dp)) {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Accounts,
                        title = stringResource(R.string.account_not_found_title),
                        message = stringResource(R.string.account_not_found_message),
                        action = { Button(onClick = onBack) { Text(stringResource(R.string.account_back_to_accounts)) } },
                    )
                }
            }
            is AccountDetailUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is AccountDetailUiState.Content -> AccountDetailContent(state, Modifier.padding(padding), onOpenEntry)
        }
    }
}

@Composable
private fun AccountDetailContent(state: AccountDetailUiState.Content, modifier: Modifier, onOpenEntry: (String) -> Unit) {
    val account = state.account
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = IconMap.of(account.icon), color = dataColor(account.color), size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(account.type.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (account.archived) TagChip(stringResource(R.string.account_chip_archived))
                        if (!account.includeInTotal) TagChip(stringResource(R.string.account_chip_not_in_total))
                    }
                }
            }
        }

        if (account.archived) {
            item(key = "archived-notice") { Banner(stringResource(R.string.account_archived_notice), BannerTone.Warn) }
        }

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(state.balance.caption.currentLabelRes()), Modifier.weight(1f)) {
                    AmountText(
                        money = state.balance.amount,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.balance.negative) ExpensifyTheme.colors.expense else Color.Unspecified,
                        textAlign = TextAlign.Start,
                    )
                }
                StatTile(stringResource(state.openingLabelRes), Modifier.weight(1f)) {
                    AmountText(money = state.opening, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Start)
                }
            }
        }

        state.utilization?.let { used ->
            item(key = "credit") {
                StatTile(stringResource(R.string.balance_caption_credit_limit)) {
                    AmountText(
                        money = account.creditLimit ?: Money.ZERO,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(Modifier.height(12.dp))
                    Meter(
                        fraction = (used.raw / 100).toFloat(),
                        tone = meterToneOf(used.raw),
                        label = stringResource(R.string.account_credit_used),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.account_credit_percent_used, used.percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "tx-title") {
            Text(
                if (state.entryCount == 0) {
                    stringResource(R.string.account_transactions_title)
                } else {
                    stringResource(R.string.account_transactions_title) + " · " +
                        pluralStringResource(R.plurals.transactions_count, state.entryCount, state.entryCount)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.days.isEmpty()) {
            item(key = "tx-empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Transactions,
                        title = stringResource(R.string.account_transactions_empty_title),
                        message = stringResource(R.string.account_transactions_empty_message),
                    )
                }
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    DayHeader(label = day.label.asString(), net = day.net)
                    day.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        EntryRow(row, onClick = { onOpenEntry(row.id) })
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AccountDetailPreview() {
    val account = SampleAccounts.card
    ExpensifyTheme {
        AccountDetailScreen(
            state = AccountDetailUiState.Content(
                account = account,
                balance = AccountRules.balanceView(account.type, account.currentBalance),
                opening = AccountRules.flipForLiability(account.type, account.openingBalance),
                openingLabelRes = R.string.balance_caption_opening_owed,
                utilization = AccountRules.utilizationView(account),
                days = emptyList(),
                entryCount = 0,
            ),
            onBack = {}, onEdit = {}, onAddEntry = {}, onOpenEntry = {}, onReconcile = {}, onArchive = {}, onRestore = {}, onDelete = {},
        )
    }
}
