package com.example.expensify.feature.budgets.ui

import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetResult
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.categories.ui.SampleCategories
import java.time.Instant
import java.time.LocalDate

/** Realistic fixtures for previews. */
object SampleBudgets {
    private val at = Instant.parse("2026-08-01T07:10:33Z")
    val today: LocalDate = LocalDate.of(2026, 10, 14)

    private fun budget(
        id: String,
        name: String,
        amount: Money,
        period: BudgetPeriod = BudgetPeriod.Monthly,
        categoryIds: List<String> = emptyList(),
        rollover: Boolean = false,
        alerts: Boolean = true,
        active: Boolean = true,
    ) = Budget(
        id = id,
        name = name,
        amount = amount,
        period = period,
        categoryIds = categoryIds,
        rollover = rollover,
        alertThresholds = if (alerts) listOf(80, 100) else emptyList(),
        lastAlert = null,
        active = active,
        createdAt = at,
        updatedAt = at,
    )

    val everything = budget("b1", "All expenses", Money.rupees(60_000), rollover = true)
    val food = budget("b2", "Food and dining", Money.rupees(15_000), categoryIds = listOf(SampleCategories.food.id))
    val weekend = budget("b3", "Weekend fun", Money.rupees(3_000), BudgetPeriod.Weekly, listOf(SampleCategories.transport.id), active = false)

    private fun result(spent: Money, limit: Money, carry: Money = Money.ZERO) = BudgetResult(
        range = Periods.month(today),
        spent = spent,
        limit = limit,
        carry = carry,
        remaining = limit - spent,
        percent = if (limit.isPositive) (spent.paisa * 100 / limit.paisa).toInt() else null,
        state = when {
            spent.paisa * 100 >= 100 * limit.paisa -> BudgetState.Over
            spent.paisa * 100 >= 80 * limit.paisa -> BudgetState.Warning
            else -> BudgetState.OnTrack
        },
    )

    val everythingProgress = BudgetProgress(
        result = result(Money.rupees(31_480, 50), Money.rupees(64_200), carry = Money.rupees(4_200)),
        daysLeft = 18,
        safePerDay = Money.rupees(1_817, 75),
    )
    val foodProgress = BudgetProgress(
        result = result(Money.rupees(12_900), Money.rupees(15_000)),
        daysLeft = 18,
        safePerDay = Money.rupees(116, 66),
    )

    val cards = listOf(
        BudgetCardUi(everything, plain("All expenses"), BudgetLabels.Icon(BudgetLabels.GENERIC_ICON, null), everythingProgress),
        BudgetCardUi(food, plain("Food and dining"), BudgetLabels.Icon(SampleCategories.food.icon, SampleCategories.food.color), foodProgress),
    )
    val pausedCards = listOf(
        BudgetCardUi(weekend, plain("Transport"), BudgetLabels.Icon(SampleCategories.transport.icon, SampleCategories.transport.color), null),
    )
}
