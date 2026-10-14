package com.example.expensify.feature.transactions.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.ui.moneyInputText
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionBody
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the form is for: a new entry, an edit, or confirming a recurring entry as edited. */
sealed interface EntryFormMode {
    data object Add : EntryFormMode

    /** `ruleId` is set while the rule that added the entry still exists (req TXN-09). */
    data class Edit(val tx: Transaction, val ruleId: String?) : EntryFormMode

    /** The ask-first entry due on `date`: the rule writes it, as edited here (req REC-07). */
    data class Confirm(val rule: RecurringRule, val date: LocalDate) : EntryFormMode
}

data class EntryFormUiState(
    val loading: Boolean = true,
    val mode: EntryFormMode = EntryFormMode.Add,
    val type: TxType = TxType.Expense,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate,
    val today: LocalDate,
    val note: String = "",
    /** Active accounts, plus an archived one the entry already uses. */
    val accounts: List<AccountPick> = emptyList(),
    /** The type's categories; empty for transfers. */
    val categories: List<CategoryPick> = emptyList(),
    val hasAccounts: Boolean = true,
    val amountError: UiText? = null,
    val accountError: UiText? = null,
    val toAccountError: UiText? = null,
    val categoryError: UiText? = null,
    val noteError: UiText? = null,
    /** A refused save. */
    val error: UiText? = null,
    val saving: Boolean = false,
    val addingDefaults: Boolean = false,
) {
    val isEdit: Boolean get() = mode is EntryFormMode.Edit
    val isConfirm: Boolean get() = mode is EntryFormMode.Confirm
    val isTransfer: Boolean get() = type == TxType.Transfer

    /** A future date: it shows as upcoming, and balances change now (req TXN-10). */
    val upcoming: Boolean get() = date > today
}

sealed interface EntryFormEvent {
    data object Saved : EntryFormEvent

    /** "Save and add another" cleared the amount; put the cursor back on it. */
    data object FocusAmount : EntryFormEvent
    data class Duplicate(val id: String) : EntryFormEvent
    data class MakeRecurring(val id: String) : EntryFormEvent
    data class OpenRule(val ruleId: String) : EntryFormEvent

    /** The entry was deleted from the form; leave it. */
    data object Left : EntryFormEvent
}

/**
 * Add or edit a transaction (req TXN-01…TXN-06, TXN-08): type, amount, a
 * category grid or the two accounts of a transfer, account, date with Today
 * and Yesterday, and a note. New entries start as an expense, today, on the
 * account this device used last. Also confirms an ask-first recurring entry
 * as edited, which the rule then writes.
 */
@HiltViewModel
class EntryFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: TransactionRepository,
    private val accountsRepository: AccountRepository,
    private val categoriesRepository: CategoryRepository,
    private val recurring: RecurringRepository,
    private val device: DeviceState,
    today: Today,
    messages: Messages,
) : TransactionActionsViewModel(repository, messages) {

    private val route = savedState.toRoute<Routes.EntryForm>()

    private val _state = MutableStateFlow(EntryFormUiState(date = today.date.value, today = today.date.value))
    val state: StateFlow<EntryFormUiState> = _state.asStateFlow()

    private val _events = Channel<EntryFormEvent>(Channel.BUFFERED)
    val events: Flow<EntryFormEvent> = _events.receiveAsFlow()

    private var allAccounts: List<Account> = emptyList()
    private var allCategories: List<Category> = emptyList()

    /** The category of each type the user last had, so switching type and back keeps it. */
    private val categoryByType = mutableMapOf<TxType, String?>()

    init {
        viewModelScope.launch {
            try {
                val (accounts, categories) = combine(accountsRepository.accounts(), categoriesRepository.categories()) { a, c -> a to c }.first()
                allAccounts = accounts
                allCategories = categories
                val day = today.date.value
                val editId = route.editId
                val duplicateOf = route.duplicateOf
                val ruleId = route.ruleId
                val dueDate = route.dueDate?.toLocalDateOrNull()
                when {
                    editId != null -> {
                        val tx = repository.get(editId)
                        val rule = tx.recurringRuleId?.let { id -> recurring.rules().first().firstOrNull { it.id == id } }
                        prefill(TransactionRules.toBody(tx), EntryFormMode.Edit(tx, rule?.id))
                    }
                    duplicateOf != null -> {
                        // A duplicate is a new entry dated today (req TXN-08).
                        val tx = repository.get(duplicateOf)
                        prefill(TransactionRules.toBody(tx).copy(date = day), EntryFormMode.Add)
                    }
                    ruleId != null && dueDate != null -> {
                        val rule = recurring.rules().first().firstOrNull { it.id == ruleId } ?: recurring.get(ruleId)
                        prefill(RecurringRules.occurrenceOf(rule.template, dueDate), EntryFormMode.Confirm(rule, dueDate))
                    }
                    else -> {
                        val last = device.lastAccountId.first()
                        val choices = accounts.map { TransactionRules.AccountChoice(it.id, it.archived) }
                        _state.update {
                            it.copy(
                                loading = false,
                                date = day,
                                accountId = route.accountId ?: TransactionRules.defaultAccountId(choices, last),
                            )
                        }
                        refreshOptions()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = plain(ErrorMessages.of(error))) }
            }
        }
        viewModelScope.launch {
            combine(accountsRepository.accounts(), categoriesRepository.categories()) { a, c -> a to c }
                .catch { }
                .collect { (accounts, categories) ->
                    allAccounts = accounts
                    allCategories = categories
                    refreshOptions()
                }
        }
        viewModelScope.launch {
            today.date.collect { day -> _state.update { it.copy(today = day) } }
        }
    }

    private fun prefill(body: TransactionBody, mode: EntryFormMode) {
        _state.update {
            it.copy(
                loading = false,
                mode = mode,
                type = body.type,
                amountText = moneyInputText(body.amount),
                accountId = body.accountId,
                toAccountId = body.toAccountId,
                categoryId = body.categoryId,
                date = body.date,
                note = body.note.orEmpty(),
            )
        }
        refreshOptions()
    }

    /** Pickers show active accounts and categories, plus an archived one the entry already uses (req TXN-03). */
    private fun refreshOptions() {
        _state.update { s ->
            s.copy(
                accounts = TxRows.accountPicks(allAccounts, listOf(s.accountId, s.toAccountId)),
                categories = TxRows.categoryPicks(allCategories, s.type, s.categoryId),
                hasAccounts = allAccounts.any { !it.archived },
            )
        }
    }

    fun onTypeChange(next: TxType) {
        val current = _state.value.type
        if (next == current) return
        if (current != TxType.Transfer) categoryByType[current] = _state.value.categoryId
        _state.update {
            it.copy(
                type = next,
                categoryId = if (next == TxType.Transfer) null else categoryByType[next],
                toAccountId = if (next == TxType.Transfer) it.toAccountId else null,
                categoryError = null,
                toAccountError = null,
            )
        }
        refreshOptions()
    }

    fun onAmountChange(value: String) = _state.update { it.copy(amountText = value, amountError = null) }
    fun onNoteChange(value: String) = _state.update { it.copy(note = value, noteError = null) }
    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }

    /** The Today and Yesterday shortcuts (req TXN-02). */
    fun setDaysAgo(days: Long) = _state.update { it.copy(date = it.today.minusDays(days)) }

    fun onAccountChange(id: String) {
        _state.update { it.copy(accountId = id, accountError = null) }
        refreshOptions()
    }

    fun onToAccountChange(id: String) {
        _state.update { it.copy(toAccountId = id, toAccountError = null) }
        refreshOptions()
    }

    fun onCategoryChange(id: String) {
        _state.update { it.copy(categoryId = id, categoryError = null) }
        refreshOptions()
    }

    /** Adds the usual categories, so the grid has something to pick from (req TXN-04). */
    fun addDefaults() {
        if (_state.value.addingDefaults) return
        viewModelScope.launch {
            _state.update { it.copy(addingDefaults = true) }
            try {
                val added = categoriesRepository.addDefaults()
                messages.show(res(if (added > 0) R.string.defaults_added else R.string.defaults_nothing_to_add))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.show(res(R.string.could_not_add_defaults, ErrorMessages.of(error)))
            } finally {
                _state.update { it.copy(addingDefaults = false) }
            }
        }
    }

    /** Writes the entry; with `another`, keeps type, account and date for the next one (req TXN-05). */
    fun save(another: Boolean = false) {
        val s = _state.value
        if (s.saving || s.loading) return

        val amount = Money.parse(s.amountText)
        val amountError = when {
            amount == null -> res(R.string.tx_form_error_amount)
            !amount.isPositive -> res(R.string.tx_form_error_amount_positive)
            else -> null
        }
        val accountError = if (s.accountId == null) res(R.string.tx_form_error_account) else null
        val toAccountError = when {
            !s.isTransfer -> null
            s.toAccountId == null -> res(R.string.tx_form_error_account)
            s.toAccountId == s.accountId -> res(R.string.tx_form_error_same_account)
            else -> null
        }
        val categoryError = if (!s.isTransfer && s.categoryId == null) res(R.string.tx_form_error_category) else null
        val note = s.note.trim()
        val noteError = if (note.length > TransactionRules.MAX_NOTE_LENGTH) res(R.string.tx_form_error_note_long) else null
        if (listOf(amountError, accountError, toAccountError, categoryError, noteError).any { it != null }) {
            _state.update {
                it.copy(
                    amountError = amountError,
                    accountError = accountError,
                    toAccountError = toAccountError,
                    categoryError = categoryError,
                    noteError = noteError,
                )
            }
            return
        }

        val body = TransactionBody(
            type = s.type,
            amount = amount!!,
            accountId = s.accountId!!,
            toAccountId = if (s.isTransfer) s.toAccountId else null,
            categoryId = if (s.isTransfer) null else s.categoryId,
            date = s.date,
            note = note.ifEmpty { null },
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                when (val mode = s.mode) {
                    is EntryFormMode.Confirm -> confirmOccurrence(mode, body)
                    is EntryFormMode.Edit -> {
                        val saved = repository.update(mode.tx.id, body)
                        device.setLastAccountId(saved.accountId)
                        messages.show(res(R.string.tx_updated, summary(body)))
                        _events.send(EntryFormEvent.Saved)
                    }
                    EntryFormMode.Add -> {
                        val saved = repository.create(body)
                        device.setLastAccountId(saved.accountId)
                        messages.show(res(R.string.tx_added, res(body.type.labelRes()), summary(body)))
                        if (another) startAnother() else _events.send(EntryFormEvent.Saved)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }

    /** The rule writes the entry as edited; a date another device already handled isn't an error (req §9.1). */
    private suspend fun confirmOccurrence(mode: EntryFormMode.Confirm, body: TransactionBody) {
        val rule = mode.rule
        try {
            recurring.addOccurrences(rule.id, listOf(mode.date), RecurringRules.nextAfter(rule, mode.date), entry = body)
            val name = body.note?.takeIf { it.isNotBlank() }?.let(::plain)
                ?: allCategories.firstOrNull { it.id == body.categoryId }?.name?.let(::plain)
                ?: res(R.string.tx_type_transfer)
            val amount = MoneyFormat.full(body.type.signed(body.amount), if (body.type == TxType.Transfer) Sign.Auto else Sign.ExceptZero)
            messages.show(res(R.string.rule_entry_added, name, amount))
        } catch (_: AppError.Stale) {
            messages.show(res(R.string.rule_already_handled))
        }
        _events.send(EntryFormEvent.Saved)
    }

    /** Clears what differs between entries and goes back to the amount. */
    private suspend fun startAnother() {
        categoryByType.clear()
        _state.update { it.copy(saving = false, amountText = "", categoryId = null, note = "", error = null) }
        refreshOptions()
        _events.send(EntryFormEvent.FocusAmount)
    }

    private fun summary(body: TransactionBody): UiText =
        TxRows.summary(body.type, body.amount, body.accountId, body.toAccountId, body.categoryId, allAccounts, allCategories)

    fun duplicate() {
        val mode = _state.value.mode as? EntryFormMode.Edit ?: return
        _events.trySend(EntryFormEvent.Duplicate(mode.tx.id))
    }

    fun makeRecurring() {
        val mode = _state.value.mode as? EntryFormMode.Edit ?: return
        _events.trySend(EntryFormEvent.MakeRecurring(mode.tx.id))
    }

    fun openRule() {
        val mode = _state.value.mode as? EntryFormMode.Edit ?: return
        mode.ruleId?.let { _events.trySend(EntryFormEvent.OpenRule(it)) }
    }

    fun requestDeleteEditing() {
        val mode = _state.value.mode as? EntryFormMode.Edit ?: return
        requestDelete(mode.tx)
    }

    override fun onDeleted(tx: Transaction) {
        _events.trySend(EntryFormEvent.Left)
    }
}
