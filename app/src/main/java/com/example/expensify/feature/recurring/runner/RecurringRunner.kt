package com.example.expensify.feature.recurring.runner

import com.example.expensify.R
import com.example.expensify.core.date.Today
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.ui.RuleLabels
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen

/**
 * Adds automatic rules' entries on their due date, and any missed while the
 * app was closed, from whichever screen is open (req REC-06, US-05; the port
 * of RecurringRunner.tsx). Each write starts at the rule's next due date and
 * its Firestore transaction refuses one that doesn't, so a date is added
 * once however many devices are open. Ask-first rules only wait here: the
 * Recurring page lists them for Confirm or Skip. Runs for as long as the app
 * shell is alive; `RecurringCatchUpWorker` does the same while it is closed.
 */
@Singleton
class RecurringRunner @Inject constructor(
    private val rules: RecurringRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val today: Today,
    private val catchUp: RecurringCatchUp,
    private val messages: Messages,
) {
    private data class Snapshot(val rules: List<RecurringRule>, val accounts: List<Account>, val categories: List<Category>, val today: LocalDate)

    suspend fun run() {
        combine(rules.rules(), accounts.accounts(), categories.categories(), today.date) { r, a, c, day -> Snapshot(r, a, c, day) }
            .retryWhen { _, attempt ->
                delay(5_000L * (attempt + 1).coerceAtMost(6))
                true
            }
            .collect { snapshot ->
                catchUp.run(snapshot.rules, snapshot.accounts, snapshot.categories, snapshot.today) { result ->
                    when (result) {
                        is CatchUpResult.Added -> {
                            val view = result.view
                            val amount = MoneyFormat.full(view.amount, if (view.kind == AmountKind.Transfer) Sign.Auto else Sign.ExceptZero)
                            if (result.dates.size == 1) {
                                messages.show(res(R.string.recurring_added_one, view.name, amount))
                            } else {
                                messages.show(res(R.string.recurring_added_many, RuleLabels.entryCount(result.dates.size), view.name, amount))
                            }
                        }
                        is CatchUpResult.Failed ->
                            messages.show(res(R.string.could_not_add_recurring, result.view.name, ErrorMessages.of(result.error)))
                    }
                }
            }
    }
}
