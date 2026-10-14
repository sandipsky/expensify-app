package com.example.expensify.feature.reports.ui

import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.budgets.ui.BudgetLabels
import com.example.expensify.feature.budgets.ui.SampleBudgets
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.dashboard.domain.DashboardRules
import com.example.expensify.feature.reports.domain.AccountStatement
import com.example.expensify.feature.reports.domain.CategoryMatrix
import com.example.expensify.feature.reports.domain.CategoryRow
import com.example.expensify.feature.reports.domain.FlowRow
import com.example.expensify.feature.reports.domain.Granularity
import com.example.expensify.feature.reports.domain.MatrixRow
import com.example.expensify.feature.reports.domain.NetWorthPoint
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.reports.domain.ReportRules
import com.example.expensify.feature.transactions.ui.SampleTransactions

/** Realistic report states for previews. */
object SampleReports {
    private val today = SampleTransactions.today
    private val allAccounts = SampleAccounts.active + SampleAccounts.archived
    private val categoriesById = SampleCategories.all.associateBy { it.id }
    private val accountItems = allAccounts.map { PickItem(it.id, plain(it.name), it.archived) }

    private fun period(preset: ReportPreset) = ReportRules.periodState(null, null, null, preset, today)

    private fun rupees(amount: Long) = Money.rupees(amount)

    val cashFlow: CashFlowUiState.Content = run {
        val state = period(ReportPreset.Last6Months)
        val buckets = ReportRules.bucketsOf(state.range, Granularity.Month)
        val incomes = listOf(85_000L, 85_000L, 92_000L, 85_000L, 85_000L, 85_000L)
        val expenses = listOf(61_200L, 70_450L, 58_900L, 66_300L, 90_100L, 31_480L)
        var cumulative = Money.ZERO
        val rows = buckets.mapIndexed { i, range ->
            val income = rupees(incomes[i])
            val expense = rupees(expenses[i])
            cumulative += income - expense
            FlowRow(range, income, expense, income - expense, DashboardRules.savingsRate(income, income - expense), 20 + i, cumulative)
        }
        val income = rupees(incomes.sum())
        val expense = rupees(expenses.sum())
        CashFlowUiState.Content(
            filters = CashFlowFilters(state, Granularity.Month, emptyList(), accountItems, plain("All accounts"), filtered = false),
            rows = rows,
            income = income,
            expense = expense,
            net = income - expense,
            count = rows.sumOf { it.count },
            savingsRate = DashboardRules.savingsRate(income, income - expense),
            positive = rows.count { it.net.isPositive },
            multiYear = false,
        )
    }

    val categories: CategoryReportUiState.Content = run {
        val state = period(ReportPreset.ThisMonth)
        val food = SampleCategories.food
        val transport = SampleCategories.transport
        val rent = SampleCategories.all.first { it.name == "Housing and rent" }
        val shopping = SampleCategories.all.first { it.name == "Shopping" }
        fun row(id: String?, amount: Long, count: Int, previous: Long, previousCount: Int, total: Long) = CategoryRow(
            categoryId = id,
            amount = rupees(amount),
            count = count,
            share = DashboardRules.shareOf(rupees(amount), rupees(total)),
            average = ReportRules.averageOf(rupees(amount), count),
            previous = rupees(previous),
            previousCount = previousCount,
            change = DashboardRules.changeOf(rupees(previous), rupees(amount)),
        )
        val total = 18_000L + 9_450L + 2_600L + 1_430L
        val rows = listOf(
            row(rent.id, 18_000, 1, 18_000, 1, total),
            row(food.id, 9_450, 23, 7_900, 19, total),
            row(transport.id, 2_600, 14, 3_100, 16, total),
            row(null, 1_430, 2, 0, 0, total),
            row(shopping.id, 0, 0, 4_200, 3, total),
        )
        val before = ReportRules.previousRange(state.range)
        val totalBefore = rupees(18_000 + 7_900 + 3_100 + 4_200)
        CategoryReportUiState.Content(
            filters = CategoryFilters(state, CategoryType.Expense, emptyList(), accountItems, plain("All accounts"), filtered = false),
            rows = rows,
            before = before,
            total = rupees(total),
            totalBefore = totalBefore,
            count = rows.sumOf { it.count },
            countBefore = rows.sumOf { it.previousCount },
            totalChange = DashboardRules.changeOf(totalBefore, rupees(total)),
            movers = rows.filter { !it.change.amount.isZero }.sortedByDescending { kotlin.math.abs(it.change.amount.paisa) }.take(TOP_MOVERS),
            moverMax = rupees(18_000),
            views = rows.associate { it.categoryId to ReportLabels.categoryView(categoriesById, it.categoryId) },
        )
    }

    val trends: CategoryTrendsUiState.Content = run {
        val state = period(ReportPreset.Last6Months)
        val months = ReportRules.bucketsOf(state.range, Granularity.Month)
        fun row(id: String, cells: List<Long>) = MatrixRow(
            categoryId = id,
            cells = cells.map(::rupees),
            total = rupees(cells.sum()),
            average = ReportRules.averageOf(rupees(cells.sum()), cells.size),
        )
        val rows = listOf(
            row(SampleCategories.all.first { it.name == "Housing and rent" }.id, listOf(18_000, 18_000, 18_000, 18_000, 18_000, 18_000)),
            row(SampleCategories.food.id, listOf(7_200, 8_100, 6_900, 7_900, 9_450, 4_100)),
            row(SampleCategories.transport.id, listOf(2_400, 2_900, 3_300, 3_100, 2_600, 1_200)),
        )
        val columnTotals = months.indices.map { i -> rows.fold(Money.ZERO) { acc, r -> acc + r.cells[i] } }
        val matrix = CategoryMatrix(rows, columnTotals, columnTotals.fold(Money.ZERO) { a, b -> a + b })
        CategoryTrendsUiState.Content(
            filters = TrendsFilters(
                period = state, range = state.range, clipped = false, type = CategoryType.Expense,
                accountIds = emptyList(), accountItems = accountItems, categoryIds = emptyList(),
                categoryItems = SampleCategories.all.filter { it.type == CategoryType.Expense }.map { PickItem(it.id, plain(it.name), it.archived) },
                accountsText = plain("All accounts"), categoriesText = plain("All categories"), filtered = false,
            ),
            months = months,
            matrix = matrix,
            average = ReportRules.averageOf(matrix.total, months.size),
            busiest = 4,
            multiYear = ReportRules.spansYears(months),
            views = rows.associate { it.categoryId to ReportLabels.categoryView(categoriesById, it.categoryId) },
        )
    }

    val accounts: AccountsReportUiState.Content = run {
        val state = period(ReportPreset.Last6Months)
        val buckets = ReportRules.bucketsOf(state.range, Granularity.Month)
        val chosen = SampleAccounts.active.filter { it.includeInTotal }
        val statements = chosen.mapIndexed { i, account ->
            val opening = account.currentBalance - rupees(12_000L * (i + 1))
            StatementRow(
                AccountStatement(
                    accountId = account.id,
                    opening = opening,
                    income = rupees(if (i == 0) 0 else 85_000),
                    expense = rupees(20_000L + 5_000L * i),
                    transfersIn = rupees(if (i == 0) 30_000 else 0),
                    transfersOut = rupees(if (i == 0) 0 else 30_000),
                    closing = account.currentBalance,
                    change = account.currentBalance - opening,
                ),
                account,
            )
        }
        var worth = rupees(2_40_000)
        val points = buckets.mapIndexed { i, range ->
            val before = worth
            worth += rupees(8_000L + 3_000L * (i % 3))
            NetWorthPoint(range, worth + rupees(45_000), rupees(45_000), worth, worth - before)
        }
        AccountsReportUiState.Content(
            filters = AccountsFilters(state, Granularity.Month, emptyList(), accountItems, plain("Accounts in your total"), filtered = false),
            statements = statements,
            points = points,
            opening = rupees(2_40_000),
            closing = points.last().netWorth,
            change = points.last().netWorth - rupees(2_40_000),
            totals = StatementTotals(
                opening = statements.fold(Money.ZERO) { a, s -> a + s.statement.opening },
                income = statements.fold(Money.ZERO) { a, s -> a + s.statement.income },
                expense = statements.fold(Money.ZERO) { a, s -> a + s.statement.expense },
                transfersIn = statements.fold(Money.ZERO) { a, s -> a + s.statement.transfersIn },
                transfersOut = statements.fold(Money.ZERO) { a, s -> a + s.statement.transfersOut },
                closing = statements.fold(Money.ZERO) { a, s -> a + s.statement.closing },
                change = statements.fold(Money.ZERO) { a, s -> a + s.statement.change },
            ),
            multiYear = ReportRules.spansYears(buckets),
            hasAnyAccount = true,
        )
    }

    val budgets: BudgetReportUiState.Content = run {
        val all = listOf(SampleBudgets.everything, SampleBudgets.food, SampleBudgets.weekend)
        val summaries = all.filter { it.active }.map { budget ->
            val rows = ReportRules.budgetPeriods(budget, emptyList(), today, 6, early = false)
            BudgetSummaryUi(
                budget = budget,
                rows = rows,
                stats = ReportRules.budgetStats(rows),
                current = rows.firstOrNull { it.current },
                scope = BudgetLabels.scopeOf(budget, categoriesById),
                icon = BudgetLabels.iconOf(budget, categoriesById),
            )
        }
        BudgetReportUiState.Content(
            filters = BudgetReportFilters(
                budgetId = null, lookback = 12, early = false, showPaused = false,
                budgetItems = listOf(BudgetPick(null, plain(""))) + all.map { BudgetPick(it.id, plain(it.name), !it.active) },
                filtered = false,
            ),
            summaries = summaries,
            selected = null,
            tracked = summaries.sumOf { it.stats.tracked },
            under = summaries.sumOf { it.stats.under },
            over = summaries.sumOf { it.stats.over },
            overspent = Money.ZERO,
            hitRate = null,
            allCount = all.size,
        )
    }
}
