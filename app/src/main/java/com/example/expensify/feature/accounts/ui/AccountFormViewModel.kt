package com.example.expensify.feature.accounts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountBody
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.accounts.domain.AccountType
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountFormUiState(
    /** Loading the account to edit. */
    val loading: Boolean = false,
    val editing: Account? = null,
    val name: String = "",
    val type: AccountType = AccountType.Cash,
    /** The opening balance, or the amount owed for cards and loans, as typed. */
    val balanceText: String = "0",
    val creditLimitText: String = "",
    val includeInTotal: Boolean = true,
    val nameError: UiText? = null,
    val balanceError: UiText? = null,
    val creditLimitError: UiText? = null,
    /** A refused save. */
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editing != null
}

sealed interface AccountFormEvent {
    data object Saved : AccountFormEvent
}

/**
 * Add or edit an account (req ACC-01, ACC-05). Cards and loans enter the
 * amount owed, stored as a negative balance. Icon and color come from the
 * type; there is no picker.
 */
@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: AccountRepository,
    private val messages: Messages,
) : ViewModel() {

    private val route = savedState.toRoute<Routes.AccountForm>()

    private val _state = MutableStateFlow(AccountFormUiState(loading = route.id != null))
    val state: StateFlow<AccountFormUiState> = _state.asStateFlow()

    private val _events = Channel<AccountFormEvent>(Channel.BUFFERED)
    val events: Flow<AccountFormEvent> = _events.receiveAsFlow()

    init {
        route.id?.let(::load)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            try {
                val account = repository.get(id)
                _state.update {
                    it.copy(
                        loading = false,
                        editing = account,
                        name = account.name,
                        type = account.type,
                        balanceText = moneyInputText(AccountRules.flipForLiability(account.type, account.openingBalance)),
                        creditLimitText = account.creditLimit?.let(::moneyInputText).orEmpty(),
                        includeInTotal = account.includeInTotal,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onTypeChange(value: AccountType) = _state.update { it.copy(type = value, creditLimitError = null) }
    fun onBalanceChange(value: String) = _state.update { it.copy(balanceText = value, balanceError = null) }
    fun onCreditLimitChange(value: String) = _state.update { it.copy(creditLimitText = value, creditLimitError = null) }
    fun onIncludeInTotalChange(value: Boolean) = _state.update { it.copy(includeInTotal = value) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return

        val name = s.name.trim()
        val nameError = when {
            name.isEmpty() -> res(R.string.account_form_error_name_required)
            name.length > AccountRules.MAX_NAME_LENGTH -> res(R.string.account_form_error_name_long)
            else -> null
        }
        val balance = Money.parse(s.balanceText)
        val balanceError = if (balance == null) res(R.string.account_form_error_amount) else null
        val creditLimit = if (s.type == AccountType.CreditCard && s.creditLimitText.isNotBlank()) Money.parse(s.creditLimitText) else null
        val creditLimitError = when {
            s.type != AccountType.CreditCard || s.creditLimitText.isBlank() -> null
            creditLimit == null -> res(R.string.account_form_error_amount)
            !creditLimit.isPositive -> res(R.string.account_form_error_positive)
            else -> null
        }
        if (nameError != null || balanceError != null || creditLimitError != null) {
            _state.update { it.copy(nameError = nameError, balanceError = balanceError, creditLimitError = creditLimitError) }
            return
        }

        val (icon, color) = AccountRules.iconAndColorFor(s.editing, s.type)
        val body = AccountBody(
            name = name,
            type = s.type,
            currency = s.editing?.currency ?: AccountRules.DEFAULT_CURRENCY,
            openingBalance = AccountRules.flipForLiability(s.type, balance!!),
            creditLimit = creditLimit,
            icon = icon,
            color = color,
            includeInTotal = s.includeInTotal,
        )

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val editing = s.editing
                val saved = if (editing == null) repository.create(body) else repository.update(editing.id, body)
                messages.show(res(if (editing == null) R.string.account_added else R.string.account_updated, saved.name))
                _events.send(AccountFormEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}
