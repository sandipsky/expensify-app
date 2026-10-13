package com.example.expensify.feature.accounts.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.AccountRules.BalanceCaption
import com.example.expensify.feature.accounts.domain.AccountType

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.Cash -> R.string.account_type_cash
    AccountType.Bank -> R.string.account_type_bank
    AccountType.CreditCard -> R.string.account_type_credit_card
    AccountType.Wallet -> R.string.account_type_wallet
    AccountType.Savings -> R.string.account_type_savings
    AccountType.Loan -> R.string.account_type_loan
    AccountType.Other -> R.string.account_type_other
}

/** The short caption on a card: Balance · Owed · In credit. */
@StringRes
fun BalanceCaption.labelRes(): Int = when (this) {
    BalanceCaption.Balance -> R.string.balance_caption_balance
    BalanceCaption.Owed -> R.string.balance_caption_owed
    BalanceCaption.InCredit -> R.string.balance_caption_in_credit
}

/** The longer caption on the account page: Current balance · Amount owed · In credit. */
@StringRes
fun BalanceCaption.currentLabelRes(): Int = when (this) {
    BalanceCaption.Balance -> R.string.balance_caption_current
    BalanceCaption.Owed -> R.string.balance_caption_amount_owed
    BalanceCaption.InCredit -> R.string.balance_caption_in_credit
}

/** An amount as a form prefills it: "12000" or "12000.50", never grouped. */
fun moneyInputText(money: Money): String {
    val sign = if (money.isNegative) "-" else ""
    val abs = money.abs()
    val rupees = abs.paisa / 100
    val paisa = abs.paisa % 100
    return if (paisa == 0L) "$sign$rupees" else "$sign$rupees.${paisa.toString().padStart(2, '0')}"
}
