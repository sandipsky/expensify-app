package com.example.expensify.feature.transactions.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.PeriodPreset
import com.example.expensify.core.date.Periods
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.categoryOrder
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TransactionRules.Filter
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A removable chip for one active filter (req LST-02). */
data class FilterChipUi(val key: String, val label: UiText)

/** What the filters sheet offers: every account, archived ones too, and the categories by type. */
data class FilterOptions(val accounts: List<AccountPick>, val categories: List<CategoryPick>)

/** The period, search and filters: shown in every state, so the user can change them while the list loads. */
data class TxToolbarUi(
    val preset: PeriodPreset,
    /** The range "Custom" shows; it starts as this month, or as the period a link opened. */
    val custom: DateRange,
    val range: DateRange,
    val periodLabel: String,
    val filter: Filter,
    val chips: List<FilterChipUi>,
    val options: FilterOptions,
)

sealed interface TransactionsUiState {
    val toolbar: TxToolbarUi

    data class Loading(override val toolbar: TxToolbarUi) : TransactionsUiState

    data class Content(
        override val toolbar: TxToolbarUi,
        val totals: TransactionRules.Totals,
        val days: List<DayUi>,
        val count: Int,
    ) : TransactionsUiState {
        val isEmpty: Boolean get() = days.isEmpty()
    }

    data class Error(override val toolbar: TxToolbarUi, val message: UiText) : TransactionsUiState
}

/** What the period's query holds, or why it couldn't be read. */
private sealed interface Loaded {
    data object Loading : Loaded
    data class Ok(val txs: List<Transaction>) : Loaded
    data class Failed(val message: UiText) : Loaded
}

/** The period, custom range and filter together, so one `combine` carries them. */
private data class Settings(val preset: PeriodPreset, val custom: DateRange, val filter: Filter)

/**
 * `/transactions` (req LST-01…LST-04, LST-06, LST-08): the period's entries
 * newest first with income, expense and net of what's shown; type, account,
 * category and amount filters that combine; search. One query loads the
 * period; filters and search run on the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val today: Today,
) : ViewModel() {

    private val route = savedState.toRoute<Routes.Transactions>()

    // A link into the list presets the period and the filters (req LST-06); malformed values are dropped.
    private val start: DateRange? = run {
        val from = route.from?.toLocalDateOrNull()
        val to = route.to?.toLocalDateOrNull()
        if (from != null && to != null) Periods.ordered(from, to) else null
    }

    private val preset = MutableStateFlow(start?.let { PeriodPreset.of(it, today.date.value) } ?: PeriodPreset.ThisMonth)
    private val custom = MutableStateFlow(start ?: Periods.month(today.date.value))
    private val filter = MutableStateFlow(
        Filter(
            type = TxType.fromStoredOrNull(route.type),
            categoryIds = idsOf(route.category),
            accountIds = idsOf(route.account),
        ),
    )

    /** The last content built, kept while the same period reloads (see [HomeViewModel.shown]); only the state flow's one collector writes it. */
    @Volatile
    private var shown: TransactionsUiState.Content? = null

    private val range: Flow<DateRange> = combine(preset, custom, today.date) { p, c, day -> p.range(day, c) }.distinctUntilChanged()

    private val loaded: Flow<Loaded> = range.flatMapLatest { r ->
        repository.inRange(r)
            .map<List<Transaction>, Loaded> { Loaded.Ok(it) }
            .onStart { emit(Loaded.Loading) }
            .catch { emit(Loaded.Failed(plain(ErrorMessages.of(it)))) }
    }

    private val settings: Flow<Settings> = combine(preset, custom, filter) { p, c, f -> Settings(p, c, f) }

    val state: StateFlow<TransactionsUiState> = combine(
        loaded,
        accounts.accounts(),
        categories.categories(),
        today.date,
        settings,
    ) { loaded, accounts, categories, day, settings ->
        val lookups = TxRows.Lookups(accounts, categories, day)
        val range = settings.preset.range(day, settings.custom)
        val options = FilterOptions(
            accounts = accounts.map { AccountPick(it.id, it.name, it.icon, it.color, it.archived) },
            categories = categories.sortedWith(categoryOrder).map { CategoryPick(it.id, it.name, it.icon, it.color, it.archived, it.type) },
        )
        val toolbar = TxToolbarUi(
            preset = settings.preset,
            custom = settings.custom,
            range = range,
            periodLabel = DateFormats.period(range),
            filter = settings.filter,
            chips = chipsOf(settings.filter, lookups),
            options = options,
        )
        when (loaded) {
            // The same period and filter loading again (the page came back after its listener was
            // released): the list stays up while it refreshes; skeletons only the first time (design checklist).
            Loaded.Loading -> shown?.takeIf { it.toolbar.range == range && it.toolbar.filter == settings.filter }?.copy(toolbar = toolbar)
                ?: TransactionsUiState.Loading(toolbar)
            is Loaded.Failed -> TransactionsUiState.Error(toolbar, loaded.message)
            is Loaded.Ok -> {
                val visible = TransactionRules.filter(loaded.txs, settings.filter) { TxRows.searchTextOf(it, lookups) }
                TransactionsUiState.Content(
                    toolbar = toolbar,
                    totals = TransactionRules.totalsOf(visible),
                    days = TxRows.days(visible, lookups),
                    count = visible.size,
                ).also { shown = it }
            }
        }
    }
        .catch { emit(TransactionsUiState.Error(currentToolbar(), plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState.Loading(currentToolbar()))

    private fun currentToolbar(): TxToolbarUi {
        val day = today.date.value
        val range = preset.value.range(day, custom.value)
        return TxToolbarUi(preset.value, custom.value, range, DateFormats.period(range), filter.value, emptyList(), FilterOptions(emptyList(), emptyList()))
    }

    fun setPreset(value: PeriodPreset) {
        preset.value = value
    }

    /** Picking an edge of the custom range switches to Custom; reversed dates are swapped. */
    fun setCustomStart(date: LocalDate) = setCustom(date, custom.value.end)
    fun setCustomEnd(date: LocalDate) = setCustom(custom.value.start, date)

    private fun setCustom(start: LocalDate, end: LocalDate) {
        custom.value = Periods.ordered(start, end)
        preset.value = PeriodPreset.Custom
    }

    fun setSearch(text: String) = filter.update { it.copy(search = text) }

    /** The filters sheet's result; the search text passes through untouched. */
    fun applyFilter(next: Filter) = filter.update { next.copy(search = it.search) }

    fun removeChip(key: String) = filter.update { f ->
        when {
            key == CHIP_TYPE -> f.copy(type = null)
            key == CHIP_AMOUNT -> f.copy(minAmount = null, maxAmount = null)
            key.startsWith(CHIP_ACCOUNT) -> f.copy(accountIds = f.accountIds - key.removePrefix(CHIP_ACCOUNT))
            key.startsWith(CHIP_CATEGORY) -> f.copy(categoryIds = f.categoryIds - key.removePrefix(CHIP_CATEGORY))
            else -> f
        }
    }

    /** "Clear all" and "Clear search and filters": back to the whole period. */
    fun clearFilters() {
        filter.value = TransactionRules.NO_FILTER
    }

    private fun chipsOf(filter: Filter, lookups: TxRows.Lookups): List<FilterChipUi> {
        val chips = mutableListOf<FilterChipUi>()
        filter.type?.let { chips += FilterChipUi(CHIP_TYPE, res(it.labelRes())) }
        for (id in filter.accountIds) chips += FilterChipUi(CHIP_ACCOUNT + id, TxRows.accountName(lookups, id))
        for (id in filter.categoryIds) {
            chips += FilterChipUi(CHIP_CATEGORY + id, lookups.categoriesById[id]?.name?.let(::plain) ?: res(R.string.tx_deleted_category))
        }
        val min = filter.minAmount
        val max = filter.maxAmount
        if (min != null || max != null) {
            chips += FilterChipUi(
                CHIP_AMOUNT,
                when {
                    min != null && max != null -> res(R.string.tx_filter_chip_between, MoneyFormat.full(min), MoneyFormat.full(max))
                    min != null -> res(R.string.tx_filter_chip_from, MoneyFormat.full(min))
                    else -> res(R.string.tx_filter_chip_up_to, MoneyFormat.full(max!!))
                },
            )
        }
        return chips
    }

    private companion object {
        const val CHIP_TYPE = "type"
        const val CHIP_AMOUNT = "amount"
        const val CHIP_ACCOUNT = "account:"
        const val CHIP_CATEGORY = "category:"

        /** What a document id can be in a link: letters and digits, as Firestore's auto ids are. */
        val ID = Regex("[A-Za-z0-9]{1,64}")

        /** The ids an id-list argument holds ("id1,id2"); anything that can't be an id is dropped. */
        fun idsOf(param: String?): List<String> = param.orEmpty().split(',').filter { ID.matches(it) }
    }
}
