package com.example.expensify.feature.transactions.domain

import com.example.expensify.core.money.Money
import com.example.expensify.feature.transactions.domain.TransactionRules.AccountChoice
import com.example.expensify.feature.transactions.domain.TransactionRules.Filter
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Req §4 balance effects, LST-02, LST-03 and the list rules of transaction.domain.ts. */
class TransactionRulesTest {

    private fun tx(
        id: String,
        type: TxType,
        amount: Long,
        date: String,
        createdAt: String = "2026-09-26T10:00:00Z",
        toAccountId: String? = null,
        accountId: String = "cash",
        categoryId: String? = if (type == TxType.Transfer) null else "food",
        note: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amount = Money.rupees(amount),
        currency = "NPR",
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        date = LocalDate.parse(date),
        note = note,
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
    fun `US-02 a transfer moves the amount across and counts as neither income nor expense`() {
        val transfer = tx("t", TxType.Transfer, 200, "2026-09-02", accountId = "bank", toAccountId = "cash")
        assertThat(TransactionRules.balanceEffects(transfer)).containsExactly("bank", Money.rupees(-200), "cash", Money.rupees(200))
        val totals = TransactionRules.totalsOf(listOf(transfer))
        assertThat(totals.income).isEqualTo(Money.ZERO)
        assertThat(totals.expense).isEqualTo(Money.ZERO)
    }

    @Test
    fun `US-03 an edit is the old effect reversed plus the new one`() {
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

    @Test
    fun `a body carries the fields a duplicate copies (TXN-08)`() {
        val original = tx("1", TxType.Transfer, 500, "2026-09-02", accountId = "bank", toAccountId = "cash", note = "Top up")
        val body = TransactionRules.toBody(original)
        assertThat(body).isEqualTo(TransactionBody(TxType.Transfer, Money.rupees(500), "bank", "cash", null, LocalDate.parse("2026-09-02"), "Top up"))
    }

    private val entries = listOf(
        tx("1", TxType.Expense, 450, "2026-09-05", categoryId = "food", note = "Lunch with Ram"),
        tx("2", TxType.Expense, 1200, "2026-09-04", categoryId = "transport"),
        tx("3", TxType.Income, 50000, "2026-09-01", accountId = "bank", categoryId = "salary"),
        tx("4", TxType.Transfer, 5000, "2026-09-03", accountId = "bank", toAccountId = "cash"),
    )

    @Test
    fun `LST-02 filters combine type, accounts on either side, categories and amount range`() {
        fun ids(filter: Filter) = TransactionRules.filter(entries, filter).map { it.id }

        assertThat(ids(Filter(type = TxType.Expense))).containsExactly("1", "2").inOrder()
        assertThat(ids(Filter(accountIds = listOf("cash")))).containsExactly("1", "2", "4").inOrder()
        assertThat(ids(Filter(accountIds = listOf("bank")))).containsExactly("3", "4").inOrder()
        assertThat(ids(Filter(categoryIds = listOf("food", "salary")))).containsExactly("1", "3").inOrder()
        assertThat(ids(Filter(minAmount = Money.rupees(1000), maxAmount = Money.rupees(5000)))).containsExactly("2", "4").inOrder()
        assertThat(ids(Filter(type = TxType.Expense, accountIds = listOf("cash"), minAmount = Money.rupees(1000)))).containsExactly("2")
        assertThat(ids(TransactionRules.NO_FILTER)).hasSize(4)

        assertThat(Filter(type = TxType.Expense, accountIds = listOf("a", "b"), minAmount = Money.rupees(1)).activeCount).isEqualTo(4)
        assertThat(Filter(search = "x").activeCount).isEqualTo(0)
        assertThat(Filter(search = "x").isFiltering).isTrue()
    }

    @Test
    fun `LST-03 every typed word must match the note, category or account, ignoring case`() {
        assertThat(TransactionRules.matchesSearch("Lunch with Ram", "ram lunch")).isTrue()
        assertThat(TransactionRules.matchesSearch("Lunch with Ram", "ram dinner")).isFalse()
        assertThat(TransactionRules.matchesSearch("anything", "   ")).isTrue()

        val names = mapOf("food" to "Food and dining", "transport" to "Transport", "salary" to "Salary")
        val found = TransactionRules.filter(entries, Filter(search = "FOOD")) { tx ->
            listOf(tx.note.orEmpty(), names[tx.categoryId].orEmpty(), tx.accountId).joinToString("\n")
        }
        assertThat(found.map { it.id }).containsExactly("1")
        val byAccount = TransactionRules.filter(entries, Filter(search = "bank")) { it.accountId }
        assertThat(byAccount.map { it.id }).containsExactly("3", "4").inOrder()
    }

    @Test
    fun `TXN-02 a new entry starts on the last-used account while active, else the first active one`() {
        val accounts = listOf(AccountChoice("old", archived = true), AccountChoice("cash", false), AccountChoice("bank", false))
        assertThat(TransactionRules.defaultAccountId(accounts, "bank")).isEqualTo("bank")
        assertThat(TransactionRules.defaultAccountId(accounts, "old")).isEqualTo("cash")
        assertThat(TransactionRules.defaultAccountId(accounts, null)).isEqualTo("cash")
        assertThat(TransactionRules.defaultAccountId(listOf(AccountChoice("old", true)), "old")).isNull()
    }
}
