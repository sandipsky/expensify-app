package com.example.expensify.feature.transactions.domain

import com.example.expensify.core.money.Money
import java.time.Instant
import java.time.LocalDate

enum class TxType(val stored: String) {
    Expense("expense"),
    Income("income"),
    Transfer("transfer"),
    ;

    companion object {
        fun fromStored(value: String?): TxType = entries.firstOrNull { it.stored == value } ?: Expense
    }
}

/** users/{uid}/transactions/{id} (req §8), without `accountIds`, which only the query uses. */
data class Transaction(
    val id: String,
    val type: TxType,
    /** Positive; `type` gives the direction. */
    val amount: Money,
    val currency: String,
    /** Source account. */
    val accountId: String,
    /** Transfers only. */
    val toAccountId: String?,
    /** Null for transfers. */
    val categoryId: String?,
    /** The user's local calendar date. */
    val date: LocalDate,
    val note: String?,
    /** The recurring rule that added it; null for an entry added by hand. */
    val recurringRuleId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
