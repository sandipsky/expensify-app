package com.example.expensify.feature.budgets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetResult
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.ui.TxRows
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How many of a period's transactions show before "Show more" (req BUD-05). */
const val BUDGET_PAGE_SIZE = 50

/** One period in the results list: the current one first, then the past ones. */
data class PeriodRowUi(
    /** 0 for the current period, −1 for the one before, … */
    val offset: Int,
    val label: String,
    val result: BudgetResult,
    /** The period ended before the budget was created: shown for comparison only. */
    val beforeBudget: Boolean,
)

sealed interface BudgetDetailUiState {
    data object Loading : BudgetDetailUiState
    data object NotFound : BudgetDetailUiState
    data class Error(val message: UiText) : BudgetDetailUiState
    data class Content(
        val budget: Budget,
        val scope: UiText,
        val icon: BudgetLabels.Icon,
        /** Null while the entries load. */
        val progress: BudgetProgress?,
        val periods: List<PeriodRowUi>,
        val selectedOffset: Int,
        val selectedLabel: String,
        /** The expenses the selected period counted, newest first, one page at a time. */
        val rows: List<EntryRowUi>,
        val hiddenCount: Int,
        val spendingError: UiText?,
    ) : BudgetDetailUiState
}

sealed interface BudgetDetailEvent {
    /** The budget was deleted from this page; leave it. */
    data object Left : BudgetDetailEvent
}

/** The selected period and how many of its rows show; starts over for another budget. */
private data class Selection(val offset: Int = 0, val limit: Int = BUDGET_PAGE_SIZE)

/**
 * `/budgets/:id` (req BUD-05): the budget's current period, its results for
 * past periods, and the transactions behind whichever period is selected.
 * One query covers every period shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: BudgetRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    transactions: TransactionRepository,
    today: Today,
    messages: Messages,
) : BudgetActionsViewModel(repository, messages) {

    private val route = savedState.toRoute<Routes.BudgetDetail>()

    private val _events = Channel<BudgetDetailEvent>(Channel.BUFFERED)
    val events: Flow<BudgetDetailEvent> = _events.receiveAsFlow()

    private val selection = MutableStateFlow(Selection())

    private val budget: Flow<Budget?> = repository.budgets().map { all -> all.firstOrNull { it.id == route.id } }

    private val spending: Flow<Spending> = combine(budget, today.date) { budget, day ->
        budget?.let { BudgetRules.neededRange(it, day, BudgetRules.historyPeriods(it.period)) }
    }
        .distinctUntilChanged()
        .flatMapLatest { range -> transactions.spendingOver(range) }

    val state: StateFlow<BudgetDetailUiState> = combine(
        combine(repository.budgets(), categories.categories(), accounts.accounts()) { b, c, a -> Triple(b, c, a) },
        today.date,
        spending,
        selection,
    ) { (all, categories, accounts), day, spending, selection ->
        val budget = all.firstOrNull { it.id == route.id } ?: return@combine BudgetDetailUiState.NotFound
        val byId = categories.associateBy { it.id }
        val txs = (spending as? Spending.Loaded)?.txs
        val progress = txs?.let { BudgetRules.progress(budget, it, day) }

        val periods = if (progress == null) {
            emptyList()
        } else {
            listOf(PeriodRowUi(0, DateFormats.period(progress.result.range), progress.result, beforeBudget = false)) +
                BudgetRules.history(budget, txs, day, BudgetRules.historyPeriods(budget.period)).mapIndexed { i, entry ->
                    PeriodRowUi(-(i + 1), DateFormats.period(entry.result.range), entry.result, entry.beforeBudget)
                }
        }
        val selected = periods.firstOrNull { it.offset == selection.offset } ?: periods.firstOrNull()

        val lookups = TxRows.Lookups(accounts, categories, day)
        val counted = if (selected == null || txs == null) {
            emptyList()
        } else {
            txs.filter { it.date in selected.result.range && BudgetRules.countsToward(budget.categoryIds, it) }
                .sortedWith(TransactionRules.newestFirst)
        }
        val rows = counted.take(selection.limit).map { TxRows.toRow(it, lookups) }

        BudgetDetailUiState.Content(
            budget = budget,
            scope = BudgetLabels.scopeOf(budget, byId),
            icon = BudgetLabels.iconOf(budget, byId),
            progress = progress,
            periods = periods,
            selectedOffset = selected?.offset ?: 0,
            selectedLabel = selected?.label.orEmpty(),
            rows = rows,
            hiddenCount = counted.size - rows.size,
            spendingError = (spending as? Spending.Failed)?.message,
        ) as BudgetDetailUiState
    }
        .catch { emit(BudgetDetailUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetDetailUiState.Loading)

    fun selectPeriod(offset: Int) {
        selection.value = Selection(offset)
    }

    fun showMore() {
        selection.update { it.copy(limit = it.limit + BUDGET_PAGE_SIZE) }
    }

    override fun onDeleted(budget: Budget) {
        _events.trySend(BudgetDetailEvent.Left)
    }
}
