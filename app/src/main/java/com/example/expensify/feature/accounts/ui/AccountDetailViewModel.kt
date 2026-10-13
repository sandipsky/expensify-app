package com.example.expensify.feature.accounts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.ui.TxRows
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** One day of the account's history, newest first. */
data class DayUi(val date: LocalDate, val label: UiText, val net: Money, val rows: List<EntryRowUi>)

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: UiText) : AccountDetailUiState
    data class Content(
        val account: Account,
        val balance: AccountRules.BalanceView,
        /** The opening figure as the page shows it: the amount owed for cards and loans. */
        val opening: Money,
        val openingLabelRes: Int,
        val utilization: AccountRules.Utilization?,
        val days: List<DayUi>,
        val entryCount: Int,
    ) : AccountDetailUiState
}

sealed interface AccountDetailEvent {
    /** The account was deleted from this page; leave it. */
    data object Left : AccountDetailEvent
}

/** `/accounts/:id` (req ACC-09): the account's figures and every entry touching it, grouped by day. */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: AccountRepository,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    today: Today,
    messages: Messages,
) : AccountActionsViewModel(repository, messages) {

    private val route = savedState.toRoute<Routes.AccountDetail>()

    private val _events = Channel<AccountDetailEvent>(Channel.BUFFERED)
    val events: Flow<AccountDetailEvent> = _events.receiveAsFlow()

    val state: StateFlow<AccountDetailUiState> = combine(
        repository.account(route.id),
        transactions.forAccount(route.id),
        repository.accounts(),
        categories.categories(),
        today.date,
    ) { account, txs, accounts, categories, today ->
        val lookups = TxRows.Lookups(accounts, categories, today)
        val days = TransactionRules.groupByDay(txs) { it.date }.map { day ->
            DayUi(
                date = day.date,
                label = TxRows.dayLabel(day.date, today),
                net = TransactionRules.totalsOf(day.items).net,
                rows = day.items.map { TxRows.toRow(it, lookups) },
            )
        }
        AccountDetailUiState.Content(
            account = account,
            balance = AccountRules.balanceView(account.type, account.currentBalance),
            opening = AccountRules.flipForLiability(account.type, account.openingBalance),
            openingLabelRes = if (account.type.isLiability) R.string.balance_caption_opening_owed else R.string.balance_caption_opening,
            utilization = AccountRules.utilizationView(account),
            days = days,
            entryCount = txs.size,
        ) as AccountDetailUiState
    }
        .catch { error ->
            emit(if (error is AppError.NotFound) AccountDetailUiState.NotFound else AccountDetailUiState.Error(plain(ErrorMessages.of(error))))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState.Loading)

    override fun onDeleted(account: Account) {
        _events.trySend(AccountDetailEvent.Left)
    }
}
