package com.example.expensify.feature.accounts.domain

import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.AccountRules.BalanceCaption
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class AccountRulesTest {

    private fun account(
        type: AccountType,
        current: Money,
        creditLimit: Money? = null,
        includeInTotal: Boolean = true,
        archived: Boolean = false,
        icon: String = type.defaultIcon,
        color: String = type.defaultColor,
        sortOrder: Long = 0,
    ) = Account(
        id = "id-${type.stored}-$sortOrder",
        name = type.stored,
        type = type,
        currency = "NPR",
        openingBalance = Money.ZERO,
        currentBalance = current,
        creditLimit = creditLimit,
        icon = icon,
        color = color,
        includeInTotal = includeInTotal,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `cards and loans flip between owed and stored`() {
        assertThat(AccountRules.flipForLiability(AccountType.CreditCard, Money.rupees(500))).isEqualTo(Money.rupees(-500))
        assertThat(AccountRules.flipForLiability(AccountType.Loan, Money.rupees(-500))).isEqualTo(Money.rupees(500))
        assertThat(AccountRules.flipForLiability(AccountType.Bank, Money.rupees(500))).isEqualTo(Money.rupees(500))
        assertThat(AccountRules.flipForLiability(AccountType.CreditCard, Money.ZERO)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `balance view reads owed, in credit, or a plain balance`() {
        assertThat(AccountRules.balanceView(AccountType.CreditCard, Money.rupees(-42000)))
            .isEqualTo(AccountRules.BalanceView(BalanceCaption.Owed, Money.rupees(42000), negative = false))
        assertThat(AccountRules.balanceView(AccountType.Loan, Money.rupees(300)))
            .isEqualTo(AccountRules.BalanceView(BalanceCaption.InCredit, Money.rupees(300), negative = false))
        assertThat(AccountRules.balanceView(AccountType.CreditCard, Money.ZERO).caption).isEqualTo(BalanceCaption.Owed)
        assertThat(AccountRules.balanceView(AccountType.Bank, Money.rupees(-20)))
            .isEqualTo(AccountRules.BalanceView(BalanceCaption.Balance, Money.rupees(-20), negative = true))
    }

    @Test
    fun `credit utilization is owed over limit, unrounded, null without a limit`() {
        assertThat(AccountRules.creditUtilization(Money.rupees(-42000), Money.rupees(50000))).isEqualTo(84.0)
        assertThat(AccountRules.creditUtilization(Money.rupees(300), Money.rupees(50000))).isEqualTo(0.0)
        assertThat(AccountRules.creditUtilization(Money.rupees(-100), null)).isNull()
        assertThat(AccountRules.creditUtilization(Money.rupees(-100), Money.ZERO)).isNull()
        val used = AccountRules.utilizationView(account(AccountType.CreditCard, Money.rupees(-39800), Money.rupees(50000)))
        assertThat(used).isEqualTo(AccountRules.Utilization(80, 79.6))
        assertThat(AccountRules.utilizationView(account(AccountType.Bank, Money.rupees(100), Money.rupees(50000)))).isNull()
    }

    @Test
    fun `total balance counts active accounts marked include in total`() {
        val accounts = listOf(
            account(AccountType.Bank, Money.rupees(1000), sortOrder = 0),
            account(AccountType.CreditCard, Money.rupees(-200), sortOrder = 1),
            account(AccountType.Savings, Money.rupees(5000), includeInTotal = false, sortOrder = 2),
            account(AccountType.Cash, Money.rupees(300), archived = true, sortOrder = 3),
        )
        assertThat(AccountRules.totalBalance(accounts)).isEqualTo(Money.rupees(800))
        assertThat(AccountRules.nextSortOrder(accounts)).isEqualTo(4)
        assertThat(AccountRules.nextSortOrder(emptyList())).isEqualTo(0)
    }

    @Test
    fun `icon and color follow a type change only while they are still defaults`() {
        assertThat(AccountRules.iconAndColorFor(null, AccountType.Bank)).isEqualTo("bank" to "#2456E6")
        val defaults = account(AccountType.Cash, Money.ZERO)
        assertThat(AccountRules.iconAndColorFor(defaults, AccountType.Loan)).isEqualTo("moneys" to "#FF4530")
        val custom = account(AccountType.Cash, Money.ZERO, icon = "star", color = "#123456")
        assertThat(AccountRules.iconAndColorFor(custom, AccountType.Loan)).isEqualTo("star" to "#123456")
    }
}
