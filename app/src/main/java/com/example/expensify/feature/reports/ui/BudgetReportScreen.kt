package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.ymd
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.ReportTable
import com.example.expensify.designsystem.components.TableCell
import com.example.expensify.designsystem.components.TableColumn
import com.example.expensify.designsystem.components.TableFigureWithSub
import com.example.expensify.designsystem.components.TableMoneyCell
import com.example.expensify.designsystem.components.TableTextWithCaption
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.components.charts.BarChart
import com.example.expensify.designsystem.components.charts.BarGroup
import com.example.expensify.designsystem.components.charts.ChartSeries
import com.example.expensify.designsystem.components.charts.ChartTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.budgets.ui.labelRes
import com.example.expensify.feature.budgets.ui.perPeriodRes
import com.example.expensify.feature.budgets.ui.view
import com.example.expensify.feature.reports.domain.BudgetLookbacks
import com.example.expensify.feature.reports.domain.BudgetPeriodRow
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.transactions.domain.TxType

@Composable
fun BudgetReportRoute(
    onBack: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onGoToBudgets: () -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
    viewModel: BudgetReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetReportScreen(
        state = state,
        onBack = onBack,
        onReset = viewModel::reset,
        onSelectBudget = viewModel::selectBudget,
        onLookback = viewModel::setLookback,
        onEarly = viewModel::setEarly,
        onShowPaused = viewModel::setShowPaused,
        onOpenBudget = onOpenBudget,
        onGoToBudgets = onGoToBudgets,
        onOpenTransactions = onOpenTransactions,
    )
}

/** The chart's axis labels follow the budget's own periods. */
private fun granularityOf(period: BudgetPeriod): Granularity = when (period) {
    BudgetPeriod.Weekly -> Granularity.Week
    BudgetPeriod.Monthly -> Granularity.Month
    BudgetPeriod.Yearly -> Granularity.Year
}

@Composable
fun BudgetReportScreen(
    state: BudgetReportUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSelectBudget: (String?) -> Unit,
    onLookback: (Int) -> Unit,
    onEarly: (Boolean) -> Unit,
    onShowPaused: (Boolean) -> Unit,
    onOpenBudget: (String) -> Unit,
    onGoToBudgets: () -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
) {
    val filters = state.filters
    val content = state as? BudgetReportUiState.Content
    val selected = content?.selected
    val lookbackLabel = stringResource(R.string.report_last_periods, ReportLabels.periodCount(filters.lookback).asString())
    val budgetsLabel = stringResource(if (filters.showPaused) R.string.report_active_and_paused_budgets else R.string.report_active_budgets)

    ReportFrame(
        key = ReportKey.Budgets,
        filtered = filters.filtered,
        summary = stringResource(R.string.report_summary_two, selected?.budget?.name ?: budgetsLabel, lookbackLabel),
        loading = state is BudgetReportUiState.Loading,
        error = (state as? BudgetReportUiState.Error)?.message,
        onBack = onBack,
        onReset = onReset,
        filters = {
            SinglePickChip(
                title = stringResource(R.string.report_field_budget),
                options = filters.budgetItems,
                value = filters.budgetItems.firstOrNull { it.id == filters.budgetId } ?: BudgetPick(null, com.example.expensify.core.ui.plain("")),
                labelOf = { pick ->
                    when {
                        pick.id == null -> stringResource(R.string.report_all_budgets)
                        pick.paused -> stringResource(R.string.report_budget_paused_name, pick.name.asString())
                        else -> pick.name.asString()
                    }
                },
                onChange = { onSelectBudget(it.id) },
                active = filters.budgetId != null,
            )
            SinglePickChip(
                title = stringResource(R.string.report_field_look_back),
                options = BudgetLookbacks.ALL,
                value = filters.lookback,
                labelOf = { stringResource(R.string.report_last_periods, ReportLabels.periodCount(it).asString()) },
                onChange = onLookback,
                active = filters.lookback != BudgetLookbacks.DEFAULT,
            )
            if (filters.budgetId == null) {
                ToggleChip(label = stringResource(R.string.report_field_paused_budgets), checked = filters.showPaused, onChange = onShowPaused)
            }
            ToggleChip(label = stringResource(R.string.report_field_early), checked = filters.early, onChange = onEarly)
        },
    ) {
        if (content == null) return@ReportFrame

        if (selected != null) {
            selectedBudget(content, selected, onOpenBudget, onOpenTransactions)
        } else {
            allBudgets(content, onSelectBudget, onGoToBudgets)
        }
    }
}

/** "This period", "Before the budget", or nothing for a past period. */
@Composable
private fun periodNote(row: BudgetPeriodRow): String? = when {
    row.current -> stringResource(R.string.report_this_period_note)
    row.beforeBudget -> stringResource(R.string.report_before_budget_note)
    else -> null
}

private fun androidx.compose.foundation.lazy.LazyListScope.selectedBudget(
    content: BudgetReportUiState.Content,
    summary: BudgetSummaryUi,
    onOpenBudget: (String) -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
) {
    val budget = summary.budget
    val stats = summary.stats
    val openPeriod = { row: BudgetPeriodRow -> onOpenTransactions(DrillDown(row.result.range, TxType.Expense, budget.categoryIds)) }

    item(key = "head") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = IconMap.of(summary.icon.name), color = summary.icon.colorHex?.let { dataColor(it) } ?: MaterialTheme.colorScheme.primary, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = listOfNotNull(
                    stringResource(budget.period.perPeriodRes(), MoneyFormat.full(budget.amount)),
                    summary.scope.asString(),
                    if (budget.rollover) stringResource(R.string.budget_chip_rolls_over) else null,
                    if (!budget.active) stringResource(R.string.budget_chip_paused) else null,
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { onOpenBudget(budget.id) }) { Text(stringResource(R.string.report_open_budget)) }
        }
    }

    item(key = "tiles") {
        ReportTiles(
            listOf(
                ReportTile(
                    label = stringResource(R.string.report_tile_hit_rate),
                    value = ReportLabels.percentText(stats.hitRate).asString(),
                    note = stringResource(R.string.report_under_limit_in, stats.under, ReportLabels.periodCount(stats.tracked).asString()),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_average_spent),
                    value = stats.averageSpent?.let { MoneyFormat.full(it) } ?: "—",
                    note = stringResource(R.string.report_used_overall, ReportLabels.percentText(stats.usedPercent).asString()),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_overspent),
                    value = MoneyFormat.full(stats.overspent),
                    tone = if (stats.overspent.isPositive) TileTone.Expense else null,
                    note = stringResource(R.string.report_over_in, ReportLabels.periodCount(stats.over).asString()),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_this_period),
                    value = summary.current?.let { MoneyFormat.full(it.result.spent) } ?: "—",
                    note = summary.current?.let { stringResource(R.string.report_of_limit_state, MoneyFormat.full(it.result.limit), stringResource(it.result.state.view().labelRes)) },
                ),
            ),
        )
    }

    val oldestFirst = summary.rows.reversed()
    val multiYear = oldestFirst.firstOrNull()?.result?.range?.start?.year != oldestFirst.lastOrNull()?.result?.range?.end?.year
    item(key = "chart") {
        ReportCard(title = stringResource(R.string.report_spent_against_limit)) {
            BarChart(
                groups = oldestFirst.map { row ->
                    BarGroup(
                        key = row.result.range.start.ymd(),
                        label = ReportLabels.bucketLabel(row.result.range, granularityOf(budget.period), multiYear),
                        title = DateFormats.period(row.result.range),
                        values = mapOf(SERIES_LIMIT to row.result.limit, SERIES_SPENT to row.result.spent),
                        caption = if (oldestFirst.size <= 12) BudgetLabels.percent(row.result.percent).asString() else null,
                    )
                },
                series = listOf(
                    ChartSeries(SERIES_LIMIT, stringResource(R.string.report_series_limit), ChartTone.Neutral),
                    ChartSeries(SERIES_SPENT, stringResource(R.string.report_series_spent), ChartTone.Accent),
                ),
                contentDescription = stringResource(R.string.report_budget_chart_description, budget.name),
                height = 200.dp,
                selectedKey = summary.current?.result?.range?.start?.ymd(),
                onGroupClick = { group -> summary.rows.firstOrNull { it.result.range.start.ymd() == group.key }?.let(openPeriod) },
            )
        }
    }

    item(key = "periods") {
        ReportCard(title = stringResource(R.string.report_periods_title), contentPadding = PaddingValues(0.dp)) {
            ReportTable(
                columns = listOf(
                    TableColumn(
                        key = "period",
                        header = stringResource(R.string.report_col_period),
                        width = 152.dp,
                        cell = { row -> TableTextWithCaption(DateFormats.period(row.result.range), periodNote(row)) },
                    ),
                    TableColumn(
                        "limit", stringResource(R.string.report_col_limit), 128.dp, numeric = true,
                        cell = { row ->
                            TableFigureWithSub(
                                figure = { TableMoneyCell(row.result.limit) },
                                sub = if (row.result.carry.isZero) null else stringResource(R.string.report_carried, MoneyFormat.full(row.result.carry, Sign.ExceptZero)),
                            )
                        },
                    ),
                    TableColumn("spent", stringResource(R.string.report_col_spent), 120.dp, numeric = true, cell = { TableMoneyCell(it.result.spent) }),
                    TableColumn(
                        "remaining", stringResource(R.string.report_col_remaining), 120.dp, numeric = true,
                        cell = { TableMoneyCell(it.result.remaining, kind = if (it.result.remaining.isNegative) AmountKind.Expense else AmountKind.Neutral, sign = Sign.ExceptZero) },
                    ),
                    TableColumn("used", stringResource(R.string.report_col_used), 72.dp, numeric = true, cell = { TableCell(BudgetLabels.percent(it.result.percent).asString(), numeric = true) }),
                    TableColumn(
                        "state", stringResource(R.string.report_col_state), 112.dp,
                        cell = { row ->
                            val view = row.result.state.view()
                            TagChip(stringResource(view.labelRes), tone = if (row.beforeBudget) TagTone.Neutral else view.tone, icon = view.icon)
                        },
                    ),
                ),
                rows = summary.rows,
                rowKey = { it.offset },
                onRowClick = openPeriod,
                isCurrent = { it.current },
                isMuted = { it.beforeBudget },
            )
        }
    }
    item(key = "note") { ReportNote(stringResource(R.string.report_hit_rate_note)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.allBudgets(
    content: BudgetReportUiState.Content,
    onSelectBudget: (String?) -> Unit,
    onGoToBudgets: () -> Unit,
) {
    val showPaused = content.filters.showPaused
    item(key = "tiles") {
        ReportTiles(
            listOf(
                ReportTile(
                    label = stringResource(R.string.report_tile_budgets),
                    value = content.summaries.size.toString(),
                    note = stringResource(if (showPaused) R.string.report_active_and_paused else R.string.report_active),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_hit_rate),
                    value = ReportLabels.percentText(content.hitRate).asString(),
                    note = stringResource(R.string.report_under_limit_in, content.under, ReportLabels.periodCount(content.tracked).asString()),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_periods_over),
                    value = content.over.toString(),
                    note = stringResource(R.string.report_periods_over_note),
                ),
                ReportTile(
                    label = stringResource(R.string.report_tile_overspent),
                    value = MoneyFormat.full(content.overspent),
                    tone = if (content.overspent.isPositive) TileTone.Expense else null,
                    note = stringResource(R.string.report_overspent_note),
                ),
            ),
        )
    }

    if (content.isEmpty) {
        item(key = "empty") {
            AppCard {
                EmptyState(
                    icon = AppIcons.Budgets,
                    title = stringResource(if (content.allCount > 0) R.string.report_budgets_empty_paused_title else R.string.report_budgets_empty_title),
                    message = stringResource(if (content.allCount > 0) R.string.report_budgets_empty_paused_message else R.string.report_budgets_empty_message),
                    action = if (content.allCount == 0) ({ EmptyAction(stringResource(R.string.report_go_to_budgets), onGoToBudgets) }) else null,
                )
            }
        }
    } else {
        item(key = "table") {
            ReportCard(title = stringResource(R.string.report_budgets_table_title), contentPadding = PaddingValues(0.dp)) {
                ReportTable(
                    columns = listOf(
                        TableColumn(
                            key = "budget",
                            header = stringResource(R.string.report_col_budget),
                            width = 176.dp,
                            cell = { s -> BudgetCell(s) },
                            total = { TableCell(stringResource(R.string.report_all_budgets), emphasis = true) },
                        ),
                        TableColumn(
                            "limit", stringResource(R.string.report_col_limit), 128.dp, numeric = true,
                            cell = { s -> TableFigureWithSub(figure = { TableMoneyCell(s.budget.amount) }, sub = stringResource(s.budget.period.labelRes())) },
                        ),
                        TableColumn(
                            "current", stringResource(R.string.report_col_this_period), 128.dp, numeric = true,
                            cell = { s ->
                                val current = s.current
                                if (current == null) {
                                    TableCell("—", numeric = true)
                                } else {
                                    TableFigureWithSub(
                                        figure = { TableMoneyCell(current.result.spent) },
                                        sub = stringResource(R.string.report_percent_used, BudgetLabels.percent(current.result.percent).asString()),
                                    )
                                }
                            },
                        ),
                        TableColumn(
                            "under", stringResource(R.string.report_col_under_limit), 96.dp, numeric = true,
                            cell = { s -> TableCell(stringResource(R.string.report_of_count, s.stats.under, s.stats.tracked), numeric = true) },
                            total = { TableCell(stringResource(R.string.report_of_count, content.under, content.tracked), numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "rate", stringResource(R.string.report_col_hit_rate), 80.dp, numeric = true,
                            cell = { s -> TableCell(ReportLabels.percentText(s.stats.hitRate).asString(), numeric = true) },
                            total = { TableCell(ReportLabels.percentText(content.hitRate).asString(), numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "average", stringResource(R.string.report_col_avg_spent), 120.dp, numeric = true,
                            cell = { s -> s.stats.averageSpent?.let { TableMoneyCell(it) } ?: TableCell("—", numeric = true) },
                        ),
                        TableColumn(
                            "used", stringResource(R.string.report_col_used_overall), 96.dp, numeric = true,
                            cell = { s -> TableCell(ReportLabels.percentText(s.stats.usedPercent).asString(), numeric = true) },
                        ),
                        TableColumn(
                            "overspent", stringResource(R.string.report_col_overspent), 120.dp, numeric = true,
                            cell = { s -> TableMoneyCell(s.stats.overspent, kind = if (s.stats.overspent.isPositive) AmountKind.Expense else AmountKind.Neutral) },
                            total = { TableMoneyCell(content.overspent, kind = if (content.overspent.isPositive) AmountKind.Expense else AmountKind.Neutral, emphasis = true) },
                        ),
                    ),
                    rows = content.summaries,
                    rowKey = { it.budget.id },
                    onRowClick = { onSelectBudget(it.budget.id) },
                    isMuted = { !it.budget.active },
                )
            }
        }
    }
    item(key = "note") { ReportNote(stringResource(R.string.report_hit_rate_note)) }
}

private const val SERIES_LIMIT = "limit"
private const val SERIES_SPENT = "spent"

/** A budget's badge and name, with what it counts (and "Paused") under it. */
@Composable
private fun BudgetCell(summary: BudgetSummaryUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = IconMap.of(summary.icon.name), color = summary.icon.colorHex?.let { dataColor(it) } ?: MaterialTheme.colorScheme.primary, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(summary.budget.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val scope = summary.scope.asString()
            Text(
                if (summary.budget.active) scope else stringResource(R.string.report_summary_two, stringResource(R.string.budget_chip_paused), scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun BudgetReportPreview() {
    ExpensifyTheme {
        BudgetReportScreen(
            state = SampleReports.budgets,
            onBack = {}, onReset = {}, onSelectBudget = {}, onLookback = {}, onEarly = {}, onShowPaused = {}, onOpenBudget = {}, onGoToBudgets = {}, onOpenTransactions = {},
        )
    }
}
