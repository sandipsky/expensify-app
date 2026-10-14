package com.example.expensify.feature.transactions.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The delete confirmation, and whether the write is in flight. */
data class TransactionDialogs(
    val deleteTarget: Transaction? = null,
    /** Buttons disable and the dialog stays while a write runs. */
    val busy: Boolean = false,
)

/**
 * The transaction flow every screen shares (port of transaction.actions.tsx):
 * delete after a confirmation that says what happens to the balance (req
 * TXN-07). A failed write reports and keeps the dialog open for another try.
 */
abstract class TransactionActionsViewModel(
    private val repository: TransactionRepository,
    protected val messages: Messages,
) : ViewModel() {

    private val _dialogs = MutableStateFlow(TransactionDialogs())
    val dialogs: StateFlow<TransactionDialogs> = _dialogs.asStateFlow()

    /** A page about the entry leaves after a delete; a list has nothing to do. */
    protected open fun onDeleted(tx: Transaction) = Unit

    fun requestDelete(tx: Transaction) {
        _dialogs.update { it.copy(deleteTarget = tx) }
    }

    fun confirmDelete() {
        val target = _dialogs.value.deleteTarget ?: return
        viewModelScope.launch {
            _dialogs.update { it.copy(busy = true) }
            try {
                repository.delete(target.id)
                _dialogs.value = TransactionDialogs()
                messages.show(res(R.string.tx_deleted, MoneyFormat.full(target.amount)))
                onDeleted(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _dialogs.update { it.copy(busy = false) }
                messages.show(res(R.string.could_not_delete_tx, ErrorMessages.of(error)))
            }
        }
    }

    fun dismissDialogs() {
        if (!_dialogs.value.busy) _dialogs.value = TransactionDialogs()
    }
}

/** The delete confirmation, whichever screen opened it: it says what the delete does to the balance. */
@Composable
fun TransactionDialogsHost(dialogs: TransactionDialogs, onConfirmDelete: () -> Unit, onDismiss: () -> Unit) {
    dialogs.deleteTarget?.let { tx ->
        val amount = MoneyFormat.full(tx.amount)
        val effect = stringResource(if (tx.type == TxType.Income) R.string.tx_delete_comes_off else R.string.tx_delete_goes_back)
        val where = stringResource(if (tx.type == TxType.Transfer) R.string.tx_delete_where_transfer else R.string.tx_delete_where_account)
        ConfirmDialog(
            title = stringResource(R.string.tx_delete_dialog_title),
            message = stringResource(R.string.tx_delete_dialog_message, amount, effect, where),
            confirmText = stringResource(R.string.tx_delete_dialog_confirm),
            onConfirm = onConfirmDelete,
            onDismiss = onDismiss,
            destructive = true,
            busy = dialogs.busy,
        )
    }
}
