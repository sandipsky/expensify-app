package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.ReportTable
import com.example.expensify.designsystem.components.TableCell
import com.example.expensify.designsystem.components.TableColumn
import com.example.expensify.designsystem.components.TableMoneyCell
import com.example.expensify.designsystem.components.TableTextWithCaption
import com.example.expensify.designsystem.components.charts.BarChart
import com.example.expensify.designsystem.components.charts.BarGroup
import com.example.expensify.designsystem.components.charts.ChartSeries
import com.example.expensify.designsystem.components.charts.ChartTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountType
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.domain.ReportPreset
import java.time.LocalDate

@Composable
fun AccountsReportRoute(onBack: () -> Unit, onOpenAccount: (String) -> Unit, viewModel: AccountsReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountsReportScreen(
        state = state,
        onBack = onBack,
        onReset = viewModel::reset,
        onPreset = viewModel::setPreset,
        onCustom = viewModel::setCustom,
        onBy = viewModel::setBy,
        onAccounts = viewModel::setAccounts,
        onOpenAccount = onOpenAccount,
    )
}

@Composable
fun AccountsReportScreen(
    state: AccountsReportUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onBy: (Granularity) -> Unit,
    onAccounts: (List<String>) -> Unit,
    onOpenAccount: (String) -> Unit,
) {
    val filters = state.filters
    val periodLabel = DateFormats.period(filters.period.range)
    val byLabel = stringResource(ReportLabels.granularityRes(filters.by)).lowercase()

    ReportFrame(
        key = ReportKey.Accounts,
        filtered = filters.filtered,
        summary = stringResource(R.string.report_summary_two, periodLabel, filters.scopeText.asString()),
        loading = state is AccountsReportUiState.Loading,
        error = (state as? AccountsReportUiState.Error)?.message,
        onBack = onBack,
        onReset = onReset,
        filters = {
            PeriodChip(state = filters.period, onPreset = onPreset, onCustom = onCustom)
            SegmentedChoice(options = Granularity.entries, value = filters.by, labelOf = { stringResource(ReportLabels.granularityRes(it)) }, onChange = onBy)
            MultiPickChip(
                title = stringResource(R.string.report_field_accounts),
                placeholder = stringResource(R.string.report_accounts_in_total),
                items = filters.accountItems,
                selected = filters.accountIds,
                onChange = onAccounts,
                countLabel = { pluralStringResource(R.plurals.report_accounts_count, it, it) },
            )
        },
    ) {
        if (state !is AccountsReportUiState.Content) return@ReportFrame

        if (state.isEmpty) {
            item(key = "empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Accounts,
                        title = stringResource(if (state.hasAnyAccount) R.string.report_accounts_empty_none_title else R.string.report_accounts_empty_title),
                        message = stringResource(if (state.hasAnyAccount) R.string.report_accounts_empty_none_message else R.string.report_accounts_empty_message),
                    )
                }
            }
            return@ReportFrame
        }

        val last = state.points.lastOrNull()
        item(key = "tiles") {
            ReportTiles(
                listOf(
                    ReportTile(
                        label = stringResource(R.string.report_tile_net_worth),
                        value = MoneyFormat.full(state.closing),
                        tone = if (state.closing.isNegative) TileTone.Expense else null,
                        note = stringResource(R.string.report_as_of, DateFormats.shortDate(filters.period.range.end)),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_change),
                        value = MoneyFormat.full(state.change, Sign.ExceptZero),
                        tone = netTone(state.change),
                        note = stringResource(R.string.report_from_at_start, MoneyFormat.full(state.opening)),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_assets),
                        value = MoneyFormat.full(last?.assets ?: Money.ZERO),
                        note = stringResource(R.string.report_assets_note),
                    ),
                    ReportTile(
                        label = stringResource(R.string.report_tile_liabilities),
                        value = MoneyFormat.full(last?.liabilities ?: Money.ZERO),
                        note = stringResource(R.string.report_liabilities_note),
                    ),
                ),
            )
        }

        item(key = "chart") {
            ReportCard(title = stringResource(R.string.report_assets_and_liabilities)) {
                BarChart(
                    groups = state.points.map { point ->
                        BarGroup(
                            key = point.range.start.ymd(),
                            label = ReportLabels.bucketLabel(point.range, filters.by, state.multiYear),
                            title = DateFormats.period(point.range),
                            values = mapOf(SERIES_ASSETS to point.assets, SERIES_LIABILITIES to point.liabilities),
                            caption = if (state.points.size <= 12) MoneyFormat.compact(point.netWorth) else null,
                        )
                    },
                    series = listOf(
                        ChartSeries(SERIES_ASSETS, stringResource(R.string.report_tile_assets), ChartTone.Income),
                        ChartSeries(SERIES_LIABILITIES, stringResource(R.string.report_tile_liabilities), ChartTone.Expense),
                    ),
                    contentDescription = stringResource(R.string.report_accounts_chart_description, byLabel, periodLabel),
                    height = 200.dp,
                )
            }
        }

        item(key = "statement") {
            ReportCard(title = stringResource(R.string.report_account_statement), contentPadding = PaddingValues(0.dp)) {
                val t = state.totals
                ReportTable(
                    columns = listOf(
                        TableColumn(
                            key = "account",
                            header = stringResource(R.string.report_col_account),
                            width = 168.dp,
                            cell = { row -> AccountCell(row.account) },
                            total = { TableCell(stringResource(R.string.report_col_total), emphasis = true) },
                        ),
                        TableColumn("opening", stringResource(R.string.report_col_opening), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.opening) }, total = { TableMoneyCell(t.opening, emphasis = true) }),
                        TableColumn("income", stringResource(R.string.report_col_income), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.income) }, total = { TableMoneyCell(t.income, emphasis = true) }),
                        TableColumn("expense", stringResource(R.string.report_col_expense), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.expense) }, total = { TableMoneyCell(t.expense, emphasis = true) }),
                        TableColumn("in", stringResource(R.string.report_col_transfers_in), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.transfersIn) }, total = { TableMoneyCell(t.transfersIn, emphasis = true) }),
                        TableColumn("out", stringResource(R.string.report_col_transfers_out), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.transfersOut) }, total = { TableMoneyCell(t.transfersOut, emphasis = true) }),
                        TableColumn("closing", stringResource(R.string.report_col_closing), 120.dp, numeric = true, cell = { TableMoneyCell(it.statement.closing, emphasis = true) }, total = { TableMoneyCell(t.closing, emphasis = true) }),
                        TableColumn(
                            "change", stringResource(R.string.report_col_change), 120.dp, numeric = true,
                            cell = { TableMoneyCell(it.statement.change, kind = netKind(it.statement.change), sign = Sign.ExceptZero) },
                            total = { TableMoneyCell(t.change, kind = netKind(t.change), sign = Sign.ExceptZero, emphasis = true) },
                        ),
                    ),
                    rows = state.statements,
                    rowKey = { it.account.id },
                    onRowClick = { onOpenAccount(it.account.id) },
                )
            }
        }

        item(key = "net-worth") {
            ReportCard(title = stringResource(R.string.report_net_worth_by, byLabel), contentPadding = PaddingValues(0.dp)) {
                ReportTable(
                    columns = listOf(
                        TableColumn(
                            key = "period",
                            header = stringResource(R.string.report_col_period),
                            width = 168.dp,
                            cell = { point -> TableTextWithCaption(DateFormats.period(point.range), stringResource(R.string.report_at_end_of, DateFormats.shortDate(point.range.end))) },
                        ),
                        TableColumn("assets", stringResource(R.string.report_col_assets), 120.dp, numeric = true, cell = { TableMoneyCell(it.assets) }),
                        TableColumn("liabilities", stringResource(R.string.report_col_liabilities), 120.dp, numeric = true, cell = { TableMoneyCell(it.liabilities) }),
                        TableColumn(
                            "worth", stringResource(R.string.report_col_net_worth), 120.dp, numeric = true,
                            cell = { TableMoneyCell(it.netWorth, kind = if (it.netWorth.isNegative) AmountKind.Expense else AmountKind.Neutral, emphasis = true) },
                        ),
                        TableColumn(
                            "change", stringResource(R.string.report_col_change), 120.dp, numeric = true,
                            cell = { TableMoneyCell(it.change, kind = netKind(it.change), sign = Sign.ExceptZero) },
                        ),
                    ),
                    rows = state.points,
                    rowKey = { it.range.start.ymd() },
                )
            }
        }

        item(key = "note") { ReportNote(stringResource(R.string.report_balance_note)) }
    }
}

private const val SERIES_ASSETS = "assets"
private const val SERIES_LIABILITIES = "liabilities"

/** The type an account reads as. */
internal fun accountTypeRes(type: AccountType): Int = when (type) {
    AccountType.Cash -> R.string.account_type_cash
    AccountType.Bank -> R.string.account_type_bank
    AccountType.CreditCard -> R.string.account_type_credit_card
    AccountType.Wallet -> R.string.account_type_wallet
    AccountType.Savings -> R.string.account_type_savings
    AccountType.Loan -> R.string.account_type_loan
    AccountType.Other -> R.string.account_type_other
}

/** An account's badge and name, its type (and "Archived") under it. */
@Composable
private fun AccountCell(account: Account) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = IconMap.of(account.icon), color = dataColor(account.color), size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(account.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val type = stringResource(accountTypeRes(account.type))
            Text(
                if (account.archived) stringResource(R.string.report_summary_two, type, stringResource(R.string.account_chip_archived)) else type,
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
private fun AccountsReportPreview() {
    ExpensifyTheme {
        AccountsReportScreen(
            state = SampleReports.accounts,
            onBack = {}, onReset = {}, onPreset = {}, onCustom = { _, _ -> }, onBy = {}, onAccounts = {}, onOpenAccount = {},
        )
    }
}
