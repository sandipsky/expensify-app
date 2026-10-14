package com.example.expensify.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.Change
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.CategoryRow
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
import kotlin.math.abs

/** Rows in the biggest changes card. */
const val TOP_MOVERS = 5

/** Slices before the rest folds into "Other". */
const val TOP_SLICES = 6

data class CategoryFilters(
    val period: PeriodState,
    val type: CategoryType,
    val accountIds: List<String>,
    val accountItems: List<PickItem>,
    val accountsText: UiText,
    val filtered: Boolean,
)

sealed interface CategoryReportUiState {
    val filters: CategoryFilters

    data class Loading(override val filters: CategoryFilters) : CategoryReportUiState
    data class Error(override val filters: CategoryFilters, val message: UiText) : CategoryReportUiState
    data class Content(
        override val filters: CategoryFilters,
        /** Every category with an amount in either period, largest first. */
        val rows: List<CategoryRow>,
        /** The period compared with (req BR-06). */
        val before: DateRange,
        val total: Money,
        val totalBefore: Money,
        val count: Int,
        val countBefore: Int,
        val totalChange: Change,
        /** The categories that moved most from the period before, either way. */
        val movers: List<CategoryRow>,
        /** The tallest bar among the movers, this period or the one before. */
        val moverMax: Money,
        /** How each row's category reads, by id; null is "Uncategorized". */
        val views: Map<String?, CategoryView>,
    ) : CategoryReportUiState {
        val isEmpty: Boolean get() = rows.isEmpty()
        val active: List<CategoryRow> get() = rows.filter { it.amount.isPositive }
    }
}

/**
 * `/reports/categories` (req RPT-05): every expense (or income) category in
 * the period with its total, share, entries and average entry, against the
 * period before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryReportViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: Today,
) : ViewModel() {

    private val period = PeriodArgs(handle, DEFAULT_PRESET)
    private val typeArg = handle.getStateFlow<String?>(KEY_TYPE, null)
    private val accountsArg = handle.getStateFlow<String?>(KEY_ACCOUNTS, null)

    private val periodArgs = period.observe(today.date)
    private val range = periodArgs.map { it.state.range }.distinctUntilChanged()

    private val current = range.flatMapLatest { transactions.inRange(it).loaded() }
    private val previous = range.map { ReportRules.previousRange(it) }.distinctUntilChanged().flatMapLatest { transactions.inRange(it).loaded() }

    val state: StateFlow<CategoryReportUiState> = combine(
        reportBase(accounts, categories, today),
        periodArgs,
        typeArg,
        accountsArg,
        combine(current, previous) { a, b -> a to b },
    ) { base, period, typeArg, accountsArg, (current, previous) ->
        val type = typeArg?.let { v -> CategoryType.entries.firstOrNull { it.stored == v } } ?: CategoryType.Expense
        val accountIds = IdsParam.parse(accountsArg)
        val filters = CategoryFilters(
            period = period.state,
            type = type,
            accountIds = accountIds,
            accountItems = base.accounts.map { PickItem(it.id, plain(it.name), it.archived) },
            accountsText = ReportLabels.accountsText(base.accountsById, accountIds),
            filtered = period.isSet || typeArg != null || accountsArg != null,
        )
        val failed = (current as? Loaded.Failed) ?: (previous as? Loaded.Failed)
        when {
            failed != null -> CategoryReportUiState.Error(filters, failed.message)
            current !is Loaded.Ok || previous !is Loaded.Ok -> CategoryReportUiState.Loading(filters)
            else -> {
                val rows = ReportRules.categoryComparison(
                    ReportRules.onAccounts(current.txs, accountIds),
                    ReportRules.onAccounts(previous.txs, accountIds),
                    type,
                )
                var total = Money.ZERO
                var totalBefore = Money.ZERO
                var count = 0
                var countBefore = 0
                for (row in rows) {
                    total += row.amount
                    totalBefore += row.previous
                    count += row.count
                    countBefore += row.previousCount
                }
                val movers = rows.filter { !it.change.amount.isZero }
                    .sortedByDescending { abs(it.change.amount.paisa) }
                    .take(TOP_MOVERS)
                CategoryReportUiState.Content(
                    filters = filters,
                    rows = rows,
                    before = ReportRules.previousRange(period.state.range),
                    total = total,
                    totalBefore = totalBefore,
                    count = count,
                    countBefore = countBefore,
                    totalChange = DashboardRules.changeOf(totalBefore, total),
                    movers = movers,
                    moverMax = movers.maxOfOrNull { maxOf(it.amount, it.previous) } ?: Money.ZERO,
                    views = rows.associate { it.categoryId to ReportLabels.categoryView(base.categoriesById, it.categoryId) },
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryReportUiState.Loading(initialFilters()))

    private fun initialFilters(): CategoryFilters {
        val state = ReportRules.periodState(
            ReportPreset.fromStored(handle[KEY_PERIOD]),
            handle.get<String?>(KEY_FROM)?.toLocalDateOrNull(),
            handle.get<String?>(KEY_TO)?.toLocalDateOrNull(),
            DEFAULT_PRESET,
            today.date.value,
        )
        return CategoryFilters(state, CategoryType.Expense, emptyList(), emptyList(), plain(""), filtered = false)
    }

    fun setPreset(preset: ReportPreset) = period.setPreset(preset, state.value.filters.period.range)

    fun setCustom(start: LocalDate, end: LocalDate) = period.setCustom(start, end)

    fun setType(type: CategoryType) {
        handle[KEY_TYPE] = type.stored
    }

    fun setAccounts(ids: List<String>) {
        handle[KEY_ACCOUNTS] = IdsParam.of(ids)
    }

    fun reset() {
        period.reset()
        handle[KEY_TYPE] = null
        handle[KEY_ACCOUNTS] = null
    }

    private companion object {
        val DEFAULT_PRESET = ReportPreset.ThisMonth
        const val KEY_PERIOD = "period"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_TYPE = "type"
        const val KEY_ACCOUNTS = "accounts"
    }
}
