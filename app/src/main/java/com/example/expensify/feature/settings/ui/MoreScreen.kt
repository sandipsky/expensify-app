package com.example.expensify.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.expensify.BuildConfig
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.recurring.ui.WaitingCount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MoreViewModel @Inject constructor(
    session: SessionRepository,
    waiting: WaitingCount,
    private val auth: AuthRepository,
) : ViewModel() {
    val session: StateFlow<Session> = session.session

    /** How many ask-first recurring entries wait, for the badge on Recurring (req REC-07). */
    val waitingCount: StateFlow<Int> = waiting.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

@Composable
fun MoreRoute(onOpenCategories: () -> Unit, onOpenRecurring: () -> Unit, viewModel: MoreViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val waiting by viewModel.waitingCount.collectAsStateWithLifecycle()
    val signedIn = session as? Session.SignedIn
    MoreScreen(
        name = signedIn?.name.orEmpty(),
        email = signedIn?.email.orEmpty(),
        waitingCount = waiting,
        onOpenCategories = onOpenCategories,
        onOpenRecurring = onOpenRecurring,
        onLogout = viewModel::logout,
    )
}

/** The rest of the sidebar: who is logged in, Recurring with its waiting count, Categories, the sections still to come, and Log out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    name: String,
    email: String,
    waitingCount: Int,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onLogout: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_more)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                initialsOf(name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                MoreItem(AppIcons.Reports, R.string.more_reports)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Recurring, R.string.more_recurring, badge = waitingCount, onClick = onOpenRecurring)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Categories, R.string.more_categories, onClick = onOpenCategories)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Settings, R.string.more_settings)
            }

            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Logout, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.more_log_out))
            }

            Text(
                stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A row to a section; without `onClick` it only says the section is still to come. A badge counts what waits there. */
@Composable
private fun MoreItem(icon: ImageVector, labelRes: Int, badge: Int = 0, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Badge(modifier = Modifier.padding(end = 8.dp)) { Text(badge.toString()) }
        }
        if (onClick == null) {
            Text(
                stringResource(R.string.coming_soon_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@PreviewLightDark
@Composable
private fun MorePreview() {
    ExpensifyTheme {
        MoreScreen(name = "Sita Rai", email = "sita@example.com", waitingCount = 2, onOpenCategories = {}, onOpenRecurring = {}, onLogout = {})
    }
}
