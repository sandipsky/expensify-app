package com.example.expensify.feature.settings.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.device.PhotoThumbnails
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.PickerRow
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.auth.domain.Preferences
import com.example.expensify.feature.auth.domain.Profile
import com.example.expensify.feature.auth.domain.ThemePref
import com.example.expensify.feature.settings.domain.SettingsRules

/** What the phone says about showing notifications at all: the app-level switch in system settings. */
data class NotificationAccess(val enabled: Boolean)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system setting can change at any time, so it's read again whenever the screen comes back.
    var access by remember { mutableStateOf(NotificationAccess(NotificationManagerCompat.from(context).areNotificationsEnabled())) }
    LifecycleResumeEffect(Unit) {
        access = NotificationAccess(NotificationManagerCompat.from(context).areNotificationsEnabled())
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = NotificationAccess(NotificationManagerCompat.from(context).areNotificationsEnabled())
        viewModel.notifyPermission(granted)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickPhoto(uri)
    }

    SettingsScreen(
        state = state,
        access = access,
        onBack = onBack,
        onEditProfile = onEditProfile,
        onChangePassword = onChangePassword,
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        onTheme = viewModel::setTheme,
        onDailyReminder = viewModel::setDailyReminder,
        onReminderTime = viewModel::setReminderTime,
        onBudgetAlerts = viewModel::setBudgetAlerts,
        onBillReminders = viewModel::setBillReminders,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(notificationSettingsIntent(context.packageName))
            }
        },
        onOpenNotificationSettings = { context.startActivity(notificationSettingsIntent(context.packageName)) },
        onDeleteAll = viewModel::requestDeleteAll,
        onConfirmDeleteAll = viewModel::confirmDeleteAll,
        onDismissDeleteAll = viewModel::dismissDeleteAll,
        onDeleteAccount = onDeleteAccount,
    )
}

/** The system page for this app's notifications, where a refused permission can be turned back on. */
private fun notificationSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    access: NotificationAccess,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onTheme: (ThemePref) -> Unit,
    onDailyReminder: (Boolean) -> Unit,
    onReminderTime: (String) -> Unit,
    onBudgetAlerts: (Boolean) -> Unit,
    onBillReminders: (Boolean) -> Unit,
    onAllowNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDeleteAll: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
    onDismissDeleteAll: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (state) {
            SettingsUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(160.dp)
                Skeleton(96.dp)
                Skeleton(240.dp)
            }
            is SettingsUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is SettingsUiState.Content -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionTitle(stringResource(R.string.settings_section_profile))
                    ProfileCard(state.profile, state.photoBusy, onEditProfile, onPickPhoto, onRemovePhoto)
                    PasswordCard(onChangePassword)

                    SectionTitle(stringResource(R.string.settings_section_appearance))
                    AppearanceCard(state.profile.preferences.theme, onTheme)

                    SectionTitle(stringResource(R.string.settings_section_notifications))
                    NotificationsCard(
                        prefs = state.profile.preferences.notifications,
                        access = access,
                        onDailyReminder = onDailyReminder,
                        onReminderTime = onReminderTime,
                        onBudgetAlerts = onBudgetAlerts,
                        onBillReminders = onBillReminders,
                        onAllow = onAllowNotifications,
                        onOpenSettings = onOpenNotificationSettings,
                    )

                    SectionTitle(stringResource(R.string.settings_section_data))
                    DataCard(
                        title = stringResource(R.string.settings_delete_all_title),
                        text = stringResource(R.string.settings_delete_all_text),
                        action = stringResource(R.string.settings_delete_all_action),
                        onClick = onDeleteAll,
                    )
                    DataCard(
                        title = stringResource(R.string.settings_delete_account_title),
                        text = stringResource(R.string.settings_delete_account_text),
                        action = stringResource(R.string.settings_delete_account_action),
                        onClick = onDeleteAccount,
                    )
                }
                state.deleteAll?.let { dialog ->
                    ConfirmDialog(
                        title = pluralStringResource(R.plurals.settings_delete_all_dialog_title, dialog.count, dialog.count),
                        message = stringResource(R.string.settings_delete_all_dialog_message),
                        confirmText = stringResource(R.string.settings_delete_all_confirm),
                        onConfirm = onConfirmDeleteAll,
                        onDismiss = onDismissDeleteAll,
                        destructive = true,
                        busy = dialog.busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The photo, or the initials on the primary container when there is none. */
@Composable
fun Avatar(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(photoUrl) { PhotoThumbnails.decodeDataUrl(photoUrl) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Text(
                    SettingsRules.initialsOf(name),
                    style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Settings › Profile: the photo, name, email (and a pending one), the username that can't change, and Edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: Profile, photoBusy: Boolean, onEdit: () -> Unit, onPickPhoto: () -> Unit, onRemovePhoto: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = profile.name, photoUrl = profile.photoUrl, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.username != null) {
                    Text(
                        stringResource(R.string.settings_username_line, profile.username),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (profile.pendingEmail != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_pending_email, profile.pendingEmail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            OutlinedButton(onClick = onEdit) {
                Icon(AppIcons.Edit, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_edit_profile))
            }
            TextButton(onClick = onPickPhoto, enabled = !photoBusy) {
                Icon(AppIcons.Photo, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (profile.photoUrl != null) R.string.settings_change_photo else R.string.settings_add_photo))
            }
            if (profile.photoUrl != null) {
                TextButton(onClick = onRemovePhoto, enabled = !photoBusy) { Text(stringResource(R.string.settings_remove_photo)) }
            }
        }
        if (profile.username != null) {
            Text(
                stringResource(R.string.settings_username_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordCard(onChangePassword: () -> Unit) {
    AppCard {
        Text(stringResource(R.string.settings_password_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.settings_password_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChangePassword) {
            Icon(AppIcons.Lock, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_change_password))
        }
    }
}

/** Settings › Appearance: light, dark or the device's setting. Applies at once (req SET-02). */
@Composable
private fun AppearanceCard(theme: ThemePref, onTheme: (ThemePref) -> Unit) {
    AppCard {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemePref.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == theme,
                    onClick = { onTheme(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemePref.entries.size),
                    label = { Text(stringResource(themeRes(option))) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_theme_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun themeRes(theme: ThemePref): Int = when (theme) {
    ThemePref.Light -> R.string.settings_theme_light
    ThemePref.Dark -> R.string.settings_theme_dark
    ThemePref.System -> R.string.settings_theme_system
}

/**
 * Settings › Notifications (req SET-03): each kind on or off, the daily
 * reminder's time, and whether the phone lets the app show notifications at
 * all. Each change saves at once.
 */
@Composable
private fun NotificationsCard(
    prefs: NotificationPrefs,
    access: NotificationAccess,
    onDailyReminder: (Boolean) -> Unit,
    onReminderTime: (String) -> Unit,
    onBudgetAlerts: (Boolean) -> Unit,
    onBillReminders: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val anyOn = prefs.dailyReminder || prefs.budgetAlerts || prefs.billReminders
    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        SwitchRow(
            label = stringResource(R.string.settings_daily_reminder),
            hint = stringResource(R.string.settings_daily_reminder_hint),
            checked = prefs.dailyReminder,
            onChange = onDailyReminder,
        )
        if (prefs.dailyReminder) {
            ReminderTimeRow(time = prefs.reminderTime, onChange = onReminderTime, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            label = stringResource(R.string.settings_budget_alerts),
            hint = stringResource(R.string.settings_budget_alerts_hint),
            checked = prefs.budgetAlerts,
            onChange = onBudgetAlerts,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(
            label = stringResource(R.string.settings_bill_reminders),
            hint = stringResource(R.string.settings_bill_reminders_hint),
            checked = prefs.billReminders,
            onChange = onBillReminders,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_on_this_phone), style = MaterialTheme.typography.titleSmall)
            if (access.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Verified, contentDescription = null, tint = ExpensifyTheme.colors.income, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_notifications_on), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAllow, enabled = anyOn) {
                        Icon(AppIcons.Notifications, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_allow_notifications))
                    }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_open_settings)) }
                }
            }
            Text(stringResource(R.string.settings_notifications_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A switch with its label and a line explaining it; the whole row toggles. */
@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The reminder time: a field that opens a sheet of every half hour (plus the current time when it's between them). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeRow(time: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerRow(
        label = stringResource(R.string.settings_reminder_time),
        value = DateFormats.timeOfDay(time),
        placeholder = "",
        onClick = { open = true },
        modifier = modifier,
        leading = { Icon(AppIcons.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding()) {
                Text(
                    stringResource(R.string.settings_reminder_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                val options = SettingsRules.reminderTimeOptions(time)
                // Opens with the current time in view, not at midnight.
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(time) - 2).coerceAtLeast(0))
                LazyColumn(Modifier.heightIn(max = 480.dp), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(options) { option ->
                        val selected = option == time
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(option); open = false })
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(DateFormats.timeOfDay(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Settings › Data and privacy: a card with what the action does and its danger button. */
@Composable
private fun DataCard(title: String, text: String, action: String, onClick: () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
            Icon(AppIcons.Delete, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(action)
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    ExpensifyTheme {
        SettingsScreen(
            state = SettingsUiState.Content(
                profile = Profile(
                    uid = "u1", name = "Sita Rai", username = "sita.r", email = "sita@example.com", pendingEmail = "sita.rai@example.com", photoUrl = null,
                    preferences = Preferences(theme = ThemePref.System, notifications = NotificationPrefs(dailyReminder = true)),
                ),
                photoBusy = false,
                deleteAll = null,
            ),
            access = NotificationAccess(enabled = false),
            onBack = {}, onEditProfile = {}, onChangePassword = {}, onPickPhoto = {}, onRemovePhoto = {}, onTheme = {},
            onDailyReminder = {}, onReminderTime = {}, onBudgetAlerts = {}, onBillReminders = {}, onAllowNotifications = {}, onOpenNotificationSettings = {},
            onDeleteAll = {}, onConfirmDeleteAll = {}, onDismissDeleteAll = {}, onDeleteAccount = {},
        )
    }
}
