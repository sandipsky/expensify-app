package com.example.expensify.feature.recurring.ui

import androidx.lifecycle.viewModelScope
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.transactions.ui.TxRows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface RecurringUiState {
    data object Loading : RecurringUiState
    data class Content(val groups: RuleGroups, val today: LocalDate) : RecurringUiState
    data class Error(val message: UiText) : RecurringUiState
}

/**
 * `/recurring` (req REC-07, REC-11): ask-first entries waiting for Confirm,
 * Skip or Edit, then the running rules with their next date, then paused and
 * ended ones. Automatic rules add their entries on their own (the runner).
 */
@HiltViewModel
class RecurringViewModel @Inject constructor(
    repository: RecurringRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    today: Today,
    messages: Messages,
) : RecurringActionsViewModel(repository, messages, today) {

    val state: StateFlow<RecurringUiState> = combine(
        repository.rules(),
        accounts.accounts(),
        categories.categories(),
        today.date,
    ) { rules, accounts, categories, day ->
        val lookups = TxRows.Lookups(accounts, categories, day)
        RecurringUiState.Content(RuleLabels.group(rules, lookups), day) as RecurringUiState
    }
        .catch { emit(RecurringUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState.Loading)
}
