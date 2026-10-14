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
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.domain.categoryOrder
import com.example.expensify.feature.reports.domain.CategoryMatrix
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

data class TrendsFilters(
    val period: PeriodState,
    /** The range the table covers: the period, cut to its last 24 months (req RPT-06). */
    val range: DateRange,
    val clipped: Boolean,
    val type: CategoryType,
    val accountIds: List<String>,
    val accountItems: List<PickItem>,
    val categoryIds: List<String>,
    /** The type's categories, in list order, archived ones too. */
    val categoryItems: List<PickItem>,
    val accountsText: UiText,
    val categoriesText: UiText,
    val filtered: Boolean,
)

sealed interface CategoryTrendsUiState {
    val filters: TrendsFilters

    data class Loading(override val filters: TrendsFilters) : CategoryTrendsUiState
    data class Error(override val filters: TrendsFilters, val message: UiText) : CategoryTrendsUiState
    data class Content(
        override val filters: TrendsFilters,
        val months: List<DateRange>,
        val matrix: CategoryMatrix,
        /** Total ÷ months. */
        val average: Money,
        /** The month with the most, as an index into `months`; -1 with none. */
        val busiest: Int,
        val multiYear: Boolean,
        val views: Map<String?, CategoryView>,
    ) : CategoryTrendsUiState {
        val isEmpty: Boolean get() = matrix.rows.isEmpty()
    }
}

/**
 * `/reports/category-trends` (req RPT-06): one row per category and one
 * column per month, with each row's total and monthly average and each
 * month's total, so spending that creeps up stands out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryTrendsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: Today,
) : ViewModel() {

    private val period = PeriodArgs(handle, DEFAULT_PRESET)
    private val typeArg = handle.getStateFlow<String?>(KEY_TYPE, null)
    private val accountsArg = handle.getStateFlow<String?>(KEY_ACCOUNTS, null)
    private val categoriesArg = handle.getStateFlow<String?>(KEY_CATEGORIES, null)

    /** The categories last seen, so switching type can drop picks of the other type. */
    private var knownCategories: List<Category> = emptyList()

    private val periodArgs = period.observe(today.date)
    private val range = periodArgs.map { ReportRules.trendRange(it.state.range).range }.distinctUntilChanged()
    private val loaded = range.flatMapLatest { transactions.inRange(it).loaded() }

    val state: StateFlow<CategoryTrendsUiState> = combine(
        reportBase(accounts, categories, today),
        periodArgs,
        combine(typeArg, accountsArg, categoriesArg) { t, a, c -> Triple(t, a, c) },
        loaded,
    ) { base, period, (typeArg, accountsArg, categoriesArg), loaded ->
        knownCategories = base.categories
        val type = typeArg?.let { v -> CategoryType.entries.firstOrNull { it.stored == v } } ?: CategoryType.Expense
        val accountIds = IdsParam.parse(accountsArg)
        val categoryIds = IdsParam.parse(categoriesArg)
        val trend = ReportRules.trendRange(period.state.range)
        val filters = TrendsFilters(
            period = period.state,
            range = trend.range,
            clipped = trend.clipped,
            type = type,
            accountIds = accountIds,
            accountItems = base.accounts.map { PickItem(it.id, plain(it.name), it.archived) },
            categoryIds = categoryIds,
            categoryItems = base.categories.filter { it.type == type }.sortedWith(categoryOrder).map { PickItem(it.id, plain(it.name), it.archived) },
            accountsText = ReportLabels.accountsText(base.accountsById, accountIds),
            categoriesText = ReportLabels.categoriesText(base.categoriesById, categoryIds),
            filtered = period.isSet || typeArg != null || accountsArg != null || categoriesArg != null,
        )
        when (loaded) {
            Loaded.Loading -> CategoryTrendsUiState.Loading(filters)
            is Loaded.Failed -> CategoryTrendsUiState.Error(filters, loaded.message)
            is Loaded.Ok -> {
                val chosen = categoryIds.toSet()
                val txs = ReportRules.onAccounts(loaded.txs, accountIds)
                    .filter { chosen.isEmpty() || (it.categoryId != null && it.categoryId in chosen) }
                val months = ReportRules.bucketsOf(trend.range, Granularity.Month)
                val matrix = ReportRules.categoryMatrix(txs, type, months)
                var busiest = -1
                matrix.columnTotals.forEachIndexed { i, amount -> if (busiest < 0 || amount > matrix.columnTotals[busiest]) busiest = i }
                CategoryTrendsUiState.Content(
                    filters = filters,
                    months = months,
                    matrix = matrix,
                    average = ReportRules.averageOf(matrix.total, months.size),
                    busiest = busiest,
                    multiYear = ReportRules.spansYears(months),
                    views = matrix.rows.associate { it.categoryId to ReportLabels.categoryView(base.categoriesById, it.categoryId) },
                )
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryTrendsUiState.Loading(initialFilters()))

    private fun initialFilters(): TrendsFilters {
        val state = ReportRules.periodState(
            ReportPreset.fromStored(handle[KEY_PERIOD]),
            handle.get<String?>(KEY_FROM)?.toLocalDateOrNull(),
            handle.get<String?>(KEY_TO)?.toLocalDateOrNull(),
            DEFAULT_PRESET,
            today.date.value,
        )
        val trend = ReportRules.trendRange(state.range)
        return TrendsFilters(state, trend.range, trend.clipped, CategoryType.Expense, emptyList(), emptyList(), emptyList(), emptyList(), plain(""), plain(""), filtered = false)
    }

    fun setPreset(preset: ReportPreset) = period.setPreset(preset, state.value.filters.period.range)

    fun setCustom(start: LocalDate, end: LocalDate) = period.setCustom(start, end)

    /** Switching type drops categories of the other type, which could never match. */
    fun setType(type: CategoryType) {
        val byId = knownCategories.associateBy { it.id }
        val keep = state.value.filters.categoryIds.filter { byId[it]?.type == type }
        handle[KEY_CATEGORIES] = IdsParam.of(keep)
        handle[KEY_TYPE] = type.stored
    }

    fun setAccounts(ids: List<String>) {
        handle[KEY_ACCOUNTS] = IdsParam.of(ids)
    }

    fun setCategories(ids: List<String>) {
        handle[KEY_CATEGORIES] = IdsParam.of(ids)
    }

    fun reset() {
        period.reset()
        handle[KEY_TYPE] = null
        handle[KEY_ACCOUNTS] = null
        handle[KEY_CATEGORIES] = null
    }

    private companion object {
        val DEFAULT_PRESET = ReportPreset.Last6Months
        const val KEY_PERIOD = "period"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_TYPE = "type"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_CATEGORIES = "categories"
    }
}
