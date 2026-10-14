package com.example.expensify.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.core.date.Today
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.BudgetLookbacks
import com.example.expensify.feature.reports.domain.BudgetPeriodRow
import com.example.expensify.feature.reports.domain.BudgetStats
import com.example.expensify.feature.reports.domain.ReportRules
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** A budget in the budget picker: null stands for "All budgets". */
data class BudgetPick(val id: String?, val name: UiText, val paused: Boolean = false)

data class BudgetReportFilters(
    /** One budget's periods; every budget's summary without it. */
    val budgetId: String?,
    val lookback: Int,
    /** Also show periods that ended before the budget was created. */
    val early: Boolean,
    /** Also list paused budgets in the summary. */
    val showPaused: Boolean,
    val budgetItems: List<BudgetPick>,
    val filtered: Boolean,
)

/** One budget in the summary, with its periods and how it did. */
data class BudgetSummaryUi(
    val budget: Budget,
    val rows: List<BudgetPeriodRow>,
    val stats: BudgetStats,
    val current: BudgetPeriodRow?,
    val scope: UiText,
    val icon: BudgetLabels.Icon,
)

sealed interface BudgetReportUiState {
    val filters: BudgetReportFilters

    data class Loading(override val filters: BudgetReportFilters) : BudgetReportUiState
    data class Error(override val filters: BudgetReportFilters, val message: UiText) : BudgetReportUiState
    data class Content(
        override val filters: BudgetReportFilters,
        val summaries: List<BudgetSummaryUi>,
        /** The one budget's summary when one is picked. */
        val selected: BudgetSummaryUi?,
        val tracked: Int,
        val under: Int,
        val over: Int,
        val overspent: Money,
        val hitRate: Int?,
        /** How many budgets the user has, paused ones too, which decides what an empty report says. */
        val allCount: Int,
    ) : BudgetReportUiState {
        val isEmpty: Boolean get() = summaries.isEmpty()
    }
}

/** The route's arguments together, so one `combine` carries them. */
private data class Args(val budgetId: String?, val lookback: Int, val early: Boolean, val showPaused: Boolean, val filtered: Boolean)

/**
 * `/reports/budgets` (req RPT-08): how the budgets did period by period.
 * Without a pick it lists every budget with its hit rate, average spending
 * and overspending; picking one shows each of its periods, spent against
 * limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetReportViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    budgets: BudgetRepository,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    today: Today,
) : ViewModel() {

    private val budgetArg = handle.getStateFlow<String?>(KEY_BUDGET, null)
    private val periodsArg = handle.getStateFlow<Int?>(KEY_PERIODS, null)
    private val earlyArg = handle.getStateFlow(KEY_EARLY, false)
    private val pausedArg = handle.getStateFlow(KEY_PAUSED, false)

    private val args: Flow<Args> = combine(budgetArg, periodsArg, earlyArg, pausedArg) { budget, periods, early, paused ->
        Args(budget, BudgetLookbacks.of(periods), early, paused, filtered = budget != null || periods != null || early || paused)
    }

    private val sortedBudgets = budgets.budgets()

    /** The budgets the report covers: the picked one, or every active one (and the paused ones when asked). */
    private fun listed(all: List<Budget>, args: Args): List<Budget> {
        val sorted = all.sortedWith(BudgetRules.listOrder)
        val selected = args.budgetId?.let { id -> sorted.firstOrNull { it.id == id } }
        return if (selected != null) listOf(selected) else sorted.filter { it.active || args.showPaused }
    }

    private val spending = combine(sortedBudgets, args, today.date) { all, args, day ->
        BudgetRules.spanOf(listed(all, args).map { BudgetRules.neededRange(it, day, args.lookback) })
    }
        .distinctUntilChanged()
        .flatMapLatest { range -> if (range == null) flowOf(Loaded.Ok(emptyList())) else transactions.inRange(range).loaded() }

    val state: StateFlow<BudgetReportUiState> = combine(
        reportBase(accounts, categories, today),
        sortedBudgets,
        args,
        spending,
    ) { base, all, args, loaded ->
        val sorted = all.sortedWith(BudgetRules.listOrder)
        val filters = BudgetReportFilters(
            budgetId = args.budgetId?.takeIf { id -> sorted.any { it.id == id } },
            lookback = args.lookback,
            early = args.early,
            showPaused = args.showPaused,
            budgetItems = listOf(BudgetPick(null, plain(""))) + sorted.map { BudgetPick(it.id, plain(it.name), paused = !it.active) },
            filtered = args.filtered,
        )
        when (loaded) {
            Loaded.Loading -> BudgetReportUiState.Loading(filters)
            is Loaded.Failed -> BudgetReportUiState.Error(filters, loaded.message)
            is Loaded.Ok -> {
                val summaries = listed(all, args).map { budget ->
                    val rows = ReportRules.budgetPeriods(budget, loaded.txs, base.today, args.lookback, args.early)
                    BudgetSummaryUi(
                        budget = budget,
                        rows = rows,
                        stats = ReportRules.budgetStats(rows),
                        current = rows.firstOrNull { it.current },
                        scope = BudgetLabels.scopeOf(budget, base.categoriesById),
                        icon = BudgetLabels.iconOf(budget, base.categoriesById),
                    )
                }
                val tracked = summaries.sumOf { it.stats.tracked }
                val under = summaries.sumOf { it.stats.under }
                var overspent = Money.ZERO
                for (s in summaries) overspent += s.stats.overspent
                BudgetReportUiState.Content(
                    filters = filters,
                    summaries = summaries,
                    selected = if (filters.budgetId != null) summaries.firstOrNull { it.budget.id == filters.budgetId } else null,
                    tracked = tracked,
                    under = under,
                    over = summaries.sumOf { it.stats.over },
                    overspent = overspent,
                    hitRate = if (tracked > 0) DashboardRules.percentOf(under.toLong(), tracked.toLong()) else null,
                    allCount = all.size,
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BudgetReportUiState.Loading(
                BudgetReportFilters(
                    budgetId = handle[KEY_BUDGET],
                    lookback = BudgetLookbacks.of(handle[KEY_PERIODS]),
                    early = handle[KEY_EARLY] ?: false,
                    showPaused = handle[KEY_PAUSED] ?: false,
                    budgetItems = emptyList(),
                    filtered = false,
                ),
            ),
        )

    fun selectBudget(id: String?) {
        handle[KEY_BUDGET] = id
    }

    fun setLookback(periods: Int) {
        handle[KEY_PERIODS] = if (periods == BudgetLookbacks.DEFAULT) null else periods
    }

    fun setEarly(early: Boolean) {
        handle[KEY_EARLY] = early
    }

    fun setShowPaused(show: Boolean) {
        handle[KEY_PAUSED] = show
    }

    fun reset() {
        handle[KEY_BUDGET] = null
        handle[KEY_PERIODS] = null
        handle[KEY_EARLY] = false
        handle[KEY_PAUSED] = false
    }

    private companion object {
        const val KEY_BUDGET = "budget"
        const val KEY_PERIODS = "periods"
        const val KEY_EARLY = "early"
        const val KEY_PAUSED = "paused"
    }
}
