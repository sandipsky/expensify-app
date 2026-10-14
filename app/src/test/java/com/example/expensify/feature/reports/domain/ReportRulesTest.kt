package com.example.expensify.feature.reports.domain

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountType
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.dashboard.domain.Change
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

/** Req RPT-03…RPT-09, BR-06, BR-17: the report figures, ported from report.domain.ts. */
class ReportRulesTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 14)
    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)
    private fun range(start: LocalDate, end: LocalDate) = DateRange(start, end)
    private val october = range(d(10, 1), d(10, 31))
    private val september = range(d(9, 1), d(9, 30))

    private fun tx(
        amount: Long,
        date: LocalDate,
        type: TxType = TxType.Expense,
        categoryId: String? = "food",
        accountId: String = "cash",
        toAccountId: String? = null,
        paisa: Long = 0,
    ) = Transaction(
        id = "t-$amount-$date-$type-$categoryId-$accountId",
        type = type,
        amount = Money.rupees(amount, paisa),
        currency = "NPR",
        accountId = accountId,
        toAccountId = if (type == TxType.Transfer) toAccountId ?: "bank" else null,
        categoryId = if (type == TxType.Transfer) null else categoryId,
        date = date,
        note = null,
        recurringRuleId = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun account(id: String, balance: Money, type: AccountType = AccountType.Cash) = Account(
        id = id, name = id, type = type, currency = "NPR", openingBalance = Money.ZERO, currentBalance = balance,
        creditLimit = null, icon = "wallet", color = "#646663", includeInTotal = true, archived = false, sortOrder = 0,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun budget(createdAt: String = "2026-09-15T00:00:00Z", rollover: Boolean = false) = Budget(
        id = "b1", name = "Food", amount = Money.rupees(500), period = BudgetPeriod.Monthly, categoryIds = listOf("food"),
        rollover = rollover, alertThresholds = listOf(80, 100), lastAlert = null, active = true,
        createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(createdAt),
    )

    // Periods

    @Test
    fun `presets stand for calendar periods, with last N months including the current one`() {
        val custom = range(d(3, 3), d(2, 2))
        assertThat(ReportRules.reportRange(ReportPreset.ThisMonth, today, custom)).isEqualTo(october)
        assertThat(ReportRules.reportRange(ReportPreset.LastMonth, today, custom)).isEqualTo(september)
        assertThat(ReportRules.reportRange(ReportPreset.Last3Months, today, custom)).isEqualTo(range(d(8, 1), d(10, 31)))
        assertThat(ReportRules.reportRange(ReportPreset.Last6Months, today, custom)).isEqualTo(range(d(5, 1), d(10, 31)))
        assertThat(ReportRules.reportRange(ReportPreset.Last12Months, today, custom)).isEqualTo(range(d(11, 1, 2025), d(10, 31)))
        assertThat(ReportRules.reportRange(ReportPreset.ThisYear, today, custom)).isEqualTo(range(d(1, 1), d(12, 31)))
        assertThat(ReportRules.reportRange(ReportPreset.LastYear, today, custom)).isEqualTo(range(d(1, 1, 2025), d(12, 31, 2025)))
        // Custom returns the given range in order.
        assertThat(ReportRules.reportRange(ReportPreset.Custom, today, custom)).isEqualTo(range(d(2, 2), d(3, 3)))
    }

    @Test
    fun `the period state falls back to the report's default and keeps a custom range`() {
        val fallback = ReportRules.periodState(null, null, null, ReportPreset.Last12Months, today)
        assertThat(fallback.preset).isEqualTo(ReportPreset.Last12Months)
        assertThat(fallback.range).isEqualTo(range(d(11, 1, 2025), d(10, 31)))
        // "Custom" starts from the default's range until a range is picked.
        assertThat(fallback.custom).isEqualTo(fallback.range)

        val custom = ReportRules.periodState(ReportPreset.Custom, d(10, 20), d(10, 5), ReportPreset.ThisMonth, today)
        assertThat(custom.range).isEqualTo(range(d(10, 5), d(10, 20)))
        assertThat(custom.custom).isEqualTo(custom.range)

        // A preset with a stale custom range still shows the preset.
        val preset = ReportRules.periodState(ReportPreset.LastMonth, d(10, 5), d(10, 20), ReportPreset.ThisMonth, today)
        assertThat(preset.range).isEqualTo(september)
        assertThat(preset.custom).isEqualTo(range(d(10, 5), d(10, 20)))
    }

    @Test
    fun `whole months and the previous range`() {
        assertThat(ReportRules.wholeMonths(october)).isEqualTo(1)
        assertThat(ReportRules.wholeMonths(range(d(8, 1), d(10, 31)))).isEqualTo(3)
        assertThat(ReportRules.wholeMonths(range(d(8, 15), d(10, 31)))).isNull()
        assertThat(ReportRules.previousRange(range(d(8, 1), d(10, 31)))).isEqualTo(range(d(5, 1), d(7, 31)))
        assertThat(ReportRules.previousRange(range(d(1, 1), d(12, 31)))).isEqualTo(range(d(1, 1, 2025), d(12, 31, 2025)))
        assertThat(ReportRules.previousRange(range(d(10, 5), d(10, 14)))).isEqualTo(range(d(9, 25), d(10, 4)))
    }

    @Test
    fun `buckets split the range into weeks, months or years, cut to the range`() {
        assertThat(ReportRules.bucketsOf(range(d(9, 15), d(11, 10)), Granularity.Month))
            .containsExactly(range(d(9, 15), d(9, 30)), october, range(d(11, 1), d(11, 10))).inOrder()
        // 14 Oct 2026 is a Wednesday; weeks end on Sunday.
        assertThat(ReportRules.bucketsOf(range(d(10, 14), d(10, 20)), Granularity.Week))
            .containsExactly(range(d(10, 14), d(10, 18)), range(d(10, 19), d(10, 20))).inOrder()
        assertThat(ReportRules.bucketsOf(range(d(6, 1, 2025), d(3, 31)), Granularity.Year))
            .containsExactly(range(d(6, 1, 2025), d(12, 31, 2025)), range(d(1, 1), d(3, 31))).inOrder()
        assertThat(ReportRules.spansYears(ReportRules.bucketsOf(range(d(6, 1, 2025), d(3, 31)), Granularity.Month))).isTrue()
        assertThat(ReportRules.spansYears(ReportRules.bucketsOf(october, Granularity.Week))).isFalse()
    }

    @Test
    fun `the trends table keeps the last 24 months of a longer range`() {
        val long = range(d(1, 1, 2024), d(10, 31))
        val trend = ReportRules.trendRange(long)
        assertThat(trend.clipped).isTrue()
        assertThat(trend.range).isEqualTo(range(d(11, 1, 2024), d(10, 31)))
        assertThat(ReportRules.trendRange(range(d(11, 1, 2024), d(10, 31)))).isEqualTo(TrendRange(range(d(11, 1, 2024), d(10, 31)), false))
    }

    @Test
    fun `on accounts keeps entries on either side of a transfer`() {
        val txs = listOf(
            tx(10, d(10, 1), accountId = "cash"),
            tx(20, d(10, 2), accountId = "bank"),
            tx(30, d(10, 3), type = TxType.Transfer, accountId = "bank", toAccountId = "cash"),
        )
        assertThat(ReportRules.onAccounts(txs, listOf("cash"))).containsExactly(txs[0], txs[2]).inOrder()
        assertThat(ReportRules.onAccounts(txs, emptyList())).isEqualTo(txs)
    }

    // Cash flow

    @Test
    fun `cash flow sums each bucket, leaves transfers out and carries a running net`() {
        val buckets = ReportRules.bucketsOf(range(d(9, 1), d(10, 31)), Granularity.Month)
        val txs = listOf(
            tx(1000, d(9, 5), type = TxType.Income, categoryId = "salary"),
            tx(300, d(9, 10)),
            tx(400, d(10, 2)),
            tx(999, d(10, 3), type = TxType.Transfer),
            tx(5, d(11, 1)), // outside the buckets
        )
        val rows = ReportRules.cashFlow(txs, buckets)
        assertThat(rows).hasSize(2)
        assertThat(rows[0]).isEqualTo(FlowRow(september, Money.rupees(1000), Money.rupees(300), Money.rupees(700), 70, 2, Money.rupees(700)))
        assertThat(rows[1]).isEqualTo(FlowRow(october, Money.ZERO, Money.rupees(400), Money.rupees(-400), null, 1, Money.rupees(300)))
    }

    // Categories

    @Test
    fun `category comparison lists this period largest first, then categories only the previous period had`() {
        val current = listOf(tx(200, d(10, 1)), tx(100, d(10, 2)), tx(100, d(10, 3), categoryId = "fun"))
        val previous = listOf(tx(200, d(9, 1)), tx(50, d(9, 2), categoryId = "car"))
        val rows = ReportRules.categoryComparison(current, previous, CategoryType.Expense)
        assertThat(rows.map { it.categoryId }).containsExactly("food", "fun", "car").inOrder()
        assertThat(rows[0]).isEqualTo(
            CategoryRow("food", Money.rupees(300), 2, 75, Money.rupees(150), Money.rupees(200), 1, Change(Money.rupees(100), 50)),
        )
        assertThat(rows[1].change).isEqualTo(Change(Money.rupees(100), null))
        assertThat(rows[2]).isEqualTo(
            CategoryRow("car", Money.ZERO, 0, 0, Money.ZERO, Money.rupees(50), 1, Change(Money.rupees(-50), -100)),
        )
    }

    @Test
    fun `averages round half up to the paisa`() {
        assertThat(ReportRules.averageOf(Money.rupees(100), 3)).isEqualTo(Money.rupees(33, 33))
        assertThat(ReportRules.averageOf(Money.rupees(0, 5), 2)).isEqualTo(Money.rupees(0, 3))
        assertThat(ReportRules.averageOf(Money.rupees(100), 0)).isEqualTo(Money.ZERO)
    }

    @Test
    fun `the category matrix has one column per month and sorts rows by total`() {
        val months = ReportRules.bucketsOf(range(d(9, 1), d(10, 31)), Granularity.Month)
        val txs = listOf(
            tx(100, d(9, 5)),
            tx(50, d(10, 5)),
            tx(200, d(10, 6), categoryId = "fun"),
            tx(900, d(10, 7), type = TxType.Income, categoryId = "salary"),
        )
        val matrix = ReportRules.categoryMatrix(txs, CategoryType.Expense, months)
        assertThat(matrix.rows).containsExactly(
            MatrixRow("fun", listOf(Money.ZERO, Money.rupees(200)), Money.rupees(200), Money.rupees(100)),
            MatrixRow("food", listOf(Money.rupees(100), Money.rupees(50)), Money.rupees(150), Money.rupees(75)),
        ).inOrder()
        assertThat(matrix.columnTotals).containsExactly(Money.rupees(100), Money.rupees(250)).inOrder()
        assertThat(matrix.total).isEqualTo(Money.rupees(350))
    }

    // Accounts

    private val cashEntries = listOf(
        tx(100, d(10, 20)),
        tx(30, d(10, 22), type = TxType.Transfer, accountId = "cash", toAccountId = "bank"),
        tx(20, d(10, 23), type = TxType.Transfer, accountId = "bank", toAccountId = "cash"),
        tx(50, d(10, 25), type = TxType.Income, categoryId = "salary"),
        tx(500, d(11, 2)), // future-dated: already in the current balance (BR-18)
    )

    @Test
    fun `balances are worked back from the current balance over every later entry, future ones too`() {
        val cash = account("cash", Money.rupees(560))
        assertThat(ReportRules.balanceDelta(cashEntries[1], "cash")).isEqualTo(Money.rupees(-30))
        assertThat(ReportRules.balanceDelta(cashEntries[1], "bank")).isEqualTo(Money.rupees(30))
        assertThat(ReportRules.balanceDelta(cashEntries[0], "bank")).isEqualTo(Money.ZERO)
        assertThat(ReportRules.balanceAt(cash, cashEntries, d(10, 31))).isEqualTo(Money.rupees(1060))
        assertThat(ReportRules.balanceAt(cash, cashEntries, d(10, 15))).isEqualTo(Money.rupees(1120))
        assertThat(ReportRules.balanceAt(cash, cashEntries, d(12, 31))).isEqualTo(Money.rupees(560))
    }

    @Test
    fun `an account statement reads opening, in and out, closing and change over the period`() {
        val cash = account("cash", Money.rupees(560))
        val statement = ReportRules.accountStatements(listOf(cash), cashEntries, october).single()
        assertThat(statement).isEqualTo(
            AccountStatement(
                accountId = "cash",
                opening = Money.rupees(1120),
                income = Money.rupees(50),
                expense = Money.rupees(100),
                transfersIn = Money.rupees(20),
                transfersOut = Money.rupees(30),
                closing = Money.rupees(1060),
                change = Money.rupees(-60),
            ),
        )
    }

    @Test
    fun `net worth is assets minus liabilities at the end of each bucket`() {
        val cash = account("cash", Money.rupees(560))
        val card = account("card", Money.rupees(-200), AccountType.CreditCard)
        val series = ReportRules.netWorthSeries(listOf(cash, card), cashEntries, listOf(october))
        assertThat(series.opening).isEqualTo(Money.rupees(920))
        assertThat(series.points).containsExactly(
            NetWorthPoint(october, Money.rupees(1060), Money.rupees(200), Money.rupees(860), Money.rupees(-60)),
        )
        assertThat(ReportRules.netWorthSeries(emptyList(), emptyList(), emptyList())).isEqualTo(NetWorthSeries(Money.ZERO, emptyList()))
    }

    // Budgets

    @Test
    fun `budget periods run from the current one back, leaving out periods before the budget unless asked`() {
        val b = budget()
        val rows = ReportRules.budgetPeriods(b, emptyList(), today, 2, early = false, zone = ZoneOffset.UTC)
        assertThat(rows.map { it.offset }).containsExactly(0, -1).inOrder()
        assertThat(rows[0].current).isTrue()
        assertThat(rows[1].current).isFalse()
        val withEarly = ReportRules.budgetPeriods(b, emptyList(), today, 2, early = true, zone = ZoneOffset.UTC)
        assertThat(withEarly.map { it.offset }).containsExactly(0, -1, -2).inOrder()
        assertThat(withEarly[2].beforeBudget).isTrue()
        assertThat(withEarly[2].result.range).isEqualTo(range(d(8, 1), d(8, 31)))
    }

    @Test
    fun `budget stats count finished periods only`() {
        // Created mid-August: July ended before it and only shows for comparison.
        val b = budget(createdAt = "2026-08-15T00:00:00Z")
        val txs = listOf(tx(100, d(10, 2)), tx(600, d(9, 2)), tx(300, d(8, 2)), tx(50, d(7, 2)))
        val rows = ReportRules.budgetPeriods(b, txs, today, 3, early = true, zone = ZoneOffset.UTC)
        assertThat(rows.map { it.result.state }).containsExactly(BudgetState.OnTrack, BudgetState.Over, BudgetState.OnTrack, BudgetState.OnTrack).inOrder()
        val stats = ReportRules.budgetStats(rows)
        assertThat(stats).isEqualTo(
            BudgetStats(tracked = 2, under = 1, over = 1, hitRate = 50, averageSpent = Money.rupees(450), usedPercent = 90, overspent = Money.rupees(100)),
        )
        assertThat(ReportRules.budgetStats(rows.filter { it.current })).isEqualTo(BudgetStats(0, 0, 0, null, null, null, Money.ZERO))
    }
}
