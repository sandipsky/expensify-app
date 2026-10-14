package com.example.expensify.feature.transactions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.budgets.ui.MenuItem
import com.example.expensify.feature.transactions.domain.Transaction

@Composable
fun TransactionDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onMakeRecurring: (String) -> Unit,
    onOpenRule: (String) -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TransactionDetailEvent.Left -> onBack()
            }
        }
    }
    TransactionDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(it.id) },
        onDuplicate = { onDuplicate(it.id) },
        onMakeRecurring = { onMakeRecurring(it.id) },
        onOpenRule = onOpenRule,
        onDelete = viewModel::requestDelete,
    )
    TransactionDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

/** One entry in full (req TXN-11): the amount, its chips, the details and the note, with every action. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDuplicate: (Transaction) -> Unit,
    onMakeRecurring: (Transaction) -> Unit,
    onOpenRule: (String) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    val content = state as? TransactionDetailUiState.Content
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.tx_back_to_transactions))
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = { onEdit(content.tx) }) {
                            Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuItem(R.string.tx_duplicate, AppIcons.Duplicate) { menuOpen = false; onDuplicate(content.tx) }
                                val ruleId = content.ruleId
                                if (ruleId != null) {
                                    MenuItem(R.string.tx_open_rule, AppIcons.Recurring) { menuOpen = false; onOpenRule(ruleId) }
                                } else {
                                    MenuItem(R.string.tx_make_recurring, AppIcons.Recurring) { menuOpen = false; onMakeRecurring(content.tx) }
                                }
                                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete(content.tx) }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (state) {
            TransactionDetailUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(56.dp)
                Skeleton(120.dp)
                Skeleton(280.dp)
            }
            TransactionDetailUiState.NotFound -> Column(Modifier.padding(padding).padding(16.dp)) {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Transactions,
                        title = stringResource(R.string.tx_not_found_title),
                        message = stringResource(R.string.tx_not_found_message),
                        action = { Button(onClick = onBack) { Text(stringResource(R.string.tx_back_to_transactions)) } },
                    )
                }
            }
            is TransactionDetailUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is TransactionDetailUiState.Content -> DetailContent(state, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(state: TransactionDetailUiState.Content, modifier: Modifier) {
    val row = state.row
    val color = if (row.colorHex == null) ExpensifyTheme.colors.transfer else dataColor(row.colorHex)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = IconMap.of(row.iconName), color = color, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title.asString(), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TagChip(stringResource(state.tx.type.labelRes()), tone = state.tx.type.chipTone())
                    if (state.upcoming) TagChip(stringResource(R.string.tx_upcoming), tone = TagTone.Info)
                    if (state.ruleId != null) TagChip(stringResource(R.string.tx_chip_recurring), icon = AppIcons.Recurring)
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
            Text(
                stringResource(state.captionRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AmountText(
                money = row.amount,
                kind = row.kind,
                sign = if (row.kind == AmountKind.Transfer) Sign.Never else Sign.ExceptZero,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.accountLabel.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppCard {
            Text(stringResource(R.string.tx_detail_details), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            state.details.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.label.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(item.value.asString(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.tx_form_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.note ?: stringResource(R.string.tx_detail_no_note),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TransactionDetailPreview() {
    val tx = SampleTransactions.lunch
    ExpensifyTheme {
        TransactionDetailScreen(
            state = TransactionDetailUiState.Content(
                tx = tx,
                row = TxRows.toRow(tx, SampleTransactions.lookups),
                captionRes = R.string.tx_detail_spent,
                accountLabel = plain("Wallet cash"),
                dateLabel = "Wed, 14 Oct 2026",
                details = listOf(
                    DetailItem(plain("Type"), plain("Expense")),
                    DetailItem(plain("Category"), plain("Food and dining")),
                    DetailItem(plain("Account"), plain("Wallet cash")),
                    DetailItem(plain("Date"), plain("Wed, 14 Oct 2026")),
                    DetailItem(plain("Added"), plain("14 Oct 2026, 12:55")),
                ),
                note = tx.note,
                upcoming = false,
                ruleId = null,
            ),
            onBack = {}, onEdit = {}, onDuplicate = {}, onMakeRecurring = {}, onOpenRule = {}, onDelete = {},
        )
    }
}
