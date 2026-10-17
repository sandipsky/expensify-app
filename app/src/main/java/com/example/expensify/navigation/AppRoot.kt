package com.example.expensify.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.example.expensify.R
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.ui.AppMessage
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.lock.AppLock
import com.example.expensify.feature.auth.lock.LockEnabled
import com.example.expensify.feature.auth.lock.LockRoute
import com.example.expensify.feature.budgets.alerts.BudgetAlertsObserver
import com.example.expensify.feature.notifications.AlertNavigator
import com.example.expensify.feature.notifications.data.AlertStore
import com.example.expensify.feature.notifications.domain.AlertTarget
import com.example.expensify.feature.notifications.reminders.DailyReminderObserver
import com.example.expensify.feature.notifications.reminders.RecurringRemindersObserver
import com.example.expensify.feature.notifications.work.ReminderScheduler
import com.example.expensify.feature.recurring.runner.RecurringRunner
import com.example.expensify.feature.recurring.work.RecurringWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    dailyReminders: DailyReminderObserver,
    recurringReminders: RecurringRemindersObserver,
    scheduler: RecurringWorkScheduler,
    reminders: ReminderScheduler,
    alerts: AlertStore,
    alertNavigator: AlertNavigator,
    auth: AuthRepository,
    deviceState: DeviceState,
    appLock: AppLock,
) : ViewModel() {
    val session: StateFlow<Session> = session.session
    val messages: SharedFlow<AppMessage> = messages.messages

    /** Where an alert asks the shell to go: a tapped notification, the snackbar's View, a row in the list. */
    val alertRequests: Flow<AlertTarget> = alertNavigator.requests

    /** The app lock (Settings › Security): whether it is on for the login shown, and whether it has engaged. */
    val lockEnabled: StateFlow<LockEnabled?> = appLock.enabled
    val lockGeneration: StateFlow<Int> = appLock.generation

    init {
        // The app-scoped observers (architecture §9) run while the shell is alive; each does nothing while logged out.
        viewModelScope.launch { budgetAlerts.run() }
        viewModelScope.launch { recurringRunner.run() }
        viewModelScope.launch { dailyReminders.run() }
        viewModelScope.launch { recurringReminders.run() }
        viewModelScope.launch {
            var wasSignedIn = false
            session.session.collect { current ->
                when (current) {
                    is Session.SignedIn -> {
                        wasSignedIn = true
                        // This phone's alerts and reminders belong to one user at a time (req NTF-07).
                        alerts.claim(current.uid)
                        // The daily catch-up job runs while the app is closed; it is kept while someone is logged in.
                        scheduler.schedule()
                    }
                    Session.SignedOut -> {
                        scheduler.cancel()
                        reminders.cancel()
                        // Ended without Log out or Delete account: the login was deleted, or its email or
                        // password changed elsewhere (req AUTH-09). Say so over the login screen.
                        if (wasSignedIn && !session.consumeExpectedEnd()) messages.show(res(R.string.login_session_ended))
                        wasSignedIn = false
                    }
                    Session.Unknown -> Unit
                }
            }
        }
        // On each login: pick up an email changed through its link, and repair a login without a profile (req §9.3).
        viewModelScope.launch {
            session.uid.collect { uid -> if (uid != null) runCatching { auth.syncLogin() } }
        }
        // The profile's theme is kept on the device so the first frame and the login screen already use it (req SET-02).
        viewModelScope.launch {
            auth.profile().mapNotNull { it?.preferences?.theme }.map { it.stored }.distinctUntilChanged().collect { deviceState.setTheme(it) }
        }
        // The reminder jobs follow the notification settings (req NTF-04) while someone is logged in.
        viewModelScope.launch {
            combine(session.uid, auth.profile().map { it?.preferences?.notifications }) { uid, prefs -> if (uid == null) null else prefs }
                .distinctUntilChanged()
                .collect { prefs -> if (prefs != null) reminders.sync(prefs) }
        }
    }
}

/**
 * The root: a loading screen until Firebase has restored the session, then
 * the login and signup screens or the signed-in shell. Switching on the
 * session clears every back stack, so nothing of one user's is left for the
 * next.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHost.showSnackbar(
                message = message.text.asString(context),
                actionLabel = message.action?.label?.asString(context),
                duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.action?.onClick?.invoke()
        }
    }

    when (val current = session) {
        Session.Unknown -> LoadingScreen()
        // A note raised as the session ends ("Your account was deleted", "Your session ended") still shows over the login screen.
        Session.SignedOut -> Box(Modifier.fillMaxSize()) {
            AuthNavHost()
            SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
        }
        is Session.SignedIn -> SignedInShell(current, viewModel, snackbarHost)
    }
}

/**
 * The shell, behind the lock screen while the app lock has engaged. A
 * loading screen until this phone has said whether the lock is on for this
 * login, so the shell never shows for a frame before the lock does. The
 * shell stays composed under the lock, so unlocking returns to the same page.
 */
@Composable
private fun SignedInShell(session: Session.SignedIn, viewModel: AppViewModel, snackbarHost: SnackbarHostState) {
    val lock by viewModel.lockEnabled.collectAsStateWithLifecycle()
    val generation by viewModel.lockGeneration.collectAsStateWithLifecycle()
    val known = lock
    if (known == null || known.uid != session.uid) {
        LoadingScreen()
    } else {
        MainNavHost(snackbarHost, viewModel.alertRequests)
        if (known.on && generation > 0) LockRoute()
    }
}
