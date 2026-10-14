package com.example.expensify.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.ui.LoginRoute
import com.example.expensify.feature.budgets.alerts.BudgetAlertsObserver
import com.example.expensify.feature.recurring.runner.RecurringRunner
import com.example.expensify.feature.recurring.work.RecurringWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionRepository,
    messages: Messages,
    budgetAlerts: BudgetAlertsObserver,
    recurringRunner: RecurringRunner,
    scheduler: RecurringWorkScheduler,
    auth: AuthRepository,
    deviceState: DeviceState,
) : ViewModel() {
    val session: StateFlow<Session> = session.session
    val messages: SharedFlow<UiText> = messages.messages

    init {
        // The app-scoped observers (architecture §9) run while the shell is alive; each does nothing while logged out.
        viewModelScope.launch { budgetAlerts.run() }
        viewModelScope.launch { recurringRunner.run() }
        // The daily catch-up job runs while the app is closed; it is kept while someone is logged in.
        viewModelScope.launch {
            session.session.collect { if (it is Session.SignedIn) scheduler.schedule() else if (it is Session.SignedOut) scheduler.cancel() }
        }
        // On each login: pick up an email changed through its link, and repair a login without a profile (req §9.3).
        viewModelScope.launch {
            session.uid.collect { uid -> if (uid != null) runCatching { auth.syncLogin() } }
        }
        // The profile's theme is kept on the device so the first frame and the login screen already use it (req SET-02).
        viewModelScope.launch {
            auth.profile().mapNotNull { it?.preferences?.theme }.map { it.stored }.distinctUntilChanged().collect { deviceState.setTheme(it) }
        }
    }
}

/**
 * The root: a loading screen until Firebase has restored the session, then
 * the login screen or the signed-in shell. Switching on the session clears
 * every back stack, so nothing of one user's is left for the next.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it.asString(context)) }
    }

    when (session) {
        Session.Unknown -> LoadingScreen()
        // A note raised as the session ends ("Your account was deleted") still shows over the login screen.
        Session.SignedOut -> Box(Modifier.fillMaxSize()) {
            LoginRoute()
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
        }
        is Session.SignedIn -> MainNavHost(snackbarHost)
    }
}
