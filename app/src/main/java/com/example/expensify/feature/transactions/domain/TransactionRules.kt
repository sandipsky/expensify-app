package com.example.expensify.feature.transactions.domain

import com.example.expensify.core.money.Money
import java.time.LocalDate

/** Port of ../expensify/src/features/transactions/transaction.domain.ts: pure rules every screen shares. */
object TransactionRules {

    const val MAX_NOTE_LENGTH = 500

    /** Newest first: by date, then creation, so a new entry tops today's list. */
    val newestFirst: Comparator<Transaction> =
        compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }.thenByDescending { it.id }

    /** Dated after today: it already counts in balances and shows as "Upcoming" (req TXN-10). */
    fun isUpcoming(tx: Transaction, today: LocalDate): Boolean = tx.date > today

    /** The two accounts a transfer touches, or the one account otherwise. */
    fun accountIdsOf(accountId: String, toAccountId: String?): List<String> =
        if (toAccountId != null) listOf(accountId, toAccountId) else listOf(accountId)

    /**
     * What an entry does to each account it touches (req §4): income adds to
     * its account, an expense takes from it, a transfer moves the amount
     * across. A `sign` of -1 reverses that, as deleting the entry does.
     */
    fun balanceEffects(
        type: TxType,
        amount: Money,
        accountId: String,
        toAccountId: String?,
        sign: Int = 1,
    ): Map<String, Money> {
        val signed = if (sign < 0) -amount else amount
        return when {
            type == TxType.Income -> mapOf(accountId to signed)
            type == TxType.Expense || toAccountId == null -> mapOf(accountId to -signed)
            else -> mapOf(accountId to -signed, toAccountId to signed)
        }
    }

    /** Several entries' effects added up per account, as one batch applies them. */
    fun combineEffects(effects: List<Map<String, Money>>): Map<String, Money> {
        val total = mutableMapOf<String, Money>()
        for (map in effects) for ((id, delta) in map) total[id] = (total[id] ?: Money.ZERO) + delta
        return total
    }

    data class Totals(val income: Money, val expense: Money) {
        /** Income − expense. */
        val net: Money get() = income - expense
    }

    /** Income, expense and net. Transfers only move money, so they don't count (req BR-04). */
    fun totalsOf(txs: List<Transaction>): Totals {
        var income = Money.ZERO
        var expense = Money.ZERO
        for (tx in txs) {
            when (tx.type) {
                TxType.Income -> income += tx.amount
                TxType.Expense -> expense += tx.amount
                TxType.Transfer -> Unit
            }
        }
        return Totals(income, expense)
    }

    data class DayGroup<T>(val date: LocalDate, val items: List<T>)

    /** Items already sorted newest first, grouped into runs of the same date. */
    fun <T> groupByDay(items: List<T>, dateOf: (T) -> LocalDate): List<DayGroup<T>> {
        val days = mutableListOf<Pair<LocalDate, MutableList<T>>>()
        for (item in items) {
            val date = dateOf(item)
            val last = days.lastOrNull()
            if (last != null && last.first == date) last.second += item else days += date to mutableListOf(item)
        }
        return days.map { (date, list) -> DayGroup(date, list) }
    }
}
