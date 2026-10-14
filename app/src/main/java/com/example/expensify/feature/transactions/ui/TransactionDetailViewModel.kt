package com.example.expensify.feature.transactions.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** A label and its value on the details card. */
data class DetailItem(val label: UiText, val value: UiText)

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data object NotFound : TransactionDetailUiState
    data class Error(val message: UiText) : TransactionDetailUiState
    data class Content(
        val tx: Transaction,
        val row: EntryRowUi,
        /** "Spent", "Received" or "Amount moved". */
        val captionRes: Int,
        /** "Cash", or "Bank → Cash". */
        val accountLabel: UiText,
        val dateLabel: String,
        val details: List<DetailItem>,
        val note: String?,
        val upcoming: Boolean,
        /** Set while the rule that added the entry still exists (req TXN-09). */
        val ruleId: String?,
    ) : TransactionDetailUiState
}

sealed interface TransactionDetailEvent {
    /** The entry was deleted from this page; leave it. */
    data object Left : TransactionDetailEvent
}

/** `/transactions/:id` (req TXN-11): one entry in full, with edit, duplicate, make recurring and delete. */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    recurring: RecurringRepository,
    today: Today,
    messages: Messages,
) : TransactionActionsViewModel(repository, messages) {

    private val route = savedState.toRoute<Routes.TransactionDetail>()

    private val _events = Channel<TransactionDetailEvent>(Channel.BUFFERED)
    val events: Flow<TransactionDetailEvent> = _events.receiveAsFlow()

    val state: StateFlow<TransactionDetailUiState> = combine(
        repository.transaction(route.id),
        accounts.accounts(),
        categories.categories(),
        recurring.rules(),
        today.date,
    ) { tx, accounts, categories, rules, day ->
        val lookups = TxRows.Lookups(accounts, categories, day)
        val transfer = tx.type == TxType.Transfer
        val category = TxRows.categoryOf(lookups, tx)
        val accountLabel = TxRows.accountLabel(lookups, tx.type, tx.accountId, tx.toAccountId)
        val details = buildList {
            add(DetailItem(UiText.Res(R.string.tx_detail_type), UiText.Res(tx.type.labelRes())))
            if (transfer) {
                add(DetailItem(UiText.Res(R.string.tx_form_from_account), TxRows.accountName(lookups, tx.accountId)))
                add(DetailItem(UiText.Res(R.string.tx_form_to_account), TxRows.accountName(lookups, tx.toAccountId)))
            } else {
                add(DetailItem(UiText.Res(R.string.tx_form_category), category.name))
                add(DetailItem(UiText.Res(R.string.tx_form_account), accountLabel))
            }
            add(DetailItem(UiText.Res(R.string.tx_form_date), plain(DateFormats.date(tx.date))))
            add(DetailItem(UiText.Res(R.string.tx_detail_added), plain(DateFormats.dateTime(tx.createdAt))))
            if (tx.updatedAt != tx.createdAt) {
                add(DetailItem(UiText.Res(R.string.tx_detail_last_edited), plain(DateFormats.dateTime(tx.updatedAt))))
            }
        }
        TransactionDetailUiState.Content(
            tx = tx,
            row = TxRows.toRow(tx, lookups),
            captionRes = when (tx.type) {
                TxType.Expense -> R.string.tx_detail_spent
                TxType.Income -> R.string.tx_detail_received
                TxType.Transfer -> R.string.tx_detail_moved
            },
            accountLabel = accountLabel,
            dateLabel = DateFormats.date(tx.date),
            details = details,
            note = tx.note?.trim()?.takeIf { it.isNotEmpty() },
            upcoming = TransactionRules.isUpcoming(tx, day),
            ruleId = tx.recurringRuleId?.takeIf { id -> rules.any { it.id == id } },
        ) as TransactionDetailUiState
    }
        .catch { error ->
            emit(if (error is AppError.NotFound) TransactionDetailUiState.NotFound else TransactionDetailUiState.Error(plain(ErrorMessages.of(error))))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionDetailUiState.Loading)

    override fun onDeleted(tx: Transaction) {
        _events.trySend(TransactionDetailEvent.Left)
    }
}
