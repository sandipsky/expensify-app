package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.ymd
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
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
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.MatrixRow
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.reports.domain.ReportRules
import java.time.LocalDate

@Composable
fun CategoryTrendsRoute(onBack: () -> Unit, onOpenTransactions: (DrillDown) -> Unit, viewModel: CategoryTrendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryTrendsScreen(
        state = state,
        onBack = onBack,
        onReset = viewModel::reset,
        onPreset = viewModel::setPreset,
        onCustom = viewModel::setCustom,
        onType = viewModel::setType,
        onAccounts = viewModel::setAccounts,
        onCategories = viewModel::setCategories,
        onOpenTransactions = onOpenTransactions,
    )
}

/** How much of its row's busiest month a cell is shaded: up to 28 % of the category's color. */
private const val MAX_HEAT = 0.28f

@Composable
fun CategoryTrendsScreen(
    state: CategoryTrendsUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onType: (CategoryType) -> Unit,
    onAccounts: (List<String>) -> Unit,
    onCategories: (List<String>) -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
) {
    val filters = state.filters
    val periodLabel = DateFormats.period(filters.range)
    val noun = stringResource(ReportLabels.nounRes(filters.type))
    val typeLabel = stringResource(ReportLabels.categoryTypeRes(filters.type))
    val txType = DashboardRules.txTypeOf(filters.type)

    ReportFrame(
        key = ReportKey.CategoryTrends,
        filtered = filters.filtered,
        summary = stringResource(R.string.report_summary_three, periodLabel, filters.accountsText.asString(), filters.categoriesText.asString()),
        loading = state is CategoryTrendsUiState.Loading,
        error = (state as? CategoryTrendsUiState.Error)?.message,
        onBack = onBack,
        onReset = onReset,
        filters = {
            PeriodChip(state = filters.period, onPreset = onPreset, onCustom = onCustom)
            SegmentedChoice(options = CategoryType.entries, value = filters.type, labelOf = { stringResource(ReportLabels.categoryTypeRes(it)) }, onChange = onType)
            MultiPickChip(
                title = stringResource(R.string.report_field_accounts),
                placeholder = stringResource(R.string.report_all_accounts),
                items = filters.accountItems,
                selected = filters.accountIds,
                onChange = onAccounts,
                countLabel = { pluralStringResource(R.plurals.report_accounts_count, it, it) },
            )
            MultiPickChip(
                title = stringResource(R.string.report_field_categories),
                placeholder = stringResource(R.string.report_all_categories),
                items = filters.categoryItems,
                selected = filters.categoryIds,
                onChange = onCategories,
                countLabel = { pluralStringResource(R.plurals.report_categories_count, it, it) },
            )
        },
    ) {
        if (state !is CategoryTrendsUiState.Content) return@ReportFrame
        val months = state.months
        val matrix = state.matrix
        val view = { row: MatrixRow -> state.views[row.categoryId] ?: ReportLabels.categoryView(emptyMap(), row.categoryId) }
        val open = { row: MatrixRow, within: DateRange ->
            row.categoryId?.let { onOpenTransactions(DrillDown(within, txType, listOf(it), filters.accountIds)) }
            Unit
        }

        if (filters.clipped) {
            item(key = "clipped") { Banner(stringResource(R.string.report_trends_clipped, ReportRules.MAX_TREND_MONTHS), BannerTone.Info) }
        }

        item(key = "tiles") {
            ReportTiles(
                listOf(
                    ReportTile(
                        label = stringResource(R.string.report_total_of, noun),
                        value = MoneyFormat.full(matrix.total),
                        tone = if (matrix.total.isPositive) (if (filters.type == CategoryType.Income) TileTone.Income else TileTone.Expense) else null,
                        note = stringResource(R.string.report_over_months, ReportLabels.unitOf(Granularity.Month, months.size).asString()),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_monthly_average),
                        value = MoneyFormat.full(state.average),
                        note = stringResource(R.string.report_total_over_months),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_highest_month),
                        value = MoneyFormat.full(matrix.columnTotals.getOrNull(state.busiest) ?: Money.ZERO),
                        note = months.getOrNull(state.busiest)?.let { DateFormats.period(it) },
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_categories),
                        value = matrix.rows.size.toString(),
                        note = matrix.rows.firstOrNull()?.let { stringResource(R.string.report_largest, view(it).name.asString()) },
                    ),
                ),
            )
        }

        if (state.isEmpty) {
            item(key = "empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Calendar,
                        title = stringResource(R.string.report_categories_empty_title, noun, periodLabel),
                        message = stringResource(R.string.report_trends_empty_message),
                    )
                }
            }
            return@ReportFrame
        }

        item(key = "chart") {
            ReportCard(title = stringResource(R.string.report_type_by_month, typeLabel)) {
                BarChart(
                    groups = months.mapIndexed { i, month ->
                        BarGroup(
                            key = month.start.ymd(),
                            label = ReportLabels.bucketLabel(month, Granularity.Month, state.multiYear),
                            title = DateFormats.period(month),
                            values = mapOf(SERIES_TOTAL to matrix.columnTotals[i]),
                        )
                    },
                    series = listOf(ChartSeries(SERIES_TOTAL, typeLabel, if (filters.type == CategoryType.Expense) ChartTone.Expense else ChartTone.Income)),
                    contentDescription = stringResource(R.string.report_trends_chart_description, typeLabel, periodLabel),
                    height = 180.dp,
                )
            }
        }

        item(key = "table") {
            ReportCard(title = stringResource(R.string.report_categories_by_month), contentPadding = PaddingValues(0.dp)) {
                val monthColumns = months.mapIndexed { i, month ->
                    TableColumn<MatrixRow>(
                        key = month.start.ymd(),
                        header = ReportLabels.bucketLabel(month, Granularity.Month, state.multiYear),
                        width = 104.dp,
                        numeric = true,
                        cell = { row -> HeatCell(row = row, index = i, view = view(row), month = month, onOpen = if (row.categoryId != null) ({ open(row, month) }) else null) },
                        total = { TableMoneyCell(matrix.columnTotals[i], emphasis = true) },
                    )
                }
                ReportTable(
                    columns = listOf<TableColumn<MatrixRow>>(
                        TableColumn(
                            key = "category",
                            header = stringResource(R.string.report_col_category),
                            width = 176.dp,
                            cell = { row -> CategoryCell(view(row), badge = 24.dp) },
                            total = { TableCell(stringResource(R.string.report_col_total), emphasis = true) },
                        ),
                    ) + monthColumns + listOf<TableColumn<MatrixRow>>(
                        TableColumn("total", stringResource(R.string.report_col_total), 120.dp, numeric = true, cell = { TableMoneyCell(it.total, emphasis = true) }, total = { TableMoneyCell(matrix.total, emphasis = true) }),
                        TableColumn("average", stringResource(R.string.report_col_monthly_avg), 120.dp, numeric = true, cell = { TableMoneyCell(it.average) }, total = { TableMoneyCell(state.average, emphasis = true) }),
                    ),
                    rows = matrix.rows,
                    rowKey = { it.categoryId ?: "none" },
                    onRowClick = { open(it, filters.range) },
                    canOpen = { it.categoryId != null },
                )
            }
        }
    }
}

private const val SERIES_TOTAL = "total"

/** A month's amount shaded against the row's busiest month; tapping it opens that month's entries. */
@Composable
private fun HeatCell(row: MatrixRow, index: Int, view: CategoryView, month: DateRange, onOpen: (() -> Unit)?) {
    val amount = row.cells[index]
    if (amount.isZero) {
        TableCell("—", numeric = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val top = row.cells.maxOf { it.paisa }.coerceAtLeast(1)
    val color = view.colorHex?.let { dataColor(it) } ?: MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.report_heat_cell_description, view.name.asString(), DateFormats.period(month), MoneyFormat.full(amount))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = MAX_HEAT * amount.paisa / top))
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            MoneyFormat.full(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

@PreviewLightDark
@Composable
private fun CategoryTrendsPreview() {
    ExpensifyTheme {
        CategoryTrendsScreen(
            state = SampleReports.trends,
            onBack = {}, onReset = {}, onPreset = {}, onCustom = { _, _ -> }, onType = {}, onAccounts = {}, onCategories = {}, onOpenTransactions = {},
        )
    }
}
