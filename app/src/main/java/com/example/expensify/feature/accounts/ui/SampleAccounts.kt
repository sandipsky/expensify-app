package com.example.expensify.feature.accounts.ui

import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountType
import java.time.Instant

/** Realistic fixtures for previews. */
object SampleAccounts {
    private val at = Instant.parse("2026-10-01T07:10:33Z")

    private fun account(
        id: String,
        name: String,
        type: AccountType,
        current: Money,
        opening: Money = current,
        creditLimit: Money? = null,
        includeInTotal: Boolean = true,
        archived: Boolean = false,
        sortOrder: Long = 0,
    ) = Account(
        id = id,
        name = name,
        type = type,
        currency = "NPR",
        openingBalance = opening,
        currentBalance = current,
        creditLimit = creditLimit,
        icon = type.defaultIcon,
        color = type.defaultColor,
        includeInTotal = includeInTotal,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = at,
        updatedAt = at,
    )

    val everyday = account("a1", "Everyday bank", AccountType.Bank, Money.rupees(84_250, 75), Money.rupees(50_000), sortOrder = 0)
    val cash = account("a2", "Wallet cash", AccountType.Cash, Money.rupees(3_450), sortOrder = 1)
    val card = account(
        "a3", "Visa card", AccountType.CreditCard, Money.rupees(-42_000), Money.ZERO,
        creditLimit = Money.rupees(50_000), sortOrder = 2,
    )
    val savings = account("a4", "Rainy day", AccountType.Savings, Money.rupees(1_20_000), includeInTotal = false, sortOrder = 3)
    val oldWallet = account("a5", "Old eSewa", AccountType.Wallet, Money.ZERO, archived = true, sortOrder = 4)

    val active = listOf(everyday, cash, card, savings)
    val archived = listOf(oldWallet)
}
