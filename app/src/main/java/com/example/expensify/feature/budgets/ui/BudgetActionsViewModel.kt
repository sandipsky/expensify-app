package com.example.expensify.feature.budgets.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The delete confirmation, and whether a write is in flight. */
data class BudgetDialogs(
    val deleteTarget: Budget? = null,
    /** Buttons disable and the dialog stays while a write runs. */
    val busy: Boolean = false,
)

/**
 * The budget flows every budgets screen shares (port of budget.actions.tsx):
 * pause and resume without a confirmation, delete after one (req BUD-06).
 * A failed write reports and keeps the dialog open for another try.
 */
abstract class BudgetActionsViewModel(
    private val repository: BudgetRepository,
    protected val messages: Messages,
) : ViewModel() {

    private val _dialogs = MutableStateFlow(BudgetDialogs())
    val dialogs: StateFlow<BudgetDialogs> = _dialogs.asStateFlow()

    /** The detail page leaves after a delete; the list has nothing to do. */
    protected open fun onDeleted(budget: Budget) = Unit

    /** Pausing keeps the budget's settings; it just stops tracking and alerting. */
    fun pause(budget: Budget) = attempt(R.string.could_not_pause_budget) {
        repository.setActive(budget.id, active = false)
        messages.show(res(R.string.budget_paused, budget.name))
    }

    fun resume(budget: Budget) = attempt(R.string.could_not_resume_budget) {
        repository.setActive(budget.id, active = true)
        messages.show(res(R.string.budget_resumed, budget.name))
    }

    fun requestDelete(budget: Budget) {
        _dialogs.update { it.copy(deleteTarget = budget) }
    }

    fun confirmDelete() {
        val target = _dialogs.value.deleteTarget ?: return
        attempt(R.string.could_not_delete_budget) {
            repository.delete(target.id)
            messages.show(res(R.string.budget_deleted, target.name))
            onDeleted(target)
        }
    }

    fun dismissDialogs() {
        if (!_dialogs.value.busy) _dialogs.value = BudgetDialogs()
    }

    private fun attempt(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            _dialogs.update { it.copy(busy = true) }
            try {
                block()
                _dialogs.value = BudgetDialogs()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _dialogs.update { it.copy(busy = false) }
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }
}
