package com.example.expensify.core.date

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** "Today", "Yesterday" or "Tomorrow", when a date is one of those. */
enum class Near { Today, Yesterday, Tomorrow }

/** Dates read in the device's language; amounts never do (req NFR-11). */
object DateFormats {

    /** "Sat, 26 Sep 2026". */
    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale).format(date)

    /** "26 Sep 2026". */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)

    /** "26 Sep 2026, 14:05", as audit fields read. */
    fun dateTime(instant: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).format(instant.atZone(zone))

    /**
     * How a period reads: "September 2026" for a calendar month, "2026" for a
     * calendar year, otherwise the range ("25 Sep – 24 Oct 2026").
     */
    fun period(range: DateRange, locale: Locale = Locale.getDefault()): String = when {
        Periods.isCalendarYear(range) -> range.start.year.toString()
        Periods.isCalendarMonth(range) -> DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(range.start)
        range.start.year == range.end.year ->
            DateTimeFormatter.ofPattern("d MMM", locale).format(range.start) + " – " + shortDate(range.end, locale)
        else -> shortDate(range.start, locale) + " – " + shortDate(range.end, locale)
    }

    /** "8:00 PM", or "20:00" where the locale uses a 24-hour clock, for a `HH:mm` time; the text itself when it isn't one. */
    fun timeOfDay(time: String, locale: Locale = Locale.getDefault()): String {
        val parsed = try {
            LocalTime.parse(time)
        } catch (_: DateTimeParseException) {
            return time
        }
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(parsed)
    }

    fun nearOf(date: LocalDate, today: LocalDate): Near? = when (date) {
        today -> Near.Today
        today.minusDays(1) -> Near.Yesterday
        today.plusDays(1) -> Near.Tomorrow
        else -> null
    }
}
