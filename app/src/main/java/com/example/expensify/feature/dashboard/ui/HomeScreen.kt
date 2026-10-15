package com.example.expensify.feature.dashboard.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.ymd
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.AppDatePickerDialog
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.EntryRow
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Meter
import com.example.expensify.designsystem.components.ReportTable
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TableCell
import com.example.expensify.designsystem.components.TableColumn
import com.example.expensify.designsystem.components.TableMoneyCell
import com.example.expensify.designsystem.components.TableTextWithCaption
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.charts.BarChart
import com.example.expensify.designsystem.components.charts.BarGroup
import com.example.expensify.designsystem.components.charts.ChartSeries
import com.example.expensify.designsystem.components.charts.ChartTone
import com.example.expensify.designsystem.components.charts.DonutChart
import com.example.expensify.designsystem.components.charts.DonutSegment
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.accounts.ui.labelRes
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.budgets.ui.budgetIconColor
import com.example.expensify.feature.budgets.ui.view
import com.example.expensify.feature.dashboard.domain.Change
import com.example.expensify.feature.dashboard.domain.DashboardPreset
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.dashboard.domain.PeriodSummary
import com.example.expensify.feature.recurring.ui.RuleBadge
import com.example.expensify.feature.recurring.ui.RuleView
import com.example.expensify.feature.reports.ui.DrillDown
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.TransactionDialogsHost
import java.time.LocalDate
import java.time.LocalTime

/** Everything the dashboard can do, in one bundle so the screen and its previews stay short. */
@Immutable
data class HomeActions(
    val onTogglePrivacy: () -> Unit = {},
    val onOpenAlerts: () -> Unit = {},
    val onPreset: (DashboardPreset) -> Unit = {},
    val onCustomStart: (LocalDate) -> Unit = {},
    val onCustomEnd: (LocalDate) -> Unit = {},
    val onSelectPeriod: (DateRange) -> Unit = {},
    val onOpenTransactions: (DrillDown) -> Unit = {},
    val onAddEntry: () -> Unit = {},
    val onOpenEntry: (String) -> Unit = {},
    val onEditEntry: (String) -> Unit = {},
    val onDeleteEntry: (Transaction) -> Unit = {},
    val onOpenAccounts: () -> Unit = {},
    val onOpenAccount: (String) -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onOpenBudgets: () -> Unit = {},
    val onOpenBudget: (String) -> Unit = {},
    val onAddBudget: () -> Unit = {},
    val onOpenRecurring: () -> Unit = {},
    val onOpenRule: (String) -> Unit = {},
    val onConfirm: (RuleView, LocalDate) -> Unit = { _, _ -> },
    val onSkip: (RuleView, LocalDate) -> Unit = { _, _ -> },
)

@Composable
fun HomeRoute(
    onOpenTransactions: (DrillDown) -> Unit,
    onOpenEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onAddBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenRule: (String) -> Unit,
    onOpenAlerts: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteDialogs by viewModel.deleteDialogs.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions = HomeActions(
            onTogglePrivacy = viewModel::togglePrivacy,
            onOpenAlerts = onOpenAlerts,
            onPreset = viewModel::setPreset,
            onCustomStart = viewModel::setCustomStart,
            onCustomEnd = viewModel::setCustomEnd,
            onSelectPeriod = viewModel::selectPeriod,
            onOpenTransactions = onOpenTransactions,
            onAddEntry = onAddEntry,
            onOpenEntry = onOpenEntry,
            onEditEntry = onEditEntry,
            onDeleteEntry = viewModel::requestDeleteEntry,
            onOpenAccounts = onOpenAccounts,
            onOpenAccount = onOpenAccount,
            onAddAccount = onAddAccount,
            onOpenBudgets = onOpenBudgets,
            onOpenBudget = onOpenBudget,
            onAddBudget = onAddBudget,
            onOpenRecurring = onOpenRecurring,
            onOpenRule = onOpenRule,
            onConfirm = viewModel::confirm,
            onSkip = viewModel::skip,
        ),
    )
    TransactionDialogsHost(deleteDialogs, onConfirmDelete = viewModel::confirmDeleteEntry, onDismiss = viewModel::dismissDeleteEntry)
}

/**
 * The dashboard (req DSH-01…DSH-13): a greeting with the privacy eye and
 * the alerts bell, the period switcher pinned on scroll, then the cards in
 * one column. A period with no entries shows one empty state in place of
 * the period cards, while balances, budgets and upcoming stay. Privacy mode
 * masks every amount. Skeletons show while a period loads for the first time.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    val header = state.header
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAddEntry,
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tx_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HeaderRow(header, actions) }
            stickyHeader(key = "period") {
                PeriodBar(header.period, onPreset = actions.onPreset, onPickStart = { picking = "start" }, onPickEnd = { picking = "end" })
            }
            when (state) {
                is HomeUiState.Loading -> card("loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Skeleton(88.dp)
                        Skeleton(240.dp)
                        Skeleton(240.dp)
                        Skeleton(240.dp)
                    }
                }
                is HomeUiState.Error -> card("error") { AppCard { ErrorState(state.message.asString()) } }
                is HomeUiState.Content -> content(state, actions)
            }
        }
    }

    when (picking) {
        "start" -> AppDatePickerDialog(initial = header.period.custom.start, onPick = actions.onCustomStart, onDismiss = { picking = null })
        "end" -> AppDatePickerDialog(initial = header.period.custom.end, onPick = actions.onCustomEnd, onDismiss = { picking = null })
    }
}

/** A card in the column, inside the page gutters; the pinned period bar spans the full width above them. */
private fun LazyListScope.card(key: String, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.padding(horizontal = 16.dp)) { content() } }
}

/** The cards, in the web's order, minus the period cards when the period is empty (req DSH-12). */
private fun LazyListScope.content(state: HomeUiState.Content, actions: HomeActions) {
    val masked = state.header.masked
    val range = state.header.period.range
    if (state.empty) {
        card("empty") {
            AppCard {
                EmptyState(
                    icon = AppIcons.Transactions,
                    title = stringResource(R.string.dashboard_empty_title, state.header.period.label),
                    message = stringResource(R.string.dashboard_empty_message),
                    action = { Button(onClick = actions.onAddEntry) { Text(stringResource(R.string.tx_add)) } },
                )
            }
        }
    } else {
        card("summary") { SummaryTiles(state.summary, range, masked, actions.onOpenTransactions) }
    }
    card("balances") { BalancesCard(state.balances, masked, actions) }
    if (!state.empty) card("spending") { SpendingCard(state.spending, state.header.period, masked, actions) }
    card("budgets") { BudgetsCard(state.budgets, masked, actions) }
    if (!state.empty) {
        card("trend") { TrendCard(state.trend, state.trendEmpty, range, masked, actions) }
        card("recent") { RecentCard(state, masked, actions) }
    }
    if (state.upcoming.isNotEmpty()) card("upcoming") { UpcomingCard(state.upcoming, state.today, masked, actions) }
}

// Header and period

@Composable
private fun HeaderRow(header: HomeHeaderUi, actions: HomeActions) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> R.string.home_greeting_morning
        hour < 17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    val bellLabel = if (header.unread > 0) {
        stringResource(R.string.dashboard_alerts_bell_new, header.unread)
    } else {
        stringResource(R.string.dashboard_alerts_bell)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(greeting, header.name).trimEnd(',', ' '),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = actions.onTogglePrivacy) {
            Icon(
                if (header.masked) AppIcons.Hidden else AppIcons.Visible,
                contentDescription = stringResource(if (header.masked) R.string.dashboard_show_amounts else R.string.dashboard_hide_amounts),
            )
        }
        IconButton(onClick = actions.onOpenAlerts, modifier = Modifier.semantics { contentDescription = bellLabel }) {
            BadgedBox(
                badge = {
                    if (header.unread > 0) Badge { Text(if (header.unread > 9) "9+" else header.unread.toString()) }
                },
            ) {
                Icon(AppIcons.Notifications, contentDescription = null)
            }
        }
    }
}

/** The period chips, its label and, for Custom, the two dates (req DSH-01). Pinned to the top while scrolling (req DSH-13). */
@Composable
private fun PeriodBar(period: DashboardPeriodUi, onPreset: (DashboardPreset) -> Unit, onPickStart: () -> Unit, onPickEnd: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DashboardPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = period.preset == preset,
                        onClick = { onPreset(preset) },
                        label = { Text(stringResource(DashboardLabels.presetRes(preset))) },
                    )
                }
            }
            Text(period.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (period.preset == DashboardPreset.Custom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChip(stringResource(R.string.tx_custom_from, DateFormats.shortDate(period.custom.start)), onPickStart)
                    DateChip(stringResource(R.string.tx_custom_to, DateFormats.shortDate(period.custom.end)), onPickEnd)
                }
            }
        }
    }
}

@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(AppIcons.Calendar, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

// Summary (DSH-02, DSH-03, DSH-04)

/** How a figure moved: an up or down arrow beside the text, never color (req NFR-06). */
private data class ChangeView(val direction: Int, val text: UiText)

private fun arrow(delta: Long, text: UiText) = ChangeView(delta.compareTo(0), text)

/** "+Rs 500.00 (+25%)", or the amount alone when there was nothing before. */
private fun amountChange(change: Change, masked: Boolean): ChangeView {
    val amount = DashboardLabels.money(change.amount, masked, Sign.ExceptZero)
    val text: UiText = if (change.percent == null) UiText.Plain(amount) else UiText.Res(R.string.dashboard_change_with_percent, listOf(amount, DashboardLabels.changeText(change.percent)))
    return arrow(change.amount.paisa, text)
}

@Composable
private fun SummaryTiles(summary: SummaryUi, range: DateRange, masked: Boolean, onOpenTransactions: (DrillDown) -> Unit) {
    val figures = summary.summary
    val change = summary.change
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(
                label = stringResource(R.string.tx_total_income),
                icon = AppIcons.IncomeArrow,
                iconTint = ExpensifyTheme.colors.income,
                change = change?.let { amountChange(it.income, masked) },
                onClick = { onOpenTransactions(DrillDown(range, type = TxType.Income)) },
                modifier = Modifier.weight(1f),
            ) { AmountText(figures.income, kind = AmountKind.Income, sign = Sign.ExceptZero, style = MaterialTheme.typography.titleLarge, masked = masked, textAlign = TextAlign.Start) }
            SummaryTile(
                label = stringResource(R.string.tx_total_expense),
                icon = AppIcons.ExpenseArrow,
                iconTint = ExpensifyTheme.colors.expense,
                change = change?.let { amountChange(it.expense, masked) },
                onClick = { onOpenTransactions(DrillDown(range, type = TxType.Expense)) },
                modifier = Modifier.weight(1f),
            ) { AmountText(-figures.expense, kind = AmountKind.Expense, sign = Sign.ExceptZero, style = MaterialTheme.typography.titleLarge, masked = masked, textAlign = TextAlign.Start) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(
                label = stringResource(R.string.tx_total_net),
                change = change?.let { amountChange(it.net, masked) },
                onClick = { onOpenTransactions(DrillDown(range)) },
                modifier = Modifier.weight(1f),
            ) { AmountText(figures.net, kind = netKind(figures.net), sign = Sign.ExceptZero, style = MaterialTheme.typography.titleLarge, masked = masked, textAlign = TextAlign.Start) }
            SummaryTile(
                label = stringResource(R.string.report_tile_savings_rate),
                change = change?.savingsRate?.let { arrow(it.toLong(), DashboardLabels.pointsText(it)) },
                onClick = { onOpenTransactions(DrillDown(range)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    DashboardLabels.percentText(figures.savingsRate).asString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The tone a net amount reads in: green above zero, red below (req NFR-06). */
private fun netKind(amount: Money): AmountKind = when {
    amount.isPositive -> AmountKind.Income
    amount.isNegative -> AmountKind.Expense
    else -> AmountKind.Neutral
}

@Composable
private fun SummaryTile(
    label: String,
    change: ChangeView?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    value: @Composable () -> Unit,
) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        value()
        if (change != null) {
            Spacer(Modifier.height(4.dp))
            val spoken = stringResource(
                when {
                    change.direction > 0 -> R.string.dashboard_change_up
                    change.direction < 0 -> R.string.dashboard_change_down
                    else -> R.string.dashboard_change_same
                },
            )
            val text = change.text.asString()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.semantics { contentDescription = "$text $spoken" },
            ) {
                if (change.direction != 0) {
                    Icon(
                        if (change.direction > 0) AppIcons.ArrowUp else AppIcons.ArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Cards

/** A card's title line, with "See all" or another action at the end. */
@Composable
private fun CardHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
private fun SeeAll(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.dashboard_see_all)) }
}

/** "Table" or "List": the chart's other view (req DSH-06, DSH-08). */
@Composable
private fun TableToggle(table: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Icon(if (table) AppIcons.ListView else AppIcons.Table, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (table) R.string.dashboard_show_list else R.string.dashboard_show_table))
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

// Balances (DSH-05)

@Composable
private fun BalancesCard(balances: BalancesUi, masked: Boolean, actions: HomeActions) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(stringResource(R.string.dashboard_balances), action = if (balances.accounts.isNotEmpty()) ({ SeeAll(actions.onOpenAccounts) }) else null)
        if (balances.accounts.isEmpty()) {
            EmptyState(
                icon = AppIcons.Accounts,
                title = stringResource(if (balances.anyAccounts) R.string.accounts_all_archived_title else R.string.accounts_empty_title),
                message = stringResource(R.string.accounts_empty_message),
                action = { Button(onClick = actions.onAddAccount) { Text(stringResource(R.string.dashboard_add_account)) } },
            )
            return@AppCard
        }
        val across = pluralStringResource(R.plurals.accounts_total_across, balances.includedCount, balances.includedCount)
        val note = if (balances.excludedCount > 0) stringResource(R.string.accounts_total_excluded, across, balances.excludedCount) else across
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = actions.onOpenAccounts)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.accounts_total_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(
                money = balances.total,
                style = MaterialTheme.typography.headlineMedium,
                color = if (balances.total.isNegative) ExpensifyTheme.colors.expense else Color.Unspecified,
                masked = masked,
                textAlign = TextAlign.Start,
            )
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        balances.accounts.forEachIndexed { index, account ->
            if (index > 0) RowDivider()
            val view = AccountRules.balanceView(account.type, account.currentBalance)
            val type = stringResource(account.type.labelRes())
            val caption = if (view.caption == AccountRules.BalanceCaption.Balance) type else stringResource(R.string.join_dot, type, stringResource(view.caption.labelRes()))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpenAccount(account.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = IconMap.of(account.icon), color = dataColor(account.color), size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                AmountText(
                    money = view.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (view.negative) ExpensifyTheme.colors.expense else Color.Unspecified,
                    masked = masked,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// Spending by category (DSH-06)

@Composable
private fun SpendingCard(spending: SpendingUi, period: DashboardPeriodUi, masked: Boolean, actions: HomeActions) {
    var table by rememberSaveable { mutableStateOf(false) }
    val neutral = ExpensifyTheme.colors.transfer
    val open = { row: SpendingRowUi -> if (row.opens) actions.onOpenTransactions(DrillDown(period.range, type = TxType.Expense, categoryIds = row.categoryIds)) }

    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(stringResource(R.string.dashboard_spending_title), action = if (spending.rows.isNotEmpty()) ({ TableToggle(table) { table = !table } }) else null)
        if (spending.rows.isEmpty()) {
            EmptyState(
                icon = AppIcons.Budgets,
                title = stringResource(R.string.dashboard_spending_empty_title),
                message = stringResource(R.string.dashboard_spending_empty_message),
                action = { Button(onClick = actions.onAddEntry) { Text(stringResource(R.string.dashboard_add_expense)) } },
            )
            return@AppCard
        }
        val segments = spending.rows.map { row ->
            DonutSegment(key = row.key, label = row.name.asString(), value = row.amount, color = row.colorHex?.let { dataColor(it) } ?: neutral)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DonutChart(
                segments = segments,
                contentDescription = stringResource(R.string.dashboard_spending_description, period.label),
                format = { DashboardLabels.money(it, masked) },
                onSegmentClick = { segment -> spending.rows.firstOrNull { it.key == segment.key }?.let(open) },
                legend = false,
                center = {
                    Text(DashboardLabels.money(spending.total, masked), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                    Text(DashboardLabels.entries(spending.count).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
        if (table) {
            ReportTable(
                columns = listOf(
                    TableColumn("category", stringResource(R.string.report_col_category), 150.dp, cell = { SpendingName(it, neutral) }),
                    TableColumn("amount", stringResource(R.string.dashboard_col_amount), 120.dp, numeric = true, cell = { TableMoneyCell(it.amount, masked = masked) }),
                    TableColumn("share", stringResource(R.string.report_col_share), 70.dp, numeric = true, cell = { TableCell(DashboardLabels.percentText(it.share).asString(), numeric = true) }),
                    TableColumn("entries", stringResource(R.string.report_col_entries), 70.dp, numeric = true, cell = { TableCell(it.count.toString(), numeric = true) }),
                ),
                rows = spending.rows,
                rowKey = { it.key },
                onRowClick = open,
                canOpen = { it.opens },
            )
        } else {
            spending.rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                SpendingRow(row, neutral, masked, onClick = if (row.opens) ({ open(row) }) else null)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpendingName(row: SpendingRowUi, neutral: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = IconMap.of(row.icon), color = row.colorHex?.let { dataColor(it) } ?: neutral, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(row.name.asString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "3 transactions", or "4 categories · 9 transactions" for Other. */
@Composable
private fun spendingCaption(row: SpendingRowUi): String {
    val count = DashboardLabels.entries(row.count).asString()
    return if (row.categoryIds.size > 1) stringResource(R.string.join_dot, DashboardLabels.categoryCount(row.categoryIds.size).asString(), count) else count
}

@Composable
private fun SpendingRow(row: SpendingRowUi, neutral: Color, masked: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = IconMap.of(row.icon), color = row.colorHex?.let { dataColor(it) } ?: neutral, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name.asString(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(spendingCaption(row), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(row.amount, style = MaterialTheme.typography.titleMedium, masked = masked)
            Text(
                DashboardLabels.percentText(row.share).asString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Budgets (DSH-07)

@Composable
private fun BudgetsCard(budgets: BudgetsUi, masked: Boolean, actions: HomeActions) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(stringResource(R.string.budgets_title), action = if (budgets.more) ({ SeeAll(actions.onOpenBudgets) }) else null)
        if (budgets.rows.isEmpty()) {
            EmptyState(
                icon = AppIcons.Budgets,
                title = stringResource(if (budgets.anyBudgets) R.string.budgets_all_paused_title else R.string.budgets_empty_title),
                message = stringResource(R.string.budgets_empty_message),
                action = { Button(onClick = actions.onAddBudget) { Text(stringResource(R.string.dashboard_set_budget)) } },
            )
            return@AppCard
        }
        budgets.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            BudgetRow(row, masked, onOpen = { actions.onOpenBudget(row.budget.id) })
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BudgetRow(row: BudgetRowUi, masked: Boolean, onOpen: () -> Unit) {
    val progress = row.progress
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = IconMap.of(row.icon.name), color = budgetIconColor(row.icon), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(row.budget.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(progress.result.spent, style = MaterialTheme.typography.titleMedium, masked = masked)
                    Text(
                        stringResource(R.string.budget_spent_of, DashboardLabels.money(progress.result.limit, masked)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (progress == null) {
            Skeleton(44.dp)
            return@Column
        }
        val result = progress.result
        val state = result.state.view()
        // A zero limit has nothing left, so its bar reads full.
        Meter(fraction = (result.percent ?: 100) / 100f, tone = state.meter, label = stringResource(R.string.budget_meter_label, row.budget.name))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip(stringResource(state.labelRes), tone = state.tone, icon = state.icon)
            Text(BudgetLabels.percent(result.percent).asString(), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    !progress.safePerDay.isPositive -> stringResource(R.string.budget_per_day_none)
                    progress.daysLeft == 1 -> stringResource(R.string.budget_per_day_today, DashboardLabels.money(progress.safePerDay, masked))
                    else -> stringResource(R.string.budget_per_day, DashboardLabels.money(progress.safePerDay, masked))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Income vs expense (DSH-08)

@Composable
private fun TrendCard(trend: List<PeriodSummary>, trendEmpty: Boolean, range: DateRange, masked: Boolean, actions: HomeActions) {
    var table by rememberSaveable { mutableStateOf(false) }
    val incomeLabel = stringResource(R.string.report_col_income)
    val expenseLabel = stringResource(R.string.report_col_expense)
    val series = remember(incomeLabel, expenseLabel) {
        listOf(ChartSeries("income", incomeLabel, ChartTone.Income), ChartSeries("expense", expenseLabel, ChartTone.Expense))
    }
    val select = { key: String -> trend.firstOrNull { it.range.start.ymd() == key }?.let { actions.onSelectPeriod(it.range) } ?: Unit }

    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(stringResource(R.string.report_income_vs_expense), action = if (!trendEmpty) ({ TableToggle(table) { table = !table } }) else null)
        when {
            trendEmpty -> EmptyState(
                icon = AppIcons.Reports,
                title = stringResource(R.string.dashboard_trend_empty_title),
                message = stringResource(R.string.dashboard_trend_empty_message),
                action = { Button(onClick = actions.onAddEntry) { Text(stringResource(R.string.tx_add)) } },
            )
            table -> ReportTable(
                columns = listOf(
                    TableColumn("period", stringResource(R.string.report_col_period), 120.dp, cell = { TableTextWithCaption(DashboardLabels.shortPeriodLabel(it.range), DateFormats.period(it.range)) }),
                    TableColumn("income", incomeLabel, 120.dp, numeric = true, cell = { TableMoneyCell(it.income, kind = AmountKind.Income, sign = Sign.ExceptZero, masked = masked) }),
                    TableColumn("expense", expenseLabel, 120.dp, numeric = true, cell = { TableMoneyCell(-it.expense, kind = AmountKind.Expense, sign = Sign.ExceptZero, masked = masked) }),
                    TableColumn("net", stringResource(R.string.report_col_net), 120.dp, numeric = true, cell = { TableMoneyCell(it.net, kind = netKind(it.net), sign = Sign.ExceptZero, masked = masked) }),
                ),
                rows = trend,
                rowKey = { it.range.start.ymd() },
                onRowClick = { actions.onSelectPeriod(it.range) },
                isCurrent = { it.range == range },
            )
            else -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BarChart(
                    groups = trend.map { s ->
                        BarGroup(
                            key = s.range.start.ymd(),
                            label = DashboardLabels.shortPeriodLabel(s.range),
                            title = DateFormats.period(s.range),
                            values = mapOf("income" to s.income, "expense" to s.expense),
                            caption = DashboardLabels.compact(s.net, masked, Sign.ExceptZero),
                        )
                    },
                    series = series,
                    contentDescription = stringResource(R.string.dashboard_trend_description, DashboardRules.TREND_PERIODS),
                    height = 180.dp,
                    format = { DashboardLabels.money(it, masked) },
                    tickFormat = { DashboardLabels.compact(it, masked) },
                    selectedKey = range.start.ymd(),
                    onGroupClick = { select(it.key) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// Recent transactions (DSH-09)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(state: HomeUiState.Content, masked: Boolean, actions: HomeActions) {
    val range = state.header.period.range
    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(
            stringResource(R.string.dashboard_recent_title),
            action = if (state.recent.isNotEmpty()) ({ SeeAll { actions.onOpenTransactions(DrillDown(range)) } }) else null,
        )
        if (state.recent.isEmpty()) {
            EmptyState(
                icon = AppIcons.Transactions,
                title = stringResource(R.string.dashboard_recent_empty_title),
                message = stringResource(R.string.dashboard_recent_empty_message),
                action = { Button(onClick = actions.onAddEntry) { Text(stringResource(R.string.dashboard_add_first_expense)) } },
            )
            return@AppCard
        }
        state.recent.forEachIndexed { index, row ->
            if (index > 0) RowDivider()
            var menuOpen by remember(row.id) { mutableStateOf(false) }
            Box {
                EntryRow(
                    row = row,
                    masked = masked,
                    modifier = Modifier.combinedClickable(
                        onClick = { actions.onOpenEntry(row.id) },
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = stringResource(R.string.dashboard_entry_actions, row.title.asString()),
                    ),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onEditEntry(row.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            state.recentTxs[row.id]?.let(actions.onDeleteEntry)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// Upcoming (DSH-10)

@Composable
private fun UpcomingCard(upcoming: List<UpcomingRowUi>, today: LocalDate, masked: Boolean, actions: HomeActions) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        CardHeader(stringResource(R.string.dashboard_upcoming_title), action = { SeeAll(actions.onOpenRecurring) })
        upcoming.forEachIndexed { index, row ->
            if (index > 0) RowDivider()
            val view = row.view
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { actions.onOpenRule(view.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuleBadge(view, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(view.name.asString(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.join_dot, DashboardLabels.dueLabel(row.date, today).asString(), view.accountLabel.asString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AmountText(
                        money = view.amount,
                        kind = view.kind,
                        sign = if (view.kind == AmountKind.Transfer) Sign.Never else Sign.ExceptZero,
                        style = MaterialTheme.typography.titleMedium,
                        masked = masked,
                    )
                }
                if (row.due && view.asksFirst) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { actions.onConfirm(view, row.date) }) {
                            Icon(AppIcons.Check, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.rule_confirm))
                        }
                        OutlinedButton(onClick = { actions.onSkip(view, row.date) }) { Text(stringResource(R.string.rule_skip)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@PreviewLightDark
@Composable
private fun HomePreview() {
    ExpensifyTheme { HomeScreen(SampleDashboard.content, HomeActions()) }
}

@PreviewLightDark
@Composable
private fun HomeMaskedPreview() {
    ExpensifyTheme { HomeScreen(SampleDashboard.content.copy(header = SampleDashboard.header.copy(masked = true)), HomeActions()) }
}

@PreviewLightDark
@Composable
private fun HomeEmptyPeriodPreview() {
    ExpensifyTheme { HomeScreen(SampleDashboard.emptyPeriod, HomeActions()) }
}

@PreviewLightDark
@Composable
private fun HomeLoadingPreview() {
    ExpensifyTheme { HomeScreen(SampleDashboard.loading, HomeActions()) }
}
