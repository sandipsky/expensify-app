package com.example.expensify.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensify.R
import com.example.expensify.core.date.ymd
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.feature.accounts.ui.AccountDetailRoute
import com.example.expensify.feature.accounts.ui.AccountFormRoute
import com.example.expensify.feature.accounts.ui.AccountsRoute
import com.example.expensify.feature.budgets.ui.BudgetDetailRoute
import com.example.expensify.feature.budgets.ui.BudgetFormRoute
import com.example.expensify.feature.budgets.ui.BudgetsRoute
import com.example.expensify.feature.categories.ui.CategoriesRoute
import com.example.expensify.feature.categories.ui.CategoryFormRoute
import com.example.expensify.feature.dashboard.ui.HomeRoute
import com.example.expensify.feature.notifications.domain.AlertTarget
import com.example.expensify.feature.notifications.ui.AlertsRoute
import com.example.expensify.feature.recurring.ui.RecurringRoute
import com.example.expensify.feature.recurring.ui.RuleDetailRoute
import com.example.expensify.feature.recurring.ui.RuleFormRoute
import com.example.expensify.feature.reports.domain.ReportKey
import com.example.expensify.feature.reports.ui.AccountsReportRoute
import com.example.expensify.feature.reports.ui.BudgetReportRoute
import com.example.expensify.feature.reports.ui.CashFlowRoute
import com.example.expensify.feature.reports.ui.CategoryReportRoute
import com.example.expensify.feature.reports.ui.CategoryTrendsRoute
import com.example.expensify.feature.reports.ui.DrillDown
import com.example.expensify.feature.reports.ui.IdsParam
import com.example.expensify.feature.reports.ui.ReportsScreen
import com.example.expensify.feature.auth.lock.AppLockPinRoute
import com.example.expensify.feature.settings.ui.ChangePasswordRoute
import com.example.expensify.feature.settings.ui.DeleteAccountRoute
import com.example.expensify.feature.settings.ui.MoreRoute
import com.example.expensify.feature.settings.ui.ProfileFormRoute
import com.example.expensify.feature.settings.ui.SettingsRoute
import com.example.expensify.feature.transactions.ui.EntryFormRoute
import com.example.expensify.feature.transactions.ui.TransactionDetailRoute
import com.example.expensify.feature.transactions.ui.TransactionsRoute
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

private class TopLevel(val route: Any, val routeClass: KClass<*>, val labelRes: Int, val icon: ImageVector)

/** The bottom bar: the web's sidebar, phone first (CLAUDE.md, Information architecture). */
private val TOP_LEVEL = listOf(
    TopLevel(Routes.Home(), Routes.Home::class, R.string.nav_home, AppIcons.Home),
    TopLevel(Routes.Transactions(), Routes.Transactions::class, R.string.nav_transactions, AppIcons.Transactions),
    TopLevel(Routes.Accounts, Routes.Accounts::class, R.string.nav_accounts, AppIcons.Accounts),
    TopLevel(Routes.Budgets, Routes.Budgets::class, R.string.nav_budgets, AppIcons.Budgets),
    TopLevel(Routes.More, Routes.More::class, R.string.nav_more, AppIcons.More),
)

private fun NavDestination?.isTopLevel(item: TopLevel) = this?.hasRoute(item.routeClass) == true

private fun NavController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** The list on the entries behind a figure (req DSH-04, RPT-02), the way the web links to `/transactions?…`. */
private fun DrillDown.toRoute() = Routes.Transactions(
    from = range.start.ymd(),
    to = range.end.ymd(),
    type = type?.stored,
    category = IdsParam.of(categoryIds),
    account = IdsParam.of(accountIds),
)

/**
 * The signed-in shell. The outer Scaffold owns the bottom bar, the snackbar
 * host and the bottom insets; each screen owns its top bar and the top
 * insets, so nothing is padded twice. `alertRequests` carries where an
 * alert asks to go: a tapped notification, a snackbar's View, a row in the
 * alerts list (req NTF-05).
 */
@Composable
fun MainNavHost(snackbarHost: SnackbarHostState, alertRequests: Flow<AlertTarget>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBar = TOP_LEVEL.any { destination.isTopLevel(it) }

    val openEntry = { id: String -> navController.navigate(Routes.TransactionDetail(id)) }
    val addEntry = { navController.navigate(Routes.EntryForm()) }
    val editEntry = { id: String -> navController.navigate(Routes.EntryForm(editId = id)) }
    val duplicateEntry = { id: String -> navController.navigate(Routes.EntryForm(duplicateOf = id)) }
    val makeRecurring = { id: String -> navController.navigate(Routes.RuleForm(fromTransaction = id)) }
    val openRule = { id: String -> navController.navigate(Routes.RuleDetail(id)) }
    val editRule = { id: String -> navController.navigate(Routes.RuleForm(id = id)) }
    val confirmEdited = { ruleId: String, date: String -> navController.navigate(Routes.EntryForm(ruleId = ruleId, dueDate = date)) }
    val openTransactions = { drill: DrillDown -> navController.navigate(drill.toRoute()) }
    val openBudget = { id: String -> navController.navigate(Routes.BudgetDetail(id)) }
    val openAccount = { id: String -> navController.navigate(Routes.AccountDetail(id)) }
    val back = { navController.popBackStack(); Unit }

    LaunchedEffect(navController) {
        alertRequests.collect { target ->
            when (target) {
                is AlertTarget.Budget -> openBudget(target.id)
                AlertTarget.Recurring -> navController.navigate(Routes.Recurring)
                AlertTarget.Transactions -> navController.navigateTopLevel(Routes.Transactions())
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TOP_LEVEL.forEach { item ->
                        NavigationBarItem(
                            selected = destination.isTopLevel(item),
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home(),
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable<Routes.Home> {
                HomeRoute(
                    onOpenTransactions = openTransactions,
                    onOpenEntry = openEntry,
                    onEditEntry = editEntry,
                    onAddEntry = addEntry,
                    onOpenAccounts = { navController.navigateTopLevel(Routes.Accounts) },
                    onOpenAccount = openAccount,
                    onAddAccount = { navController.navigate(Routes.AccountForm()) },
                    onOpenBudgets = { navController.navigateTopLevel(Routes.Budgets) },
                    onOpenBudget = openBudget,
                    onAddBudget = { navController.navigate(Routes.BudgetForm()) },
                    onOpenRecurring = { navController.navigate(Routes.Recurring) },
                    onOpenRule = openRule,
                    onOpenAlerts = { navController.navigate(Routes.Alerts) },
                )
            }
            composable<Routes.Alerts> {
                AlertsRoute(
                    onBack = back,
                    onOpen = { target ->
                        when (target) {
                            is AlertTarget.Budget -> openBudget(target.id)
                            AlertTarget.Recurring -> navController.navigate(Routes.Recurring)
                            AlertTarget.Transactions -> navController.navigateTopLevel(Routes.Transactions())
                        }
                    },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable<Routes.Transactions> {
                TransactionsRoute(onOpenEntry = openEntry, onAddEntry = addEntry)
            }
            composable<Routes.TransactionDetail> {
                TransactionDetailRoute(
                    onBack = back,
                    onEdit = editEntry,
                    onDuplicate = duplicateEntry,
                    onMakeRecurring = makeRecurring,
                    onOpenRule = openRule,
                )
            }
            composable<Routes.EntryForm> {
                EntryFormRoute(
                    onDone = back,
                    onDuplicate = { id ->
                        navController.navigate(Routes.EntryForm(duplicateOf = id)) { popUpTo<Routes.EntryForm> { inclusive = true } }
                    },
                    onMakeRecurring = { id ->
                        navController.navigate(Routes.RuleForm(fromTransaction = id)) { popUpTo<Routes.EntryForm> { inclusive = true } }
                    },
                    onOpenRule = { id ->
                        navController.navigate(Routes.RuleDetail(id)) { popUpTo<Routes.EntryForm> { inclusive = true } }
                    },
                )
            }
            composable<Routes.Accounts> {
                AccountsRoute(
                    onOpenAccount = openAccount,
                    onAddAccount = { navController.navigate(Routes.AccountForm()) },
                    onEditAccount = { navController.navigate(Routes.AccountForm(it)) },
                )
            }
            composable<Routes.Budgets> {
                BudgetsRoute(
                    onOpenBudget = openBudget,
                    onAddBudget = { navController.navigate(Routes.BudgetForm()) },
                    onEditBudget = { navController.navigate(Routes.BudgetForm(it)) },
                )
            }
            composable<Routes.More> {
                MoreRoute(
                    onOpenReports = { navController.navigate(Routes.Reports) },
                    onOpenRecurring = { navController.navigate(Routes.Recurring) },
                    onOpenCategories = { navController.navigate(Routes.Categories) },
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                )
            }
            composable<Routes.AccountDetail> {
                AccountDetailRoute(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.AccountForm(it)) },
                    onAddEntry = { navController.navigate(Routes.EntryForm(accountId = it)) },
                    onOpenEntry = openEntry,
                )
            }
            composable<Routes.AccountForm> {
                AccountFormRoute(onDone = back)
            }
            composable<Routes.Categories> {
                CategoriesRoute(
                    onBack = back,
                    onAdd = { navController.navigate(Routes.CategoryForm(type = it.stored)) },
                    onEdit = { navController.navigate(Routes.CategoryForm(type = it.type.stored, id = it.id)) },
                )
            }
            composable<Routes.CategoryForm> {
                CategoryFormRoute(onDone = back)
            }
            composable<Routes.BudgetDetail> {
                BudgetDetailRoute(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.BudgetForm(it)) },
                    onOpenEntry = openEntry,
                )
            }
            composable<Routes.BudgetForm> {
                BudgetFormRoute(onDone = back)
            }
            composable<Routes.Recurring> {
                RecurringRoute(
                    onBack = back,
                    onOpenRule = openRule,
                    onAddRule = { navController.navigate(Routes.RuleForm()) },
                    onEditRule = editRule,
                    onConfirmEdited = confirmEdited,
                )
            }
            composable<Routes.RuleDetail> {
                RuleDetailRoute(
                    onBack = back,
                    onEditRule = editRule,
                    onOpenEntry = openEntry,
                    onConfirmEdited = confirmEdited,
                )
            }
            composable<Routes.RuleForm> {
                RuleFormRoute(onDone = back)
            }

            // Reports (req RPT-01…RPT-08)
            composable<Routes.Reports> {
                ReportsScreen(
                    onBack = back,
                    onOpen = { key ->
                        navController.navigate(
                            when (key) {
                                ReportKey.CashFlow -> Routes.CashFlowReport()
                                ReportKey.Categories -> Routes.CategoryReport()
                                ReportKey.CategoryTrends -> Routes.CategoryTrendsReport()
                                ReportKey.Accounts -> Routes.AccountsReport()
                                ReportKey.Budgets -> Routes.BudgetReport()
                            },
                        )
                    },
                )
            }
            composable<Routes.CashFlowReport> {
                CashFlowRoute(onBack = back, onOpenTransactions = openTransactions)
            }
            composable<Routes.CategoryReport> {
                CategoryReportRoute(onBack = back, onOpenTransactions = openTransactions)
            }
            composable<Routes.CategoryTrendsReport> {
                CategoryTrendsRoute(onBack = back, onOpenTransactions = openTransactions)
            }
            composable<Routes.AccountsReport> {
                AccountsReportRoute(onBack = back, onOpenAccount = openAccount)
            }
            composable<Routes.BudgetReport> {
                BudgetReportRoute(
                    onBack = back,
                    onOpenBudget = openBudget,
                    onGoToBudgets = { navController.navigateTopLevel(Routes.Budgets) },
                    onOpenTransactions = openTransactions,
                )
            }

            // Settings (req SET-01…SET-05)
            composable<Routes.Settings> {
                SettingsRoute(
                    onBack = back,
                    onEditProfile = { navController.navigate(Routes.ProfileForm) },
                    onChangePassword = { navController.navigate(Routes.ChangePassword) },
                    onAppLockPin = { navController.navigate(Routes.AppLockPin(it.stored)) },
                    onDeleteAccount = { navController.navigate(Routes.DeleteAccount) },
                )
            }
            composable<Routes.ProfileForm> {
                ProfileFormRoute(onDone = back)
            }
            composable<Routes.ChangePassword> {
                ChangePasswordRoute(onDone = back)
            }
            composable<Routes.AppLockPin> {
                AppLockPinRoute(onDone = back)
            }
            composable<Routes.DeleteAccount> {
                DeleteAccountRoute(onClose = back)
            }
        }
    }
}
