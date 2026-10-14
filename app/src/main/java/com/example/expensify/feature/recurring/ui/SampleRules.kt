package com.example.expensify.feature.recurring.ui

import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringTemplate
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.TxRows
import java.time.Instant
import java.time.LocalDate

/** Realistic fixtures for previews. */
object SampleRules {
    val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val at = Instant.parse("2026-08-01T07:10:33Z")

    private fun rule(
        id: String,
        template: RecurringTemplate,
        frequency: Frequency = Frequency.Monthly,
        dayOfMonth: Int? = 1,
        weekdays: List<Int> = emptyList(),
        startDate: LocalDate = LocalDate.of(2026, 8, 1),
        nextDueDate: LocalDate = LocalDate.of(2026, 11, 1),
        mode: RuleMode = RuleMode.Auto,
        active: Boolean = true,
        endType: EndType = EndType.Never,
        maxCount: Int? = null,
        occurrences: Int = 3,
    ) = RecurringRule(
        id = id,
        template = template,
        frequency = frequency,
        interval = 1,
        weekdays = weekdays,
        dayOfMonth = dayOfMonth,
        startDate = startDate,
        endType = endType,
        endDate = null,
        maxCount = maxCount,
        mode = mode,
        nextDueDate = nextDueDate,
        occurrences = occurrences,
        active = active,
        createdAt = at,
        updatedAt = at,
    )

    val salary = rule(
        "r1",
        RecurringTemplate(TxType.Income, Money.rupees(85_000), SampleAccounts.everyday.id, null, SampleCategories.all.first { it.name == "Salary" }.id, "Salary"),
    )
    val rent = rule(
        "r2",
        RecurringTemplate(TxType.Expense, Money.rupees(18_000), SampleAccounts.everyday.id, null, SampleCategories.all.first { it.name == "Housing and rent" }.id, "Rent"),
        dayOfMonth = 14,
        nextDueDate = today,
        mode = RuleMode.Confirm,
    )
    val gym = rule(
        "r3",
        RecurringTemplate(TxType.Expense, Money.rupees(2_500), SampleAccounts.cash.id, null, SampleCategories.all.first { it.name == "Health" }.id, null),
        frequency = Frequency.Weekly,
        dayOfMonth = null,
        weekdays = listOf(1, 4),
        nextDueDate = LocalDate.of(2026, 10, 15),
        active = false,
    )
    val loan = rule(
        "r4",
        RecurringTemplate(TxType.Transfer, Money.rupees(10_000), SampleAccounts.everyday.id, SampleAccounts.savings.id, null, "Savings top-up"),
        endType = EndType.Count,
        maxCount = 3,
        occurrences = 3,
    )

    val all: List<RecurringRule> = listOf(salary, rent, gym, loan)

    val lookups = TxRows.Lookups(SampleAccounts.active + SampleAccounts.archived, SampleCategories.all, today)

    val groups: RuleGroups = RuleLabels.group(all, lookups)

    val rentView: RuleView = RuleLabels.toView(rent, lookups)
}
