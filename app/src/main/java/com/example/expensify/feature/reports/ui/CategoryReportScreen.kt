package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.ReportTable
import com.example.expensify.designsystem.components.TableCell
import com.example.expensify.designsystem.components.TableColumn
import com.example.expensify.designsystem.components.TableFigureWithSub
import com.example.expensify.designsystem.components.TableMoneyCell
import com.example.expensify.designsystem.components.charts.DonutChart
import com.example.expensify.designsystem.components.charts.DonutSegment
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.CategoryTotal
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.CategoryRow
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.reports.domain.ReportRules
import java.time.LocalDate

@Composable
fun CategoryReportRoute(onBack: () -> Unit, onOpenTransactions: (DrillDown) -> Unit, viewModel: CategoryReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryReportScreen(
        state = state,
        onBack = onBack,
        onReset = viewModel::reset,
        onPreset = viewModel::setPreset,
        onCustom = viewModel::setCustom,
        onType = viewModel::setType,
        onAccounts = viewModel::setAccounts,
        onOpenTransactions = onOpenTransactions,
    )
}

/** Whether a change is good news (spending less, or earning more) or bad; null for none. Always beside a sign. */
internal fun changeKind(type: CategoryType, amount: Money): AmountKind = when {
    amount.isZero -> AmountKind.Neutral
    amount.isPositive == (type == CategoryType.Income) -> AmountKind.Income
    else -> AmountKind.Expense
}

internal fun changeTone(type: CategoryType, amount: Money): TileTone? = when (changeKind(type, amount)) {
    AmountKind.Income -> TileTone.Income
    AmountKind.Expense -> TileTone.Expense
    else -> null
}

@Composable
fun CategoryReportScreen(
    state: CategoryReportUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onType: (CategoryType) -> Unit,
    onAccounts: (List<String>) -> Unit,
    onOpenTransactions: (DrillDown) -> Unit,
) {
    val filters = state.filters
    val periodLabel = DateFormats.period(filters.period.range)
    val beforeLabel = DateFormats.period(ReportRules.previousRange(filters.period.range))
    val noun = stringResource(ReportLabels.nounRes(filters.type))
    val typeLabel = stringResource(ReportLabels.categoryTypeRes(filters.type))
    val txType = DashboardRules.txTypeOf(filters.type)

    ReportFrame(
        key = ReportKey.Categories,
        filtered = filters.filtered,
        summary = stringResource(R.string.report_summary_two, stringResource(R.string.report_against, periodLabel, beforeLabel), filters.accountsText.asString()),
        loading = state is CategoryReportUiState.Loading,
        error = (state as? CategoryReportUiState.Error)?.message,
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
        },
    ) {
        if (state !is CategoryReportUiState.Content) return@ReportFrame
        val view = { row: CategoryRow -> state.views[row.categoryId] ?: ReportLabels.categoryView(emptyMap(), row.categoryId) }
        val open = { row: CategoryRow ->
            row.categoryId?.let { onOpenTransactions(DrillDown(filters.period.range, txType, listOf(it), filters.accountIds)) }
            Unit
        }
        val canOpen = { row: CategoryRow -> row.categoryId != null && row.amount.isPositive }
        val days = filters.period.range.days.toInt()
        val largest = state.active.firstOrNull()

        item(key = "tiles") {
            ReportTiles(
                listOf(
                    ReportTile(
                        label = stringResource(R.string.report_total_of, noun),
                        value = MoneyFormat.full(state.total),
                        tone = if (state.total.isPositive) (if (filters.type == CategoryType.Income) TileTone.Income else TileTone.Expense) else null,
                        note = ReportLabels.entries(state.count).asString(),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_previous_period),
                        value = MoneyFormat.full(state.totalBefore),
                        note = stringResource(R.string.report_summary_two, beforeLabel, ReportLabels.entries(state.countBefore).asString()),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_change),
                        value = MoneyFormat.full(state.totalChange.amount, Sign.ExceptZero),
                        tone = changeTone(filters.type, state.totalChange.amount),
                        note = if (state.totalBefore.isZero) stringResource(R.string.report_nothing_before) else ReportLabels.changeText(state.totalChange.percent).asString(),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_per_day),
                        value = MoneyFormat.full(ReportRules.averageOf(state.total, days)),
                        note = if (largest != null) stringResource(R.string.report_largest, view(largest).name.asString()) else stringResource(R.string.report_over_days, days),
                    ),
                ),
            )
        }

        if (state.isEmpty) {
            item(key = "empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Budgets,
                        title = stringResource(R.string.report_categories_empty_title, noun, periodLabel),
                        message = stringResource(R.string.report_categories_empty_message),
                    )
                }
            }
            return@ReportFrame
        }

        item(key = "donut") {
            ReportCard(title = stringResource(R.string.report_split_of, noun)) {
                val active = state.active
                if (active.isEmpty()) {
                    EmptyState(icon = AppIcons.Budgets, title = stringResource(R.string.report_categories_empty_title, noun, periodLabel), message = "")
                } else {
                    val neutral = ExpensifyTheme.colors.transfer
                    val keep = if (active.size <= TOP_SLICES + 1) active.size else TOP_SLICES
                    val split = DashboardRules.topAndOther(active.map { CategoryTotal(it.categoryId, it.amount, it.count) }, keep)
                    val segments = split.top.map { total ->
                        val v = state.views[total.categoryId] ?: ReportLabels.categoryView(emptyMap(), total.categoryId)
                        DonutSegment(
                            key = total.categoryId ?: KEY_NONE,
                            label = v.name.asString(),
                            value = total.amount,
                            color = v.colorHex?.let { dataColor(it) } ?: neutral,
                        )
                    } + listOfNotNull(split.other?.let { DonutSegment(KEY_OTHER, stringResource(R.string.report_other), it.amount, neutral) })
                    DonutChart(
                        segments = segments,
                        contentDescription = stringResource(R.string.report_categories_chart_description, typeLabel, periodLabel),
                        onSegmentClick = { segment -> active.firstOrNull { (it.categoryId ?: KEY_NONE) == segment.key }?.let(open) },
                        center = {
                            Text(MoneyFormat.full(state.total), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                            Text(ReportLabels.entries(state.count).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }
            }
        }

        item(key = "movers") {
            ReportCard(title = stringResource(R.string.report_biggest_changes, beforeLabel), contentPadding = PaddingValues(bottom = 8.dp)) {
                if (state.movers.isEmpty()) {
                    EmptyState(
                        icon = AppIcons.Reports,
                        title = stringResource(R.string.report_no_changes_title),
                        message = stringResource(R.string.report_no_changes_message, beforeLabel),
                    )
                } else {
                    state.movers.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MoverRow(row = row, view = view(row), max = state.moverMax, type = filters.type, onClick = if (canOpen(row)) ({ open(row) }) else null)
                    }
                    Text(
                        stringResource(R.string.report_movers_legend, periodLabel, beforeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item(key = "table") {
            ReportCard(title = stringResource(R.string.report_all_categories_title), contentPadding = PaddingValues(0.dp)) {
                ReportTable(
                    columns = listOf(
                        TableColumn(
                            key = "category",
                            header = stringResource(R.string.report_col_category),
                            width = 168.dp,
                            cell = { row -> CategoryCell(view(row)) },
                            total = { TableCell(stringResource(R.string.report_col_total), emphasis = true) },
                        ),
                        TableColumn("amount", typeLabel, 120.dp, numeric = true, cell = { TableMoneyCell(it.amount) }, total = { TableMoneyCell(state.total, emphasis = true) }),
                        TableColumn(
                            "share", stringResource(R.string.report_col_share), 72.dp, numeric = true,
                            cell = { TableCell(ReportLabels.percentText(it.share).asString(), numeric = true) },
                            total = { TableCell(if (state.total.isPositive) "100%" else "—", numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "count", stringResource(R.string.report_col_entries), 72.dp, numeric = true,
                            cell = { TableCell(it.count.toString(), numeric = true) },
                            total = { TableCell(state.count.toString(), numeric = true, emphasis = true) },
                        ),
                        TableColumn(
                            "average", stringResource(R.string.report_col_avg_entry), 120.dp, numeric = true,
                            cell = { if (it.count > 0) TableMoneyCell(it.average) else TableCell("—", numeric = true) },
                            total = { if (state.count > 0) TableMoneyCell(ReportRules.averageOf(state.total, state.count), emphasis = true) else TableCell("—", numeric = true) },
                        ),
                        TableColumn("previous", stringResource(R.string.report_col_previous), 120.dp, numeric = true, cell = { TableMoneyCell(it.previous) }, total = { TableMoneyCell(state.totalBefore, emphasis = true) }),
                        TableColumn(
                            "change", stringResource(R.string.report_col_change), 128.dp, numeric = true,
                            cell = { row ->
                                TableFigureWithSub(
                                    figure = { TableMoneyCell(row.change.amount, kind = changeKind(filters.type, row.change.amount), sign = Sign.ExceptZero) },
                                    sub = ReportLabels.changeText(row.change.percent).asString(),
                                )
                            },
                            total = {
                                TableFigureWithSub(
                                    figure = { TableMoneyCell(state.totalChange.amount, kind = changeKind(filters.type, state.totalChange.amount), sign = Sign.ExceptZero, emphasis = true) },
                                    sub = ReportLabels.changeText(state.totalChange.percent).asString(),
                                )
                            },
                        ),
                    ),
                    rows = state.rows,
                    rowKey = { it.categoryId ?: KEY_NONE },
                    onRowClick = open,
                    canOpen = canOpen,
                )
            }
        }
    }
}

private const val KEY_NONE = "none"
private const val KEY_OTHER = "other"

/** A category's badge and name, with "Archived" under it when it is. */
@Composable
internal fun CategoryCell(view: CategoryView, badge: androidx.compose.ui.unit.Dp = 28.dp) {
    val neutral = ExpensifyTheme.colors.transfer
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = IconMap.of(view.icon), color = view.colorHex?.let { dataColor(it) } ?: neutral, size = badge)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(view.name.asString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (view.archived) {
                Text(stringResource(R.string.account_chip_archived), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One of the biggest changes: the category, "before → now", the change, and two bars to compare the periods. */
@Composable
private fun MoverRow(row: CategoryRow, view: CategoryView, max: Money, type: CategoryType, onClick: (() -> Unit)?) {
    val neutral = ExpensifyTheme.colors.transfer
    val color = view.colorHex?.let { dataColor(it) } ?: neutral
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = IconMap.of(view.icon), color = color, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(view.name.asString(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.report_mover_caption, MoneyFormat.full(row.previous), MoneyFormat.full(row.amount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Bar(fraction = fractionOf(row.amount, max), color = color)
            Bar(fraction = fractionOf(row.previous, max), color = neutral)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(money = row.change.amount, kind = changeKind(type, row.change.amount), sign = Sign.ExceptZero, style = MaterialTheme.typography.titleMedium)
            Text(ReportLabels.changeText(row.change.percent).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fractionOf(amount: Money, max: Money): Float =
    if (!max.isPositive) 0f else (amount.paisa.toFloat() / max.paisa).coerceIn(0f, 1f)

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color))
    }
}

@PreviewLightDark
@Composable
private fun CategoryReportPreview() {
    ExpensifyTheme {
        CategoryReportScreen(
            state = SampleReports.categories,
            onBack = {}, onReset = {}, onPreset = {}, onCustom = { _, _ -> }, onType = {}, onAccounts = {}, onOpenTransactions = {},
        )
    }
}
