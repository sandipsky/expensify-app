package com.example.expensify.feature.dashboard.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.money.Money
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.budgets.domain.BudgetResult
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringTemplate
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Req BR-04, BR-14, DSH-01, DSH-03, DSH-07, DSH-08, DSH-10 and the shared category totals of dashboard.domain.ts. */
class DashboardRulesTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val september = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    private fun tx(amount: Long, type: TxType = TxType.Expense, categoryId: String? = "food", paisa: Long = 0, date: LocalDate = LocalDate.of(2026, 10, 5)) =
        Transaction(
            id = "t-$amount-$type-$categoryId-$date",
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

    @Test
    fun `DSH-03 the change from the previous period is an amount and percent per figure, and points for the savings rate`() {
        val previous = PeriodSummary(september, Money.rupees(1000), Money.rupees(400), Money.rupees(600), 60)
        val current = PeriodSummary(october, Money.rupees(1200), Money.rupees(600), Money.rupees(600), 50)
        assertThat(DashboardRules.summaryChange(previous, current)).isEqualTo(
            SummaryChange(
                income = Change(Money.rupees(200), 20),
                expense = Change(Money.rupees(200), 50),
                net = Change(Money.ZERO, 0),
                savingsRate = -10,
            ),
        )
        // No income on either side: no savings rate to compare.
        assertThat(DashboardRules.summaryChange(previous.copy(savingsRate = null), current).savingsRate).isNull()
        // Only transfers before: nothing to compare with at all.
        assertThat(DashboardRules.hasComparison(listOf(tx(50, type = TxType.Transfer)))).isFalse()
        assertThat(DashboardRules.hasComparison(listOf(tx(50)))).isTrue()
    }

    @Test
    fun `period summaries give each range its own figures from one list`() {
        val txs = listOf(
            tx(100, date = d("2026-09-10")),
            tx(300, type = TxType.Income, date = d("2026-10-02")),
            tx(20, date = d("2026-11-01")),
        )
        val summaries = DashboardRules.periodSummaries(txs, listOf(september, october))
        assertThat(summaries.map { it.expense }).containsExactly(Money.rupees(100), Money.ZERO).inOrder()
        assertThat(summaries.map { it.income }).containsExactly(Money.ZERO, Money.rupees(300)).inOrder()
    }

    @Test
    fun `DSH-01 presets stand for this month, last month, or the custom dates in order`() {
        val custom = DateRange(d("2026-10-10"), d("2026-10-01"))
        assertThat(DashboardRules.dashboardRange(DashboardPreset.ThisMonth, today, custom)).isEqualTo(october)
        assertThat(DashboardRules.dashboardRange(DashboardPreset.LastMonth, today, custom)).isEqualTo(september)
        assertThat(DashboardRules.dashboardRange(DashboardPreset.Custom, today, custom)).isEqualTo(DateRange(d("2026-10-01"), d("2026-10-10")))
        assertThat(DashboardRules.dashboardPresetOf(october, today)).isEqualTo(DashboardPreset.ThisMonth)
        assertThat(DashboardRules.dashboardPresetOf(september, today)).isEqualTo(DashboardPreset.LastMonth)
        assertThat(DashboardRules.dashboardPresetOf(DateRange(d("2026-10-01"), d("2026-10-10")), today)).isEqualTo(DashboardPreset.Custom)
        assertThat(DashboardPreset.fromStored("last_month")).isEqualTo(DashboardPreset.LastMonth)
        assertThat(DashboardPreset.fromStored("this_year")).isNull()
    }

    @Test
    fun `DSH-08 the trend covers the period and the five before it, as months or as ranges of the same length`() {
        val months = DashboardRules.trendPeriods(DashboardPreset.ThisMonth, october, today)
        assertThat(months.map { it.start }).containsExactly(
            d("2026-05-01"), d("2026-06-01"), d("2026-07-01"), d("2026-08-01"), d("2026-09-01"), d("2026-10-01"),
        ).inOrder()
        assertThat(DashboardRules.trendPeriods(DashboardPreset.LastMonth, september, today).last()).isEqualTo(september)

        val tenDays = DateRange(d("2026-10-01"), d("2026-10-10"))
        assertThat(DashboardRules.previousRanges(tenDays, 2)).containsExactly(
            DateRange(d("2026-09-11"), d("2026-09-20")),
            DateRange(d("2026-09-21"), d("2026-09-30")),
        ).inOrder()
        val custom = DashboardRules.trendPeriods(DashboardPreset.Custom, tenDays, today)
        assertThat(custom).hasSize(6)
        assertThat(custom.first()).isEqualTo(DateRange(d("2026-08-12"), d("2026-08-21")))
        assertThat(custom.last()).isEqualTo(tenDays)
        assertThat(DashboardRules.earlierRange(custom, tenDays)).isEqualTo(DateRange(d("2026-08-12"), d("2026-09-30")))
    }

    @Test
    fun `DSH-07 budgets sort most used first, a zero limit over everything, and loading ones last`() {
        fun progress(percent: Int?) = BudgetProgress(
            result = BudgetResult(october, Money.rupees(100), Money.rupees(500), Money.ZERO, Money.rupees(400), percent, BudgetState.OnTrack),
            daysLeft = 18,
            safePerDay = Money.rupees(22),
        )
        assertThat(DashboardRules.usedOf(null)).isEqualTo(-1.0)
        assertThat(DashboardRules.usedOf(progress(86))).isEqualTo(86.0)
        assertThat(DashboardRules.usedOf(progress(null))).isEqualTo(Double.POSITIVE_INFINITY)
    }

    @Test
    fun `DSH-10 upcoming lists a running rule's dates within a week, and only its next date due by today can be acted on`() {
        fun rule(next: String, frequency: Frequency = Frequency.Monthly, dayOfMonth: Int? = 14, active: Boolean = true) = RecurringRule(
            id = "r1",
            template = RecurringTemplate(TxType.Expense, Money.rupees(18_000), "a1", null, "rent", "Rent"),
            frequency = frequency,
            interval = 1,
            weekdays = emptyList(),
            dayOfMonth = dayOfMonth,
            startDate = d("2026-08-14"),
            endType = EndType.Never,
            endDate = null,
            maxCount = null,
            mode = RuleMode.Confirm,
            nextDueDate = d(next),
            occurrences = 2,
            active = active,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        assertThat(DashboardRules.upcoming(rule("2026-10-14"), RuleStatus.Active, today)).containsExactly(UpcomingDate(d("2026-10-14"), due = true))
        assertThat(DashboardRules.upcoming(rule("2026-10-18"), RuleStatus.Active, today)).containsExactly(UpcomingDate(d("2026-10-18"), due = false))
        assertThat(DashboardRules.upcoming(rule("2026-11-14"), RuleStatus.Active, today)).isEmpty()
        assertThat(DashboardRules.upcoming(rule("2026-10-14", active = false), RuleStatus.Paused, today)).isEmpty()

        val daily = DashboardRules.upcoming(rule("2026-10-13", Frequency.Daily, null), RuleStatus.Active, today)
        assertThat(daily.map { it.date }.first()).isEqualTo(d("2026-10-13"))
        assertThat(daily.map { it.date }.last()).isEqualTo(d("2026-10-21"))
        assertThat(daily.filter { it.due }.map { it.date }).containsExactly(d("2026-10-13"))
    }
}
