package com.example.expensify.feature.recurring.domain

import com.example.expensify.core.money.Money
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Req BR-12, BR-13, REC-02…REC-11 and the US-05, US-06 acceptance criteria. */
class RecurringRulesTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    private fun schedule(
        frequency: Frequency,
        start: String,
        interval: Int = 1,
        weekdays: List<Int> = emptyList(),
        dayOfMonth: Int? = null,
    ) = ScheduleSpec(frequency, interval, weekdays, dayOfMonth, d(start))

    private val template = RecurringTemplate(TxType.Income, Money.rupees(50_000), "bank", null, "salary", "Salary")

    private fun rule(
        schedule: Schedule = schedule(Frequency.Monthly, "2026-10-01", dayOfMonth = 1),
        nextDueDate: String = "2026-10-01",
        occurrences: Int = 0,
        endType: EndType = EndType.Never,
        endDate: String? = null,
        maxCount: Int? = null,
        mode: RuleMode = RuleMode.Auto,
        active: Boolean = true,
        template: RecurringTemplate = this.template,
    ) = RecurringRule(
        id = "r1",
        template = template,
        frequency = schedule.frequency,
        interval = schedule.interval,
        weekdays = schedule.weekdays,
        dayOfMonth = schedule.dayOfMonth,
        startDate = schedule.startDate,
        endType = endType,
        endDate = endDate?.let(::d),
        maxCount = maxCount,
        mode = mode,
        nextDueDate = d(nextDueDate),
        occurrences = occurrences,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `BR-12 daily rules count every N days from the start date`() {
        val every3 = schedule(Frequency.Daily, "2026-10-01", interval = 3)
        assertThat(RecurringRules.firstDueDate(every3)).isEqualTo(d("2026-10-01"))
        assertThat(RecurringRules.firstOnOrAfter(every3, d("2026-10-05"))).isEqualTo(d("2026-10-07"))
        assertThat(RecurringRules.nextAfter(every3, d("2026-10-07"))).isEqualTo(d("2026-10-10"))
        // Never before the start date.
        assertThat(RecurringRules.firstOnOrAfter(every3, d("2026-09-01"))).isEqualTo(d("2026-10-01"))
    }

    @Test
    fun `BR-12 weekly rules use the chosen ISO weekdays, every N Monday-to-Sunday weeks`() {
        // 1 Oct 2026 is a Thursday.
        val monThu = schedule(Frequency.Weekly, "2026-10-01", weekdays = listOf(4, 1))
        assertThat(RecurringRules.weekdaysOf(monThu)).containsExactly(1, 4).inOrder()
        assertThat(RecurringRules.firstDueDate(monThu)).isEqualTo(d("2026-10-01"))
        assertThat(RecurringRules.nextAfter(monThu, d("2026-10-01"))).isEqualTo(d("2026-10-05"))
        assertThat(RecurringRules.nextAfter(monThu, d("2026-10-05"))).isEqualTo(d("2026-10-08"))

        val fortnightly = schedule(Frequency.Weekly, "2026-10-01", interval = 2, weekdays = listOf(1, 4))
        // The start week is week 0 (Mon 28 Sep); week 1 is skipped, week 2 starts Mon 12 Oct.
        assertThat(RecurringRules.nextAfter(fortnightly, d("2026-10-01"))).isEqualTo(d("2026-10-12"))

        // No weekdays means the start date's weekday.
        val none = schedule(Frequency.Weekly, "2026-10-01")
        assertThat(RecurringRules.weekdaysOf(none)).containsExactly(4)
        assertThat(RecurringRules.nextAfter(none, d("2026-10-01"))).isEqualTo(d("2026-10-08"))
    }

    @Test
    fun `BR-12 monthly rules fall on the last day of shorter months and count N months from the start month`() {
        val day31 = schedule(Frequency.Monthly, "2026-01-31", dayOfMonth = 31)
        assertThat(RecurringRules.firstDueDate(day31)).isEqualTo(d("2026-01-31"))
        assertThat(RecurringRules.firstOnOrAfter(day31, d("2026-02-01"))).isEqualTo(d("2026-02-28"))
        assertThat(RecurringRules.nextAfter(day31, d("2026-02-28"))).isEqualTo(d("2026-03-31"))
        assertThat(RecurringRules.nextAfter(day31, d("2026-03-31"))).isEqualTo(d("2026-04-30"))

        val every2 = schedule(Frequency.Monthly, "2026-01-15", interval = 2, dayOfMonth = 15)
        assertThat(RecurringRules.firstOnOrAfter(every2, d("2026-02-01"))).isEqualTo(d("2026-03-15"))
        assertThat(RecurringRules.firstOnOrAfter(every2, d("2026-03-16"))).isEqualTo(d("2026-05-15"))

        // No day means the start date's day.
        val noDay = schedule(Frequency.Monthly, "2026-01-15")
        assertThat(RecurringRules.dayOfMonthOf(noDay)).isEqualTo(15)
    }

    @Test
    fun `BR-12 yearly rules repeat the start day and month, 29 February on the 28th in common years`() {
        val leap = schedule(Frequency.Yearly, "2024-02-29")
        assertThat(RecurringRules.nextAfter(leap, d("2024-02-29"))).isEqualTo(d("2025-02-28"))
        assertThat(RecurringRules.firstOnOrAfter(leap, d("2026-01-01"))).isEqualTo(d("2026-02-28"))
        assertThat(RecurringRules.firstOnOrAfter(leap, d("2028-01-01"))).isEqualTo(d("2028-02-29"))
        val every2 = schedule(Frequency.Yearly, "2026-12-25", interval = 2)
        assertThat(RecurringRules.nextAfter(every2, d("2026-12-25"))).isEqualTo(d("2028-12-25"))
    }

    @Test
    fun `REC-08 an edit counts from the earlier of the next due date and today, REC-09 a resume from the later`() {
        val monthly = schedule(Frequency.Monthly, "2026-01-01", dayOfMonth = 1)
        val today = d("2026-10-14")
        assertThat(RecurringRules.rescheduledDueDate(monthly, d("2026-09-01"), today)).isEqualTo(d("2026-09-01"))
        assertThat(RecurringRules.rescheduledDueDate(monthly, d("2026-12-01"), today)).isEqualTo(d("2026-11-01"))
        assertThat(RecurringRules.resumedDueDate(monthly, d("2026-09-01"), today)).isEqualTo(d("2026-11-01"))
        assertThat(RecurringRules.resumedDueDate(monthly, d("2026-12-01"), today)).isEqualTo(d("2026-12-01"))
    }

    @Test
    fun `same schedule ignores the fields a frequency doesn't use`() {
        val a = schedule(Frequency.Monthly, "2026-01-01", dayOfMonth = 1, weekdays = listOf(1))
        val b = schedule(Frequency.Monthly, "2026-01-01", dayOfMonth = 1, weekdays = listOf(5))
        assertThat(RecurringRules.sameSchedule(a, b)).isTrue()
        assertThat(RecurringRules.sameSchedule(a, b.copy(dayOfMonth = 2))).isFalse()
        assertThat(RecurringRules.sameSchedule(a, b.copy(interval = 2))).isFalse()
        assertThat(RecurringRules.sameSchedule(a, b.copy(frequency = Frequency.Weekly))).isFalse()
    }

    @Test
    fun `BR-13 a rule ends when its count is reached or its next date is past the end date`() {
        assertThat(RecurringRules.isEnded(rule(endType = EndType.Count, maxCount = 3, occurrences = 2))).isFalse()
        assertThat(RecurringRules.isEnded(rule(endType = EndType.Count, maxCount = 3, occurrences = 3))).isTrue()
        assertThat(RecurringRules.isEnded(rule(endType = EndType.Until, endDate = "2026-12-01", nextDueDate = "2026-12-01"))).isFalse()
        assertThat(RecurringRules.isEnded(rule(endType = EndType.Until, endDate = "2026-12-01", nextDueDate = "2027-01-01"))).isTrue()
        assertThat(RecurringRules.isEnded(rule(occurrences = 999))).isFalse()
    }

    @Test
    fun `pending dates start at the next due date and stop at the end or the through date`() {
        val r = rule()
        assertThat(RecurringRules.pendingDates(r, 3)).containsExactly(d("2026-10-01"), d("2026-11-01"), d("2026-12-01")).inOrder()
        assertThat(RecurringRules.pendingDates(r, 10, through = d("2026-11-15"))).containsExactly(d("2026-10-01"), d("2026-11-01")).inOrder()
        assertThat(RecurringRules.pendingDates(rule(endType = EndType.Count, maxCount = 2, occurrences = 1), 10)).containsExactly(d("2026-10-01"))
        assertThat(RecurringRules.pendingDates(rule(endType = EndType.Until, endDate = "2026-11-01"), 10)).hasSize(2)
        assertThat(RecurringRules.pendingDates(rule(endType = EndType.Count, maxCount = 1, occurrences = 1), 10)).isEmpty()
    }

    @Test
    fun `US-05 a monthly rule missed on the 1st is still due, dated the 1st, and a paused one adds nothing`() {
        val today = d("2026-10-03")
        assertThat(RecurringRules.dueDates(rule(), today)).containsExactly(d("2026-10-01"))
        assertThat(RecurringRules.dueDates(rule(active = false), today)).isEmpty()
        assertThat(RecurringRules.dueDates(rule(nextDueDate = "2026-11-01"), today)).isEmpty()
        // A catch-up over a long gap is capped at one write's worth.
        val daily = rule(schedule(Frequency.Daily, "2026-01-01"), nextDueDate = "2026-01-01")
        assertThat(RecurringRules.dueDates(daily, today)).hasSize(RecurringRules.CATCH_UP_LIMIT)
        assertThat(RecurringRules.upcomingDates(rule(nextDueDate = "2026-11-01"), d("2026-10-26"), 7)).containsExactly(d("2026-11-01"))
        assertThat(RecurringRules.upcomingDates(rule(nextDueDate = "2026-11-01"), d("2026-10-24"), 7)).isEmpty()
    }

    @Test
    fun `US-06 an ask-first rule due today waits, and once handled it no longer counts`() {
        val today = d("2026-10-14")
        val rent = rule(schedule(Frequency.Monthly, "2026-10-14", dayOfMonth = 14), nextDueDate = "2026-10-14", mode = RuleMode.Confirm)
        val exists = { _: String -> true }
        assertThat(RecurringRules.statusOf(rent, exists)).isEqualTo(RuleStatus.Active)
        assertThat(RecurringRules.waitingCount(listOf(rent), exists, today)).isEqualTo(1)
        // Confirm or Skip moves the rule on to the next schedule date.
        val handled = rent.copy(nextDueDate = RecurringRules.nextAfter(rent, d("2026-10-14")))
        assertThat(handled.nextDueDate).isEqualTo(d("2026-11-14"))
        assertThat(RecurringRules.waitingCount(listOf(handled), exists, today)).isEqualTo(0)
        // Automatic rules never wait; a rule missing an account can't run.
        assertThat(RecurringRules.waitingCount(listOf(rent.copy(mode = RuleMode.Auto)), exists, today)).isEqualTo(0)
        assertThat(RecurringRules.waitingCount(listOf(rent), { false }, today)).isEqualTo(0)
    }

    @Test
    fun `REC-11 status is paused, ended, blocked by a deleted account, or active`() {
        val exists = { id: String -> id == "bank" }
        assertThat(RecurringRules.statusOf(rule(active = false), exists)).isEqualTo(RuleStatus.Paused)
        assertThat(RecurringRules.statusOf(rule(endType = EndType.Count, maxCount = 1, occurrences = 1), exists)).isEqualTo(RuleStatus.Ended)
        assertThat(RecurringRules.statusOf(rule(template = template.copy(accountId = "gone")), exists)).isEqualTo(RuleStatus.MissingAccount)
        val transfer = template.copy(type = TxType.Transfer, toAccountId = "gone", categoryId = null)
        assertThat(RecurringRules.statusOf(rule(template = transfer), exists)).isEqualTo(RuleStatus.MissingAccount)
        assertThat(RecurringRules.statusOf(rule(), exists)).isEqualTo(RuleStatus.Active)
    }

    @Test
    fun `an occurrence takes the template's fields on the date, clearing what its type doesn't use`() {
        val body = RecurringRules.occurrenceOf(template.copy(toAccountId = "stray"), d("2026-10-01"))
        assertThat(body.type).isEqualTo(TxType.Income)
        assertThat(body.amount).isEqualTo(Money.rupees(50_000))
        assertThat(body.toAccountId).isNull()
        assertThat(body.categoryId).isEqualTo("salary")
        assertThat(body.date).isEqualTo(d("2026-10-01"))
        assertThat(body.note).isEqualTo("Salary")
    }

    @Test
    fun `TXN-09 make recurring starts one period after the entry, on the same weekday or day`() {
        val tx = Transaction(
            id = "t1", type = TxType.Expense, amount = Money.rupees(15_000), currency = "NPR",
            accountId = "bank", toAccountId = null, categoryId = "rent", date = d("2026-01-31"),
            note = "Rent", recurringRuleId = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        assertThat(RecurringRules.templateOf(tx)).isEqualTo(RecurringTemplate(TxType.Expense, Money.rupees(15_000), "bank", null, "rent", "Rent"))

        val monthly = RecurringRules.scheduleAfter(tx.date, Frequency.Monthly)
        assertThat(monthly.startDate).isEqualTo(d("2026-02-28"))
        assertThat(monthly.dayOfMonth).isEqualTo(31)
        assertThat(RecurringRules.nextAfter(monthly, monthly.startDate)).isEqualTo(d("2026-03-31"))

        // 1 Oct 2026 is a Thursday.
        val weekly = RecurringRules.scheduleAfter(d("2026-10-01"), Frequency.Weekly)
        assertThat(weekly.startDate).isEqualTo(d("2026-10-08"))
        assertThat(weekly.weekdays).containsExactly(4)
        assertThat(RecurringRules.scheduleAfter(d("2026-10-01"), Frequency.Daily).startDate).isEqualTo(d("2026-10-02"))
        assertThat(RecurringRules.scheduleAfter(d("2024-02-29"), Frequency.Yearly).startDate).isEqualTo(d("2025-02-28"))
    }
}
