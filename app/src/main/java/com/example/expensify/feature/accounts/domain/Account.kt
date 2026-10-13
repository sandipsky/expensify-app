package com.example.expensify.feature.accounts.domain

import com.example.expensify.core.money.Money
import java.time.Instant

/** The seven account types with the icon and color a new account of each gets (req Appendix A). */
enum class AccountType(val stored: String, val defaultIcon: String, val defaultColor: String) {
    Cash("cash", "money", "#36B37E"),
    Bank("bank", "bank", "#2456E6"),
    CreditCard("credit_card", "card", "#AB20A9"),
    Wallet("wallet", "wallet", "#00B8D9"),
    Savings("savings", "savings", "#FFAB00"),
    Loan("loan", "moneys", "#FF4530"),
    Other("other", "wallet_alt", "#646663"),
    ;

    /** Credit cards and loans hold money owed, so their balances read as amounts owed (req BR-16). */
    val isLiability: Boolean get() = this == CreditCard || this == Loan

    companion object {
        fun fromStored(value: String?): AccountType = entries.firstOrNull { it.stored == value } ?: Other
    }
}

/** users/{uid}/accounts/{id} (req §8). */
data class Account(
    val id: String,
    /** 1–40 characters. */
    val name: String,
    val type: AccountType,
    /** ISO 4217 code; `NPR` today. */
    val currency: String,
    /** May be negative (cards, loans, overdrafts). */
    val openingBalance: Money,
    /** Opening balance plus every entry's effect; only the repository writes move it. */
    val currentBalance: Money,
    /** Credit cards only. */
    val creditLimit: Money?,
    /** A lumen-ui icon name. */
    val icon: String,
    /** `#RRGGBB`. */
    val color: String,
    val includeInTotal: Boolean,
    val archived: Boolean,
    val sortOrder: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** What a form saves; the repository sets the id, current balance, archived flag, sort order and timestamps. */
data class AccountBody(
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalance: Money,
    val creditLimit: Money?,
    val icon: String,
    val color: String,
    val includeInTotal: Boolean,
)
