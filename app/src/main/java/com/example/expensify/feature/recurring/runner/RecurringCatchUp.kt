package com.example.expensify.feature.recurring.runner

import com.example.expensify.core.error.AppError
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.recurring.ui.RuleLabels
import com.example.expensify.feature.recurring.ui.RuleView
import com.example.expensify.feature.transactions.ui.TxRows
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** What one catch-up pass did for a rule. */
sealed interface CatchUpResult {
    data class Added(val view: RuleView, val dates: List<LocalDate>) : CatchUpResult
    data class Failed(val view: RuleView, val error: Exception) : CatchUpResult
}

/**
 * One pass of the recurring runner (req REC-06): for every automatic rule,
 * the entries due by today are added in one write, oldest first. The
 * foreground observer and the background worker both call it, so a rule is
 * handled the same way whether the app is open or not. A date another
 * device already handled is skipped quietly.
 */
@Singleton
class RecurringCatchUp @Inject constructor(private val repository: RecurringRepository) {

    /** Rule and first date already sent this process, so a stale snapshot doesn't send it again. */
    private val sent = mutableSetOf<String>()

    suspend fun run(
        rules: List<RecurringRule>,
        accounts: List<Account>,
        categories: List<Category>,
        today: LocalDate,
        report: suspend (CatchUpResult) -> Unit = {},
    ) {
        val lookups = TxRows.Lookups(accounts, categories, today)
        for (rule in rules) {
            if (rule.mode != RuleMode.Auto) continue
            val view = RuleLabels.toView(rule, lookups)
            // At most a batch at a time; the refreshed rule brings the next batch.
            val dates = view.due
            if (dates.isEmpty()) continue
            val key = "${rule.id}|${dates[0]}"
            if (!sent.add(key)) continue

            try {
                repository.addOccurrences(rule.id, dates, RecurringRules.nextAfter(rule, dates.last()))
                report(CatchUpResult.Added(view, dates))
            } catch (_: AppError.Stale) {
                // Another device got there first: nothing to tell.
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Offline or refused: the key is released so the next pass tries again.
                sent.remove(key)
                report(CatchUpResult.Failed(view, error))
            }
        }
    }
}
