package com.example.expensify.core.date

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** Req BR-06: Monday-to-Sunday weeks, calendar months and years, and what "previous" means. */
class PeriodsTest {
    private val wednesday = LocalDate.of(2026, 9, 30)

    @Test
    fun `weeks run Monday to Sunday`() {
        assertThat(Periods.week(wednesday)).isEqualTo(DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
        assertThat(Periods.week(LocalDate.of(2026, 9, 28))).isEqualTo(DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
        assertThat(Periods.week(LocalDate.of(2026, 10, 4)).start).isEqualTo(LocalDate.of(2026, 9, 28))
        assertThat(Periods.week(wednesday, -1).start).isEqualTo(LocalDate.of(2026, 9, 21))
    }

    @Test
    fun `months and years follow the calendar`() {
        assertThat(Periods.month(wednesday)).isEqualTo(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        assertThat(Periods.month(wednesday, -1).end).isEqualTo(LocalDate.of(2026, 8, 31))
        assertThat(Periods.month(LocalDate.of(2026, 1, 15), -1)).isEqualTo(DateRange(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)))
        assertThat(Periods.year(wednesday)).isEqualTo(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        assertThat(Periods.lastMonths(3, wednesday)).isEqualTo(DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `previous of a month is the month before, of a year the year before`() {
        assertThat(Periods.previous(Periods.month(wednesday))).isEqualTo(Periods.month(wednesday, -1))
        assertThat(Periods.previous(Periods.year(wednesday))).isEqualTo(Periods.year(wednesday, -1))
        assertThat(Periods.previous(Periods.lastMonths(3, wednesday))).isEqualTo(DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun `previous of a custom range is the same number of days before it`() {
        val range = DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19))
        assertThat(range.days).isEqualTo(10)
        assertThat(Periods.previous(range)).isEqualTo(DateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9)))
    }

    @Test
    fun `period labels`() {
        assertThat(Periods.isCalendarMonth(Periods.month(wednesday))).isTrue()
        assertThat(Periods.isCalendarYear(Periods.year(wednesday))).isTrue()
        assertThat(Periods.isCalendarMonth(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29)))).isFalse()
        assertThat(DateFormats.period(Periods.month(wednesday), java.util.Locale.ENGLISH)).isEqualTo("September 2026")
        assertThat(DateFormats.period(Periods.year(wednesday), java.util.Locale.ENGLISH)).isEqualTo("2026")
    }

    @Test
    fun `ymd round trips`() {
        assertThat(wednesday.ymd()).isEqualTo("2026-09-30")
        assertThat("2026-09-30".toLocalDateOrNull()).isEqualTo(wednesday)
        assertThat("2026-02-30".toLocalDateOrNull()).isNull()
    }
}
