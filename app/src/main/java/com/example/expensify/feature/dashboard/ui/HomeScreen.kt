package com.example.expensify.feature.dashboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class HomeViewModel @Inject constructor(session: SessionRepository) : ViewModel() {
    val session: StateFlow<Session> = session.session
}

@Composable
fun HomeRoute(onSetUpAccounts: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val name = (session as? Session.SignedIn)?.name?.substringBefore(' ').orEmpty()
    HomeScreen(name = name, onSetUpAccounts = onSetUpAccounts)
}

/** The dashboard's shell: a greeting and, until the cards exist, the way to get started. */
@Composable
fun HomeScreen(name: String, onSetUpAccounts: () -> Unit) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> R.string.home_greeting_morning
        hour < 17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Text(stringResource(greeting, name).trimEnd(',', ' '), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            AppCard {
                EmptyState(
                    icon = AppIcons.Home,
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    action = { Button(onClick = onSetUpAccounts) { Text(stringResource(R.string.home_empty_action)) } },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomePreview() {
    ExpensifyTheme { HomeScreen(name = "Sita", onSetUpAccounts = {}) }
}
