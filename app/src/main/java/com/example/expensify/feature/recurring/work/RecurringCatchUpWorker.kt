package com.example.expensify.feature.recurring.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.expensify.core.date.Today
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.runner.CatchUpResult
import com.example.expensify.feature.recurring.runner.RecurringCatchUp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The recurring catch-up while the app is closed (architecture §9): a daily
 * WorkManager job that runs the same pass as the foreground runner, so the
 * 1st-of-month salary is added without the app being opened. The rules are
 * read from the server, since the on-device cache can't know about a rule
 * added or moved on the web while the app was closed. It bails out while
 * nobody is logged in, and asks for a retry when the server can't be
 * reached. The foreground runner still catches up on open, as the web does,
 * so this is a bonus, never the only path.
 */
@HiltWorker
class RecurringCatchUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionRepository,
    private val rules: RecurringRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val today: Today,
    private val catchUp: RecurringCatchUp,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Firebase restores the session on process start; give it a moment.
        val signedIn = withTimeoutOrNull(SESSION_TIMEOUT_MS) { session.session.first { it != Session.Unknown } }
        if (signedIn !is Session.SignedIn) return Result.success()
        today.refresh()

        val snapshot = try {
            withTimeoutOrNull(READ_TIMEOUT_MS) {
                Triple(rules.fetchAll(), accounts.accounts().first(), categories.categories().first())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return Result.retry()

        var failed = false
        catchUp.run(snapshot.first, snapshot.second, snapshot.third, today.date.value) { result ->
            if (result is CatchUpResult.Failed) failed = true
        }
        return if (failed) Result.retry() else Result.success()
    }

    private companion object {
        const val SESSION_TIMEOUT_MS = 15_000L
        const val READ_TIMEOUT_MS = 30_000L
    }
}

/** Keeps the daily catch-up job scheduled while someone is logged in. */
@Singleton
class RecurringWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Once a day with an hour of flex, when the network is up; a job already scheduled is kept. */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<RecurringCatchUpWorker>(24, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One pass as soon as the network allows, outside the daily schedule. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<RecurringCatchUpWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NOW_NAME)
    }

    private companion object {
        const val WORK_NAME = "recurring-catch-up"
        const val WORK_NOW_NAME = "recurring-catch-up-now"
    }
}
