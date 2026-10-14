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
import com.example.expensify.designsystem.components.ComingSoonScreen
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
import com.example.expensify.feature.settings.ui.MoreRoute
import kotlin.reflect.KClass

private class TopLevel(val route: Any, val routeClass: KClass<*>, val labelRes: Int, val icon: ImageVector)

/** The bottom bar: the web's sidebar, phone first (CLAUDE.md, Information architecture). */
private val TOP_LEVEL = listOf(
    TopLevel(Routes.Home, Routes.Home::class, R.string.nav_home, AppIcons.Home),
    TopLevel(Routes.Transactions, Routes.Transactions::class, R.string.nav_transactions, AppIcons.Transactions),
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

/**
 * The signed-in shell. The outer Scaffold owns the bottom bar, the snackbar
 * host and the bottom insets; each screen owns its top bar and the top
 * insets, so nothing is padded twice.
 */
@Composable
fun MainNavHost(snackbarHost: SnackbarHostState) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBar = TOP_LEVEL.any { destination.isTopLevel(it) }

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
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable<Routes.Home> {
                HomeRoute(onSetUpAccounts = { navController.navigateTopLevel(Routes.Accounts) })
            }
            composable<Routes.Transactions> {
                ComingSoonScreen(stringResource(R.string.nav_transactions), AppIcons.Transactions)
            }
            composable<Routes.Accounts> {
                AccountsRoute(
                    onOpenAccount = { navController.navigate(Routes.AccountDetail(it)) },
                    onAddAccount = { navController.navigate(Routes.AccountForm()) },
                    onEditAccount = { navController.navigate(Routes.AccountForm(it)) },
                )
            }
            composable<Routes.Budgets> {
                BudgetsRoute(
                    onOpenBudget = { navController.navigate(Routes.BudgetDetail(it)) },
                    onAddBudget = { navController.navigate(Routes.BudgetForm()) },
                    onEditBudget = { navController.navigate(Routes.BudgetForm(it)) },
                )
            }
            composable<Routes.More> {
                MoreRoute(onOpenCategories = { navController.navigate(Routes.Categories) })
            }
            composable<Routes.AccountDetail> {
                AccountDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.AccountForm(it)) },
                )
            }
            composable<Routes.AccountForm> {
                AccountFormRoute(onDone = { navController.popBackStack() })
            }
            composable<Routes.Categories> {
                CategoriesRoute(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.CategoryForm(type = it.stored)) },
                    onEdit = { navController.navigate(Routes.CategoryForm(type = it.type.stored, id = it.id)) },
                )
            }
            composable<Routes.CategoryForm> {
                CategoryFormRoute(onDone = { navController.popBackStack() })
            }
            composable<Routes.BudgetDetail> {
                BudgetDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.BudgetForm(it)) },
                )
            }
            composable<Routes.BudgetForm> {
                BudgetFormRoute(onDone = { navController.popBackStack() })
            }
        }
    }
}
