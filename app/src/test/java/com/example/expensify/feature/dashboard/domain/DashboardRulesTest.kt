package com.example.expensify.feature.dashboard.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.money.Money
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Req BR-04, BR-14 and the shared category totals of dashboard.domain.ts. */
class DashboardRulesTest {

    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

    private fun tx(amount: Long, type: TxType = TxType.Expense, categoryId: String? = "food", paisa: Long = 0, date: LocalDate = LocalDate.of(2026, 10, 5)) =
        Transaction(
            id = "t-$amount-$type-$categoryId",
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
    fun `percentages round half up toward positive infinity, as Math round does`() {
        assertThat(DashboardRules.percentOf(1, 8)).isEqualTo(13) // 12.5
        assertThat(DashboardRules.percentOf(-1, 8)).isEqualTo(-12) // -12.5
        assertThat(DashboardRules.percentOf(1, 3)).isEqualTo(33)
        assertThat(DashboardRules.percentOf(2, 3)).isEqualTo(67)
        assertThat(DashboardRules.percentOf(0, 5)).isEqualTo(0)
    }

    @Test
    fun `share, savings rate and change are whole percents, or null with nothing to compare`() {
        assertThat(DashboardRules.shareOf(Money.rupees(250), Money.rupees(1000))).isEqualTo(25)
        assertThat(DashboardRules.shareOf(Money.rupees(250), Money.ZERO)).isNull()
        assertThat(DashboardRules.savingsRate(Money.rupees(1000), Money.rupees(345))).isEqualTo(35)
        assertThat(DashboardRules.savingsRate(Money.ZERO, Money.rupees(-20))).isNull()
        assertThat(DashboardRules.changePercent(Money.rupees(200), Money.rupees(250))).isEqualTo(25)
        assertThat(DashboardRules.changePercent(Money.rupees(-200), Money.rupees(-100))).isEqualTo(50)
        assertThat(DashboardRules.changePercent(Money.ZERO, Money.rupees(100))).isNull()
        assertThat(DashboardRules.changeOf(Money.rupees(200), Money.rupees(150))).isEqualTo(Change(Money.rupees(-50), -25))
    }

    @Test
    fun `category totals count one type, keep uncategorized under null and sort largest first then by id`() {
        val txs = listOf(
            tx(100, categoryId = "food"),
            tx(60, categoryId = "fun"),
            tx(60, categoryId = "car"),
            tx(5, categoryId = null),
            tx(900, type = TxType.Income, categoryId = "salary"),
            tx(50, type = TxType.Transfer),
            tx(40, categoryId = "food"),
        )
        val totals = DashboardRules.categoryTotals(txs, CategoryType.Expense)
        assertThat(totals.map { it.categoryId }).containsExactly("food", "car", "fun", null).inOrder()
        assertThat(totals[0]).isEqualTo(CategoryTotal("food", Money.rupees(140), 2))
        assertThat(DashboardRules.categoryTotals(txs, CategoryType.Income)).containsExactly(CategoryTotal("salary", Money.rupees(900), 1))
    }

    @Test
    fun `top and other folds the rest into one row, or nothing when nothing is left`() {
        val rows = listOf(
            CategoryTotal("a", Money.rupees(50), 1),
            CategoryTotal("b", Money.rupees(30), 2),
            CategoryTotal(null, Money.rupees(5), 1),
        )
        val split = DashboardRules.topAndOther(rows, 1)
        assertThat(split.top).containsExactly(rows[0])
        assertThat(split.other).isEqualTo(DashboardRules.Other(Money.rupees(35), 3, listOf("b")))
        assertThat(DashboardRules.topAndOther(rows, 3).other).isNull()
    }

    @Test
    fun `a summary leaves transfers out and reads the savings rate from net over income`() {
        val summary = DashboardRules.summaryOf(october, listOf(tx(1000, type = TxType.Income), tx(400), tx(999, type = TxType.Transfer)))
        assertThat(summary).isEqualTo(PeriodSummary(october, Money.rupees(1000), Money.rupees(400), Money.rupees(600), 60))
    }
}
