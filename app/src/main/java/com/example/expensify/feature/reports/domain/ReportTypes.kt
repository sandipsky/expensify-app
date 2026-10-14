package com.example.expensify.feature.reports.domain

// Port of ../expensify/src/features/reports/report.types.ts: what a report's filters can be.

/** The periods a report's period picker offers (req RPT-03). */
enum class ReportPreset(val stored: String) {
    ThisMonth("this_month"),
    LastMonth("last_month"),
    Last3Months("last_3_months"),
    Last6Months("last_6_months"),
    Last12Months("last_12_months"),
    ThisYear("this_year"),
    LastYear("last_year"),
    Custom("custom"),
    ;

    companion object {
        fun fromStored(value: String?): ReportPreset? = entries.firstOrNull { it.stored == value }
    }
}

/** How a report over time splits its range: Monday-to-Sunday weeks, calendar months or years. */
enum class Granularity(val stored: String) {
    Week("week"),
    Month("month"),
    Year("year"),
    ;

    companion object {
        fun fromStored(value: String?): Granularity? = entries.firstOrNull { it.stored == value }
    }
}

/** The five reports, in the order the Reports page lists them (req RPT-01). */
enum class ReportKey(val stored: String) {
    CashFlow("cash-flow"),
    Categories("categories"),
    CategoryTrends("category-trends"),
    Accounts("accounts"),
    Budgets("budgets"),
    ;

    companion object {
        fun fromStored(value: String?): ReportKey? = entries.firstOrNull { it.stored == value }
    }
}

/** How many finished periods the budget report looks back over (req RPT-08). */
object BudgetLookbacks {
    val ALL: List<Int> = listOf(6, 12, 24)
    const val DEFAULT = 12

    /** The given look-back when it's one the report offers, else the default. */
    fun of(value: Int?): Int = if (value != null && value in ALL) value else DEFAULT
}
