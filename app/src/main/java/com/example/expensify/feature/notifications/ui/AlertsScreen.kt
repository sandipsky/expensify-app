package com.example.expensify.feature.notifications.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.AlertLinks
import com.example.expensify.feature.notifications.domain.AlertTarget
import com.example.expensify.feature.notifications.domain.AlertTone
import com.example.expensify.feature.notifications.domain.AppAlert

@Composable
fun AlertsRoute(
    onBack: () -> Unit,
    onOpen: (AlertTarget) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertsScreen(
        state = state,
        onBack = onBack,
        onOpenAlert = { alert -> AlertLinks.parse(alert.link)?.let(onOpen) },
        onRemove = viewModel::remove,
        onClear = viewModel::clear,
        onOpenSettings = onOpenSettings,
    )
}

/** The list of recent alerts, newest first, with a way to remove one, clear all, or open the notification settings (req NTF-06). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    state: AlertsUiState,
    onBack: () -> Unit,
    onOpenAlert: (AppAlert) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alerts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (state is AlertsUiState.Content && !state.isEmpty) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.alerts_clear_all)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (state) {
            AlertsUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(72.dp)
                Skeleton(72.dp)
                Skeleton(72.dp)
            }
            is AlertsUiState.Content -> if (state.isEmpty) {
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    AppCard {
                        EmptyState(
                            icon = AppIcons.Notifications,
                            title = stringResource(R.string.alerts_empty_title),
                            message = stringResource(R.string.alerts_empty_message),
                            action = {
                                OutlinedButton(onClick = onOpenSettings) {
                                    Icon(AppIcons.Settings, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.alerts_settings))
                                }
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                ) {
                    item(key = "list") {
                        AppCard(contentPadding = PaddingValues(0.dp)) {
                            state.rows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                AlertRow(row = row, onOpen = { onOpenAlert(row.alert) }, onRemove = { onRemove(row.alert.id) })
                            }
                        }
                    }
                    item(key = "settings") {
                        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(AppIcons.Settings, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.alerts_settings))
                        }
                    }
                }
            }
        }
    }
}

/** The badge color an alert's tone takes, beside its icon and its words (req NFR-06). */
@Composable
private fun AlertTone.color(): Color = when (this) {
    AlertTone.Info -> ExpensifyTheme.colors.info
    AlertTone.Success -> ExpensifyTheme.colors.income
    AlertTone.Warn -> ExpensifyTheme.colors.warn
    AlertTone.Error -> ExpensifyTheme.colors.expense
}

/** One alert: its kind's icon in its tone, the title with a dot while new, the message, and how long ago; a cross removes it. */
@Composable
private fun AlertRow(row: AlertRowUi, onOpen: () -> Unit, onRemove: () -> Unit) {
    val alert = row.alert
    val newLabel = stringResource(R.string.alerts_new)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon = AlertLabels.icon(alert.kind), color = alert.tone.color(), size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.fresh) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics { contentDescription = newLabel },
                    )
                }
                Text(
                    alert.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.fresh) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.timeAgo.asString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(AppIcons.Close, contentDescription = stringResource(R.string.alerts_remove, alert.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@PreviewLightDark
@Composable
private fun AlertsPreview() {
    val now = 1_792_000_000_000L
    val rows = listOf(
        AlertRowUi(
            AppAlert("budget|b2|2026-10-01|80", AlertKind.Budget, AlertTone.Warn, "Food and dining: 86% used", "Rs 2,100.00 left · 18 days left", AlertLinks.budget("b2"), now - 120_000, false),
            fresh = true,
            timeAgo = plain("2 minutes ago"),
        ),
        AlertRowUi(
            AppAlert("bill|r2|upcoming|2026-10-15", AlertKind.Bill, AlertTone.Info, "Rent is due tomorrow", "-Rs 18,000.00 · Everyday bank. You'll be asked to confirm it.", AlertLinks.RECURRING, now - 3 * 3_600_000, true),
            fresh = false,
            timeAgo = plain("3 hours ago"),
        ),
        AlertRowUi(
            AppAlert("reminder|2026-10-13", AlertKind.Reminder, AlertTone.Info, "Time to log today's spending", "Nothing is logged for today yet. Adding it takes a few seconds.", AlertLinks.TRANSACTIONS, now - 26 * 3_600_000, true),
            fresh = false,
            timeAgo = plain("yesterday"),
        ),
    )
    ExpensifyTheme {
        AlertsScreen(AlertsUiState.Content(rows), onBack = {}, onOpenAlert = {}, onRemove = {}, onClear = {}, onOpenSettings = {})
    }
}

@PreviewLightDark
@Composable
private fun AlertsEmptyPreview() {
    ExpensifyTheme {
        AlertsScreen(AlertsUiState.Content(emptyList()), onBack = {}, onOpenAlert = {}, onRemove = {}, onClear = {}, onOpenSettings = {})
    }
}
