package com.example.expensify.feature.recurring.ui

import com.example.expensify.core.date.Today
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import com.example.expensify.feature.recurring.domain.RecurringRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** How many ask-first entries wait for Confirm or Skip, for the badge on More's Recurring item (req REC-07). */
@Singleton
class WaitingCount @Inject constructor(
    rules: RecurringRepository,
    accounts: AccountRepository,
    today: Today,
) {
    val count: Flow<Int> = combine(rules.rules(), accounts.accounts(), today.date) { rules, accounts, day ->
        val ids = accounts.map { it.id }.toSet()
        RecurringRules.waitingCount(rules, { it in ids }, day)
    }
        .catch { emit(0) }
        .distinctUntilChanged()
}
