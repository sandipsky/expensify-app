package com.example.expensify.feature.dashboard.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.money.Money
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import kotlin.math.abs

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

/**
 * Port of ../expensify/src/features/dashboard/dashboard.domain.ts: the
 * figures the dashboard and the reports share. Every total leaves transfers
 * out (req BR-04). Percentages are whole numbers rounded half up (req BR-14),
 * worked out in whole paisa so float noise never moves one.
 */
object DashboardRules {

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
}
