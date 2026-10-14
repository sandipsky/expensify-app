package com.example.expensify.feature.recurring.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.ymd
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.EntryRow
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.budgets.ui.MenuItem
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.transactions.ui.DetailItem
import java.time.LocalDate

@Composable
fun RuleDetailRoute(
    onBack: () -> Unit,
    onEditRule: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onConfirmEdited: (String, String) -> Unit,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RuleDetailEvent.Left -> onBack()
            }
        }
    }
    RuleDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEditRule(it.id) },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onDelete = viewModel::requestDelete,
        onConfirm = viewModel::confirm,
        onSkip = viewModel::skip,
        onEditEntry = { view, date -> onConfirmEdited(view.id, date.ymd()) },
        onOpenEntry = onOpenEntry,
        onShowMore = viewModel::showMore,
    )
    RuleDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

/** The rule's amount and schedule, details, next dates and the entries it added (req REC-12). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(
    state: RuleDetailUiState,
    onBack: () -> Unit,
    onEdit: (RuleView) -> Unit,
    onPause: (RuleView) -> Unit,
    onResume: (RuleView) -> Unit,
    onDelete: (RuleView) -> Unit,
    onConfirm: (RuleView, LocalDate) -> Unit,
    onSkip: (RuleView, LocalDate) -> Unit,
    onEditEntry: (RuleView, LocalDate) -> Unit,
    onOpenEntry: (String) -> Unit,
    onShowMore: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val view = (state as? RuleDetailUiState.Content)?.view
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.rule_back))
                    }
                },
                actions = {
                    if (view != null) {
                        IconButton(onClick = { onEdit(view) }) {
                            Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                // An ended rule has nothing to pause: edit its end to run it again.
                                if (view.status != RuleStatus.Ended) {
                                    if (view.rule.active) {
                                        MenuItem(R.string.rule_pause, AppIcons.Pause) { menuOpen = false; onPause(view) }
                                    } else {
                                        MenuItem(R.string.rule_resume, AppIcons.Resume) { menuOpen = false; onResume(view) }
                                    }
                                }
                                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete(view) }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when (state) {
            RuleDetailUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(56.dp)
                Skeleton(220.dp)
                Skeleton(200.dp)
            }
            RuleDetailUiState.NotFound -> Column(Modifier.padding(padding).padding(16.dp)) {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Recurring,
                        title = stringResource(R.string.rule_not_found_title),
                        message = stringResource(R.string.rule_not_found_message),
                        action = { Button(onClick = onBack) { Text(stringResource(R.string.rule_back)) } },
                    )
                }
            }
            is RuleDetailUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is RuleDetailUiState.Content -> RuleDetailContent(
                state = state,
                modifier = Modifier.padding(padding),
                onEdit = onEdit,
                onConfirm = onConfirm,
                onSkip = onSkip,
                onEditEntry = onEditEntry,
                onOpenEntry = onOpenEntry,
                onShowMore = onShowMore,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDetailContent(
    state: RuleDetailUiState.Content,
    modifier: Modifier,
    onEdit: (RuleView) -> Unit,
    onConfirm: (RuleView, LocalDate) -> Unit,
    onSkip: (RuleView, LocalDate) -> Unit,
    onEditEntry: (RuleView, LocalDate) -> Unit,
    onOpenEntry: (String) -> Unit,
    onShowMore: () -> Unit,
) {
    val view = state.view
    val rule = view.rule
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RuleBadge(view, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(view.name.asString(), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(view.schedule.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (view.status == RuleStatus.Paused || view.status == RuleStatus.Ended || view.asksFirst) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (view.status == RuleStatus.Paused) TagChip(stringResource(R.string.rule_chip_paused))
                            if (view.status == RuleStatus.Ended) TagChip(stringResource(R.string.rule_chip_ended))
                            if (view.asksFirst) TagChip(stringResource(R.string.rule_chip_asks_first))
                        }
                    }
                }
            }
        }

        if (view.status == RuleStatus.MissingAccount) {
            item(key = "missing") { Banner(stringResource(R.string.rule_missing_account_banner), BannerTone.Error) }
        }
        if (view.status == RuleStatus.Paused) {
            item(key = "paused") { Banner(stringResource(R.string.rule_paused_banner), BannerTone.Warn) }
        }

        if (state.waiting) {
            item(key = "waiting") {
                AppCard {
                    Text(stringResource(R.string.recurring_waiting_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(RuleLabels.dueCaption(view).asString(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConfirm(view, view.due[0]) }) {
                            Icon(AppIcons.Check, contentDescription = null, Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.rule_confirm))
                        }
                        OutlinedButton(onClick = { onSkip(view, view.due[0]) }) { Text(stringResource(R.string.rule_skip)) }
                        TextButton(onClick = { onEditEntry(view, view.due[0]) }) { Text(stringResource(R.string.rule_edit_entry)) }
                    }
                }
            }
        }

        item(key = "amount") {
            AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                Text(
                    stringResource(RuleLabels.captionRes(rule.template.type)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                RuleAmount(view, style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                Spacer(Modifier.height(4.dp))
                Text(view.schedule.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item(key = "details") {
            AppCard {
                Text(stringResource(R.string.rule_detail_details), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.details.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.label.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(item.value.asString(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
                    }
                }
                if (state.upcoming.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.rule_next_dates), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    state.upcoming.forEach { date ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DateFormats.date(date), style = MaterialTheme.typography.bodyLarge)
                            if (date <= state.today) TagChip(stringResource(R.string.rule_chip_due), tone = TagTone.Warn)
                        }
                    }
                }
            }
        }

        item(key = "entries-title") {
            Text(
                stringResource(R.string.rule_entries_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when {
            state.historyLoading -> item(key = "entries-loading") { Skeleton(120.dp) }
            state.historyError != null -> item(key = "entries-error") { Banner(state.historyError.asString(), BannerTone.Error) }
            state.rows.isEmpty() -> item(key = "entries-empty") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Transactions,
                        title = stringResource(R.string.rule_entries_empty_title),
                        message = stringResource(if (view.next != null) R.string.rule_entries_empty_future else R.string.rule_entries_empty_ended),
                        action = { OutlinedButton(onClick = { onEdit(view) }) { Text(stringResource(R.string.rule_edit)) } },
                    )
                }
            }
            else -> {
                item(key = "entries") {
                    AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        state.rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            EntryRow(row, onClick = { onOpenEntry(row.id) })
                        }
                    }
                }
                if (state.hiddenCount > 0) {
                    item(key = "entries-more") {
                        OutlinedButton(onClick = onShowMore, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.rule_show_more, state.hiddenCount))
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun RuleDetailPreview() {
    val view = SampleRules.rentView
    ExpensifyTheme {
        RuleDetailScreen(
            state = RuleDetailUiState.Content(
                view = view,
                today = SampleRules.today,
                upcoming = RecurringRules.pendingDates(view.rule, 5),
                waiting = true,
                details = listOf(
                    DetailItem(plain("Category"), plain("Housing and rent")),
                    DetailItem(plain("Account"), plain("Everyday bank")),
                    DetailItem(plain("Starts"), plain("Sat, 1 Aug 2026")),
                    DetailItem(plain("Ends"), plain("Never")),
                ),
                rows = emptyList(),
                hiddenCount = 0,
                historyLoading = false,
                historyError = null,
            ),
            onBack = {}, onEdit = {}, onPause = {}, onResume = {}, onDelete = {},
            onConfirm = { _, _ -> }, onSkip = { _, _ -> }, onEditEntry = { _, _ -> }, onOpenEntry = {}, onShowMore = {},
        )
    }
}
