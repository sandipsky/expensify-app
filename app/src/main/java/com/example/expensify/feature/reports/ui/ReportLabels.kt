package com.example.expensify.feature.reports.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.transactions.ui.TxRows
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One of the five reports as the Reports page lists it: name, what it shows, and its badge. */
data class ReportInfo(
    val key: ReportKey,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    /** A lumen icon name, drawn through `IconMap` as everywhere else. */
    val icon: String,
    val colorHex: String,
)

/** How a category reads in a report, with stand-ins for entries without one or a deleted one. */
data class CategoryView(val name: UiText, val icon: String, val colorHex: String?, val archived: Boolean)

/** Port of ../expensify/src/features/reports/report.labels.ts: how reports read on screen. */
object ReportLabels {

    /** The report pages, in the order the Reports page lists them (req RPT-01). */
    val REPORTS: List<ReportInfo> = listOf(
        ReportInfo(ReportKey.CashFlow, R.string.report_cash_flow_title, R.string.report_cash_flow_description, "bar_chart", "#15803D"),
        ReportInfo(ReportKey.Categories, R.string.report_categories_title, R.string.report_categories_description, "pie_chart", "#EA580C"),
        ReportInfo(ReportKey.CategoryTrends, R.string.report_trends_title, R.string.report_trends_description, "calendar", "#9333EA"),
        ReportInfo(ReportKey.Accounts, R.string.report_accounts_title, R.string.report_accounts_description, "bank", "#2456E6"),
        ReportInfo(ReportKey.Budgets, R.string.report_budgets_title, R.string.report_budgets_description, "donut_large", "#0D9488"),
    )

    fun info(key: ReportKey): ReportInfo = REPORTS.first { it.key == key }

    @StringRes
    fun presetRes(preset: ReportPreset): Int = when (preset) {
        ReportPreset.ThisMonth -> R.string.report_preset_this_month
        ReportPreset.LastMonth -> R.string.report_preset_last_month
        ReportPreset.Last3Months -> R.string.report_preset_last_3_months
        ReportPreset.Last6Months -> R.string.report_preset_last_6_months
        ReportPreset.Last12Months -> R.string.report_preset_last_12_months
        ReportPreset.ThisYear -> R.string.report_preset_this_year
        ReportPreset.LastYear -> R.string.report_preset_last_year
        ReportPreset.Custom -> R.string.report_preset_custom
    }

    @StringRes
    fun granularityRes(by: Granularity): Int = when (by) {
        Granularity.Week -> R.string.report_by_week
        Granularity.Month -> R.string.report_by_month
        Granularity.Year -> R.string.report_by_year
    }

    /** "Avg %1$s a week", for the cash flow tiles. */
    @StringRes
    fun averagePerRes(by: Granularity): Int = when (by) {
        Granularity.Week -> R.string.report_avg_per_week
        Granularity.Month -> R.string.report_avg_per_month
        Granularity.Year -> R.string.report_avg_per_year
    }

    /** "weeks" / "week", for "Saved money in 3 of 12 months". */
    fun unitOf(by: Granularity, count: Int): UiText = when (by) {
        Granularity.Week -> UiText.Plural(R.plurals.report_unit_weeks, count, listOf(count))
        Granularity.Month -> UiText.Plural(R.plurals.report_unit_months, count, listOf(count))
        Granularity.Year -> UiText.Plural(R.plurals.report_unit_years, count, listOf(count))
    }

    @StringRes
    fun categoryTypeRes(type: CategoryType): Int = when (type) {
        CategoryType.Expense -> R.string.category_type_expense
        CategoryType.Income -> R.string.category_type_income
    }

    /** "spending" or "income", as the category reports word their totals. */
    @StringRes
    fun nounRes(type: CategoryType): Int = when (type) {
        CategoryType.Expense -> R.string.report_noun_spending
        CategoryType.Income -> R.string.report_noun_income
    }

    /**
     * A bucket's short axis label: "Sep" (or "Sep 26" when the report spans
     * years) for a month, "28 Sep" for a week, "2026" for a year. A bucket cut
     * short by the range still reads as the period it belongs to.
     */
    fun bucketLabel(range: DateRange, by: Granularity, multiYear: Boolean, locale: Locale = Locale.getDefault()): String = when (by) {
        Granularity.Year -> range.start.year.toString()
        Granularity.Week -> DateTimeFormatter.ofPattern("d MMM", locale).format(range.start)
        Granularity.Month -> DateTimeFormatter.ofPattern(if (multiYear) "MMM yy" else "MMM", locale).format(range.start)
    }

    /** "34%", or "—" when there's nothing to take a share of. */
    fun percentText(percent: Int?): UiText =
        if (percent == null) res(R.string.budget_percent_none) else res(R.string.budget_percent, percent)

    /** "+12%", "-5%", "0%", or "New" when there was nothing before (req BR-14). */
    fun changeText(percent: Int?): UiText = when {
        percent == null -> res(R.string.report_change_new)
        percent > 0 -> plain("+$percent%")
        else -> plain("$percent%")
    }

    /** "1 period", "6 periods". */
    fun periodCount(count: Int): UiText = UiText.Plural(R.plurals.report_period_count, count, listOf(count))

    /** "1 transaction", "12 transactions". */
    fun entries(count: Int): UiText = UiText.Plural(R.plurals.transactions_count, count, listOf(count))

    fun categoryView(categoriesById: Map<String, Category>, categoryId: String?): CategoryView {
        val category = categoryId?.let { categoriesById[it] }
        if (category != null) return CategoryView(plain(category.name), category.icon, category.color, category.archived)
        return CategoryView(
            name = if (categoryId == null) res(R.string.report_uncategorized) else res(R.string.tx_deleted_category),
            icon = TxRows.MISSING_CATEGORY_ICON,
            colorHex = null,
            archived = false,
        )
    }

    /** The accounts a filter names: "Cash, Bank", or `none` when empty. */
    fun accountsText(accountsById: Map<String, Account>, ids: List<String>, none: UiText = res(R.string.report_all_accounts)): UiText {
        if (ids.isEmpty()) return none
        return plain(ids.joinToString(", ") { id -> accountsById[id]?.name ?: DELETED_ACCOUNT })
    }

    /** The categories a filter names: "Food, Transport", or "All categories" when empty. */
    fun categoriesText(categoriesById: Map<String, Category>, ids: List<String>): UiText {
        if (ids.isEmpty()) return res(R.string.report_all_categories)
        return UiText.Plain(ids.joinToString(", ") { id -> categoriesById[id]?.name ?: DELETED_CATEGORY })
    }

    private const val DELETED_ACCOUNT = "Deleted account"
    private const val DELETED_CATEGORY = "Deleted category"
}
