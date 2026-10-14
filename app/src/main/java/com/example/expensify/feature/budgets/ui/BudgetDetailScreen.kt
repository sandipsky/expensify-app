package com.example.expensify.feature.budgets.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.EntryRow
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Meter
import com.example.expensify.designsystem.components.MeterTone
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.budgets.domain.Budget

@Composable
fun BudgetDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: BudgetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BudgetDetailEvent.Left -> onBack()
            }
        }
    }
    BudgetDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEdit(it.id) },
        onOpenEntry = onOpenEntry,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onDelete = viewModel::requestDelete,
        onSelectPeriod = viewModel::selectPeriod,
        onShowMore = viewModel::showMore,
    )
    BudgetDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    state: BudgetDetailUiState,
    onBack: () -> Unit,
    onEdit: (Budget) -> Unit,
    onOpenEntry: (String) -> Unit,
    onPause: (Budget) -> Unit,
    onResume: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
    onSelectPeriod: (Int) -> Unit,
    onShowMore: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val budget = (state as? BudgetDetailUiState.Content)?.budget
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
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.budget_back_to_budgets))
                    }
                },
                actions = {
                    if (budget != null) {
                        IconButton(onClick = { onEdit(budget) }) {
                            Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (budget.active) {
                                    MenuItem(R.string.budget_pause, AppIcons.Pause) { menuOpen = false; onPause(budget) }
                                } else {
                                    MenuItem(R.string.budget_resume, AppIcons.Resume) { menuOpen = false; onResume(budget) }
                                }
                                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete(budget) }
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
    ) { padding ->
        when (state) {
            BudgetDetailUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(56.dp)
                Skeleton(160.dp)
                Skeleton(240.dp)
            }
            BudgetDetailUiState.NotFound -> Column(Modifier.padding(padding).padding(16.dp)) {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Budgets,
                        title = stringResource(R.string.budget_not_found_title),
                        message = stringResource(R.string.budget_not_found_message),
                        action = { Button(onClick = onBack) { Text(stringResource(R.string.budget_back_to_budgets)) } },
                    )
                }
            }
            is BudgetDetailUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is BudgetDetailUiState.Content -> BudgetDetailContent(state, Modifier.padding(padding), onSelectPeriod, onShowMore, onOpenEntry)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDetailContent(
    state: BudgetDetailUiState.Content,
    modifier: Modifier,
    onSelectPeriod: (Int) -> Unit,
    onShowMore: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val budget = state.budget
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = IconMap.of(state.icon.name), color = budgetIconColor(state.icon), size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(budget.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.budget_detail_meta, stringResource(budget.period.labelRes()), state.scope.asString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!budget.active || budget.rollover || budget.alertThresholds.isEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!budget.active) TagChip(stringResource(R.string.budget_chip_paused))
                            if (budget.rollover) TagChip(stringResource(R.string.budget_chip_rolls_over))
                            if (budget.alertThresholds.isEmpty()) TagChip(stringResource(R.string.budget_chip_no_alerts))
                        }
                    }
                }
            }
        }

        if (!budget.active) {
            item(key = "paused-notice") {
                Banner(
                    stringResource(R.string.budget_paused_notice, stringResource(budget.period.perPeriodRes(), MoneyFormat.full(budget.amount))),
                    BannerTone.Warn,
                )
            }
        }

        state.spendingError?.let { message ->
            item(key = "spending-error") { Banner(message.asString(), BannerTone.Error) }
        }

        item(key = "meter") {
            AppCard {
                if (state.progress != null) {
                    BudgetMeterView(progress = state.progress, period = budget.period, name = budget.name)
                } else {
                    Skeleton(92.dp)
                }
            }
        }

        item(key = "results-title") { SectionTitle(stringResource(R.string.budget_results_title)) }

        item(key = "results") {
            AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                if (state.periods.isEmpty()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { Skeleton(56.dp) }
                    }
                } else {
                    state.periods.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PeriodRow(row = row, selected = row.offset == state.selectedOffset, onClick = { onSelectPeriod(row.offset) })
                    }
                }
            }
        }

        if (state.periods.isNotEmpty()) {
            item(key = "tx-title") { SectionTitle(stringResource(R.string.budget_transactions_title, state.selectedLabel)) }

            if (state.rows.isEmpty()) {
                item(key = "tx-empty") {
                    AppCard {
                        EmptyState(
                            icon = AppIcons.Transactions,
                            title = stringResource(R.string.budget_nothing_spent_title),
                            message = stringResource(R.string.budget_nothing_spent_message),
                        )
                    }
                }
            } else {
                item(key = "tx-rows") {
                    AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        state.rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            EntryRow(row, onClick = { onOpenEntry(row.id) })
                        }
                    }
                }
                if (state.hiddenCount > 0) {
                    item(key = "tx-more") {
                        OutlinedButton(onClick = onShowMore, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.budget_show_more, state.hiddenCount))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** One period's result (req BUD-05): its label, spent of limit, a bar, and its state or "Before this budget started". */
@Composable
private fun PeriodRow(row: PeriodRowUi, selected: Boolean, onClick: () -> Unit) {
    val state = row.result.state.view()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.offset == 0) TagChip(stringResource(R.string.budget_chip_this_period), tone = TagTone.Info)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AmountText(
                money = row.result.spent,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.budget_spent_of, MoneyFormat.full(row.result.limit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                stringResource(R.string.budget_percent_used, BudgetLabels.percent(row.result.percent).asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Meter(
            fraction = (row.result.percent ?: 100) / 100f,
            tone = if (row.beforeBudget) MeterTone.Accent else state.meter,
            label = stringResource(R.string.budget_percent_used, row.label),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.beforeBudget) {
                Text(
                    stringResource(R.string.budget_before_started),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(state.icon, contentDescription = null, tint = stateColor(state.tone), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(state.labelRes), style = MaterialTheme.typography.bodySmall, color = stateColor(state.tone))
            }
        }
    }
}

@Composable
private fun stateColor(tone: TagTone): Color = when (tone) {
    TagTone.Success -> ExpensifyTheme.colors.income
    TagTone.Warn -> ExpensifyTheme.colors.warn
    TagTone.Error -> ExpensifyTheme.colors.expense
    TagTone.Info -> ExpensifyTheme.colors.info
    TagTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

@PreviewLightDark
@Composable
private fun BudgetDetailPreview() {
    val card = SampleBudgets.cards[1]
    val progress = SampleBudgets.foodProgress
    ExpensifyTheme {
        BudgetDetailScreen(
            state = BudgetDetailUiState.Content(
                budget = card.budget,
                scope = card.scope,
                icon = card.icon,
                progress = progress,
                periods = listOf(
                    PeriodRowUi(0, "October 2026", progress.result, beforeBudget = false),
                    PeriodRowUi(-1, "September 2026", progress.result.copy(spent = com.example.expensify.core.money.Money.rupees(15_600), remaining = com.example.expensify.core.money.Money.rupees(-600), percent = 104, state = com.example.expensify.feature.budgets.domain.BudgetState.Over), beforeBudget = false),
                    PeriodRowUi(-2, "August 2026", progress.result.copy(spent = com.example.expensify.core.money.Money.rupees(9_000), percent = 60, state = com.example.expensify.feature.budgets.domain.BudgetState.OnTrack), beforeBudget = true),
                ),
                selectedOffset = 0,
                selectedLabel = "October 2026",
                rows = emptyList(),
                hiddenCount = 0,
                spendingError = null,
            ),
            onBack = {}, onEdit = {}, onOpenEntry = {}, onPause = {}, onResume = {}, onDelete = {}, onSelectPeriod = {}, onShowMore = {},
        )
    }
}

@Suppress("unused")
private val previewHeight = Modifier.height(0.dp)
