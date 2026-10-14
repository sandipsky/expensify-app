package com.example.expensify.feature.budgets.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.money.Money
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

/** Req BR-07…BR-11, BUD-01…BUD-05, NTF-01 and the US-04 acceptance criterion. */
class BudgetRulesTest {

    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val september = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

    private fun budget(
        amount: Money = Money.rupees(500),
        period: BudgetPeriod = BudgetPeriod.Monthly,
        categoryIds: List<String> = listOf("food"),
        rollover: Boolean = false,
        alertThresholds: List<Int> = listOf(80, 100),
        lastAlert: BudgetAlert? = null,
        active: Boolean = true,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        name: String = "Food",
    ) = Budget(
        id = "b1",
        name = name,
        amount = amount,
        period = period,
        categoryIds = categoryIds,
        rollover = rollover,
        alertThresholds = alertThresholds,
        lastAlert = lastAlert,
        active = active,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun tx(amount: Long, date: LocalDate, type: TxType = TxType.Expense, categoryId: String? = "food", paisa: Long = 0) = Transaction(
        id = "t-$amount-$date-$type",
        type = type,
        amount = Money.rupees(amount, paisa),
        currency = "NPR",
        accountId = "a1",
        toAccountId = if (type == TxType.Transfer) "a2" else null,
        categoryId = if (type == TxType.Transfer) null else categoryId,
        date = date,
        note = null,
        recurringRuleId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `only expenses in the budget's categories count, and only inside the range`() {
        val b = budget()
        val txs = listOf(
            tx(100, LocalDate.of(2026, 10, 3)),
            tx(50, LocalDate.of(2026, 10, 5), categoryId = "fun"),
            tx(70, LocalDate.of(2026, 10, 6), type = TxType.Income),
            tx(90, LocalDate.of(2026, 10, 7), type = TxType.Transfer),
            tx(30, LocalDate.of(2026, 9, 30)),
            tx(20, LocalDate.of(2026, 10, 31)),
        )
        assertThat(BudgetRules.spent(b.categoryIds, txs, october)).isEqualTo(Money.rupees(120))
        // No categories means every expense.
        assertThat(BudgetRules.spent(emptyList(), txs, october)).isEqualTo(Money.rupees(170))
        assertThat(BudgetRules.countsToward(listOf("food"), tx(1, today, categoryId = null))).isFalse()
    }

    @Test
    fun `thresholds compare without rounding and percent used rounds down`() {
        val limit = Money.rupees(500)
        assertThat(BudgetRules.hasReached(Money.rupees(398), limit, 80)).isFalse()
        assertThat(BudgetRules.hasReached(Money.rupees(400), limit, 80)).isTrue()
        assertThat(BudgetRules.hasReached(Money.rupees(430), limit, 100)).isFalse()
        assertThat(BudgetRules.hasReached(Money.rupees(500), limit, 100)).isTrue()
        assertThat(BudgetRules.hasReached(Money.ZERO, Money.ZERO, 80)).isTrue()

        assertThat(BudgetRules.percentUsed(Money.rupees(398), limit)).isEqualTo(79)
        assertThat(BudgetRules.percentUsed(Money.rupees(430), limit)).isEqualTo(86)
        assertThat(BudgetRules.percentUsed(Money.rupees(0, 30), Money.rupees(1))).isEqualTo(30)
        assertThat(BudgetRules.percentUsed(Money.rupees(10), Money.ZERO)).isNull()

        assertThat(BudgetRules.stateOf(Money.rupees(398), limit)).isEqualTo(BudgetState.OnTrack)
        assertThat(BudgetRules.stateOf(Money.rupees(430), limit)).isEqualTo(BudgetState.Warning)
        assertThat(BudgetRules.stateOf(Money.rupees(500), limit)).isEqualTo(BudgetState.Over)
        assertThat(BudgetRules.stateOf(Money.ZERO, Money.ZERO)).isEqualTo(BudgetState.Over)
    }

    @Test
    fun `days left include today, all of a future period, none of a past one`() {
        assertThat(BudgetRules.daysLeft(october, today)).isEqualTo(18)
        assertThat(BudgetRules.daysLeft(october, LocalDate.of(2026, 10, 31))).isEqualTo(1)
        assertThat(BudgetRules.daysLeft(october, LocalDate.of(2026, 9, 20))).isEqualTo(31)
        assertThat(BudgetRules.daysLeft(september, today)).isEqualTo(0)
    }

    @Test
    fun `safe to spend per day rounds down to the paisa and is zero with nothing left`() {
        assertThat(BudgetRules.safePerDay(Money.rupees(1000), 7)).isEqualTo(Money.rupees(142, 85))
        assertThat(BudgetRules.safePerDay(Money.rupees(100), 3)).isEqualTo(Money.rupees(33, 33))
        assertThat(BudgetRules.safePerDay(Money.ZERO, 5)).isEqualTo(Money.ZERO)
        assertThat(BudgetRules.safePerDay(Money.rupees(-20), 5)).isEqualTo(Money.ZERO)
        assertThat(BudgetRules.safePerDay(Money.rupees(100), 0)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `rollover carries last period's unspent or overspent amount, one period back only`() {
        val b = budget(rollover = true)
        assertThat(BudgetRules.carryOver(b, september, Money.rupees(300), zone)).isEqualTo(Money.rupees(200))
        assertThat(BudgetRules.carryOver(b, september, Money.rupees(600), zone)).isEqualTo(Money.rupees(-100))
        assertThat(BudgetRules.carryOver(budget(rollover = false), september, Money.rupees(300), zone)).isEqualTo(Money.ZERO)
        // Nothing rolls in from a period that ended before the budget was created.
        val young = budget(rollover = true, createdAt = Instant.parse("2026-10-02T00:00:00Z"))
        assertThat(BudgetRules.carryOver(young, september, Money.rupees(300), zone)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `a period's result applies the carry and never lets the limit go below zero`() {
        val b = budget(rollover = true)
        val unspent = listOf(tx(300, LocalDate.of(2026, 9, 10)), tx(100, LocalDate.of(2026, 10, 2)))
        val r1 = BudgetRules.result(b, unspent, today, 0, zone)
        assertThat(r1.range).isEqualTo(october)
        assertThat(r1.carry).isEqualTo(Money.rupees(200))
        assertThat(r1.limit).isEqualTo(Money.rupees(700))
        assertThat(r1.remaining).isEqualTo(Money.rupees(600))
        assertThat(r1.percent).isEqualTo(14)
        assertThat(r1.state).isEqualTo(BudgetState.OnTrack)

        val overspent = listOf(tx(1200, LocalDate.of(2026, 9, 10)), tx(10, LocalDate.of(2026, 10, 2)))
        val r2 = BudgetRules.result(b, overspent, today, 0, zone)
        assertThat(r2.carry).isEqualTo(Money.rupees(-700))
        assertThat(r2.limit).isEqualTo(Money.ZERO)
        assertThat(r2.remaining).isEqualTo(Money.rupees(-10))
        assertThat(r2.percent).isNull()
        assertThat(r2.state).isEqualTo(BudgetState.Over)

        // Without rollover the carry is zero and the limit is the amount.
        val r3 = BudgetRules.result(budget(), overspent, today, 0, zone)
        assertThat(r3.carry).isEqualTo(Money.ZERO)
        assertThat(r3.limit).isEqualTo(Money.rupees(500))
    }

    @Test
    fun `progress adds days left and the safe amount per day`() {
        val p = BudgetRules.progress(budget(), listOf(tx(140, LocalDate.of(2026, 10, 3))), today, zone)
        assertThat(p.result.remaining).isEqualTo(Money.rupees(360))
        assertThat(p.daysLeft).isEqualTo(18)
        assertThat(p.safePerDay).isEqualTo(Money.rupees(20))
    }

    @Test
    fun `history lists past periods newest first and marks ones before the budget`() {
        val b = budget(createdAt = Instant.parse("2026-08-15T00:00:00Z"))
        val history = BudgetRules.history(b, emptyList(), today, 6, zone)
        assertThat(history).hasSize(6)
        assertThat(history[0].result.range).isEqualTo(september)
        assertThat(history[0].beforeBudget).isFalse()
        assertThat(history[1].result.range.start).isEqualTo(LocalDate.of(2026, 8, 1))
        assertThat(history[1].beforeBudget).isFalse()
        assertThat(history[2].result.range.start).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(history[2].beforeBudget).isTrue()
        assertThat(BudgetRules.historyPeriods(BudgetPeriod.Weekly)).isEqualTo(8)
        assertThat(BudgetRules.historyPeriods(BudgetPeriod.Yearly)).isEqualTo(2)
    }

    @Test
    fun `needed range reaches one period further back with rollover`() {
        assertThat(BudgetRules.neededRange(budget(), today)).isEqualTo(october)
        assertThat(BudgetRules.neededRange(budget(rollover = true), today)).isEqualTo(DateRange(september.start, october.end))
        assertThat(BudgetRules.neededRange(budget(), today, 6).start).isEqualTo(LocalDate.of(2026, 4, 1))
        assertThat(BudgetRules.neededRange(budget(period = BudgetPeriod.Weekly), today))
            .isEqualTo(DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)))
        assertThat(BudgetRules.periodRange(BudgetPeriod.Yearly, today)).isEqualTo(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `US-04 each threshold alerts once per period and a jump past both alerts only the higher`() {
        val b = budget()
        fun result(spent: Long) = BudgetRules.result(b, listOf(tx(spent, LocalDate.of(2026, 10, 3))), today, 0, zone)

        // Rs 380 spent, then a Rs 50 Food expense: the warning state and one 80 % alert.
        assertThat(BudgetRules.alertDue(b, result(380))).isNull()
        assertThat(result(430).state).isEqualTo(BudgetState.Warning)
        assertThat(result(430).percent).isEqualTo(86)
        assertThat(BudgetRules.alertDue(b, result(430))).isEqualTo(80)

        // Later Food expenses that month raise no second 80 % alert.
        val alerted80 = b.copy(lastAlert = BudgetAlert(october.start, 80))
        assertThat(BudgetRules.alertDue(alerted80, result(450))).isNull()
        // Crossing Rs 500 raises one 100 % alert.
        assertThat(BudgetRules.alertDue(alerted80, result(500))).isEqualTo(100)
        val alerted100 = b.copy(lastAlert = BudgetAlert(october.start, 100))
        assertThat(BudgetRules.alertDue(alerted100, result(900))).isNull()

        // A jump past both shows only the higher.
        assertThat(BudgetRules.alertDue(b, result(600))).isEqualTo(100)
        // Last month's alert doesn't count for this month.
        val lastMonth = b.copy(lastAlert = BudgetAlert(september.start, 100))
        assertThat(BudgetRules.alertDue(lastMonth, result(430))).isEqualTo(80)
        // Paused budgets and budgets with alerts off never alert.
        assertThat(BudgetRules.alertDue(b.copy(active = false), result(600))).isNull()
        assertThat(BudgetRules.alertDue(b.copy(alertThresholds = emptyList()), result(600))).isNull()
    }

    @Test
    fun `budgets for every expense list first, then by name`() {
        val sorted = listOf(
            budget(name = "Transport", categoryIds = listOf("t")),
            budget(name = "All expenses", categoryIds = emptyList()),
            budget(name = "food", categoryIds = listOf("f")),
        ).sortedWith(BudgetRules.listOrder)
        assertThat(sorted.map { it.name }).containsExactly("All expenses", "food", "Transport").inOrder()
    }

    @Test
    fun `a suggested name follows the categories`() {
        assertThat(BudgetRules.suggestName(emptyList())).isEqualTo("All expenses")
        assertThat(BudgetRules.suggestName(listOf("Food and dining"))).isEqualTo("Food and dining")
        assertThat(BudgetRules.suggestName(listOf("Food and dining", "Transport"))).isEqualTo("Food and dining & Transport")
        assertThat(BudgetRules.suggestName(listOf("Food and dining", "Transport", "Health"))).isEqualTo("Food and dining & 2 more")
        assertThat(BudgetRules.suggestName(listOf("A".repeat(30), "B".repeat(30))).length).isEqualTo(40)
    }

    @Test
    fun `span covers every range`() {
        assertThat(BudgetRules.spanOf(emptyList())).isNull()
        assertThat(BudgetRules.spanOf(listOf(october, september))).isEqualTo(DateRange(september.start, october.end))
    }
}
