package com.example.expensify.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.expensify.feature.auth.ui.LoginRoute
import com.example.expensify.feature.auth.ui.SignupRoute

/**
 * The signed-out half of the app: Log in, and Sign up pushed over it. It has
 * its own back stack, dropped with it when the session starts, so nothing of
 * the login screen's is left behind once someone is in.
 */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Login,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable<Routes.Login> {
            LoginRoute(onSignup = { navController.navigate(Routes.Signup) { launchSingleTop = true } })
        }
        composable<Routes.Signup> {
            SignupRoute(onLogin = { if (!navController.popBackStack()) navController.navigate(Routes.Login) })
        }
    }
}
