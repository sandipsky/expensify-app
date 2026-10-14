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

    /** The fields a transaction is written with, for an edit to start from or a duplicate to copy. */
    fun toBody(tx: Transaction): TransactionBody = TransactionBody(
        type = tx.type,
        amount = tx.amount,
        accountId = tx.accountId,
        toAccountId = tx.toAccountId,
        categoryId = tx.categoryId,
        date = tx.date,
        note = tx.note,
    )

    /** The two accounts a transfer touches, or the one account otherwise. */
    fun accountIdsOf(accountId: String, toAccountId: String?): List<String> =
        if (toAccountId != null) listOf(accountId, toAccountId) else listOf(accountId)

    fun accountIdsOf(body: TransactionBody): List<String> = accountIdsOf(body.accountId, body.toAccountId)

    fun accountIdsOf(tx: Transaction): List<String> = accountIdsOf(tx.accountId, tx.toAccountId)

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

    fun balanceEffects(body: TransactionBody, sign: Int = 1): Map<String, Money> =
        balanceEffects(body.type, body.amount, body.accountId, body.toAccountId, sign)

    fun balanceEffects(tx: Transaction, sign: Int = 1): Map<String, Money> =
        balanceEffects(tx.type, tx.amount, tx.accountId, tx.toAccountId, sign)

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

    /** What the list shows of a period (req LST-02, LST-03). Every part narrows the result. */
    data class Filter(
        val type: TxType? = null,
        /** Entries touching any of these accounts, on either side of a transfer. */
        val accountIds: List<String> = emptyList(),
        /** Entries in any of these categories. */
        val categoryIds: List<String> = emptyList(),
        /** Inclusive bounds on `amount`. */
        val minAmount: Money? = null,
        val maxAmount: Money? = null,
        /** Words to find; every word must match. */
        val search: String = "",
    ) {
        /** How many filters are set, not counting the search box. */
        val activeCount: Int
            get() = (if (type != null) 1 else 0) + accountIds.size + categoryIds.size +
                (if (minAmount != null || maxAmount != null) 1 else 0)

        /** Whether anything narrows the list, the search included. */
        val isFiltering: Boolean get() = activeCount > 0 || search.isNotBlank()
    }

    val NO_FILTER = Filter()

    /** Whether every word of `search` appears in the text, ignoring case. */
    fun matchesSearch(text: String, search: String): Boolean {
        val words = search.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = text.lowercase()
        return words.all { it in haystack }
    }

    /**
     * The entries that pass every part of the filter, in their original order.
     * The search looks at the text `searchText` gives an entry: the note and
     * the category and account names, so a name finds its entries too.
     */
    fun filter(
        txs: List<Transaction>,
        filter: Filter,
        searchText: (Transaction) -> String = { it.note.orEmpty() },
    ): List<Transaction> {
        val accounts = filter.accountIds.toSet()
        val categories = filter.categoryIds.toSet()
        return txs.filter { tx ->
            if (filter.type != null && tx.type != filter.type) return@filter false
            if (accounts.isNotEmpty() && accountIdsOf(tx).none { it in accounts }) return@filter false
            if (categories.isNotEmpty() && (tx.categoryId == null || tx.categoryId !in categories)) return@filter false
            if (filter.minAmount != null && tx.amount < filter.minAmount) return@filter false
            if (filter.maxAmount != null && tx.amount > filter.maxAmount) return@filter false
            matchesSearch(searchText(tx), filter.search)
        }
    }

    /** An account as `defaultAccountId` sees it. */
    data class AccountChoice(val id: String, val archived: Boolean)

    /**
     * The account a new entry starts on (req TXN-02): the one this device used
     * last while it's still active, otherwise the first active account.
     */
    fun defaultAccountId(accounts: List<AccountChoice>, last: String?): String? {
        val active = accounts.filter { !it.archived }
        return (active.firstOrNull { it.id == last } ?: active.firstOrNull())?.id
    }
}
