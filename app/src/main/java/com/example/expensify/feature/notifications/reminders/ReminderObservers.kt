package com.example.expensify.feature.notifications.reminders

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Today
import com.example.expensify.core.ui.AppVisibility
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/** Timers may fire a little early; waking just after the minute keeps the check true. */
private const val TICK_MS = 60_000L

/** The notification settings, with the defaults a new profile gets while none has loaded. */
private fun AuthRepository.notificationPrefs(): Flow<NotificationPrefs> =
    profile().map { it?.preferences?.notifications ?: NotificationPrefs() }.distinctUntilChanged()

/**
 * The daily reminder while the app is open (req NTF-02; the port of
 * DailyReminder.tsx): checked every minute, and again when the app comes
 * back to the front, since a timer can run late while the phone sleeps. An
 * app opened after the reminder time is reminded then. Today's entries are
 * only read while the reminder is on. `ReminderWorker` does the same while
 * the app is closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DailyReminderObserver @Inject constructor(
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val today: Today,
    private val visibility: AppVisibility,
    private val clock: Clock,
    private val checks: ReminderChecks,
) {
    private data class Settings(val prefs: NotificationPrefs, val today: LocalDate)

    suspend fun run() {
        val settings = combine(auth.notificationPrefs(), today.date) { prefs, day -> Settings(prefs, day) }.distinctUntilChanged()
        val todays: Flow<List<Transaction>> = settings
            .map { it.prefs.dailyReminder to it.today }
            .distinctUntilChanged()
            .flatMapLatest { (on, day) -> if (!on) flowOf(emptyList()) else transactions.inRange(DateRange(day, day)) }
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }

        combine(settings, todays, ticks, visibility.foreground) { s, txs, _, _ -> s to txs }
            .retryWhen { _, attempt ->
                delay(5_000L * (attempt + 1).coerceAtMost(6))
                true
            }
            .collect { (s, txs) -> checks.dailyReminder(s.prefs, txs, LocalDateTime.now(clock)) }
    }
}

/**
 * Reminders for ask-first recurring entries while the app is open (req
 * NTF-03; the port of RecurringReminders.tsx): checked whenever the rules,
 * the accounts, the categories or the date change, unless turned off in
 * Settings. `ReminderWorker` does the same each morning while the app is closed.
 */
@Singleton
class RecurringRemindersObserver @Inject constructor(
    private val auth: AuthRepository,
    private val rules: RecurringRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val today: Today,
    private val checks: ReminderChecks,
) {
    private data class Snapshot(
        val enabled: Boolean,
        val rules: List<RecurringRule>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val today: LocalDate,
    )

    suspend fun run() {
        val enabled = auth.notificationPrefs().map { it.billReminders }.distinctUntilChanged()
        combine(enabled, rules.rules(), accounts.accounts(), categories.categories(), today.date) { on, r, a, c, day -> Snapshot(on, r, a, c, day) }
            .retryWhen { _, attempt ->
                delay(5_000L * (attempt + 1).coerceAtMost(6))
                true
            }
            .collect { checks.billReminders(it.enabled, it.rules, it.accounts, it.categories, it.today) }
    }
}
