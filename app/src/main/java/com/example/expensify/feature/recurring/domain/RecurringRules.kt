package com.example.expensify.feature.recurring.domain

import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionBody
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** Where a rule stands: running, paused, run its course, or unable to run because an account it needs was deleted (req REC-11). */
enum class RuleStatus { Active, Paused, Ended, MissingAccount }

/**
 * Port of ../expensify/src/features/recurring/recurring.domain.ts: pure
 * functions on local dates. The rule form's preview, the Recurring pages and
 * the runner that adds due entries all read their dates from here.
 */
object RecurringRules {

    /** Largest "every N" the form accepts. */
    const val MAX_INTERVAL = 99

    /** Most entries a `count` rule can end after. */
    const val MAX_OCCURRENCES = 999

    /** Most entries one catch-up write adds; the runner goes again for the rest. */
    const val CATCH_UP_LIMIT = 100

    /** The day-of-month value that stands for "the last day". */
    const val LAST_DAY = 31

    private fun intervalOf(schedule: Schedule): Int = max(1, schedule.interval)

    /** The rule's weekdays in order; a weekly rule without any repeats on its start date's weekday. */
    fun weekdaysOf(schedule: Schedule): List<Int> {
        val days = schedule.weekdays.filter { it in 1..7 }.distinct().sorted()
        return days.ifEmpty { listOf(schedule.startDate.dayOfWeek.value) }
    }

    /** The rule's day of the month, 1–31; a monthly rule without one repeats on its start date's day. */
    fun dayOfMonthOf(schedule: Schedule): Int =
        schedule.dayOfMonth?.takeIf { it in 1..31 } ?: schedule.startDate.dayOfMonth

    /** Day `day` of the month, or its last day when the month is shorter. */
    fun dayInMonth(month: YearMonth, day: Int): LocalDate = month.atDay(min(day, month.lengthOfMonth()))

    /** Smallest whole number of `n`-steps covering `count` steps; `count` is never negative here. */
    private fun ceilDiv(count: Long, n: Int): Long = if (count <= 0) 0 else (count + n - 1) / n

    /**
     * The first schedule date on or after `date` (and never before the start
     * date), ignoring how the rule ends (req BR-12):
     * - daily: every `interval` days from the start date;
     * - weekly: the chosen weekdays, every `interval` weeks counted in
     *   Monday-to-Sunday weeks from the week that contains the start date;
     * - monthly: day `dayOfMonth` (29–31 fall on the last day of shorter
     *   months), every `interval` months counted from the start date's month;
     * - yearly: the start date's day and month every `interval` years (29
     *   February falls on the 28th in other years).
     */
    fun firstOnOrAfter(schedule: Schedule, date: LocalDate): LocalDate {
        val start = schedule.startDate
        val from = if (date > start) date else start
        val n = intervalOf(schedule)
        return when (schedule.frequency) {
            Frequency.Daily -> {
                val steps = ceilDiv(ChronoUnit.DAYS.between(start, from), n)
                start.plusDays(steps * n)
            }
            Frequency.Weekly -> firstWeekly(start, from, n, weekdaysOf(schedule))
            Frequency.Yearly -> {
                val at = { k: Long -> dayInMonth(YearMonth.of(start.year + (k * n).toInt(), start.month), start.dayOfMonth) }
                val k = ceilDiv((from.year - start.year).toLong(), n)
                if (at(k) < from) at(k + 1) else at(k)
            }
            Frequency.Monthly -> {
                val day = dayOfMonthOf(schedule)
                val startMonth = YearMonth.from(start)
                val at = { k: Long -> dayInMonth(startMonth.plusMonths(k * n), day) }
                val k = ceilDiv(ChronoUnit.MONTHS.between(startMonth, YearMonth.from(from)), n)
                if (at(k) < from) at(k + 1) else at(k)
            }
        }
    }

    private fun firstWeekly(start: LocalDate, from: LocalDate, n: Int, days: List<Int>): LocalDate {
        // Weeks are numbered from the Monday of the week the rule starts in.
        val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        var week = Math.floorDiv(ChronoUnit.DAYS.between(monday, from), 7L)
        var weekday = from.dayOfWeek.value
        while (true) {
            val aligned = ceilDiv(week, n) * n
            if (aligned != week) {
                week = aligned
                weekday = 1
            }
            val day = days.firstOrNull { it >= weekday }
            if (day != null) return monday.plusDays(week * 7 + day - 1)
            week += 1
            weekday = 1
        }
    }

    /** The first schedule date after `date`. */
    fun nextAfter(schedule: Schedule, date: LocalDate): LocalDate = firstOnOrAfter(schedule, date.plusDays(1))

    /** A new rule's first entry: the first schedule date on or after its start date. */
    fun firstDueDate(schedule: Schedule): LocalDate = firstOnOrAfter(schedule, schedule.startDate)

    /**
     * The next due date after the schedule was edited (req REC-08). Edits
     * reach future entries only, so it counts from today, or from an earlier
     * date still waiting to be confirmed so that one isn't lost.
     */
    fun rescheduledDueDate(schedule: Schedule, nextDueDate: LocalDate, today: LocalDate): LocalDate =
        firstOnOrAfter(schedule, if (nextDueDate < today) nextDueDate else today)

    /**
     * The next due date when a paused rule resumes (req REC-09): dates that
     * passed while it was paused are left out, and nothing already handled comes back.
     */
    fun resumedDueDate(schedule: Schedule, nextDueDate: LocalDate, today: LocalDate): LocalDate =
        firstOnOrAfter(schedule, if (nextDueDate > today) nextDueDate else today)

    /** Whether the two schedules give the same dates, so an edit leaves the next due date alone. */
    fun sameSchedule(a: Schedule, b: Schedule): Boolean {
        fun weekdays(s: Schedule) = if (s.frequency == Frequency.Weekly) weekdaysOf(s) else emptyList()
        fun day(s: Schedule) = if (s.frequency == Frequency.Monthly) dayOfMonthOf(s) else null
        return a.frequency == b.frequency &&
            intervalOf(a) == intervalOf(b) &&
            a.startDate == b.startDate &&
            weekdays(a) == weekdays(b) &&
            day(a) == day(b)
    }

    /** Whether the rule has run its course: its count is reached or its next date is past the end date (req BR-13). */
    fun isEnded(rule: RuleState): Boolean = when (rule.endType) {
        EndType.Count -> rule.maxCount != null && rule.occurrences >= rule.maxCount!!
        EndType.Until -> rule.endDate != null && rule.nextDueDate > rule.endDate!!
        EndType.Never -> false
    }

    /**
     * The rule's next `limit` entry dates from `nextDueDate`, stopping where it
     * ends and, when given, after `through`. A `count` rule assumes each of them is added.
     */
    fun pendingDates(rule: RuleState, limit: Int, through: LocalDate? = null): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var date = rule.nextDueDate
        var count = rule.occurrences
        val endDate = rule.endDate
        val maxCount = rule.maxCount
        while (dates.size < limit) {
            if (through != null && date > through) break
            if (rule.endType == EndType.Until && endDate != null && date > endDate) break
            if (rule.endType == EndType.Count && maxCount != null && count >= maxCount) break
            dates += date
            count += 1
            date = nextAfter(rule, date)
        }
        return dates
    }

    /**
     * The entries due by `today`, oldest first, including any missed while the
     * app was closed (req REC-06). None while the rule is paused.
     */
    fun dueDates(rule: RecurringRule, today: LocalDate, limit: Int = CATCH_UP_LIMIT): List<LocalDate> =
        if (rule.active) pendingDates(rule, limit, today) else emptyList()

    /**
     * The rule's entries due by today or in the next `days` days, soonest
     * first: what the dashboard lists as upcoming (req DSH-10). None while paused.
     */
    fun upcomingDates(rule: RecurringRule, today: LocalDate, days: Long, limit: Int = CATCH_UP_LIMIT): List<LocalDate> =
        if (rule.active) pendingDates(rule, limit, today.plusDays(days)) else emptyList()

    /** The entry a rule adds on `date`, as the transaction form takes it. */
    fun occurrenceOf(template: RecurringTemplate, date: LocalDate): TransactionBody {
        val transfer = template.type == TxType.Transfer
        return TransactionBody(
            type = template.type,
            amount = template.amount,
            accountId = template.accountId,
            toAccountId = if (transfer) template.toAccountId else null,
            categoryId = if (transfer) null else template.categoryId,
            date = date,
            note = template.note,
        )
    }

    /** A rule template from a transaction, for "Make recurring" (req TXN-09). */
    fun templateOf(tx: Transaction): RecurringTemplate {
        val transfer = tx.type == TxType.Transfer
        return RecurringTemplate(
            type = tx.type,
            amount = tx.amount,
            accountId = tx.accountId,
            toAccountId = if (transfer) tx.toAccountId else null,
            categoryId = if (transfer) null else tx.categoryId,
            note = tx.note,
        )
    }

    /**
     * A schedule that repeats a transaction dated `date`: same weekday or day of
     * the month, starting one period later, so the transaction itself isn't added again.
     */
    fun scheduleAfter(date: LocalDate, frequency: Frequency): ScheduleSpec {
        val next = when (frequency) {
            Frequency.Daily -> date.plusDays(1)
            Frequency.Weekly -> date.plusDays(7)
            Frequency.Monthly -> dayInMonth(YearMonth.from(date).plusMonths(1), date.dayOfMonth)
            Frequency.Yearly -> dayInMonth(YearMonth.of(date.year + 1, date.month), date.dayOfMonth)
        }
        return ScheduleSpec(
            frequency = frequency,
            interval = 1,
            weekdays = if (frequency == Frequency.Weekly) listOf(date.dayOfWeek.value) else emptyList(),
            dayOfMonth = if (frequency == Frequency.Monthly) date.dayOfMonth else null,
            startDate = next,
        )
    }

    /** The accounts a rule's template touches. */
    fun accountIdsOf(template: RecurringTemplate): List<String> =
        if (template.type == TxType.Transfer && template.toAccountId != null) {
            listOf(template.accountId, template.toAccountId)
        } else {
            listOf(template.accountId)
        }

    /** Paused first, then ended, then blocked by a deleted account, otherwise active (req REC-11). */
    fun statusOf(rule: RecurringRule, accountExists: (String) -> Boolean): RuleStatus = when {
        !rule.active -> RuleStatus.Paused
        isEnded(rule) -> RuleStatus.Ended
        accountIdsOf(rule.template).any { !accountExists(it) } -> RuleStatus.MissingAccount
        else -> RuleStatus.Active
    }

    /** Ask-first entries due by today and not yet handled, for the badge and "Waiting for you" (req REC-07). */
    fun waitingDates(rule: RecurringRule, status: RuleStatus, today: LocalDate): List<LocalDate> =
        if (rule.mode == RuleMode.Confirm && status == RuleStatus.Active) dueDates(rule, today) else emptyList()

    /** How many ask-first entries wait for Confirm or Skip across every rule. */
    fun waitingCount(rules: List<RecurringRule>, accountExists: (String) -> Boolean, today: LocalDate): Int =
        rules.sumOf { waitingDates(it, statusOf(it, accountExists), today).size }
}
