package com.example.expensify.feature.budgets.ui

import androidx.lifecycle.viewModelScope
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One budget as the list draws it; the ViewModel resolves names, the card only draws. */
data class BudgetCardUi(
    val budget: Budget,
    /** "All expenses", or the categories it counts. */
    val scope: UiText,
    val icon: BudgetLabels.Icon,
    /** The current figures; null while they load, and always for a paused budget. */
    val progress: BudgetProgress?,
)

sealed interface BudgetsUiState {
    data object Loading : BudgetsUiState
    data class Content(
        val active: List<BudgetCardUi>,
        val paused: List<BudgetCardUi>,
        /** The entries couldn't be read: cards show without figures. */
        val spendingError: UiText?,
    ) : BudgetsUiState {
        val isEmpty: Boolean get() = active.isEmpty() && paused.isEmpty()
    }
    data class Error(val message: UiText) : BudgetsUiState
}

/**
 * `/budgets` (req BUD-02, BUD-04): every active budget with its current
 * period, then the paused ones. All of them read one query over the dates
 * they need, the same one the alerts read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    repository: BudgetRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    today: Today,
    messages: Messages,
) : BudgetActionsViewModel(repository, messages) {

    private val spending: Flow<Spending> = combine(repository.budgets(), today.date) { all, day ->
        BudgetRules.spanOf(all.filter { it.active }.map { BudgetRules.neededRange(it, day) })
    }
        .distinctUntilChanged()
        .flatMapLatest { range -> transactions.spendingOver(range) }

    val state: StateFlow<BudgetsUiState> = combine(
        repository.budgets(),
        categories.categories(),
        today.date,
        spending,
    ) { all, categories, day, spending ->
        val byId = categories.associateBy { it.id }
        val txs = (spending as? Spending.Loaded)?.txs
        val cards = all.map { budget ->
            BudgetCardUi(
                budget = budget,
                scope = BudgetLabels.scopeOf(budget, byId),
                icon = BudgetLabels.iconOf(budget, byId),
                progress = if (budget.active && txs != null) BudgetRules.progress(budget, txs, day) else null,
            )
        }
        BudgetsUiState.Content(
            active = cards.filter { it.budget.active },
            paused = cards.filter { !it.budget.active },
            spendingError = (spending as? Spending.Failed)?.message,
        ) as BudgetsUiState
    }
        .catch { emit(BudgetsUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState.Loading)
}
