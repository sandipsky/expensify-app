package com.example.expensify.feature.accounts.ui

import androidx.lifecycle.viewModelScope
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Content(
        val currency: String,
        val total: Money,
        val includedCount: Int,
        val excludedCount: Int,
        val active: List<Account>,
        val archived: List<Account>,
    ) : AccountsUiState {
        val isEmpty: Boolean get() = active.isEmpty() && archived.isEmpty()
    }
    data class Error(val message: UiText) : AccountsUiState
}

/** `/accounts`: the total of included accounts, each account's balance, and archived ones (req ACC-03). */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    repository: AccountRepository,
    messages: Messages,
) : AccountActionsViewModel(repository, messages) {

    val state: StateFlow<AccountsUiState> = repository.accounts()
        .map<List<Account>, AccountsUiState> { all ->
            val active = all.filter { !it.archived }
            val excluded = active.count { !it.includeInTotal }
            AccountsUiState.Content(
                // Accounts share one currency until multi-currency exists.
                currency = all.firstOrNull()?.currency ?: AccountRules.DEFAULT_CURRENCY,
                total = AccountRules.totalBalance(all),
                includedCount = active.size - excluded,
                excludedCount = excluded,
                active = active,
                archived = all.filter { it.archived },
            )
        }
        .catch { emit(AccountsUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState.Loading)
}
