package com.example.expensify.feature.dashboard.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Near
import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.dashboard.domain.DashboardPreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Port of ../expensify/src/features/dashboard/dashboard.labels.ts: how the dashboard's figures read. */
object DashboardLabels {

    /** What every amount reads as in privacy mode (req DSH-11). */
    const val MASKED = "••••"

    @StringRes
    fun presetRes(preset: DashboardPreset): Int = when (preset) {
        DashboardPreset.ThisMonth -> R.string.period_this_month
        DashboardPreset.LastMonth -> R.string.period_last_month
        DashboardPreset.Custom -> R.string.period_custom
    }

    /** A full amount, or the mask. */
    fun money(money: Money, masked: Boolean, sign: Sign = Sign.Auto): String =
        if (masked) MASKED else MoneyFormat.full(money, sign)

    /** A short amount for chart axes and captions, or the mask. */
    fun compact(money: Money, masked: Boolean, sign: Sign = Sign.Auto): String =
        if (masked) MASKED else MoneyFormat.compact(money, sign)

    /** "34%", or "—" when there's nothing to share or no income. */
    fun percentText(percent: Int?): UiText =
        if (percent == null) res(R.string.budget_percent_none) else res(R.string.budget_percent, percent)

    /** "+12%", "-5%", "0%", or "New" when there was nothing before (req BR-14). */
    fun changeText(percent: Int?): UiText = when {
        percent == null -> res(R.string.report_change_new)
        percent > 0 -> plain("+$percent%")
        else -> plain("$percent%")
    }

    /** "+5 pts", "-3 pts", "0 pts": how a savings rate moved, in percentage points. */
    fun pointsText(points: Int): UiText = res(R.string.dashboard_points, if (points > 0) "+$points" else "$points")

    /** "1 category", "3 categories". */
    fun categoryCount(count: Int): UiText = UiText.Plural(R.plurals.report_categories_count, count, listOf(count))

    /** "1 transaction", "12 transactions". */
    fun entries(count: Int): UiText = UiText.Plural(R.plurals.transactions_count, count, listOf(count))

    /** "Sep" for a calendar month, "25 Aug" (its first day) for any other period. */
    fun shortPeriodLabel(range: DateRange, locale: Locale = Locale.getDefault()): String {
        val pattern = if (Periods.isCalendarMonth(range)) "MMM" else "d MMM"
        return DateTimeFormatter.ofPattern(pattern, locale).format(range.start)
    }

    /** "Today", "Tomorrow", or "Thu, 1 Oct 2026"; a date already passed reads "Due Sat, 26 Sep 2026". */
    fun dueLabel(date: LocalDate, today: LocalDate): UiText = when (DateFormats.nearOf(date, today)) {
        Near.Today -> res(R.string.day_today)
        Near.Tomorrow -> res(R.string.day_tomorrow)
        else -> if (date < today) res(R.string.dashboard_due_on, DateFormats.date(date)) else plain(DateFormats.date(date))
    }
}
