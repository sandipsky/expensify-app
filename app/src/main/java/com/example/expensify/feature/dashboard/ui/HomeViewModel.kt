package com.example.expensify.feature.dashboard.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Periods
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.budgets.ui.Spending
import com.example.expensify.feature.budgets.ui.spendingOver
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.DashboardPreset
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.dashboard.domain.PeriodSummary
import com.example.expensify.feature.dashboard.domain.SummaryChange
import com.example.expensify.feature.notifications.data.AlertStore
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.recurring.ui.RecurringActionsViewModel
import com.example.expensify.feature.recurring.ui.RuleLabels
import com.example.expensify.feature.recurring.ui.RuleView
import com.example.expensify.feature.reports.ui.Loaded
import com.example.expensify.feature.reports.ui.loaded
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.ui.TransactionDialogs
import com.example.expensify.feature.transactions.ui.TxRows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The period the dashboard shows (req DSH-01): the preset, the custom range it would start from, the range and its label. */
@Immutable
data class DashboardPeriodUi(
    val preset: DashboardPreset,
    /** The range "Custom" shows; it starts as this month, or the period a link opened. */
    val custom: DateRange,
    val range: DateRange,
    /** "October 2026", or the date range. */
    val label: String,
)

/** Income, expense, net and savings rate for the period, and how each moved from the previous one (req DSH-02, DSH-03). */
@Immutable
data class SummaryUi(val summary: PeriodSummary, val change: SummaryChange?)

/** One row of the spending card: a category, or "Other" for the rest (req DSH-06). */
@Immutable
data class SpendingRowUi(
    val key: String,
    /** The category ids the row stands for; "Other" holds several, an uncategorized row none. */
    val categoryIds: List<String>,
    val name: UiText,
    /** A stored lumen icon name. */
    val icon: String,
    /** `#RRGGBB`, or null for the neutral grey of "Other" and missing categories. */
    val colorHex: String?,
    val amount: Money,
    val count: Int,
    val share: Int?,
) {
    val opens: Boolean get() = categoryIds.isNotEmpty()
}

@Immutable
data class SpendingUi(val total: Money, val count: Int, val rows: List<SpendingRowUi>)

/** One budget on the card with its current figures (req DSH-07); figures are null while they load. */
@Immutable
data class BudgetRowUi(val budget: Budget, val icon: BudgetLabels.Icon, val progress: BudgetProgress?)

@Immutable
data class BudgetsUi(
    val rows: List<BudgetRowUi>,
    /** More than the card shows: "See all". */
    val more: Boolean,
    /** Any budget at all, paused ones too: which empty state to show. */
    val anyBudgets: Boolean,
)

/** The total and every active account (req DSH-05). */
@Immutable
data class BalancesUi(
    val total: Money,
    val includedCount: Int,
    val excludedCount: Int,
    val accounts: List<Account>,
    /** Any account at all, archived ones too: which empty state to show. */
    val anyAccounts: Boolean,
)

/** One recurring occurrence in the next days (req DSH-10). */
@Immutable
data class UpcomingRowUi(
    val key: String,
    val view: RuleView,
    val date: LocalDate,
    /** The rule's next date, and due by today: an ask-first rule can be confirmed or skipped. */
    val due: Boolean,
)

/** What shows in every state: the greeting, the period switcher, privacy mode and the bell. */
@Immutable
data class HomeHeaderUi(
    val name: String,
    val period: DashboardPeriodUi,
    /** Privacy mode: every amount reads "••••" (req DSH-11). */
    val masked: Boolean,
    /** Alerts nobody has seen yet, for the bell's dot (req NTF-06). */
    val unread: Int,
)

sealed interface HomeUiState {
    val header: HomeHeaderUi

    data class Loading(override val header: HomeHeaderUi) : HomeUiState

    data class Error(override val header: HomeHeaderUi, val message: UiText) : HomeUiState

    data class Content(
        override val header: HomeHeaderUi,
        val today: LocalDate,
        /** The period has no entries at all: one empty state stands in for the period cards (req DSH-12). */
        val empty: Boolean,
        val summary: SummaryUi,
        val balances: BalancesUi,
        val spending: SpendingUi,
        val budgets: BudgetsUi,
        /** The selected period and the five before it, oldest first (req DSH-08). */
        val trend: List<PeriodSummary>,
        val trendEmpty: Boolean,
        /** The period's newest entries (req DSH-09), and the entries behind them for Edit and Delete. */
        val recent: List<EntryRowUi>,
        val recentTxs: Map<String, Transaction>,
        val upcoming: List<UpcomingRowUi>,
    ) : HomeUiState
}

/** Key of the donut's "Other" slice, and of entries without a category. */
internal const val KEY_OTHER = "other"
internal const val KEY_NONE = "none"

/** What a budget over several categories, and "Other", draw. */
private const val GENERIC_CATEGORY_ICON = "category"

/**
 * The spending card's rows: the top categories and "Other" (only when it
 * would hold two or more), each with its share (req DSH-06).
 */
internal fun buildSpending(txs: List<Transaction>, categoriesById: Map<String, Category>): SpendingUi {
    val totals = DashboardRules.categoryTotals(txs, CategoryType.Expense)
    var spent = Money.ZERO
    var count = 0
    for (total in totals) {
        spent += total.amount
        count += total.count
    }
    val keep = if (totals.size <= DashboardRules.TOP_CATEGORIES + 1) totals.size else DashboardRules.TOP_CATEGORIES
    val split = DashboardRules.topAndOther(totals, keep)
    val rows = split.top.map { total ->
        val category = total.categoryId?.let { categoriesById[it] }
        SpendingRowUi(
            key = total.categoryId ?: KEY_NONE,
            categoryIds = listOfNotNull(total.categoryId),
            name = when {
                category != null -> plain(category.name)
                total.categoryId == null -> res(R.string.report_uncategorized)
                else -> res(R.string.tx_deleted_category)
            },
            icon = category?.icon ?: TxRows.MISSING_CATEGORY_ICON,
            colorHex = category?.color,
            amount = total.amount,
            count = total.count,
            share = DashboardRules.shareOf(total.amount, spent),
        )
    } + listOfNotNull(
        split.other?.let { other ->
            SpendingRowUi(
                key = KEY_OTHER,
                categoryIds = other.categoryIds,
                name = res(R.string.report_other),
                icon = GENERIC_CATEGORY_ICON,
                colorHex = null,
                amount = other.amount,
                count = other.count,
                share = DashboardRules.shareOf(other.amount, spent),
            )
        },
    )
    return SpendingUi(spent, count, rows)
}

/** Active budgets, most used first (a zero limit counts as fully used), at most `TOP_BUDGETS` (req DSH-07). */
internal fun buildBudgets(budgets: List<Budget>, spending: List<Transaction>?, categoriesById: Map<String, Category>, today: LocalDate): BudgetsUi {
    val active = budgets.filter { it.active }
    val rows = active
        .map { budget ->
            BudgetRowUi(
                budget = budget,
                icon = BudgetLabels.iconOf(budget, categoriesById),
                progress = if (spending != null) BudgetRules.progress(budget, spending, today) else null,
            )
        }
        .sortedByDescending { DashboardRules.usedOf(it.progress) }
        .take(DashboardRules.TOP_BUDGETS)
    return BudgetsUi(rows, more = active.size > DashboardRules.TOP_BUDGETS, anyBudgets = budgets.isNotEmpty())
}

/** The total of the accounts marked "include in total", then every active account (req DSH-05). */
internal fun buildBalances(accounts: List<Account>): BalancesUi {
    val active = accounts.filter { !it.archived }
    val excluded = active.count { !it.includeInTotal }
    return BalancesUi(
        total = AccountRules.totalBalance(accounts),
        includedCount = active.size - excluded,
        excludedCount = excluded,
        accounts = active,
        anyAccounts = accounts.isNotEmpty(),
    )
}

/** Occurrences of running rules due by today or in the next seven days, soonest first (req DSH-10). */
internal fun buildUpcoming(rules: List<RecurringRule>, lookups: TxRows.Lookups): List<UpcomingRowUi> =
    rules
        .map { RuleLabels.toView(it, lookups) }
        .filter { it.status == RuleStatus.Active }
        .flatMap { view ->
            DashboardRules.upcoming(view.rule, view.status, lookups.today).map { UpcomingRowUi("${view.id}_${it.date}", view, it.date, it.due) }
        }
        .sortedWith(compareBy<UpcomingRowUi> { it.date }.thenBy { it.view.sortName.lowercase() })

/**
 * The period part of the Home route (`period`, `from`, `to`), read from and
 * written back to the SavedStateHandle so Back from a drill-down, process
 * death and a link into the dashboard all restore it, as the web keeps it
 * in the URL (req DSH-01).
 */
private class HomeArgs(private val handle: SavedStateHandle) {
    val preset = handle.getStateFlow<String?>(KEY_PERIOD, null)
    val from = handle.getStateFlow<String?>(KEY_FROM, null)
    val to = handle.getStateFlow<String?>(KEY_TO, null)

    fun set(preset: DashboardPreset, range: DateRange?) {
        handle[KEY_FROM] = range?.start?.ymd()
        handle[KEY_TO] = range?.end?.ymd()
        handle[KEY_PERIOD] = preset.stored
    }

    private companion object {
        const val KEY_PERIOD = "period"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
    }
}

/**
 * `/` (req DSH-01…DSH-12; the port of useDashboard.ts): the selected period
 * from the route, one query on it and one over the five periods before it,
 * and every card's figures from the shared domain functions. Accounts,
 * categories, budgets and rules come from the app-wide listeners. Privacy
 * mode starts on each time the dashboard opens (req DSH-11). Confirm and
 * Skip for upcoming entries come from the recurring actions; Edit and Delete
 * on a recent entry from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: SessionRepository,
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    alerts: AlertStore,
    private val today: Today,
    messages: Messages,
) : RecurringActionsViewModel(recurring, messages, today) {

    private val args = HomeArgs(savedState)
    private val masked = MutableStateFlow(true)
    private val deleteDialog = MutableStateFlow(TransactionDialogs())

    /** The period and the ranges around it: the trend periods, the one compared with, and one range over the earlier ones. */
    private data class PeriodState(
        val today: LocalDate,
        val ui: DashboardPeriodUi,
        val periods: List<DateRange>,
        val previous: DateRange,
        val earlier: DateRange,
    )

    private fun periodState(preset: String?, from: String?, to: String?, day: LocalDate): PeriodState {
        val chosen = DashboardPreset.fromStored(preset) ?: DashboardPreset.ThisMonth
        val start = from?.toLocalDateOrNull()
        val end = to?.toLocalDateOrNull()
        val custom = if (start != null && end != null) Periods.ordered(start, end) else Periods.month(day)
        val range = DashboardRules.dashboardRange(chosen, day, custom)
        val periods = DashboardRules.trendPeriods(chosen, range, day)
        return PeriodState(
            today = day,
            ui = DashboardPeriodUi(chosen, custom, range, DateFormats.period(range)),
            periods = periods,
            previous = periods[DashboardRules.TREND_PERIODS - 2],
            earlier = DashboardRules.earlierRange(periods, range),
        )
    }

    private val period: Flow<PeriodState> =
        combine(args.preset, args.from, args.to, today.date) { p, f, t, day -> periodState(p, f, t, day) }.distinctUntilChanged()

    // The two ranges the page reads: one query each, never one per card.
    private val current: Flow<Loaded> = period.map { it.ui.range }.distinctUntilChanged().flatMapLatest { transactions.inRange(it).loaded() }
    private val earlier: Flow<Loaded> = period.map { it.earlier }.distinctUntilChanged().flatMapLatest { transactions.inRange(it).loaded() }

    // The same range the Budgets page and the alerts read, so all three share one listener's worth of entries.
    private val spending: Flow<Spending> = combine(budgets.budgets(), today.date) { all, day ->
        BudgetRules.spanOf(all.filter { it.active }.map { BudgetRules.neededRange(it, day) })
    }
        .distinctUntilChanged()
        .flatMapLatest { range -> transactions.spendingOver(range) }

    private data class Lists(
        val accounts: List<Account>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val rules: List<RecurringRule>,
        val spending: Spending,
    )

    private val lists: Flow<Lists> =
        combine(accounts.accounts(), categories.categories(), budgets.budgets(), recurring.rules(), spending) { a, c, b, r, s -> Lists(a, c, b, r, s) }

    private data class Header(val name: String, val masked: Boolean, val unread: Int)

    private val header: Flow<Header> = combine(session.session, masked, alerts.unreadCount) { s, m, unread ->
        Header((s as? Session.SignedIn)?.name?.substringBefore(' ').orEmpty(), m, unread)
    }

    private val initialHeader = HomeHeaderUi(
        name = (session.session.value as? Session.SignedIn)?.name?.substringBefore(' ').orEmpty(),
        period = periodState(args.preset.value, args.from.value, args.to.value, today.date.value).ui,
        masked = masked.value,
        unread = 0,
    )

    val state: StateFlow<HomeUiState> = combine(period, current, earlier, lists, header) { p, cur, ear, l, h -> build(p, cur, ear, l, h) }
        .flowOn(Dispatchers.Default)
        .catch { emit(HomeUiState.Error(initialHeader, plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading(initialHeader))

    /** The "Delete this transaction?" confirmation for a recent entry (req DSH-09, TXN-07). */
    val deleteDialogs: StateFlow<TransactionDialogs> = deleteDialog.asStateFlow()

    private fun build(p: PeriodState, current: Loaded, earlier: Loaded, lists: Lists, header: Header): HomeUiState {
        val headerUi = HomeHeaderUi(header.name, p.ui, header.masked, header.unread)
        (current as? Loaded.Failed)?.let { return HomeUiState.Error(headerUi, it.message) }
        (earlier as? Loaded.Failed)?.let { return HomeUiState.Error(headerUi, it.message) }
        if (current !is Loaded.Ok || earlier !is Loaded.Ok) return HomeUiState.Loading(headerUi)

        val lookups = TxRows.Lookups(lists.accounts, lists.categories, p.today)
        /** The selected period's entries, newest first. */
        val txs = current.txs
        /** The five earlier periods' entries. */
        val earlierTxs = earlier.txs

        val summary = DashboardRules.summaryOf(p.ui.range, txs)
        val previousTxs = earlierTxs.filter { it.date in p.previous }
        // The change from the previous period, or null when it had no income or expense entries.
        val change = if (DashboardRules.hasComparison(previousTxs)) DashboardRules.summaryChange(DashboardRules.summaryOf(p.previous, previousTxs), summary) else null

        val recentTxs = txs.take(DashboardRules.RECENT_COUNT)

        return HomeUiState.Content(
            header = headerUi,
            today = p.today,
            empty = txs.isEmpty(),
            summary = SummaryUi(summary, change),
            balances = buildBalances(lists.accounts),
            spending = buildSpending(txs, lookups.categoriesById),
            budgets = buildBudgets(lists.budgets, (lists.spending as? Spending.Loaded)?.txs, lookups.categoriesById, p.today),
            trend = DashboardRules.periodSummaries(earlierTxs + txs, p.periods),
            trendEmpty = earlierTxs.isEmpty() && txs.isEmpty(),
            recent = recentTxs.map { TxRows.toRow(it, lookups) },
            recentTxs = recentTxs.associateBy { it.id },
            upcoming = buildUpcoming(lists.rules, lookups),
        )
    }

    // Privacy (DSH-11)

    fun togglePrivacy() = masked.update { !it }

    // Period (DSH-01). It lives in the route's arguments, so Back from a drill-down returns to it.

    /** Custom starts from whatever the page showed. */
    fun setPreset(preset: DashboardPreset) {
        val shown = state.value.header.period.range
        args.set(preset, if (preset == DashboardPreset.Custom) shown else null)
    }

    /** A custom range; reversed dates are swapped. */
    fun setCustom(start: LocalDate, end: LocalDate) = args.set(DashboardPreset.Custom, Periods.ordered(start, end))

    fun setCustomStart(date: LocalDate) = setCustom(date, state.value.header.period.custom.end)

    fun setCustomEnd(date: LocalDate) = setCustom(state.value.header.period.custom.start, date)

    /** Selects the period in the switcher, under its preset when it is one (a tapped trend bar, req DSH-08). */
    fun selectPeriod(range: DateRange) {
        val preset = DashboardRules.dashboardPresetOf(range, today.date.value)
        if (preset == DashboardPreset.Custom) setCustom(range.start, range.end) else setPreset(preset)
    }

    // Recent entries: Delete behind a confirmation that says what happens to the balance (req DSH-09, TXN-07).

    fun requestDeleteEntry(tx: Transaction) = deleteDialog.update { it.copy(deleteTarget = tx) }

    fun confirmDeleteEntry() {
        val target = deleteDialog.value.deleteTarget ?: return
        viewModelScope.launch {
            deleteDialog.update { it.copy(busy = true) }
            try {
                transactions.delete(target.id)
                deleteDialog.value = TransactionDialogs()
                messages.show(res(R.string.tx_deleted, MoneyFormat.full(target.amount)))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deleteDialog.update { it.copy(busy = false) }
                messages.show(res(R.string.could_not_delete_tx, ErrorMessages.of(error)))
            }
        }
    }

    fun dismissDeleteEntry() {
        if (!deleteDialog.value.busy) deleteDialog.value = TransactionDialogs()
    }
}
