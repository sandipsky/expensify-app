package com.example.expensify.feature.recurring.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.ymd
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.budgets.ui.MenuItem
import com.example.expensify.feature.recurring.domain.RuleStatus
import java.time.LocalDate

@Composable
fun RecurringRoute(
    onBack: () -> Unit,
    onOpenRule: (String) -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onConfirmEdited: (String, String) -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    RecurringScreen(
        state = state,
        onBack = onBack,
        onOpen = { onOpenRule(it.id) },
        onAdd = onAddRule,
        onEdit = { onEditRule(it.id) },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onDelete = viewModel::requestDelete,
        onConfirm = viewModel::confirm,
        onSkip = viewModel::skip,
        onEditEntry = { view, date -> onConfirmEdited(view.id, date.ymd()) },
    )
    RuleDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

/** Waiting for you, then active rules, then paused and ended ones (req REC-07, REC-11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onBack: () -> Unit,
    onOpen: (RuleView) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuleView) -> Unit,
    onPause: (RuleView) -> Unit,
    onResume: (RuleView) -> Unit,
    onDelete: (RuleView) -> Unit,
    onConfirm: (RuleView, LocalDate) -> Unit,
    onSkip: (RuleView, LocalDate) -> Unit,
    onEditEntry: (RuleView, LocalDate) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val showFab = state is RecurringUiState.Content && !state.groups.isEmpty

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.recurring_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            RecurringUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(120.dp)
                Skeleton(220.dp)
            }
            is RecurringUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is RecurringUiState.Content -> RecurringContent(
                state = state,
                modifier = Modifier.padding(padding),
                onOpen = onOpen,
                onAdd = onAdd,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onDelete = onDelete,
                onConfirm = onConfirm,
                onSkip = onSkip,
                onEditEntry = onEditEntry,
            )
        }
    }
}

@Composable
private fun RecurringContent(
    state: RecurringUiState.Content,
    modifier: Modifier,
    onOpen: (RuleView) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RuleView) -> Unit,
    onPause: (RuleView) -> Unit,
    onResume: (RuleView) -> Unit,
    onDelete: (RuleView) -> Unit,
    onConfirm: (RuleView, LocalDate) -> Unit,
    onSkip: (RuleView, LocalDate) -> Unit,
    onEditEntry: (RuleView, LocalDate) -> Unit,
) {
    val groups = state.groups
    var pausedOpen by rememberSaveable { mutableStateOf(false) }
    var endedOpen by rememberSaveable { mutableStateOf(false) }

    if (groups.isEmpty) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            AppCard {
                EmptyState(
                    icon = AppIcons.Recurring,
                    title = stringResource(R.string.recurring_empty_title),
                    message = stringResource(R.string.recurring_empty_message),
                    action = { Button(onClick = onAdd) { Text(stringResource(R.string.recurring_add)) } },
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (groups.waiting.isNotEmpty()) {
            item(key = "waiting-title") { SectionTitle(stringResource(R.string.recurring_waiting_title)) }
            item(key = "waiting") {
                AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    groups.waiting.forEachIndexed { index, view ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DueItem(
                            view = view,
                            onOpen = { onOpen(view) },
                            onConfirm = { onConfirm(view, view.due[0]) },
                            onSkip = { onSkip(view, view.due[0]) },
                            onEdit = { onEditEntry(view, view.due[0]) },
                        )
                    }
                }
            }
        }

        item(key = "active-title") { SectionTitle(stringResource(R.string.recurring_active_title)) }
        if (groups.active.isEmpty()) {
            item(key = "no-active") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Recurring,
                        title = stringResource(R.string.recurring_no_active_title),
                        message = stringResource(R.string.recurring_no_active_message),
                        action = { Button(onClick = onAdd) { Text(stringResource(R.string.recurring_add)) } },
                    )
                }
            }
        } else {
            item(key = "active") {
                RuleList(groups.active, state.today, onOpen, onEdit, onPause, onResume, onDelete)
            }
        }

        if (groups.paused.isNotEmpty()) {
            item(key = "paused-header") {
                SectionToggle(stringResource(R.string.recurring_paused_section, groups.paused.size), pausedOpen) { pausedOpen = !pausedOpen }
            }
            item(key = "paused") {
                AnimatedVisibility(visible = pausedOpen) {
                    RuleList(groups.paused, state.today, onOpen, onEdit, onPause, onResume, onDelete)
                }
            }
        }
        if (groups.ended.isNotEmpty()) {
            item(key = "ended-header") {
                SectionToggle(stringResource(R.string.recurring_ended_section, groups.ended.size), endedOpen) { endedOpen = !endedOpen }
            }
            item(key = "ended") {
                AnimatedVisibility(visible = endedOpen) {
                    RuleList(groups.ended, state.today, onOpen, onEdit, onPause, onResume, onDelete)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionToggle(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(if (open) AppIcons.ExpandLess else AppIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RuleList(
    views: List<RuleView>,
    today: LocalDate,
    onOpen: (RuleView) -> Unit,
    onEdit: (RuleView) -> Unit,
    onPause: (RuleView) -> Unit,
    onResume: (RuleView) -> Unit,
    onDelete: (RuleView) -> Unit,
) {
    AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        views.forEachIndexed { index, view ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
            RuleRow(
                view = view,
                today = today,
                onOpen = { onOpen(view) },
                onEdit = { onEdit(view) },
                onPause = { onPause(view) },
                onResume = { onResume(view) },
                onDelete = { onDelete(view) },
            )
        }
    }
}

/** The badge a rule leads with: its category's icon in its color, or neutral grey for transfers and missing categories. */
@Composable
fun RuleBadge(view: RuleView, size: androidx.compose.ui.unit.Dp) {
    val color = if (view.color == null) ExpensifyTheme.colors.transfer else dataColor(view.color)
    IconBadge(icon = IconMap.of(view.icon), color = color, size = size)
}

/** The signed amount a rule adds, in its color. */
@Composable
fun RuleAmount(
    view: RuleView,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.End,
) {
    AmountText(
        money = view.amount,
        kind = view.kind,
        sign = if (view.kind == AmountKind.Transfer) Sign.Never else Sign.ExceptZero,
        style = style,
        modifier = modifier,
        textAlign = textAlign,
    )
}

/**
 * One rule on the Recurring page: what it adds, how often, from which
 * account, its amount and next date, and a menu with its actions. Tapping
 * the row opens the rule's page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleRow(
    view: RuleView,
    today: LocalDate,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val missing = view.status == RuleStatus.MissingAccount
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleBadge(view, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(view.name.asString(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.rule_caption, view.schedule.asString(), view.accountLabel.asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (view.asksFirst || missing) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (view.asksFirst) TagChip(stringResource(R.string.rule_chip_asks_first))
                    if (missing) TagChip(stringResource(R.string.rule_chip_account_deleted), tone = TagTone.Error)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RuleAmount(view)
            val due = view.due.isNotEmpty()
            Text(
                RuleLabels.nextLabel(view, today).asString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (due) ExpensifyTheme.colors.warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.More, contentDescription = stringResource(R.string.rule_actions_for, view.name.asString()))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(R.string.action_edit, AppIcons.Edit) { menuOpen = false; onEdit() }
                // An ended rule has nothing to pause: edit its end to run it again.
                if (view.status != RuleStatus.Ended) {
                    if (view.rule.active) {
                        MenuItem(R.string.rule_pause, AppIcons.Pause) { menuOpen = false; onPause() }
                    } else {
                        MenuItem(R.string.rule_resume, AppIcons.Resume) { menuOpen = false; onResume() }
                    }
                }
                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
            }
        }
    }
}

/** An ask-first entry waiting for Confirm, Skip or Edit (req REC-07). Each acts on the oldest due date. */
@Composable
fun DueItem(view: RuleView, onOpen: () -> Unit, onConfirm: () -> Unit, onSkip: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleBadge(view, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(view.name.asString(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    RuleLabels.dueCaption(view).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RuleAmount(view)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onConfirm) {
                Icon(AppIcons.Check, contentDescription = null, Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rule_confirm))
            }
            OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.rule_skip)) }
            TextButton(onClick = onEdit) {
                Icon(AppIcons.Edit, contentDescription = null, Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rule_edit_entry))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun RecurringPreview() {
    ExpensifyTheme {
        RecurringScreen(
            state = RecurringUiState.Content(SampleRules.groups, SampleRules.today),
            onBack = {}, onOpen = {}, onAdd = {}, onEdit = {}, onPause = {}, onResume = {}, onDelete = {},
            onConfirm = { _, _ -> }, onSkip = { _, _ -> }, onEditEntry = { _, _ -> },
        )
    }
}

@PreviewLightDark
@Composable
private fun RecurringEmptyPreview() {
    ExpensifyTheme {
        RecurringScreen(
            state = RecurringUiState.Content(RuleGroups(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), SampleRules.today),
            onBack = {}, onOpen = {}, onAdd = {}, onEdit = {}, onPause = {}, onResume = {}, onDelete = {},
            onConfirm = { _, _ -> }, onSkip = { _, _ -> }, onEditEntry = { _, _ -> },
        )
    }
}
