package com.example.expensify.feature.transactions.ui

import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import java.time.Instant
import java.time.LocalDate

/** Realistic fixtures for previews. */
object SampleTransactions {
    val today: LocalDate = LocalDate.of(2026, 10, 14)
    private val at = Instant.parse("2026-10-14T07:10:33Z")

    private fun tx(
        id: String,
        type: TxType,
        amount: Money,
        date: LocalDate,
        accountId: String = SampleAccounts.cash.id,
        toAccountId: String? = null,
        categoryId: String? = SampleCategories.food.id,
        note: String? = null,
        recurringRuleId: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amount = amount,
        currency = "NPR",
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = if (type == TxType.Transfer) null else categoryId,
        date = date,
        note = note,
        recurringRuleId = recurringRuleId,
        createdAt = at,
        updatedAt = at,
    )

    val lunch = tx("t1", TxType.Expense, Money.rupees(450), today, note = "Lunch with Ram")
    val bus = tx("t2", TxType.Expense, Money.rupees(60), today, categoryId = SampleCategories.transport.id)
    val topUp = tx("t3", TxType.Transfer, Money.rupees(5_000), today.minusDays(1), accountId = SampleAccounts.everyday.id, toAccountId = SampleAccounts.cash.id)
    val salary = tx(
        "t4", TxType.Income, Money.rupees(85_000), today.withDayOfMonth(1), accountId = SampleAccounts.everyday.id,
        categoryId = SampleCategories.all.first { it.name == "Salary" }.id, note = "Salary", recurringRuleId = "r1",
    )
    val rent = tx(
        "t5", TxType.Expense, Money.rupees(18_000), today.plusDays(3), accountId = SampleAccounts.everyday.id,
        categoryId = SampleCategories.all.first { it.name == "Housing and rent" }.id, note = "Rent",
    )

    val all: List<Transaction> = listOf(rent, lunch, bus, topUp, salary)

    val lookups = TxRows.Lookups(SampleAccounts.active + SampleAccounts.archived, SampleCategories.all, today)

    val days: List<DayUi> = TxRows.days(all, lookups)
}
