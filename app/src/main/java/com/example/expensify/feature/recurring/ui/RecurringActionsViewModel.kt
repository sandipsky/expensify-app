package com.example.expensify.feature.recurring.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRules
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The delete confirmation, and whether a write is in flight. */
data class RuleDialogs(
    val deleteTarget: RuleView? = null,
    /** Buttons disable and the dialog stays while a write runs. */
    val busy: Boolean = false,
)

/**
 * The recurring flows every screen shares (port of recurring.actions.tsx):
 * pause and resume without a confirmation, delete behind one (req REC-09,
 * REC-10), and Confirm or Skip for entries that ask first (req REC-07).
 */
abstract class RecurringActionsViewModel(
    private val repository: RecurringRepository,
    protected val messages: Messages,
    private val today: Today,
) : ViewModel() {

    private val _dialogs = MutableStateFlow(RuleDialogs())
    val dialogs: StateFlow<RuleDialogs> = _dialogs.asStateFlow()

    /** The rule's page leaves after a delete; the list has nothing to do. */
    protected open fun onDeleted(view: RuleView) = Unit

    /** No new entries while paused. */
    fun pause(view: RuleView) = report(R.string.could_not_pause_rule) {
        repository.setActive(view.id, active = false)
        messages.show(res(R.string.rule_paused, view.name))
    }

    /** Picks up from today; dates that passed while paused are left out (req REC-09). */
    fun resume(view: RuleView) = report(R.string.could_not_resume_rule) {
        val next = RecurringRules.resumedDueDate(view.rule, view.rule.nextDueDate, today.date.value)
        repository.setActive(view.id, active = true, nextDueDate = next)
        messages.show(res(R.string.rule_resumed, view.name))
    }

    fun requestDelete(view: RuleView) {
        _dialogs.update { it.copy(deleteTarget = view) }
    }

    fun confirmDelete() {
        val target = _dialogs.value.deleteTarget ?: return
        viewModelScope.launch {
            _dialogs.update { it.copy(busy = true) }
            try {
                repository.delete(target.id)
                _dialogs.value = RuleDialogs()
                messages.show(res(R.string.rule_deleted, target.name))
                onDeleted(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _dialogs.update { it.copy(busy = false) }
                messages.show(res(R.string.could_not_delete_rule, ErrorMessages.of(error)))
            }
        }
    }

    fun dismissDialogs() {
        if (!_dialogs.value.busy) _dialogs.value = RuleDialogs()
    }

    /** Adds the ask-first entry due on `date` as the rule has it. */
    fun confirm(view: RuleView, date: LocalDate) = handle(R.string.could_not_add_entry) {
        repository.addOccurrences(view.id, listOf(date), RecurringRules.nextAfter(view.rule, date))
        val amount = MoneyFormat.full(view.amount, if (view.kind == AmountKind.Transfer) Sign.Auto else Sign.ExceptZero)
        messages.show(res(R.string.rule_entry_added, view.name, amount))
    }

    /** Passes over the entry due on `date` without adding anything. */
    fun skip(view: RuleView, date: LocalDate) = handle(R.string.could_not_skip_entry) {
        repository.skip(view.id, date, RecurringRules.nextAfter(view.rule, date))
        messages.show(res(R.string.rule_entry_skipped, view.name, DateFormats.date(date)))
    }

    /** A date another device already handled isn't an error; anything else is (req §9.1). */
    private fun handle(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: AppError.Stale) {
                messages.show(res(R.string.rule_already_handled))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }

    private fun report(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }
}

/** The delete confirmation, whichever recurring screen opened it. */
@Composable
fun RuleDialogsHost(dialogs: RuleDialogs, onConfirmDelete: () -> Unit, onDismiss: () -> Unit) {
    dialogs.deleteTarget?.let { view ->
        ConfirmDialog(
            title = stringResource(R.string.rule_delete_dialog_title),
            message = stringResource(R.string.rule_delete_dialog_message, view.name.asString()),
            confirmText = stringResource(R.string.rule_delete_dialog_confirm),
            onConfirm = onConfirmDelete,
            onDismiss = onDismiss,
            destructive = true,
            busy = dialogs.busy,
        )
    }
}
