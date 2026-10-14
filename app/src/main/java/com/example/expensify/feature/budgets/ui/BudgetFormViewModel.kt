package com.example.expensify.feature.budgets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.ui.moneyInputText
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetBody
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.domain.categoryOrder
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An expense category the form offers; archived ones only while the budget still counts them. */
data class CategoryOption(val id: String, val name: String, val icon: String, val color: String, val archived: Boolean)

data class BudgetFormUiState(
    /** Loading the budget to edit. */
    val loading: Boolean = true,
    val editing: Budget? = null,
    val period: BudgetPeriod = BudgetPeriod.Monthly,
    /** "September 2026": what the chosen period covers today. */
    val thisPeriod: String = "",
    val amountText: String = "",
    /** In the order they were picked, so the suggested name follows them. */
    val categoryIds: List<String> = emptyList(),
    val options: List<CategoryOption> = emptyList(),
    val name: String = BudgetRules.ALL_EXPENSES_NAME,
    val rollover: Boolean = false,
    val alerts: Boolean = true,
    val amountError: UiText? = null,
    val nameError: UiText? = null,
    /** A refused save. */
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editing != null
}

sealed interface BudgetFormEvent {
    data object Saved : BudgetFormEvent
}

/**
 * Add or edit a budget (req BUD-01): a limit per week, month or year for
 * every expense or for chosen categories, with rollover and alerts at 80 %
 * and 100 %. A new budget's name follows its categories until the user types one.
 */
@HiltViewModel
class BudgetFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: BudgetRepository,
    categories: CategoryRepository,
    private val today: Today,
    private val messages: Messages,
) : ViewModel() {

    private val route = savedState.toRoute<Routes.BudgetForm>()

    private val _state = MutableStateFlow(BudgetFormUiState(thisPeriod = periodLabel(BudgetPeriod.Monthly)))
    val state: StateFlow<BudgetFormUiState> = _state.asStateFlow()

    private val _events = Channel<BudgetFormEvent>(Channel.BUFFERED)
    val events: Flow<BudgetFormEvent> = _events.receiveAsFlow()

    /** Set once the user types a name; until then a new budget's name follows its categories. */
    private var nameEdited = false

    private var allCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch {
            categories.categories().catch { }.collect { all ->
                allCategories = all
                refreshOptions()
            }
        }
        viewModelScope.launch {
            try {
                val id = route.id
                if (id == null) {
                    _state.update { it.copy(loading = false) }
                } else {
                    val budget = repository.budgets().first().firstOrNull { it.id == id } ?: repository.get(id)
                    nameEdited = true
                    _state.update {
                        it.copy(
                            loading = false,
                            editing = budget,
                            period = budget.period,
                            thisPeriod = periodLabel(budget.period),
                            amountText = moneyInputText(budget.amount),
                            categoryIds = budget.categoryIds,
                            name = budget.name,
                            rollover = budget.rollover,
                            alerts = budget.alertThresholds.isNotEmpty(),
                        )
                    }
                    refreshOptions()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }

    private fun periodLabel(period: BudgetPeriod): String =
        DateFormats.period(BudgetRules.periodRange(period, today.date.value))

    /** Expense categories in list order, plus archived ones the budget still counts. */
    private fun refreshOptions() {
        val kept = _state.value.editing?.categoryIds?.toSet() ?: emptySet()
        val options = allCategories
            .filter { it.type == CategoryType.Expense && (!it.archived || it.id in kept) }
            .sortedWith(categoryOrder)
            .map { CategoryOption(it.id, it.name, it.icon, it.color, it.archived) }
        _state.update { it.copy(options = options) }
    }

    fun onPeriodChange(value: BudgetPeriod) = _state.update { it.copy(period = value, thisPeriod = periodLabel(value)) }
    fun onAmountChange(value: String) = _state.update { it.copy(amountText = value, amountError = null) }
    fun onRolloverChange(value: Boolean) = _state.update { it.copy(rollover = value) }
    fun onAlertsChange(value: Boolean) = _state.update { it.copy(alerts = value) }

    fun onNameChange(value: String) {
        nameEdited = true
        _state.update { it.copy(name = value, nameError = null) }
    }

    fun onCategoryToggle(id: String) {
        _state.update { s ->
            val ids = if (id in s.categoryIds) s.categoryIds - id else s.categoryIds + id
            val name = if (nameEdited) s.name else BudgetRules.suggestName(ids.mapNotNull { picked -> s.options.firstOrNull { it.id == picked }?.name })
            s.copy(categoryIds = ids, name = name, nameError = null)
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return

        val amount = Money.parse(s.amountText)
        val amountError = when {
            amount == null -> res(R.string.budget_form_error_amount)
            !amount.isPositive -> res(R.string.budget_form_error_amount_positive)
            else -> null
        }
        val name = s.name.trim()
        val nameError = when {
            name.isEmpty() -> res(R.string.budget_form_error_name_required)
            name.length > BudgetRules.MAX_NAME_LENGTH -> res(R.string.budget_form_error_name_long)
            else -> null
        }
        if (amountError != null || nameError != null) {
            _state.update { it.copy(amountError = amountError, nameError = nameError) }
            return
        }

        // Keeping alerts on keeps the thresholds the budget already has.
        val alertThresholds = when {
            !s.alerts -> emptyList()
            !s.editing?.alertThresholds.isNullOrEmpty() -> s.editing!!.alertThresholds
            else -> BudgetRules.DEFAULT_ALERT_THRESHOLDS
        }
        // A category deleted since can't be saved; the budget counts the rest.
        val known = s.options.map { it.id }.toSet()
        val body = BudgetBody(
            name = name,
            amount = amount!!,
            period = s.period,
            categoryIds = s.categoryIds.filter { it in known },
            rollover = s.rollover,
            alertThresholds = alertThresholds,
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val editing = s.editing
                val saved = if (editing == null) repository.create(body) else repository.update(editing.id, body)
                messages.show(res(if (editing == null) R.string.budget_added else R.string.budget_updated, saved.name))
                _events.send(BudgetFormEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}
