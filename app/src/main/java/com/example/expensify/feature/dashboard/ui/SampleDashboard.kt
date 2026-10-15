package com.example.expensify.feature.dashboard.ui

import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.budgets.ui.SampleBudgets
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.dashboard.domain.Change
import com.example.expensify.feature.dashboard.domain.DashboardPreset
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.dashboard.domain.PeriodSummary
import com.example.expensify.feature.dashboard.domain.SummaryChange
import com.example.expensify.feature.recurring.ui.SampleRules
import com.example.expensify.feature.transactions.ui.SampleTransactions
import com.example.expensify.feature.transactions.ui.TxRows

/** Realistic fixtures for previews, built with the same functions the ViewModel uses. */
object SampleDashboard {
    val today = SampleTransactions.today
    private val range = Periods.month(today)
    private val lookups = SampleTransactions.lookups

    val period = DashboardPeriodUi(DashboardPreset.ThisMonth, range, range, "October 2026")
    val header = HomeHeaderUi(name = "Sita", period = period, masked = false, unread = 2)

    private val summary = DashboardRules.summaryOf(range, SampleTransactions.all)
    private val change = SummaryChange(
        income = Change(Money.rupees(5_000), 6),
        expense = Change(Money.rupees(-1_200), -6),
        net = Change(Money.rupees(6_200), 10),
        savingsRate = 3,
    )

    private val trend: List<PeriodSummary> = DashboardRules.trendPeriods(DashboardPreset.ThisMonth, range, today).mapIndexed { i, r ->
        val income = Money.rupees(80_000L + i * 1_000)
        val expense = Money.rupees(52_000L + (i % 3) * 6_000)
        PeriodSummary(r, income, expense, income - expense, DashboardRules.savingsRate(income, income - expense))
    }

    val content = HomeUiState.Content(
        header = header,
        today = today,
        empty = false,
        summary = SummaryUi(summary, change),
        balances = buildBalances(SampleAccounts.active + SampleAccounts.archived),
        spending = buildSpending(SampleTransactions.all, SampleCategories.all.associateBy { it.id }),
        budgets = BudgetsUi(
            rows = SampleBudgets.cards.map { BudgetRowUi(it.budget, it.icon, it.progress) },
            more = false,
            anyBudgets = true,
        ),
        trend = trend,
        trendEmpty = false,
        recent = SampleTransactions.all.take(DashboardRules.RECENT_COUNT).map { TxRows.toRow(it, lookups) },
        recentTxs = SampleTransactions.all.associateBy { it.id },
        upcoming = listOf(UpcomingRowUi("r2_2026-10-14", SampleRules.rentView, today, due = true)),
    )

    val emptyPeriod = content.copy(
        header = header.copy(period = DashboardPeriodUi(DashboardPreset.LastMonth, Periods.month(today, -1), Periods.month(today, -1), "September 2026")),
        empty = true,
        upcoming = emptyList(),
    )

    val loading = HomeUiState.Loading(header.copy(masked = true, unread = 0))
}
