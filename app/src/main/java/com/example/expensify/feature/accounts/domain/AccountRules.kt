package com.example.expensify.feature.accounts.domain

import com.example.expensify.core.money.Money
import com.example.expensify.core.money.sum
import kotlin.math.roundToInt

/** Port of ../expensify/src/features/accounts/account.domain.ts and account.labels.ts. */
object AccountRules {

    const val MAX_NAME_LENGTH = 40
    const val DEFAULT_CURRENCY = "NPR"

    /**
     * Converts between a stored balance and what the form shows. For cards and
     * loans the form edits the amount owed, which is stored as a negative
     * balance; other types pass through. The conversion is its own inverse.
     */
    fun flipForLiability(type: AccountType, value: Money): Money = if (type.isLiability) -value else value

    /** What a negative balance owes, as a positive amount; zero when the balance is zero or in credit. */
    fun amountOwed(balance: Money): Money = if (balance.isNegative) -balance else Money.ZERO

    /** Share of a credit limit in use, as an unrounded percent. Null without a limit. */
    fun creditUtilization(currentBalance: Money, creditLimit: Money?): Double? {
        if (creditLimit == null || !creditLimit.isPositive) return null
        return amountOwed(currentBalance).paisa * 100.0 / creditLimit.paisa
    }

    /** Sum of the active accounts marked "include in total" (req DSH-05). */
    fun totalBalance(accounts: List<Account>): Money =
        accounts.filter { it.includeInTotal && !it.archived }.map { it.currentBalance }.sum()

    /**
     * The icon and color an account should carry after a type change. They
     * follow the new type only while they are still the old type's defaults,
     * so a custom choice made elsewhere survives (req ACC-05).
     */
    fun iconAndColorFor(account: Account?, type: AccountType): Pair<String, String> {
        if (account == null) return type.defaultIcon to type.defaultColor
        val before = account.type
        val icon = if (account.icon == before.defaultIcon) type.defaultIcon else account.icon
        val color = if (account.color == before.defaultColor) type.defaultColor else account.color
        return icon to color
    }

    /** One more than the highest, so accounts list in creation order. */
    fun nextSortOrder(accounts: List<Account>): Long = (accounts.maxOfOrNull { it.sortOrder } ?: -1L) + 1

    enum class BalanceCaption { Balance, Owed, InCredit }

    /** How an account's balance reads on screen. */
    data class BalanceView(
        val caption: BalanceCaption,
        /** An amount owed is shown as a positive number. */
        val amount: Money,
        /** A normal account below zero, shown in the expense color with its minus sign. */
        val negative: Boolean,
    )

    /** Cards and loans read as the amount owed, or "in credit" when overpaid (req ACC-04). */
    fun balanceView(type: AccountType, currentBalance: Money): BalanceView {
        if (type.isLiability) {
            return if (currentBalance.isPositive) {
                BalanceView(BalanceCaption.InCredit, currentBalance, negative = false)
            } else {
                BalanceView(BalanceCaption.Owed, amountOwed(currentBalance), negative = false)
            }
        }
        return BalanceView(BalanceCaption.Balance, currentBalance, negative = currentBalance.isNegative)
    }

    /** Credit used, rounded for display, with the unrounded figure the bar color follows. */
    data class Utilization(val percent: Int, val raw: Double)

    /** Credit cards with a limit only. */
    fun utilizationView(account: Account): Utilization? {
        if (account.type != AccountType.CreditCard) return null
        val used = creditUtilization(account.currentBalance, account.creditLimit) ?: return null
        return Utilization(used.roundToInt(), used)
    }
}
