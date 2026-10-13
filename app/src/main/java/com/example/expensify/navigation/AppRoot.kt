package com.example.expensify.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.ui.LoginRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionRepository,
    messages: Messages,
) : ViewModel() {
    val session: StateFlow<Session> = session.session
    val messages: SharedFlow<UiText> = messages.messages
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
        Session.SignedOut -> LoginRoute()
        is Session.SignedIn -> MainNavHost(snackbarHost)
    }
}
