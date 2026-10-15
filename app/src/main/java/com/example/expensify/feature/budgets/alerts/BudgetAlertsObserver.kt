package com.example.expensify.feature.budgets.alerts

import com.example.expensify.R
import com.example.expensify.core.date.Today
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetAlert
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.notifications.AlertInput
import com.example.expensify.feature.notifications.Notifier
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.AlertLinks
import com.example.expensify.feature.notifications.domain.AlertTone
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * Budget alerts while the app is open (req NTF-01, BUD-07), the port of
 * BudgetAlerts.tsx: an alert when a budget reaches 80 % or 100 % of its
 * limit, once per threshold per period, unless turned off in Settings. The
 * budget's `lastAlert` records what was shown, so another device or a
 * restart doesn't show it again. Each alert goes through the `Notifier`: into
 * the list behind the bell, and on screen or as a system notification (req
 * NTF-05). Runs for as long as the app shell is alive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetAlertsObserver @Inject constructor(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val auth: AuthRepository,
    private val today: Today,
    private val notifier: Notifier,
) {
    private data class Snapshot(val enabled: Boolean, val budgets: List<Budget>, val txs: List<Transaction>, val today: LocalDate)

    /** Alerts shown this run, in case a snapshot lands before `lastAlert` is written back. */
    private val shown = mutableSetOf<String>()

    suspend fun run() {
        val enabled = auth.profile()
            .map { it?.preferences?.notifications?.budgetAlerts ?: false }
            .distinctUntilChanged()
        val watched = budgets.budgets().map { all -> all.filter { it.active && it.alertThresholds.isNotEmpty() } }
        // The same range the Budgets page reads, so both share one listener's worth of entries.
        val spending = combine(watched, today.date) { list, day -> BudgetRules.spanOf(list.map { BudgetRules.neededRange(it, day) }) }
            .distinctUntilChanged()
            .flatMapLatest { range -> if (range == null) flowOf(emptyList()) else transactions.inRange(range) }

        combine(enabled, watched, spending, today.date) { on, list, txs, day -> Snapshot(on, list, txs, day) }
            .retryWhen { _, attempt ->
                delay(5_000L * (attempt + 1).coerceAtMost(6))
                true
            }
            .collect { snapshot -> if (snapshot.enabled) check(snapshot) }
    }

    private suspend fun check(snapshot: Snapshot) {
        for (budget in snapshot.budgets) {
            val progress = BudgetRules.progress(budget, snapshot.txs, snapshot.today)
            val threshold = BudgetRules.alertDue(budget, progress.result) ?: continue
            val key = "${budget.id}|${progress.result.range.start}|$threshold"
            if (!shown.add(key)) continue

            try {
                budgets.recordAlert(budget.id, BudgetAlert(progress.result.range.start, threshold))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Offline or refused: the alert is shown once here; the next run re-reads `lastAlert`.
            }
            notifier.deliver(alert(budget, progress, threshold, key))
        }
    }

    /** "Food: over budget · Rs 120.00 over · 5 days left", or "Food: 86% used · Rs 70.00 left · 5 days left". */
    private fun alert(budget: Budget, progress: BudgetProgress, threshold: Int, key: String): AlertInput {
        val result = progress.result
        val left = BudgetLabels.daysLeft(progress.daysLeft)
        val over = result.state == BudgetState.Over
        return AlertInput(
            id = "budget|$key",
            kind = AlertKind.Budget,
            tone = if (over) AlertTone.Error else AlertTone.Warn,
            title = if (over) {
                res(R.string.budget_alert_title_over, budget.name)
            } else {
                res(R.string.budget_alert_title_used, budget.name, result.percent ?: threshold)
            },
            message = when {
                !over -> res(R.string.budget_alert_message_left, MoneyFormat.full(result.remaining), left)
                result.remaining.isNegative -> res(R.string.budget_alert_message_over, MoneyFormat.full(-result.remaining), left)
                else -> res(R.string.budget_alert_message_nothing_left, left)
            },
            link = AlertLinks.budget(budget.id),
        )
    }
}
