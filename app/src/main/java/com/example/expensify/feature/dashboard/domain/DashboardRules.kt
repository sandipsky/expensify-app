package com.example.expensify.feature.dashboard.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.feature.budgets.domain.BudgetProgress
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate
import kotlin.math.abs

/** The periods the dashboard's switcher offers (req DSH-01), `ThisMonth` first as the default. */
enum class DashboardPreset(val stored: String) {
    ThisMonth("this_month"),
    LastMonth("last_month"),
    Custom("custom"),
    ;

    companion object {
        fun fromStored(value: String?): DashboardPreset? = entries.firstOrNull { it.stored == value }
    }
}

/** How a figure moved from the previous period: the difference and, when there was something before, the whole percent. */
data class Change(val amount: Money, val percent: Int?)

/** One category's part of a period's income or expense. */
data class CategoryTotal(
    /** Null for entries without a category. */
    val categoryId: String?,
    val amount: Money,
    val count: Int,
)

/** Income, expense, net and savings rate for one period. */
data class PeriodSummary(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val net: Money,
    /** Whole percent; null ("—") when there's no income. */
    val savingsRate: Int?,
)

/** Each summary figure's change from the previous period (req DSH-03). */
data class SummaryChange(
    val income: Change,
    val expense: Change,
    val net: Change,
    /** In percentage points; null when either period has no income. */
    val savingsRate: Int?,
)

/** One recurring occurrence the dashboard lists as upcoming (req DSH-10). */
data class UpcomingDate(
    val date: LocalDate,
    /** The rule's next date, and due by today: an ask-first rule can be confirmed or skipped. */
    val due: Boolean,
)

/**
 * Port of ../expensify/src/features/dashboard/dashboard.domain.ts: the
 * figures the dashboard and the reports share. Every total leaves transfers
 * out (req BR-04). Percentages are whole numbers rounded half up (req BR-14),
 * worked out in whole paisa so float noise never moves one.
 */
object DashboardRules {

    /** The trend shows the selected period and the five before it (req DSH-08). */
    const val TREND_PERIODS = 6

    /** Slices before the rest folds into "Other" (req DSH-06). */
    const val TOP_CATEGORIES = 5

    /** Budgets on the card before "See all" (req DSH-07). */
    const val TOP_BUDGETS = 5

    /** Entries on the recent card (req DSH-09). */
    const val RECENT_COUNT = 5

    /** How far ahead the upcoming card looks (req DSH-10). */
    const val UPCOMING_DAYS = 7L

    /** `numerator × 100 ÷ denominator`, rounded half up (toward +∞, as `Math.round` does). `denominator` must be above 0. */
    fun percentOf(numerator: Long, denominator: Long): Int =
        Math.floorDiv(numerator * 200 + denominator, denominator * 2).toInt()

    /** Whole percent of `total`, or null when there's no total to share. */
    fun shareOf(amount: Money, total: Money): Int? =
        if (total.isPositive) percentOf(amount.paisa, total.paisa) else null

    /** Savings rate: net ÷ income × 100, whole percent; null ("—") when there's no income. */
    fun savingsRate(income: Money, net: Money): Int? =
        if (income.isPositive) percentOf(net.paisa, income.paisa) else null

    /** Change from `previous` to `current` as whole percent; null when there was nothing before. */
    fun changePercent(previous: Money, current: Money): Int? =
        if (previous.isZero) null else percentOf((current - previous).paisa, abs(previous.paisa))

    fun changeOf(previous: Money, current: Money): Change =
        Change(current - previous, changePercent(previous, current))

    /** The transaction type a category type's entries have. */
    fun txTypeOf(type: CategoryType): TxType = when (type) {
        CategoryType.Expense -> TxType.Expense
        CategoryType.Income -> TxType.Income
    }

    /**
     * The type's totals per category, largest first. Entries without a
     * category count under null so the total still adds up; ties sort by id
     * so the list never reorders between renders.
     */
    fun categoryTotals(txs: List<Transaction>, type: CategoryType): List<CategoryTotal> {
        val txType = txTypeOf(type)
        val totals = LinkedHashMap<String?, CategoryTotal>()
        for (tx in txs) {
            if (tx.type != txType) continue
            val total = totals[tx.categoryId] ?: CategoryTotal(tx.categoryId, Money.ZERO, 0)
            totals[tx.categoryId] = total.copy(amount = total.amount + tx.amount, count = total.count + 1)
        }
        return totals.values.sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.categoryId ?: "" })
    }

    /** What the rows past the first `keep` add up to ("Other"). */
    data class Other(val amount: Money, val count: Int, val categoryIds: List<String>)

    data class TopAndOther(val top: List<CategoryTotal>, val other: Other?)

    /** The first `keep` rows, and what the rest add up to, or null when nothing's left over. */
    fun topAndOther(rows: List<CategoryTotal>, keep: Int): TopAndOther {
        val top = rows.take(keep)
        val rest = rows.drop(keep)
        if (rest.isEmpty()) return TopAndOther(top, null)
        var amount = Money.ZERO
        var count = 0
        for (row in rest) {
            amount += row.amount
            count += row.count
        }
        return TopAndOther(top, Other(amount, count, rest.mapNotNull { it.categoryId }))
    }

    /** One summary of everything in `txs`, labelled with `range`. */
    fun summaryOf(range: DateRange, txs: List<Transaction>): PeriodSummary {
        val totals = TransactionRules.totalsOf(txs)
        return PeriodSummary(range, totals.income, totals.expense, totals.net, savingsRate(totals.income, totals.net))
    }

    fun summaryChange(previous: PeriodSummary, current: PeriodSummary): SummaryChange = SummaryChange(
        income = changeOf(previous.income, current.income),
        expense = changeOf(previous.expense, current.expense),
        net = changeOf(previous.net, current.net),
        savingsRate = if (previous.savingsRate == null || current.savingsRate == null) null else current.savingsRate - previous.savingsRate,
    )

    /** Whether a previous period gives anything to compare with: it had an income or expense entry (req DSH-03). */
    fun hasComparison(previousTxs: List<Transaction>): Boolean = previousTxs.any { it.type != TxType.Transfer }

    /** Each range's summary, in the order given. `txs` must cover them all and may cover more. */
    fun periodSummaries(txs: List<Transaction>, ranges: List<DateRange>): List<PeriodSummary> =
        ranges.map { range -> summaryOf(range, txs.filter { it.date in range }) }

    /** The range a preset stands for on `today`; `Custom` returns the given range, in order (req DSH-01). */
    fun dashboardRange(preset: DashboardPreset, today: LocalDate, custom: DateRange): DateRange = when (preset) {
        DashboardPreset.ThisMonth -> Periods.month(today)
        DashboardPreset.LastMonth -> Periods.month(today, -1)
        DashboardPreset.Custom -> Periods.ordered(custom.start, custom.end)
    }

    /** The preset a range falls under on `today`, or `Custom` for any other range. */
    fun dashboardPresetOf(range: DateRange, today: LocalDate): DashboardPreset = when (range) {
        Periods.month(today) -> DashboardPreset.ThisMonth
        Periods.month(today, -1) -> DashboardPreset.LastMonth
        else -> DashboardPreset.Custom
    }

    /** The `count` ranges of the same length before `range`, oldest first, ending the day before it starts. */
    fun previousRanges(range: DateRange, count: Int): List<DateRange> {
        val days = range.days
        return (0 until count).map { i ->
            val back = (count - i) * days
            DateRange(range.start.minusDays(back), range.start.minusDays(back).plusDays(days - 1))
        }
    }

    /**
     * The selected period and the five before it, oldest first: calendar
     * months for the presets, ranges of the same length for a custom range (req DSH-08).
     */
    fun trendPeriods(preset: DashboardPreset, range: DateRange, today: LocalDate): List<DateRange> {
        if (preset == DashboardPreset.Custom) return previousRanges(range, TREND_PERIODS - 1) + range
        val last = if (preset == DashboardPreset.ThisMonth) 0L else -1L
        return (0 until TREND_PERIODS).map { i -> Periods.month(today, last - (TREND_PERIODS - 1) + i) }
    }

    /** One range over the earlier trend periods: they end the day before the selected one starts. */
    fun earlierRange(periods: List<DateRange>, range: DateRange): DateRange =
        DateRange(periods.first().start, range.start.minusDays(1))

    /** Percent used for sorting budgets most used first: a zero limit is over everything, and figures still loading sort last. */
    fun usedOf(progress: BudgetProgress?): Double {
        if (progress == null) return -1.0
        return progress.result.percent?.toDouble() ?: Double.POSITIVE_INFINITY
    }

    /**
     * The rule's occurrences due by today or within the next week (req
     * DSH-10), soonest first. Only a running rule has any; the one at its next
     * date and due by today can be confirmed or skipped when the rule asks first.
     */
    fun upcoming(rule: RecurringRule, status: RuleStatus, today: LocalDate): List<UpcomingDate> {
        if (status != RuleStatus.Active) return emptyList()
        return RecurringRules.upcomingDates(rule, today, UPCOMING_DAYS).map { date ->
            UpcomingDate(date, due = date <= today && date == rule.nextDueDate)
        }
    }
}

