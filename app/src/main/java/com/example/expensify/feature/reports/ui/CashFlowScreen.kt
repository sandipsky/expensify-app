package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.ymd
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ReportTable
import com.example.expensify.designsystem.components.TableCell
import com.example.expensify.designsystem.components.TableColumn
import com.example.expensify.designsystem.components.TableMoneyCell
import com.example.expensify.designsystem.components.charts.BarChart
import com.example.expensify.designsystem.components.charts.BarGroup
import com.example.expensify.designsystem.components.charts.ChartSeries
import com.example.expensify.designsystem.components.charts.ChartTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.reports.domain.FlowRow
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.domain.ReportPreset
import java.time.LocalDate

@Composable
fun CashFlowRoute(onBack: () -> Unit, onOpenTransactions: (DrillDown) -> Unit, viewModel: CashFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CashFlowScreen(
        state = state,
        onBack = onBack,
        onReset = viewModel::reset,
        onPreset = viewModel::setPreset,
        onCustom = viewModel::setCustom,
        onBy = viewModel::setBy,
        onAccounts = viewModel::setAccounts,
        onOpenTransactions = onOpenTransactions,
    )
}

/** The tone a net amount reads in: green above zero, red below (req NFR-06). */
internal fun netKind(amount: Money): AmountKind = when {
    amount.isPositive -> AmountKind.Income
    amount.isNegative -> AmountKind.Expense
    else -> AmountKind.Neutral
}

internal fun netTone(amount: Money): TileTone? = when {
    amount.isPositive -> TileTone.Income
    amount.isNegative -> TileTone.Expense
    else -> null
}

@Composable
fun CashFlowScreen(
    state: CashFlowUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onBy: (Granularity) -> Unit,
    onAccounts: (List<String>) -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
) {
    val filters = state.filters
    val periodLabel = DateFormats.period(filters.period.range)
    val byLabel = stringResource(ReportLabels.granularityRes(filters.by))

    ReportFrame(
        key = ReportKey.CashFlow,
        filtered = filters.filtered,
        summary = stringResource(R.string.report_summary_three, periodLabel, stringResource(groupTitleRes(filters.by)), filters.accountsText.asString()),
        loading = state is CashFlowUiState.Loading,
        error = (state as? CashFlowUiState.Error)?.message,
        onBack = onBack,
        onReset = onReset,
        filters = {
            PeriodChip(state = filters.period, onPreset = onPreset, onCustom = onCustom)
            SegmentedChoice(options = Granularity.entries, value = filters.by, labelOf = { stringResource(ReportLabels.granularityRes(it)) }, onChange = onBy)
            MultiPickChip(
                title = stringResource(R.string.report_field_accounts),
                placeholder = stringResource(R.string.report_all_accounts),
                items = filters.accountItems,
                selected = filters.accountIds,
                onChange = onAccounts,
                countLabel = { pluralStringResource(R.plurals.report_accounts_count, it, it) },
            )
        },
    ) {
        if (state !is CashFlowUiState.Content) return@ReportFrame
        val rows = state.rows
        val periods = rows.size
        val drill = { row: FlowRow -> onOpenTransactions(DrillDown(row.range, accountIds = filters.accountIds)) }

        item(key = "tiles") {
            ReportTiles(
                listOf(
                    ReportTile(
                        label = stringResource(R.string.report_tile_income),
                        value = MoneyFormat.full(state.income, Sign.ExceptZero),
                        tone = if (state.income.isPositive) TileTone.Income else null,
                        note = stringResource(ReportLabels.averagePerRes(filters.by), MoneyFormat.full(averageOver(state.income, periods))),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_expense),
                        value = MoneyFormat.full(-state.expense, Sign.ExceptZero),
                        tone = if (state.expense.isPositive) TileTone.Expense else null,
                        note = stringResource(ReportLabels.averagePerRes(filters.by), MoneyFormat.full(averageOver(state.expense, periods))),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_net),
                        value = MoneyFormat.full(state.net, Sign.ExceptZero),
                        tone = netTone(state.net),
                        note = stringResource(R.string.report_saved_in, state.positive, ReportLabels.unitOf(filters.by, periods).asString()),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_savings_rate),
                        value = ReportLabels.percentText(state.savingsRate).asString(),
                        note = stringResource(R.string.report_net_share_of_income),
                    ),
                ),
            )
        }

        if (state.isEmpty) {
            item(key = "empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Reports,
                        title = stringResource(R.string.report_cash_flow_empty_title, periodLabel),
                        message = stringResource(R.string.report_cash_flow_empty_message),
                    )
                }
            }
            return@ReportFrame
        }

        item(key = "chart") {
            ReportCard(title = stringResource(R.string.report_income_vs_expense)) {
                BarChart(
                    groups = rows.map { row ->
                        BarGroup(
                            key = row.range.start.ymd(),
                            label = ReportLabels.bucketLabel(row.range, filters.by, state.multiYear),
                            title = DateFormats.period(row.range),
                            values = mapOf(SERIES_INCOME to row.income, SERIES_EXPENSE to row.expense),
                            caption = if (periods <= 12) MoneyFormat.compact(row.net, Sign.ExceptZero) else null,
                        )
                    },
                    series = listOf(
                        ChartSeries(SERIES_INCOME, stringResource(R.string.report_tile_income), ChartTone.Income),
                        ChartSeries(SERIES_EXPENSE, stringResource(R.string.report_tile_expense), ChartTone.Expense),
                    ),
                    contentDescription = stringResource(R.string.report_cash_flow_chart_description, byLabel.lowercase(), periodLabel),
                    height = 220.dp,
                    onGroupClick = { group -> rows.firstOrNull { it.range.start.ymd() == group.key }?.let(drill) },
                )
            }
        }

        item(key = "table") {
            ReportCard(title = stringResource(groupTitleRes(filters.by)), contentPadding = PaddingValues(0.dp)) {
                ReportTable(
                    columns = listOf(
                        TableColumn(
                            key = "period",
                            header = stringResource(R.string.report_col_period),
                            width = 128.dp,
                            cell = { TableCell(DateFormats.period(it.range)) },
                            total = { TableCell(stringResource(R.string.report_col_total), emphasis = true) },
                        ),
                        TableColumn("income", stringResource(R.string.report_col_income), 120.dp, numeric = true, cell = { TableMoneyCell(it.income) }, total = { TableMoneyCell(state.income, emphasis = true) }),
                        TableColumn("expense", stringResource(R.string.report_col_expense), 120.dp, numeric = true, cell = { TableMoneyCell(it.expense) }, total = { TableMoneyCell(state.expense, emphasis = true) }),
                        TableColumn(
                            "net", stringResource(R.string.report_col_net), 120.dp, numeric = true,
                            cell = { TableMoneyCell(it.net, kind = netKind(it.net), sign = Sign.ExceptZero) },
                            total = { TableMoneyCell(state.net, kind = netKind(state.net), sign = Sign.ExceptZero, emphasis = true) },
                        ),
                        TableColumn(
                            "rate", stringResource(R.string.report_col_savings_rate), 100.dp, numeric = true,
                            cell = { TableCell(ReportLabels.percentText(it.savingsRate).asString(), numeric = true) },
                            total = { TableCell(ReportLabels.percentText(state.savingsRate).asString(), numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "count", stringResource(R.string.report_col_entries), 72.dp, numeric = true,
                            cell = { TableCell(it.count.toString(), numeric = true) },
                            total = { TableCell(state.count.toString(), numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "cumulative", stringResource(R.string.report_col_running_net), 120.dp, numeric = true,
                            cell = { TableMoneyCell(it.cumulative, kind = netKind(it.cumulative), sign = Sign.ExceptZero) },
                        ),
                    ),
                    rows = rows,
                    rowKey = { it.range.start.ymd() },
                    onRowClick = drill,
                    canOpen = { it.count > 0 },
                )
            }
        }
    }
}

private const val SERIES_INCOME = "income"
private const val SERIES_EXPENSE = "expense"

/** The title the group-by chip and the table share: "By week", "By month", "By year". */
internal fun groupTitleRes(by: Granularity): Int = when (by) {
    Granularity.Week -> R.string.report_group_by_week
    Granularity.Month -> R.string.report_group_by_month
    Granularity.Year -> R.string.report_group_by_year
}

/** `amount ÷ periods` to the paisa; the amount itself when there's one period or none. */
internal fun averageOver(amount: Money, periods: Int): Money =
    if (periods <= 1) amount else Money(Math.floorDiv(amount.paisa * 2 + periods, 2L * periods))

@PreviewLightDark
@Composable
private fun CashFlowPreview() {
    ExpensifyTheme {
        CashFlowScreen(
            state = SampleReports.cashFlow,
            onBack = {}, onReset = {}, onPreset = {}, onCustom = { _, _ -> }, onBy = {}, onAccounts = {}, onOpenTransactions = {},
        )
    }
}
