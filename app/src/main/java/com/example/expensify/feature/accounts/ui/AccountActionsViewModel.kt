package com.example.expensify.feature.accounts.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the account dialogs is open, and for which account. */
data class AccountDialogs(
    val archiveTarget: Account? = null,
    val deleteTarget: Account? = null,
    val reconcileTarget: Account? = null,
    /** What the reconcile sheet's field holds, as typed. */
    val reconcileInput: String = "",
    /** A write is in flight: buttons disable, dialogs stay. */
    val busy: Boolean = false,
)

/**
 * The account flows every accounts screen shares (port of account.actions.tsx):
 * archive with a warning when the balance isn't zero, restore, delete after
 * a confirmation, and reconcile in a sheet. A failed write reports and keeps
 * the dialog open for another try.
 */
abstract class AccountActionsViewModel(
    private val repository: AccountRepository,
    protected val messages: Messages,
) : ViewModel() {

    private val _dialogs = MutableStateFlow(AccountDialogs())
    val dialogs: StateFlow<AccountDialogs> = _dialogs.asStateFlow()

    /** The detail page leaves after a delete; the list has nothing to do. */
    protected open fun onDeleted(account: Account) = Unit

    /** Archives at once when the balance is zero; otherwise warns first, because the money leaves the total. */
    fun requestArchive(account: Account) {
        if (account.currentBalance.isZero) archive(account) else _dialogs.update { it.copy(archiveTarget = account) }
    }

    fun confirmArchive() {
        _dialogs.value.archiveTarget?.let(::archive)
    }

    private fun archive(account: Account) = attempt(R.string.could_not_archive) {
        repository.setArchived(account.id, archived = true)
        messages.show(res(R.string.account_archived, account.name))
    }

    fun restore(account: Account) = attempt(R.string.could_not_restore) {
        repository.setArchived(account.id, archived = false)
        messages.show(res(R.string.account_restored, account.name))
    }

    fun requestDelete(account: Account) {
        _dialogs.update { it.copy(deleteTarget = account) }
    }

    fun confirmDelete() {
        val target = _dialogs.value.deleteTarget ?: return
        attempt(R.string.could_not_delete) {
            repository.delete(target.id)
            messages.show(res(R.string.account_deleted, target.name))
            onDeleted(target)
        }
    }

    fun openReconcile(account: Account) {
        val shown = AccountRules.flipForLiability(account.type, account.currentBalance)
        _dialogs.update { it.copy(reconcileTarget = account, reconcileInput = moneyInputText(shown)) }
    }

    fun onReconcileInput(text: String) {
        _dialogs.update { it.copy(reconcileInput = text) }
    }

    fun saveReconcile() {
        val account = _dialogs.value.reconcileTarget ?: return
        val typed = Money.parse(_dialogs.value.reconcileInput) ?: return
        // The typed figure (owed for cards and loans) as a stored balance.
        val stored = AccountRules.flipForLiability(account.type, typed)
        val difference = stored - account.currentBalance
        attempt(R.string.could_not_reconcile) {
            repository.reconcile(account.id, stored)
            messages.show(
                if (difference.isZero) {
                    res(R.string.reconcile_already_matches, account.name)
                } else {
                    res(R.string.reconcile_adjusted, account.name)
                },
            )
        }
    }

    fun dismissDialogs() {
        if (!_dialogs.value.busy) _dialogs.value = AccountDialogs()
    }

    /** A write behind a dialog: success closes every dialog; failure reports and leaves it open. */
    private fun attempt(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            _dialogs.update { it.copy(busy = true) }
            try {
                block()
                _dialogs.value = AccountDialogs()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _dialogs.update { it.copy(busy = false) }
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }
}
