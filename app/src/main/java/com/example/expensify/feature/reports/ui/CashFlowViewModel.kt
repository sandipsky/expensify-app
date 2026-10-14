package com.example.expensify.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.FlowRow
import com.example.expensify.feature.reports.domain.Granularity
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

/** The cash flow report's filters, shown in every state so they can change while it loads. */
data class CashFlowFilters(
    val period: PeriodState,
    val by: Granularity,
    val accountIds: List<String>,
    val accountItems: List<PickItem>,
    val accountsText: UiText,
    /** Whether anything differs from the defaults, so Reset is offered. */
    val filtered: Boolean,
)

sealed interface CashFlowUiState {
    val filters: CashFlowFilters

    data class Loading(override val filters: CashFlowFilters) : CashFlowUiState
    data class Error(override val filters: CashFlowFilters, val message: UiText) : CashFlowUiState
    data class Content(
        override val filters: CashFlowFilters,
        val rows: List<FlowRow>,
        val income: Money,
        val expense: Money,
        val net: Money,
        val count: Int,
        val savingsRate: Int?,
        /** Periods that ended with more income than expense. */
        val positive: Int,
        val multiYear: Boolean,
    ) : CashFlowUiState {
        val isEmpty: Boolean get() = count == 0
    }
}

/**
 * `/reports/cash-flow` (req RPT-04): income, expense, net and savings rate
 * per week, month or year over the period, for every account or the ones
 * picked. The filters live in the route's saved state, as the web keeps them
 * in the URL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashFlowViewModel @Inject constructor(
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

    private val loaded = periodArgs.map { it.state.range }.distinctUntilChanged()
        .flatMapLatest { transactions.inRange(it).loaded() }

    val state: StateFlow<CashFlowUiState> = combine(
        reportBase(accounts, categories, today),
        periodArgs,
        byArg,
        accountsArg,
        loaded,
    ) { base, period, byArg, accountsArg, loaded ->
        val by = Granularity.fromStored(byArg) ?: DEFAULT_BY
        val accountIds = IdsParam.parse(accountsArg)
        val filters = CashFlowFilters(
            period = period.state,
            by = by,
            accountIds = accountIds,
            accountItems = base.accounts.map { PickItem(it.id, plain(it.name), it.archived) },
            accountsText = ReportLabels.accountsText(base.accountsById, accountIds),
            filtered = period.isSet || byArg != null || accountsArg != null,
        )
        when (loaded) {
            Loaded.Loading -> CashFlowUiState.Loading(filters)
            is Loaded.Failed -> CashFlowUiState.Error(filters, loaded.message)
            is Loaded.Ok -> {
                val buckets = ReportRules.bucketsOf(period.state.range, by)
                val rows = ReportRules.cashFlow(ReportRules.onAccounts(loaded.txs, accountIds), buckets)
                var income = Money.ZERO
                var expense = Money.ZERO
                var count = 0
                for (row in rows) {
                    income += row.income
                    expense += row.expense
                    count += row.count
                }
                val net = income - expense
                CashFlowUiState.Content(
                    filters = filters,
                    rows = rows,
                    income = income,
                    expense = expense,
                    net = net,
                    count = count,
                    savingsRate = DashboardRules.savingsRate(income, net),
                    positive = rows.count { it.net.isPositive },
                    multiYear = ReportRules.spansYears(buckets),
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashFlowUiState.Loading(initialFilters()))

    private fun initialFilters(): CashFlowFilters {
        val state = ReportRules.periodState(
            ReportPreset.fromStored(handle[KEY_PERIOD]),
            handle.get<String?>(KEY_FROM)?.toLocalDateOrNull(),
            handle.get<String?>(KEY_TO)?.toLocalDateOrNull(),
            DEFAULT_PRESET,
            today.date.value,
        )
        return CashFlowFilters(state, Granularity.fromStored(handle[KEY_BY]) ?: DEFAULT_BY, emptyList(), emptyList(), plain(""), filtered = false)
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
