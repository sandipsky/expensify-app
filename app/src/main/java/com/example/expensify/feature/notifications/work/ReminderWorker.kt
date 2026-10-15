package com.example.expensify.feature.notifications.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Today
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.notifications.domain.NotificationRules
import com.example.expensify.feature.notifications.reminders.ReminderChecks
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The reminders while the app is closed (architecture §9): a daily
 * WorkManager job at the user's reminder time for the daily reminder, and
 * one each morning for the day-before and waiting reminders of ask-first
 * rules. Each runs the same check as the foreground observer, reading from
 * the server so an entry logged on the web counts, and bails out while
 * nobody is logged in. What was shown is kept on the device, so the app
 * opening later doesn't repeat it. A phone's bonus over the web, which can
 * only remind while a tab is open.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionRepository,
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val rules: RecurringRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val today: Today,
    private val clock: Clock,
    private val checks: ReminderChecks,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Firebase restores the session on process start; give it a moment.
        val signedIn = withTimeoutOrNull(SESSION_TIMEOUT_MS) { session.session.first { it != Session.Unknown } }
        if (signedIn !is Session.SignedIn) return Result.success()
        today.refresh()

        // A login without a profile gets the defaults, as the app does; a read that fails asks for a retry.
        val prefs = read { auth.profile().first()?.preferences?.notifications ?: NotificationPrefs() } ?: return Result.retry()
        return when (inputData.getString(KEY_KIND)) {
            KIND_DAILY -> daily(prefs)
            KIND_BILLS -> bills(prefs)
            else -> Result.success()
        }
    }

    private suspend fun daily(prefs: NotificationPrefs): Result {
        if (!prefs.dailyReminder) return Result.success()
        val day = today.date.value
        val todays = read { transactions.fetchInRange(DateRange(day, day)) } ?: return Result.retry()
        checks.dailyReminder(prefs, todays, LocalDateTime.now(clock))
        return Result.success()
    }

    private suspend fun bills(prefs: NotificationPrefs): Result {
        if (!prefs.billReminders) return Result.success()
        val snapshot = read { Triple(rules.fetchAll(), accounts.accounts().first(), categories.categories().first()) } ?: return Result.retry()
        checks.billReminders(true, snapshot.first, snapshot.second, snapshot.third, today.date.value)
        return Result.success()
    }

    /** A read from the server within the time limit, or null when it couldn't be made, which asks for a retry. */
    private suspend fun <T> read(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(READ_TIMEOUT_MS) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_DAILY = "daily"
        const val KIND_BILLS = "bills"
        private const val SESSION_TIMEOUT_MS = 15_000L
        private const val READ_TIMEOUT_MS = 30_000L
    }
}

/**
 * Keeps the reminder jobs in step with the notification settings (req
 * NTF-04): each is a daily job whose first run is the next time its hour
 * comes round, re-aimed whenever the settings change or the app starts, and
 * cancelled while its switch is off or nobody is logged in.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(prefs: NotificationPrefs) {
        val manager = WorkManager.getInstance(context)
        val now = LocalDateTime.now(clock)
        val reminderTime = NotificationRules.timeOfDay(prefs.reminderTime)
        if (prefs.dailyReminder && reminderTime != null) {
            enqueue(manager, DAILY_WORK, ReminderWorker.KIND_DAILY, NotificationRules.nextTime(reminderTime, now), now)
        } else {
            manager.cancelUniqueWork(DAILY_WORK)
        }
        if (prefs.billReminders) {
            enqueue(manager, BILLS_WORK, ReminderWorker.KIND_BILLS, NotificationRules.nextTime(NotificationRules.BILL_REMINDER_TIME, now), now)
        } else {
            manager.cancelUniqueWork(BILLS_WORK)
        }
    }

    fun cancel() {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(DAILY_WORK)
        manager.cancelUniqueWork(BILLS_WORK)
    }

    private fun enqueue(manager: WorkManager, name: String, kind: String, at: LocalDateTime, now: LocalDateTime) {
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(0) + SLACK_MS
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind))
            .setConstraints(online)
            .build()
        // Re-aimed from now, so the first run lands at the next reminder time whatever ran before.
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    private companion object {
        const val DAILY_WORK = "daily-reminder"
        const val BILLS_WORK = "bill-reminders"

        /** Waking just after the minute keeps "the time has come" true. */
        const val SLACK_MS = 1_000L
    }
}
