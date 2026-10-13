package com.example.expensify.feature.transactions.domain

import com.example.expensify.core.money.Money
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Req §4 balance effects and the list rules of transaction.domain.ts. */
class TransactionRulesTest {

    private fun tx(
        id: String,
        type: TxType,
        amount: Long,
        date: String,
        createdAt: String = "2026-09-26T10:00:00Z",
        toAccountId: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amount = Money.rupees(amount),
        currency = "NPR",
        accountId = "cash",
        toAccountId = toAccountId,
        categoryId = if (type == TxType.Transfer) null else "food",
        date = LocalDate.parse(date),
        note = null,
        recurringRuleId = null,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(createdAt),
    )

    @Test
    fun `balance effects per type, and reversed`() {
        assertThat(TransactionRules.balanceEffects(TxType.Income, Money.rupees(100), "cash", null))
            .containsExactly("cash", Money.rupees(100))
        assertThat(TransactionRules.balanceEffects(TxType.Expense, Money.rupees(30), "cash", null))
            .containsExactly("cash", Money.rupees(-30))
        assertThat(TransactionRules.balanceEffects(TxType.Transfer, Money.rupees(200), "bank", "cash"))
            .containsExactly("bank", Money.rupees(-200), "cash", Money.rupees(200))
        assertThat(TransactionRules.balanceEffects(TxType.Expense, Money.rupees(30), "cash", null, sign = -1))
            .containsExactly("cash", Money.rupees(30))
    }

    @Test
    fun `an edit is the old effect reversed plus the new one (US-03)`() {
        val before = TransactionRules.balanceEffects(TxType.Expense, Money.rupees(30), "cash", null, sign = -1)
        val after = TransactionRules.balanceEffects(TxType.Expense, Money.rupees(45), "bank", null)
        assertThat(TransactionRules.combineEffects(listOf(before, after)))
            .containsExactly("cash", Money.rupees(30), "bank", Money.rupees(-45))
    }

    @Test
    fun `totals leave transfers out`() {
        val totals = TransactionRules.totalsOf(
            listOf(
                tx("1", TxType.Income, 1000, "2026-09-01"),
                tx("2", TxType.Expense, 300, "2026-09-02"),
                tx("3", TxType.Transfer, 5000, "2026-09-03", toAccountId = "bank"),
            ),
        )
        assertThat(totals.income).isEqualTo(Money.rupees(1000))
        assertThat(totals.expense).isEqualTo(Money.rupees(300))
        assertThat(totals.net).isEqualTo(Money.rupees(700))
    }

    @Test
    fun `newest first by date, then creation, then id`() {
        val list = listOf(
            tx("a", TxType.Expense, 1, "2026-09-01", "2026-09-01T09:00:00Z"),
            tx("b", TxType.Expense, 1, "2026-09-02", "2026-09-02T09:00:00Z"),
            tx("c", TxType.Expense, 1, "2026-09-02", "2026-09-02T10:00:00Z"),
            tx("d", TxType.Expense, 1, "2026-09-02", "2026-09-02T10:00:00Z"),
        ).sortedWith(TransactionRules.newestFirst)
        assertThat(list.map { it.id }).containsExactly("d", "c", "b", "a").inOrder()
    }

    @Test
    fun `grouped by day in order, with upcoming flagged`() {
        val today = LocalDate.parse("2026-09-02")
        val sorted = listOf(
            tx("1", TxType.Expense, 1, "2026-09-03"),
            tx("2", TxType.Expense, 1, "2026-09-02"),
            tx("3", TxType.Expense, 1, "2026-09-02"),
            tx("4", TxType.Expense, 1, "2026-09-01"),
        )
        val days = TransactionRules.groupByDay(sorted) { it.date }
        assertThat(days.map { it.date.toString() }).containsExactly("2026-09-03", "2026-09-02", "2026-09-01").inOrder()
        assertThat(days[1].items).hasSize(2)
        assertThat(TransactionRules.isUpcoming(sorted[0], today)).isTrue()
        assertThat(TransactionRules.isUpcoming(sorted[1], today)).isFalse()
    }
}
