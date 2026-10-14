package com.example.expensify.feature.recurring.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.DetailItem
import com.example.expensify.feature.transactions.ui.TxRows
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How many entries the history shows before "Show more" (req REC-12). */
const val RULE_PAGE_SIZE = 50

/** How many upcoming dates the details list. */
private const val UPCOMING = 5

sealed interface RuleDetailUiState {
    data object Loading : RuleDetailUiState
    data object NotFound : RuleDetailUiState
    data class Error(val message: UiText) : RuleDetailUiState
    data class Content(
        val view: RuleView,
        val today: LocalDate,
        /** The next dates an entry is due, while the rule runs. */
        val upcoming: List<LocalDate>,
        /** An ask-first entry is due and waits for Confirm, Skip or Edit. */
        val waiting: Boolean,
        val details: List<DetailItem>,
        /** The entries the rule added, newest first, one page at a time; each row leads with its date. */
        val rows: List<EntryRowUi>,
        val hiddenCount: Int,
        val historyLoading: Boolean,
        val historyError: UiText?,
    ) : RuleDetailUiState
}

sealed interface RuleDetailEvent {
    /** The rule was deleted from this page; leave it. */
    data object Left : RuleDetailEvent
}

/** The rule's entries, or why they couldn't be read. */
private sealed interface History {
    data object Loading : History
    data class Ok(val txs: List<Transaction>) : History
    data class Failed(val message: UiText) : History
}

/**
 * `/recurring/:id` (req REC-12): the rule's entry and schedule, its next
 * dates, an entry waiting to be confirmed, and the entries it has added.
 */
@HiltViewModel
class RuleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: RecurringRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    today: Today,
    messages: Messages,
) : RecurringActionsViewModel(repository, messages, today) {

    private val route = savedState.toRoute<Routes.RuleDetail>()

    private val _events = Channel<RuleDetailEvent>(Channel.BUFFERED)
    val events: Flow<RuleDetailEvent> = _events.receiveAsFlow()

    private val limit = MutableStateFlow(RULE_PAGE_SIZE)

    private val history: Flow<History> = transactions.ofRule(route.id)
        .map<List<Transaction>, History> { History.Ok(it) }
        .onStart { emit(History.Loading) }
        .catch { emit(History.Failed(plain(ErrorMessages.of(it)))) }

    val state: StateFlow<RuleDetailUiState> = combine(
        combine(repository.rules(), accounts.accounts(), categories.categories()) { r, a, c -> Triple(r, a, c) },
        today.date,
        history,
        limit,
    ) { (rules, accounts, categories), day, history, limit ->
        val rule = rules.firstOrNull { it.id == route.id } ?: return@combine RuleDetailUiState.NotFound
        val lookups = TxRows.Lookups(accounts, categories, day)
        val view = RuleLabels.toView(rule, lookups)
        val transfer = rule.template.type == TxType.Transfer

        val details = buildList {
            if (!transfer) add(DetailItem(UiText.Res(R.string.rule_detail_category), TxRows.categoryOf(lookups, rule.template.type, rule.template.categoryId).name))
            add(DetailItem(UiText.Res(if (transfer) R.string.rule_detail_accounts else R.string.rule_detail_account), view.accountLabel))
            add(DetailItem(UiText.Res(R.string.rule_detail_starts), plain(DateFormats.date(rule.startDate))))
            add(DetailItem(UiText.Res(R.string.rule_detail_ends), RuleLabels.endLabel(rule) ?: UiText.Res(R.string.rule_end_never)))
            add(
                DetailItem(
                    UiText.Res(R.string.rule_detail_on_due),
                    UiText.Res(if (rule.mode == RuleMode.Confirm) R.string.rule_detail_asks_first else R.string.rule_detail_adds_auto),
                ),
            )
            add(DetailItem(UiText.Res(R.string.rule_detail_added_so_far), RuleLabels.entryCount(rule.occurrences)))
        }

        // Every entry has the same note and category, so each row leads with its date.
        val all = (history as? History.Ok)?.txs.orEmpty()
        val rows = all.take(limit).map { TxRows.toRow(it, lookups).copy(title = plain(DateFormats.date(it.date))) }

        RuleDetailUiState.Content(
            view = view,
            today = day,
            upcoming = if (view.status == RuleStatus.Active) RecurringRules.pendingDates(rule, UPCOMING) else emptyList(),
            waiting = view.asksFirst && view.due.isNotEmpty(),
            details = details,
            rows = rows,
            hiddenCount = all.size - rows.size,
            historyLoading = history is History.Loading,
            historyError = (history as? History.Failed)?.message,
        ) as RuleDetailUiState
    }
        .catch { emit(RuleDetailUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleDetailUiState.Loading)

    fun showMore() = limit.update { it + RULE_PAGE_SIZE }

    override fun onDeleted(view: RuleView) {
        _events.trySend(RuleDetailEvent.Left)
    }
}
