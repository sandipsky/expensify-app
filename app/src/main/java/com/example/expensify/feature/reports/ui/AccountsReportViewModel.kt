package com.example.expensify.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.reports.domain.AccountStatement
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.NetWorthPoint
import com.example.expensify.feature.reports.domain.PeriodState
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.reports.domain.ReportRules
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AccountsFilters(
    val period: PeriodState,
    val by: Granularity,
    val accountIds: List<String>,
    val accountItems: List<PickItem>,
    /** "Accounts in your total", or the picked accounts' names. */
    val scopeText: UiText,
    val filtered: Boolean,
)

/** One statement row with its account. */
data class StatementRow(val statement: AccountStatement, val account: Account)

/** The statement table's totals row. */
data class StatementTotals(
    val opening: Money,
    val income: Money,
    val expense: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    val closing: Money,
    val change: Money,
)

sealed interface AccountsReportUiState {
    val filters: AccountsFilters

    data class Loading(override val filters: AccountsFilters) : AccountsReportUiState
    data class Error(override val filters: AccountsFilters, val message: UiText) : AccountsReportUiState
    data class Content(
        override val filters: AccountsFilters,
        val statements: List<StatementRow>,
        val points: List<NetWorthPoint>,
        /** Net worth at the end of the day before the period. */
        val opening: Money,
        val closing: Money,
        val change: Money,
        val totals: StatementTotals,
        val multiYear: Boolean,
        /** Whether the user has any account at all, which decides what an empty report says. */
        val hasAnyAccount: Boolean,
    ) : AccountsReportUiState {
        val isEmpty: Boolean get() = statements.isEmpty()
    }
}

/**
 * `/reports/accounts` (req RPT-07): each account's opening balance, money
 * in and out, transfers and closing balance over the period, then net worth
 * at the end of each week, month or year. Without a pick it covers the
 * accounts in the total, as the dashboard does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsReportViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: Today,
) : ViewModel() {

    private val period = PeriodArgs(handle, DEFAULT_PRESET)
    private val byArg = handle.getStateFlow<String?>(KEY_BY, null)
    private val accountsArg = handle.getStateFlow<String?>(KEY_ACCOUNTS, null)

    private val periodArgs = period.observe(today.date)

    // Every entry from the period's start on, future ones too: balances are worked back from today's (BR-17).
    private val loaded = periodArgs.map { it.state.range.start }.distinctUntilChanged()
        .flatMapLatest { transactions.fromDate(it).loaded() }

    val state: StateFlow<AccountsReportUiState> = combine(
        reportBase(accounts, categories, today),
        periodArgs,
        byArg,
        accountsArg,
        loaded,
    ) { base, period, byArg, accountsArg, loaded ->
        val by = Granularity.fromStored(byArg) ?: DEFAULT_BY
        val accountIds = IdsParam.parse(accountsArg)
        val filters = AccountsFilters(
            period = period.state,
            by = by,
            accountIds = accountIds,
            accountItems = base.accounts.map { PickItem(it.id, plain(it.name), it.archived) },
            scopeText = ReportLabels.accountsText(base.accountsById, accountIds, res(R.string.report_accounts_in_total)),
            filtered = period.isSet || byArg != null || accountsArg != null,
        )
        when (loaded) {
            Loaded.Loading -> AccountsReportUiState.Loading(filters)
            is Loaded.Failed -> AccountsReportUiState.Error(filters, loaded.message)
            is Loaded.Ok -> {
                val picked = accountIds.toSet()
                val chosen = if (picked.isEmpty()) base.accounts.filter { it.includeInTotal && !it.archived } else base.accounts.filter { it.id in picked }
                val range = period.state.range
                val statements = ReportRules.accountStatements(chosen, loaded.txs, range).mapIndexed { i, s -> StatementRow(s, chosen[i]) }
                val buckets = ReportRules.bucketsOf(range, by)
                val series = ReportRules.netWorthSeries(chosen, loaded.txs, buckets)
                val closing = series.points.lastOrNull()?.netWorth ?: series.opening
                var totals = StatementTotals(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)
                for (row in statements) {
                    val s = row.statement
                    totals = StatementTotals(
                        opening = totals.opening + s.opening,
                        income = totals.income + s.income,
                        expense = totals.expense + s.expense,
                        transfersIn = totals.transfersIn + s.transfersIn,
                        transfersOut = totals.transfersOut + s.transfersOut,
                        closing = totals.closing + s.closing,
                        change = totals.change + s.change,
                    )
                }
                AccountsReportUiState.Content(
                    filters = filters,
                    statements = statements,
                    points = series.points,
                    opening = series.opening,
                    closing = closing,
                    change = closing - series.opening,
                    totals = totals,
                    multiYear = ReportRules.spansYears(buckets),
                    hasAnyAccount = base.accounts.isNotEmpty(),
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsReportUiState.Loading(initialFilters()))

    private fun initialFilters(): AccountsFilters {
        val state = ReportRules.periodState(
            ReportPreset.fromStored(handle[KEY_PERIOD]),
            handle.get<String?>(KEY_FROM)?.toLocalDateOrNull(),
            handle.get<String?>(KEY_TO)?.toLocalDateOrNull(),
            DEFAULT_PRESET,
            today.date.value,
        )
        return AccountsFilters(state, Granularity.fromStored(handle[KEY_BY]) ?: DEFAULT_BY, emptyList(), emptyList(), plain(""), filtered = false)
    }

    fun setPreset(preset: ReportPreset) = period.setPreset(preset, state.value.filters.period.range)

    fun setCustom(start: LocalDate, end: LocalDate) = period.setCustom(start, end)

    fun setBy(by: Granularity) {
        handle[KEY_BY] = by.stored
    }

    fun setAccounts(ids: List<String>) {
        handle[KEY_ACCOUNTS] = IdsParam.of(ids)
    }

    fun reset() {
        period.reset()
        handle[KEY_BY] = null
        handle[KEY_ACCOUNTS] = null
    }

    private companion object {
        val DEFAULT_PRESET = ReportPreset.Last12Months
        val DEFAULT_BY = Granularity.Month
        const val KEY_PERIOD = "period"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_BY = "by"
        const val KEY_ACCOUNTS = "accounts"
    }
}
