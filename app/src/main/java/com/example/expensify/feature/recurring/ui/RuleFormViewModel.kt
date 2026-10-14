package com.example.expensify.feature.recurring.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
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
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RecurringTemplate
import com.example.expensify.feature.recurring.domain.RuleBody
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.recurring.domain.RuleSpec
import com.example.expensify.feature.recurring.domain.Schedule
import com.example.expensify.feature.recurring.domain.ScheduleSpec
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.AccountPick
import com.example.expensify.feature.transactions.ui.CategoryPick
import com.example.expensify.feature.transactions.ui.TxRows
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

data class RuleFormUiState(
    val loading: Boolean = true,
    val editing: RecurringRule? = null,
    val today: LocalDate,
    // The entry it adds.
    val type: TxType = TxType.Expense,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    // The schedule.
    val frequency: Frequency = Frequency.Monthly,
    val intervalText: String = "1",
    /** Every frequency keeps a weekday list and a day ready, for switching to weekly or monthly. */
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int = 1,
    val startDate: LocalDate,
    val endType: EndType = EndType.Never,
    val maxCountText: String = "",
    val endDate: LocalDate? = null,
    val mode: RuleMode = RuleMode.Auto,
    // Pickers.
    val accounts: List<AccountPick> = emptyList(),
    val categories: List<CategoryPick> = emptyList(),
    // "Next: Thu, 1 Oct 2026 · …", and how many a past start date adds at once (req REC-05).
    val preview: UiText? = null,
    val pastHint: UiText? = null,
    // Errors.
    val amountError: UiText? = null,
    val accountError: UiText? = null,
    val toAccountError: UiText? = null,
    val categoryError: UiText? = null,
    val noteError: UiText? = null,
    val intervalError: UiText? = null,
    val weekdaysError: UiText? = null,
    val countError: UiText? = null,
    val endDateError: UiText? = null,
    /** A refused save. */
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editing != null
    val isTransfer: Boolean get() = type == TxType.Transfer
}

sealed interface RuleFormEvent {
    data object Saved : RuleFormEvent
}

/**
 * Add or edit a recurring rule (req REC-01…REC-05, REC-08): the entry it
 * adds, how often, when it starts and ends, and whether each entry is added
 * automatically or asks first. A preview lists the next dates. An edit
 * reaches entries from now on only. "Make recurring" starts from a
 * transaction, one period after it.
 */
@HiltViewModel
class RuleFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: RecurringRepository,
    private val accountsRepository: AccountRepository,
    private val categoriesRepository: CategoryRepository,
    private val transactions: TransactionRepository,
    device: DeviceState,
    private val today: Today,
    private val messages: Messages,
) : ViewModel() {

    private val route = savedState.toRoute<Routes.RuleForm>()

    private val _state = MutableStateFlow(RuleFormUiState(today = today.date.value, startDate = today.date.value))
    val state: StateFlow<RuleFormUiState> = _state.asStateFlow()

    private val _events = Channel<RuleFormEvent>(Channel.BUFFERED)
    val events: Flow<RuleFormEvent> = _events.receiveAsFlow()

    private var allAccounts: List<Account> = emptyList()
    private var allCategories: List<Category> = emptyList()

    /** The date of the entry being made recurring: the start stays one period after it until the user picks one. */
    private var fromDate: LocalDate? = null

    /** Schedule fields the user has set, so they stop following the start date or the frequency. */
    private var weekdaysPicked = false
    private var dayPicked = false
    private var startPicked = false

    /** The category of each type the user last had, so switching type and back keeps it. */
    private val categoryByType = mutableMapOf<TxType, String?>()

    init {
        viewModelScope.launch {
            try {
                val (accounts, categories) = combine(accountsRepository.accounts(), categoriesRepository.categories()) { a, c -> a to c }.first()
                allAccounts = accounts
                allCategories = categories
                val day = today.date.value
                val id = route.id
                val fromTransaction = route.fromTransaction
                when {
                    id != null -> {
                        val rule = repository.rules().first().firstOrNull { it.id == id } ?: repository.get(id)
                        weekdaysPicked = true
                        dayPicked = true
                        startPicked = true
                        _state.update {
                            it.copy(
                                loading = false,
                                editing = rule,
                                type = rule.template.type,
                                amountText = moneyInputText(rule.template.amount),
                                accountId = rule.template.accountId,
                                toAccountId = rule.template.toAccountId,
                                categoryId = rule.template.categoryId,
                                note = rule.template.note.orEmpty(),
                                frequency = rule.frequency,
                                intervalText = rule.interval.toString(),
                                weekdays = RecurringRules.weekdaysOf(rule),
                                dayOfMonth = RecurringRules.dayOfMonthOf(rule),
                                startDate = rule.startDate,
                                endType = rule.endType,
                                maxCountText = rule.maxCount?.toString().orEmpty(),
                                endDate = rule.endDate,
                                mode = rule.mode,
                            )
                        }
                    }
                    fromTransaction != null -> {
                        // "Make recurring": the entry's fields, repeating monthly from one period after it (req TXN-09).
                        val tx = transactions.get(fromTransaction)
                        val template = RecurringRules.templateOf(tx)
                        val after = RecurringRules.scheduleAfter(tx.date, Frequency.Monthly)
                        fromDate = tx.date
                        _state.update {
                            it.copy(
                                loading = false,
                                type = template.type,
                                amountText = moneyInputText(template.amount),
                                accountId = template.accountId,
                                toAccountId = template.toAccountId,
                                categoryId = template.categoryId,
                                note = template.note.orEmpty(),
                                frequency = after.frequency,
                                weekdays = RecurringRules.weekdaysOf(after),
                                dayOfMonth = RecurringRules.dayOfMonthOf(after),
                                startDate = after.startDate,
                            )
                        }
                    }
                    else -> {
                        val last = device.lastAccountId.first()
                        val choices = accounts.map { TransactionRules.AccountChoice(it.id, it.archived) }
                        _state.update {
                            it.copy(
                                loading = false,
                                accountId = TransactionRules.defaultAccountId(choices, last),
                                weekdays = listOf(day.dayOfWeek.value),
                                dayOfMonth = day.dayOfMonth,
                                startDate = day,
                            )
                        }
                    }
                }
                refreshOptions()
                recompute()
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
            today.date.collect { day ->
                _state.update { it.copy(today = day) }
                recompute()
            }
        }
    }

    private fun refreshOptions() {
        _state.update { s ->
            s.copy(
                accounts = TxRows.accountPicks(allAccounts, listOf(s.accountId, s.toAccountId)),
                categories = TxRows.categoryPicks(allCategories, s.type, s.categoryId),
            )
        }
    }

    /** The schedule as the form stands, or null while a field it needs is incomplete. */
    private fun scheduleOf(s: RuleFormUiState): ScheduleSpec? {
        val interval = s.intervalText.trim().toIntOrNull() ?: return null
        if (interval !in 1..RecurringRules.MAX_INTERVAL) return null
        if (s.frequency == Frequency.Weekly && s.weekdays.isEmpty()) return null
        return ScheduleSpec(
            frequency = s.frequency,
            interval = interval,
            weekdays = if (s.frequency == Frequency.Weekly) s.weekdays.sorted() else emptyList(),
            dayOfMonth = if (s.frequency == Frequency.Monthly) s.dayOfMonth else null,
            startDate = s.startDate,
        )
    }

    /** A new rule starts at its first date; an edit moves on only when the schedule changed (req REC-08). */
    private fun nextDueDateOf(schedule: Schedule, editing: RecurringRule?): LocalDate = when {
        editing == null -> RecurringRules.firstDueDate(schedule)
        RecurringRules.sameSchedule(schedule, editing) -> editing.nextDueDate
        else -> RecurringRules.rescheduledDueDate(schedule, editing.nextDueDate, today.date.value)
    }

    /** The preview of the next dates, and how many a past start date would add at once (req REC-05). */
    private fun recompute() {
        _state.update { s ->
            val schedule = scheduleOf(s) ?: return@update s.copy(preview = null, pastHint = null)
            val spec = RuleSpec(
                frequency = schedule.frequency,
                interval = schedule.interval,
                weekdays = schedule.weekdays,
                dayOfMonth = schedule.dayOfMonth,
                startDate = schedule.startDate,
                endType = s.endType,
                endDate = if (s.endType == EndType.Until) s.endDate else null,
                maxCount = if (s.endType == EndType.Count) s.maxCountText.trim().toIntOrNull() else null,
                occurrences = s.editing?.occurrences ?: 0,
                nextDueDate = nextDueDateOf(schedule, s.editing),
            )
            val next = RecurringRules.pendingDates(spec, 3)
            val preview = if (next.isEmpty()) {
                res(R.string.rule_form_preview_ended)
            } else {
                res(R.string.rule_form_preview, next.joinToString(" · ") { DateFormats.date(it) })
            }
            var pastHint: UiText? = null
            if (s.editing == null) {
                val count = RecurringRules.pendingDates(spec, RecurringRules.MAX_OCCURRENCES + 1, s.today).size
                if (count > 0) {
                    val entries: UiText = if (count > RecurringRules.MAX_OCCURRENCES) {
                        res(R.string.rule_form_more_than, RecurringRules.MAX_OCCURRENCES)
                    } else {
                        RuleLabels.entryCount(count)
                    }
                    pastHint = res(if (s.mode == RuleMode.Confirm) R.string.rule_form_past_confirm else R.string.rule_form_past_auto, entries)
                }
            }
            s.copy(preview = preview, pastHint = pastHint)
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

    /** "Make recurring": the start stays one period after the entry for the frequency chosen. */
    fun onFrequencyChange(next: Frequency) {
        _state.update { s ->
            var updated = s.copy(frequency = next, weekdaysError = null, intervalError = null)
            val from = fromDate
            if (from != null && !startPicked) {
                val after = RecurringRules.scheduleAfter(from, next)
                updated = updated.copy(
                    startDate = after.startDate,
                    weekdays = if (weekdaysPicked) s.weekdays else RecurringRules.weekdaysOf(after),
                    dayOfMonth = if (dayPicked) s.dayOfMonth else RecurringRules.dayOfMonthOf(after),
                )
            }
            updated
        }
        recompute()
    }

    fun onIntervalChange(value: String) {
        _state.update { it.copy(intervalText = value.filter { c -> c.isDigit() }.take(2), intervalError = null) }
        recompute()
    }

    fun toggleWeekday(day: Int) {
        weekdaysPicked = true
        _state.update { s ->
            val days = if (day in s.weekdays) s.weekdays - day else (s.weekdays + day).sorted()
            s.copy(weekdays = days, weekdaysError = null)
        }
        recompute()
    }

    fun onDayOfMonthChange(day: Int) {
        dayPicked = true
        _state.update { it.copy(dayOfMonth = day) }
        recompute()
    }

    /** A new rule's weekday and day of the month follow its start date until the user picks them. */
    fun onStartDateChange(date: LocalDate) {
        startPicked = true
        _state.update { s ->
            var updated = s.copy(startDate = date, endDateError = null)
            if (s.editing == null && fromDate == null) {
                if (!weekdaysPicked) updated = updated.copy(weekdays = listOf(date.dayOfWeek.value))
                if (!dayPicked) updated = updated.copy(dayOfMonth = date.dayOfMonth)
            }
            updated
        }
        recompute()
    }

    /** "After" starts from a year of monthly entries. */
    fun onEndTypeChange(next: EndType) {
        _state.update { s ->
            s.copy(
                endType = next,
                maxCountText = if (next == EndType.Count && s.maxCountText.isBlank()) "12" else s.maxCountText,
                countError = null,
                endDateError = null,
            )
        }
        recompute()
    }

    fun onMaxCountChange(value: String) {
        _state.update { it.copy(maxCountText = value.filter { c -> c.isDigit() }.take(3), countError = null) }
        recompute()
    }

    fun onEndDateChange(date: LocalDate) {
        _state.update { it.copy(endDate = date, endDateError = null) }
        recompute()
    }

    fun onModeChange(mode: RuleMode) {
        _state.update { it.copy(mode = mode) }
        recompute()
    }

    fun save() {
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
        val interval = s.intervalText.trim().toIntOrNull()
        val intervalError = if (interval == null || interval !in 1..RecurringRules.MAX_INTERVAL) res(R.string.rule_form_error_interval) else null
        val weekdaysError = if (s.frequency == Frequency.Weekly && s.weekdays.isEmpty()) res(R.string.rule_form_error_weekdays) else null
        val maxCount = s.maxCountText.trim().toIntOrNull()
        val countError = if (s.endType == EndType.Count && (maxCount == null || maxCount !in 1..RecurringRules.MAX_OCCURRENCES)) {
            res(R.string.rule_form_error_count)
        } else {
            null
        }
        val endDateError = when {
            s.endType != EndType.Until -> null
            s.endDate == null -> res(R.string.rule_form_error_end_date)
            s.endDate < s.startDate -> res(R.string.rule_form_error_end_date)
            else -> null
        }
        val errors = listOf(amountError, accountError, toAccountError, categoryError, noteError, intervalError, weekdaysError, countError, endDateError)
        if (errors.any { it != null }) {
            _state.update {
                it.copy(
                    amountError = amountError,
                    accountError = accountError,
                    toAccountError = toAccountError,
                    categoryError = categoryError,
                    noteError = noteError,
                    intervalError = intervalError,
                    weekdaysError = weekdaysError,
                    countError = countError,
                    endDateError = endDateError,
                )
            }
            return
        }

        val schedule = scheduleOf(s) ?: return
        val template = RecurringTemplate(
            type = s.type,
            amount = amount!!,
            accountId = s.accountId!!,
            toAccountId = if (s.isTransfer) s.toAccountId else null,
            categoryId = if (s.isTransfer) null else s.categoryId,
            note = note.ifEmpty { null },
        )
        val body = RuleBody(
            template = template,
            frequency = schedule.frequency,
            interval = schedule.interval,
            weekdays = schedule.weekdays,
            dayOfMonth = schedule.dayOfMonth,
            startDate = schedule.startDate,
            endType = s.endType,
            endDate = if (s.endType == EndType.Until) s.endDate else null,
            maxCount = if (s.endType == EndType.Count) maxCount else null,
            mode = s.mode,
            nextDueDate = nextDueDateOf(schedule, s.editing),
        )
        val name: UiText = template.note?.let(::plain)
            ?: allCategories.firstOrNull { it.id == template.categoryId }?.name?.let(::plain)
            ?: res(R.string.tx_type_transfer)

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val editing = s.editing
                if (editing == null) {
                    repository.create(body)
                    messages.show(res(R.string.rule_added, name))
                } else {
                    repository.update(editing.id, body)
                    messages.show(res(R.string.rule_updated, name))
                }
                _events.send(RuleFormEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}
