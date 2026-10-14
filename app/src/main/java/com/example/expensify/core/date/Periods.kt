package com.example.expensify.core.date

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Calendar dates as the user's local `YYYY-MM-DD`: what every entry is dated
 * with, so a date never shifts with the time zone (req BR-05). `LocalDate`
 * prints and parses exactly that.
 */
typealias Ymd = String

fun LocalDate.ymd(): Ymd = toString()

fun Ymd.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (_: DateTimeParseException) {
    null
}

/** An inclusive range of local calendar dates. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    /** How many days it covers, both ends included. */
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    operator fun contains(date: LocalDate): Boolean = date in start..end
}

/** Period rules from req BR-06: Monday-to-Sunday weeks, calendar months and years. */
object Periods {

    /** The Monday-to-Sunday week containing `today`, or the one `offset` weeks away. */
    fun week(today: LocalDate, offset: Long = 0): DateRange {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset)
        return DateRange(start, start.plusDays(6))
    }

    /** The calendar month containing `today`, or the one `offset` months away. */
    fun month(today: LocalDate, offset: Long = 0): DateRange {
        val month = YearMonth.from(today).plusMonths(offset)
        return DateRange(month.atDay(1), month.atEndOfMonth())
    }

    /** The calendar year containing `today`, or the one `offset` years away. */
    fun year(today: LocalDate, offset: Long = 0): DateRange {
        val year = today.year + offset.toInt()
        return DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }

    /** "Last N months" includes the current month. */
    fun lastMonths(count: Int, today: LocalDate): DateRange =
        DateRange(month(today, -(count - 1L)).start, month(today).end)

    /** The two dates as a range, earlier one first. */
    fun ordered(a: LocalDate, b: LocalDate): DateRange = if (a <= b) DateRange(a, b) else DateRange(b, a)

    fun isCalendarMonth(range: DateRange): Boolean =
        range.start.dayOfMonth == 1 && range.end == YearMonth.from(range.start).atEndOfMonth()

    fun isCalendarYear(range: DateRange): Boolean =
        range.start == LocalDate.of(range.start.year, 1, 1) && range.end == LocalDate.of(range.start.year, 12, 31)

    /** Whole calendar months from the 1st of one to the end of another. */
    fun isWholeMonths(range: DateRange): Boolean =
        range.start.dayOfMonth == 1 && range.end == YearMonth.from(range.end).atEndOfMonth()

    /**
     * The period before this one: the year, month or run of whole months
     * before it, otherwise the same number of days before it.
     */
    fun previous(range: DateRange): DateRange {
        if (isWholeMonths(range)) {
            val months = ChronoUnit.MONTHS.between(YearMonth.from(range.start), YearMonth.from(range.end)) + 1
            return DateRange(range.start.minusMonths(months), range.start.minusDays(1))
        }
        val days = range.days
        return DateRange(range.start.minusDays(days), range.start.minusDays(1))
    }
}

/** The periods the transaction list offers (req LST-01), `ThisMonth` first as the default. */
enum class PeriodPreset(val stored: String) {
    ThisMonth("this_month"),
    LastMonth("last_month"),
    ThisYear("this_year"),
    Custom("custom"),
    ;

    /** The range the preset stands for on `today`; `Custom` returns the given range, in order. */
    fun range(today: LocalDate, custom: DateRange): DateRange = when (this) {
        ThisMonth -> Periods.month(today)
        LastMonth -> Periods.month(today, -1)
        ThisYear -> Periods.year(today)
        Custom -> Periods.ordered(custom.start, custom.end)
    }

    companion object {
        /** The preset a range falls under on `today`, or `Custom` for any other range. */
        fun of(range: DateRange, today: LocalDate): PeriodPreset = when (range) {
            Periods.month(today) -> ThisMonth
            Periods.month(today, -1) -> LastMonth
            Periods.year(today) -> ThisYear
            else -> Custom
        }
    }
}
