package com.example.expensify.feature.budgets.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate
import java.time.ZoneId

/** Budget state from % used (req BR-08). */
enum class BudgetState { OnTrack, Warning, Over }

/** A budget's figures for one period. */
data class BudgetResult(
    val range: DateRange,
    val spent: Money,
    /** `amount` plus what rolled in, never below 0. */
    val limit: Money,
    /** What rolled in from the previous period: negative after overspending, zero without rollover. */
    val carry: Money,
    /** Limit − spent; negative when over. */
    val remaining: Money,
    /** Whole percent used, rounded down; null for a zero limit. */
    val percent: Int?,
    val state: BudgetState,
)

/** The current period's figures, with what's safe to spend each day. */
data class BudgetProgress(
    val result: BudgetResult,
    val daysLeft: Int,
    val safePerDay: Money,
)

/** A past period's figures. */
data class BudgetHistoryEntry(
    val result: BudgetResult,
    /** The period ended before the budget was created: shown for comparison only. */
    val beforeBudget: Boolean,
)

/**
 * Port of ../expensify/src/features/budgets/budget.domain.ts: pure rules on
 * exact paisa and local dates. The budgets screens and the alerts get their
 * figures from here, and later the dashboard.
 */
object BudgetRules {

    /** Longest budget name (req BR-15). */
    const val MAX_NAME_LENGTH = 40

    /** Percent used from which a budget is in the warning state. */
    const val WARNING_PERCENT = 80

    /** Percent used from which a budget is over. */
    const val OVER_PERCENT = 100

    /** `alertThresholds` for a budget with alerts on. */
    val DEFAULT_ALERT_THRESHOLDS: List<Int> = listOf(WARNING_PERCENT, OVER_PERCENT)

    /** The name a budget over every expense gets until the user types one; stored as is, so it matches the web. */
    const val ALL_EXPENSES_NAME = "All expenses"

    /** How many past periods a budget's page shows: two months of weeks, half a year of months (req BUD-05). */
    fun historyPeriods(period: BudgetPeriod): Int = when (period) {
        BudgetPeriod.Weekly -> 8
        BudgetPeriod.Monthly -> 6
        BudgetPeriod.Yearly -> 2
    }

    /** The budget's period that contains `today`, or the one `offset` periods away. */
    fun periodRange(period: BudgetPeriod, today: LocalDate, offset: Long = 0): DateRange = when (period) {
        BudgetPeriod.Weekly -> Periods.week(today, offset)
        BudgetPeriod.Monthly -> Periods.month(today, offset)
        BudgetPeriod.Yearly -> Periods.year(today, offset)
    }

    /**
     * Whether a transaction counts toward the budget: expenses only, never
     * income or transfers (req BR-07). A budget with categories counts those;
     * one without counts every expense.
     */
    fun countsToward(categoryIds: List<String>, tx: Transaction): Boolean {
        if (tx.type != TxType.Expense) return false
        if (categoryIds.isEmpty()) return true
        return tx.categoryId != null && tx.categoryId in categoryIds
    }

    /** The budget's spending in the range. `txs` may cover more than the range. */
    fun spent(categoryIds: List<String>, txs: List<Transaction>, range: DateRange): Money {
        var total = Money.ZERO
        for (tx in txs) if (tx.date in range && countsToward(categoryIds, tx)) total += tx.amount
        return total
    }

    /**
     * Whether `spent` has reached `percent` of `limit`, compared in whole paisa
     * so rounding can never move a budget across a threshold (req BR-08). A
     * zero limit has reached every threshold: there's nothing left to spend.
     */
    fun hasReached(spent: Money, limit: Money, percent: Int): Boolean =
        spent.paisa * 100 >= percent.toLong() * limit.paisa

    /** On track under 80 % used, warning from 80 %, over from 100 %. */
    fun stateOf(spent: Money, limit: Money): BudgetState = when {
        hasReached(spent, limit, OVER_PERCENT) -> BudgetState.Over
        hasReached(spent, limit, WARNING_PERCENT) -> BudgetState.Warning
        else -> BudgetState.OnTrack
    }

    /**
     * Whole percent used, for display. Rounded down, so the figure never
     * contradicts the state: 79.6 % reads 79 %, on track. Null for a zero
     * limit, shown as "—".
     */
    fun percentUsed(spent: Money, limit: Money): Int? =
        if (limit.isPositive) Math.floorDiv(spent.paisa * 100, limit.paisa).toInt() else null

    /** Days left in the range from `today`, both included; all of it before it starts, 0 once it's over (req BR-09). */
    fun daysLeft(range: DateRange, today: LocalDate): Int = when {
        today > range.end -> 0
        today < range.start -> range.days.toInt()
        else -> DateRange(today, range.end).days.toInt()
    }

    /** Remaining ÷ days left, rounded down to the paisa, so spending it every day stays within the limit; zero when nothing is left. */
    fun safePerDay(remaining: Money, days: Int): Money =
        if (!remaining.isPositive || days <= 0) Money.ZERO else Money(remaining.paisa / days)

    /** The local date the budget was created. */
    fun createdDate(budget: Budget, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        budget.createdAt.atZone(zone).toLocalDate()

    /**
     * What rolls into a period from the one before it (req BR-10): the plain
     * `amount` minus what that period spent, so unspent money adds to the
     * limit and overspending takes from it. It reaches back one period only,
     * and nothing rolls in from a period that ended before the budget was created.
     */
    fun carryOver(budget: Budget, previousRange: DateRange, previousSpent: Money, zone: ZoneId = ZoneId.systemDefault()): Money {
        if (!budget.rollover || createdDate(budget, zone) > previousRange.end) return Money.ZERO
        return budget.amount - previousSpent
    }

    /**
     * The budget's figures for the period `offset` periods from the current
     * one. `txs` must cover that period and, with rollover, the one before it
     * (`neededRange`).
     */
    fun result(
        budget: Budget,
        txs: List<Transaction>,
        today: LocalDate,
        offset: Long = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetResult {
        val range = periodRange(budget.period, today, offset)
        val spent = spent(budget.categoryIds, txs, range)
        var carry = Money.ZERO
        if (budget.rollover) {
            val previous = periodRange(budget.period, today, offset - 1)
            carry = carryOver(budget, previous, spent(budget.categoryIds, txs, previous), zone)
        }
        val limit = maxOf(Money.ZERO, budget.amount + carry)
        return BudgetResult(
            range = range,
            spent = spent,
            limit = limit,
            carry = carry,
            remaining = limit - spent,
            percent = percentUsed(spent, limit),
            state = stateOf(spent, limit),
        )
    }

    /** The current period's figures and what's safe to spend per day (req BUD-02). */
    fun progress(budget: Budget, txs: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): BudgetProgress {
        val result = result(budget, txs, today, 0, zone)
        val days = daysLeft(result.range, today)
        return BudgetProgress(result, days, safePerDay(result.remaining, days))
    }

    /**
     * The `count` periods before the current one, newest first (req BUD-05).
     * `txs` must cover `neededRange(budget, today, count)`. Periods that ended
     * before the budget was created are marked, since it didn't apply then.
     */
    fun history(
        budget: Budget,
        txs: List<Transaction>,
        today: LocalDate,
        count: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BudgetHistoryEntry> {
        val created = createdDate(budget, zone)
        return (1..count).map { back ->
            val result = result(budget, txs, today, -back.toLong(), zone)
            BudgetHistoryEntry(result, beforeBudget = result.range.end < created)
        }
    }

    /**
     * The dates the budget's figures read: the current period back
     * `periodsBack` more periods, plus the one before those when it rolls over.
     */
    fun neededRange(budget: Budget, today: LocalDate, periodsBack: Int = 0): DateRange {
        val back = periodsBack + (if (budget.rollover) 1 else 0)
        return DateRange(
            start = periodRange(budget.period, today, -back.toLong()).start,
            end = periodRange(budget.period, today).end,
        )
    }

    /**
     * The highest alert threshold the period has newly reached, or null (req
     * NTF-01). `lastAlert` holds the highest one already shown for a period,
     * so each threshold alerts once per period and a jump past both shows
     * only the higher. Paused budgets never alert.
     */
    fun alertDue(budget: Budget, result: BudgetResult): Int? {
        if (!budget.active) return null
        val last = budget.lastAlert
        val sent = if (last != null && last.periodStart == result.range.start) last.threshold else 0
        var due: Int? = null
        for (threshold in budget.alertThresholds) {
            if (threshold > sent && hasReached(result.spent, result.limit, threshold)) {
                due = maxOf(due ?: threshold, threshold)
            }
        }
        return due
    }

    /** Budgets for every expense first, then by name (req BUD-04). */
    val listOrder: Comparator<Budget> =
        compareBy<Budget> { if (it.categoryIds.isEmpty()) 0 else 1 }.thenBy { it.name.lowercase() }

    /** The shortest range that covers all of them, or null for none. */
    fun spanOf(ranges: List<DateRange>): DateRange? {
        if (ranges.isEmpty()) return null
        return DateRange(ranges.minOf { it.start }, ranges.maxOf { it.end })
    }

    /**
     * A name for a new budget from its categories, until the user types one
     * (req BUD-01): "All expenses", "Food and dining", "Food and dining &
     * Transport", "Food and dining & 2 more".
     */
    fun suggestName(names: List<String>): String {
        val name = when (names.size) {
            0 -> ALL_EXPENSES_NAME
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            else -> "${names[0]} & ${names.size - 1} more"
        }
        return name.take(MAX_NAME_LENGTH)
    }
}
