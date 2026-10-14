package com.example.expensify.feature.reports.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetResult
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.Change
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The selected preset and range of a report, from its route. */
data class PeriodState(
    val preset: ReportPreset,
    /** What "Custom" shows: the route's range, or the default preset's until one is picked. */
    val custom: DateRange,
    val range: DateRange,
)

/** The range cut to the trends table's limit, and whether it was cut. */
data class TrendRange(val range: DateRange, val clipped: Boolean)

/** One period of the cash flow: income, expense, net and savings rate, plus how many entries it has (req RPT-04). */
data class FlowRow(
    val range: DateRange,
    val income: Money,
    val expense: Money,
    val net: Money,
    /** Whole percent; null when the period had no income. */
    val savingsRate: Int?,
    val count: Int,
    /** Net of this period and every one before it in the report. */
    val cumulative: Money,
)

/** One category's figures in a period and the one before it (req RPT-05). */
data class CategoryRow(
    /** Null for entries without a category. */
    val categoryId: String?,
    val amount: Money,
    val count: Int,
    /** Whole percent of the period's total; null when the total is zero. */
    val share: Int?,
    /** Amount per entry; zero without entries. */
    val average: Money,
    val previous: Money,
    val previousCount: Int,
    /** From the previous period to this one. */
    val change: Change,
)

/** One category across the months of the trends table (req RPT-06). */
data class MatrixRow(
    val categoryId: String?,
    /** One amount per month, oldest first. */
    val cells: List<Money>,
    val total: Money,
    /** Total ÷ months. */
    val average: Money,
)

data class CategoryMatrix(
    val rows: List<MatrixRow>,
    /** Every category's amount per month. */
    val columnTotals: List<Money>,
    val total: Money,
)

/** One account's movement over the period (req RPT-07). */
data class AccountStatement(
    val accountId: String,
    /** The balance at the end of the day before the period. */
    val opening: Money,
    val income: Money,
    val expense: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    /** The balance at the end of the period's last day. */
    val closing: Money,
    /** Closing − opening. */
    val change: Money,
)

/** What the accounts held at the end of a period (req BR-17). */
data class NetWorthPoint(
    val range: DateRange,
    /** Balances above zero, whatever the account type. */
    val assets: Money,
    /** Balances below zero (cards and loans owed, overdrafts), as a positive amount. */
    val liabilities: Money,
    /** Assets − liabilities: every balance added up. */
    val netWorth: Money,
    /** From the end of the period before. */
    val change: Money,
)

data class NetWorthSeries(
    /** Net worth at the end of the day before the first bucket. */
    val opening: Money,
    val points: List<NetWorthPoint>,
)

/** One period of a budget in the report (req RPT-08). */
data class BudgetPeriodRow(
    val result: BudgetResult,
    /** 0 for the current period, −1 for the one before, … */
    val offset: Int,
    /** The period ended before the budget was created. */
    val beforeBudget: Boolean,
    /** Still running, so it doesn't count toward the hit rate yet. */
    val current: Boolean,
)

/** How a budget did over its finished periods since it was created. */
data class BudgetStats(
    /** Finished periods since the budget was created. */
    val tracked: Int,
    /** Of those, the ones that stayed within the limit. */
    val under: Int,
    val over: Int,
    /** Whole percent of tracked periods under the limit; null with none tracked. */
    val hitRate: Int?,
    val averageSpent: Money?,
    /**
     * What they spent as a share of their limits added up, whole percent
     * rounded down; null when there's no limit to take a share of.
     */
    val usedPercent: Int?,
    /** What the over periods spent past their limits. */
    val overspent: Money,
)

/**
 * Port of ../expensify/src/features/reports/report.domain.ts: report figures
 * as pure functions on exact paisa and local dates. Income and expense
 * totals share the dashboard's rules (transfers never count, req RPT-09);
 * budget periods come from the budget rules; balances are worked back from
 * each account's current balance, which every entry write moves (BR-17).
 */
object ReportRules {

    /** The most months the category trends table spans; a longer range keeps its last 24 (req RPT-06). */
    const val MAX_TREND_MONTHS = 24

    // Periods

    /** The range a preset stands for on `today`; `Custom` returns the given range, in order. */
    fun reportRange(preset: ReportPreset, today: LocalDate, custom: DateRange): DateRange = when (preset) {
        ReportPreset.ThisMonth -> Periods.month(today)
        ReportPreset.LastMonth -> Periods.month(today, -1)
        ReportPreset.Last3Months -> Periods.lastMonths(3, today)
        ReportPreset.Last6Months -> Periods.lastMonths(6, today)
        ReportPreset.Last12Months -> Periods.lastMonths(12, today)
        ReportPreset.ThisYear -> Periods.year(today)
        ReportPreset.LastYear -> Periods.year(today, -1)
        ReportPreset.Custom -> Periods.ordered(custom.start, custom.end)
    }

    /** The preset and range a report shows from its route, falling back to the report's default. */
    fun periodState(
        preset: ReportPreset?,
        from: LocalDate?,
        to: LocalDate?,
        fallback: ReportPreset,
        today: LocalDate,
    ): PeriodState {
        val chosen = preset ?: fallback
        val custom = if (from != null && to != null) Periods.ordered(from, to) else reportRange(fallback, today, Periods.month(today))
        return PeriodState(chosen, custom, reportRange(chosen, today, custom))
    }

    /** How many whole calendar months the range covers, or null when it starts or ends mid-month. */
    fun wholeMonths(range: DateRange): Int? {
        if (!Periods.isWholeMonths(range)) return null
        return (ChronoUnit.MONTHS.between(YearMonth.from(range.start), YearMonth.from(range.end)) + 1).toInt()
    }

    /**
     * The period a range is compared with (req BR-06): the month, year or run
     * of whole months before it, or the same number of days before any other range.
     */
    fun previousRange(range: DateRange): DateRange = Periods.previous(range)

    /**
     * The range split into weeks (Monday to Sunday), calendar months or
     * calendar years, oldest first. The first and last pieces are cut to the
     * range, so a range starting mid-month starts with part of that month.
     */
    fun bucketsOf(range: DateRange, by: Granularity): List<DateRange> {
        val buckets = mutableListOf<DateRange>()
        var start = range.start
        while (start <= range.end) {
            val whole = when (by) {
                Granularity.Week -> Periods.week(start)
                Granularity.Month -> Periods.month(start)
                Granularity.Year -> Periods.year(start)
            }
            val end = minOf(whole.end, range.end)
            buckets += DateRange(start, end)
            start = end.plusDays(1)
        }
        return buckets
    }

    /** The range cut to its last `MAX_TREND_MONTHS` months, and whether it was cut. */
    fun trendRange(range: DateRange): TrendRange {
        val months = bucketsOf(range, Granularity.Month)
        if (months.size <= MAX_TREND_MONTHS) return TrendRange(range, clipped = false)
        return TrendRange(DateRange(months[months.size - MAX_TREND_MONTHS].start, range.end), clipped = true)
    }

    /** Whether the buckets cross a new year, so month labels need the year. */
    fun spansYears(buckets: List<DateRange>): Boolean =
        buckets.isNotEmpty() && buckets.first().start.year != buckets.last().end.year

    /** Index of the bucket a date falls in, or -1. Buckets are in order and don't overlap. */
    private fun bucketIndex(buckets: List<DateRange>, date: LocalDate): Int {
        var lo = 0
        var hi = buckets.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                date < buckets[mid].start -> hi = mid - 1
                date > buckets[mid].end -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    /** Entries on any of the accounts, on either side of a transfer; all of them with none given. */
    fun onAccounts(txs: List<Transaction>, accountIds: List<String>): List<Transaction> {
        if (accountIds.isEmpty()) return txs
        val ids = accountIds.toSet()
        return txs.filter { it.accountId in ids || (it.toAccountId != null && it.toAccountId in ids) }
    }

    /** `amount ÷ count`, rounded half up to the paisa; zero for no count. */
    fun averageOf(amount: Money, count: Int): Money =
        if (count <= 0) Money.ZERO else Money(Math.floorDiv(amount.paisa * 2 + count, 2L * count))

    // Cash flow

    /** Income, expense, net and savings rate per bucket, oldest first (req RPT-04). */
    fun cashFlow(txs: List<Transaction>, buckets: List<DateRange>): List<FlowRow> {
        val income = LongArray(buckets.size)
        val expense = LongArray(buckets.size)
        val count = IntArray(buckets.size)
        for (tx in txs) {
            if (tx.type == TxType.Transfer) continue
            val i = bucketIndex(buckets, tx.date)
            if (i < 0) continue
            if (tx.type == TxType.Income) income[i] += tx.amount.paisa else expense[i] += tx.amount.paisa
            count[i] += 1
        }
        var cumulative = 0L
        return buckets.mapIndexed { i, range ->
            val net = income[i] - expense[i]
            cumulative += net
            FlowRow(
                range = range,
                income = Money(income[i]),
                expense = Money(expense[i]),
                net = Money(net),
                savingsRate = DashboardRules.savingsRate(Money(income[i]), Money(net)),
                count = count[i],
                cumulative = Money(cumulative),
            )
        }
    }

    // Categories

    /**
     * Every category of the type with an amount in either period, largest
     * first; a category the previous period had and this one doesn't comes
     * last with 0 (req RPT-05).
     */
    fun categoryComparison(current: List<Transaction>, previous: List<Transaction>, type: CategoryType): List<CategoryRow> {
        val now = DashboardRules.categoryTotals(current, type)
        val before = DashboardRules.categoryTotals(previous, type).associateBy { it.categoryId }.toMutableMap()
        var total = Money.ZERO
        for (t in now) total += t.amount
        val rows = now.map { t ->
            val prev = before.remove(t.categoryId)
            CategoryRow(
                categoryId = t.categoryId,
                amount = t.amount,
                count = t.count,
                share = DashboardRules.shareOf(t.amount, total),
                average = averageOf(t.amount, t.count),
                previous = prev?.amount ?: Money.ZERO,
                previousCount = prev?.count ?: 0,
                change = DashboardRules.changeOf(prev?.amount ?: Money.ZERO, t.amount),
            )
        }.toMutableList()
        for (prev in before.values) {
            rows += CategoryRow(
                categoryId = prev.categoryId,
                amount = Money.ZERO,
                count = 0,
                share = DashboardRules.shareOf(Money.ZERO, total),
                average = Money.ZERO,
                previous = prev.amount,
                previousCount = prev.count,
                change = DashboardRules.changeOf(prev.amount, Money.ZERO),
            )
        }
        return rows
    }

    /**
     * The type's amounts per category and month, largest total first; ties by
     * id, so rows never reorder between renders. Only categories with an
     * amount show (req RPT-06).
     */
    fun categoryMatrix(txs: List<Transaction>, type: CategoryType, months: List<DateRange>): CategoryMatrix {
        val txType = DashboardRules.txTypeOf(type)
        val byCategory = LinkedHashMap<String?, LongArray>()
        val columns = LongArray(months.size)
        for (tx in txs) {
            if (tx.type != txType) continue
            val i = bucketIndex(months, tx.date)
            if (i < 0) continue
            val cells = byCategory.getOrPut(tx.categoryId) { LongArray(months.size) }
            cells[i] += tx.amount.paisa
            columns[i] += tx.amount.paisa
        }
        val rows = byCategory.entries.map { (categoryId, cells) ->
            val total = cells.sum()
            MatrixRow(
                categoryId = categoryId,
                cells = cells.map { Money(it) },
                total = Money(total),
                average = averageOf(Money(total), months.size),
            )
        }.sortedWith(compareByDescending<MatrixRow> { it.total }.thenBy { it.categoryId ?: "" })
        return CategoryMatrix(rows, columns.map { Money(it) }, Money(columns.sum()))
    }

    // Accounts

    /** How an entry moves an account's balance: in for income and transfers in, out otherwise. */
    fun balanceDelta(tx: Transaction, accountId: String): Money = when (tx.type) {
        TxType.Income -> if (tx.accountId == accountId) tx.amount else Money.ZERO
        TxType.Expense -> if (tx.accountId == accountId) -tx.amount else Money.ZERO
        TxType.Transfer -> {
            var delta = Money.ZERO
            if (tx.accountId == accountId) delta -= tx.amount
            if (tx.toAccountId == accountId) delta += tx.amount
            delta
        }
    }

    /**
     * The account's balance at the end of `date` (req BR-17): its current
     * balance less every entry dated after it. `txs` must hold every entry
     * after `date`, future-dated ones too, since they already count in the
     * current balance.
     */
    fun balanceAt(account: Account, txs: List<Transaction>, date: LocalDate): Money {
        var later = Money.ZERO
        for (tx in txs) if (tx.date > date) later += balanceDelta(tx, account.id)
        return account.currentBalance - later
    }

    /**
     * Opening balance, money in and out, and closing balance per account.
     * `txs` must hold every entry from the period's start on, future ones too.
     */
    fun accountStatements(accounts: List<Account>, txs: List<Transaction>, range: DateRange): List<AccountStatement> =
        accounts.map { account ->
            var income = Money.ZERO
            var expense = Money.ZERO
            var transfersIn = Money.ZERO
            var transfersOut = Money.ZERO
            for (tx in txs) {
                if (tx.date !in range) continue
                when (tx.type) {
                    TxType.Income -> if (tx.accountId == account.id) income += tx.amount
                    TxType.Expense -> if (tx.accountId == account.id) expense += tx.amount
                    TxType.Transfer -> {
                        // A transfer from an account to itself moves nothing, so it counts both ways.
                        if (tx.accountId == account.id) transfersOut += tx.amount
                        if (tx.toAccountId == account.id) transfersIn += tx.amount
                    }
                }
            }
            val closing = balanceAt(account, txs, range.end)
            val change = income - expense + transfersIn - transfersOut
            AccountStatement(
                accountId = account.id,
                opening = closing - change,
                income = income,
                expense = expense,
                transfersIn = transfersIn,
                transfersOut = transfersOut,
                closing = closing,
                change = change,
            )
        }

    /** Assets, liabilities and net worth of balances. */
    private fun worth(range: DateRange, balances: List<Money>, change: Money = Money.ZERO): NetWorthPoint {
        var assets = Money.ZERO
        var liabilities = Money.ZERO
        for (balance in balances) if (balance.isPositive) assets += balance else liabilities -= balance
        return NetWorthPoint(range, assets, liabilities, assets - liabilities, change)
    }

    /**
     * Net worth at the end of each bucket, oldest first, and at the end of
     * the day before the first one. `txs` must hold every entry from the
     * first bucket's start on, future ones too.
     */
    fun netWorthSeries(accounts: List<Account>, txs: List<Transaction>, buckets: List<DateRange>): NetWorthSeries {
        fun at(range: DateRange, date: LocalDate) = worth(range, accounts.map { balanceAt(it, txs, date) })
        val opening = if (buckets.isEmpty()) Money.ZERO else at(buckets[0], buckets[0].start.minusDays(1)).netWorth
        var before = opening
        val points = buckets.map { range ->
            val point = at(range, range.end)
            val result = point.copy(change = point.netWorth - before)
            before = point.netWorth
            result
        }
        return NetWorthSeries(opening, points)
    }

    // Budgets

    /**
     * The current period and the `count` before it, newest first. Periods
     * that ended before the budget was created are left out unless `early`
     * is set. `txs` must cover `BudgetRules.neededRange(budget, today, count)`.
     */
    fun budgetPeriods(
        budget: Budget,
        txs: List<Transaction>,
        today: LocalDate,
        count: Int,
        early: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BudgetPeriodRow> {
        val created = BudgetRules.createdDate(budget, zone)
        val rows = mutableListOf<BudgetPeriodRow>()
        for (offset in 0 downTo -count) {
            val range = BudgetRules.periodRange(budget.period, today, offset.toLong())
            val beforeBudget = range.end < created
            if (beforeBudget && !early) continue
            rows += BudgetPeriodRow(
                result = BudgetRules.result(budget, txs, today, offset.toLong(), zone),
                offset = offset,
                beforeBudget = beforeBudget,
                current = offset == 0,
            )
        }
        return rows
    }

    fun budgetStats(rows: List<BudgetPeriodRow>): BudgetStats {
        val finished = rows.filter { !it.current && !it.beforeBudget }
        val over = finished.filter { it.result.state == BudgetState.Over }
        var spent = Money.ZERO
        var limits = Money.ZERO
        for (row in finished) {
            spent += row.result.spent
            limits += row.result.limit
        }
        var overspent = Money.ZERO
        for (row in over) overspent -= row.result.remaining
        return BudgetStats(
            tracked = finished.size,
            under = finished.size - over.size,
            over = over.size,
            hitRate = if (finished.isEmpty()) null else DashboardRules.percentOf((finished.size - over.size).toLong(), finished.size.toLong()),
            averageSpent = if (finished.isEmpty()) null else averageOf(spent, finished.size),
            usedPercent = if (limits.isPositive) Math.floorDiv(spent.paisa * 100, limits.paisa).toInt() else null,
            overspent = overspent,
        )
    }
}
