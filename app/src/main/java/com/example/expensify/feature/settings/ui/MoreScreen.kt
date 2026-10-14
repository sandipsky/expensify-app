package com.example.expensify.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Who is logged in, as the account card shows: the profile's name, email and photo, with the login as a fallback. */
data class MoreAccountUi(val name: String, val email: String, val photoUrl: String?)

@HiltViewModel
class MoreViewModel @Inject constructor(
    session: SessionRepository,
    waiting: WaitingCount,
    private val auth: AuthRepository,
) : ViewModel() {
    val account: StateFlow<MoreAccountUi> = combine(session.session, auth.profile()) { session, profile ->
        val signedIn = session as? Session.SignedIn
        MoreAccountUi(
            name = profile?.name?.takeIf { it.isNotBlank() } ?: signedIn?.name.orEmpty(),
            email = profile?.email?.takeIf { it.isNotBlank() } ?: signedIn?.email.orEmpty(),
            photoUrl = profile?.photoUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreAccountUi("", "", null))

    /** How many ask-first recurring entries wait, for the badge on Recurring (req REC-07). */
    val waitingCount: StateFlow<Int> = waiting.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

@Composable
fun MoreRoute(
    onOpenReports: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val waiting by viewModel.waitingCount.collectAsStateWithLifecycle()
    MoreScreen(
        account = account,
        waitingCount = waiting,
        onOpenReports = onOpenReports,
        onOpenRecurring = onOpenRecurring,
        onOpenCategories = onOpenCategories,
        onOpenSettings = onOpenSettings,
        onLogout = viewModel::logout,
    )
}

/** The rest of the sidebar: who is logged in, Reports, Recurring with its waiting count, Categories, Settings, and Log out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    account: MoreAccountUi,
    waitingCount: Int,
    onOpenReports: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
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
            AppCard(onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = account.name, photoUrl = account.photoUrl, size = 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                MoreItem(AppIcons.Reports, R.string.more_reports, onClick = onOpenReports)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Recurring, R.string.more_recurring, badge = waitingCount, onClick = onOpenRecurring)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Categories, R.string.more_categories, onClick = onOpenCategories)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MoreItem(AppIcons.Settings, R.string.more_settings, onClick = onOpenSettings)
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

/** A row to a section; a badge counts what waits there. */
@Composable
private fun MoreItem(icon: ImageVector, labelRes: Int, badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Badge(modifier = Modifier.padding(end = 8.dp)) { Text(badge.toString()) }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@PreviewLightDark
@Composable
private fun MorePreview() {
    ExpensifyTheme {
        MoreScreen(
            account = MoreAccountUi("Sita Rai", "sita@example.com", null),
            waitingCount = 2,
            onOpenReports = {}, onOpenRecurring = {}, onOpenCategories = {}, onOpenSettings = {}, onLogout = {},
        )
    }
}
