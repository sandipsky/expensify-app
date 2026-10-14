package com.example.expensify.feature.budgets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.budgets.domain.Budget

@Composable
fun BudgetsRoute(
    onOpenBudget: (String) -> Unit,
    onAddBudget: () -> Unit,
    onEditBudget: (String) -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    BudgetsScreen(
        state = state,
        onOpen = { onOpenBudget(it.id) },
        onAdd = onAddBudget,
        onEdit = { onEditBudget(it.id) },
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onDelete = viewModel::requestDelete,
    )
    BudgetDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

/** The delete confirmation, whichever budgets screen opened it. */
@Composable
fun BudgetDialogsHost(dialogs: BudgetDialogs, onConfirmDelete: () -> Unit, onDismiss: () -> Unit) {
    dialogs.deleteTarget?.let { budget ->
        ConfirmDialog(
            title = stringResource(R.string.budget_delete_dialog_title, budget.name),
            message = stringResource(R.string.budget_delete_dialog_message),
            confirmText = stringResource(R.string.budget_delete_dialog_confirm),
            onConfirm = onConfirmDelete,
            onDismiss = onDismiss,
            destructive = true,
            busy = dialogs.busy,
        )
    }
}

/** Every active budget with its meter, then the paused ones (req BUD-04). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onOpen: (Budget) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Budget) -> Unit,
    onPause: (Budget) -> Unit,
    onResume: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val showFab = state is BudgetsUiState.Content && !state.isEmpty

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
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
                    text = { Text(stringResource(R.string.budgets_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            BudgetsUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { Skeleton(196.dp) }
            }
            is BudgetsUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is BudgetsUiState.Content -> BudgetsContent(
                state = state,
                modifier = Modifier.padding(padding),
                onOpen = onOpen,
                onAdd = onAdd,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun BudgetsContent(
    state: BudgetsUiState.Content,
    modifier: Modifier,
    onOpen: (Budget) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Budget) -> Unit,
    onPause: (Budget) -> Unit,
    onResume: (Budget) -> Unit,
    onDelete: (Budget) -> Unit,
) {
    var pausedOpen by rememberSaveable { mutableStateOf(false) }

    if (state.isEmpty) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            AppCard {
                EmptyState(
                    icon = AppIcons.Budgets,
                    title = stringResource(R.string.budgets_empty_title),
                    message = stringResource(R.string.budgets_empty_message),
                    action = { Button(onClick = onAdd) { Text(stringResource(R.string.budgets_add)) } },
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
        state.spendingError?.let { message ->
            item(key = "spending-error") { Banner(message.asString(), BannerTone.Error) }
        }

        if (state.active.isEmpty()) {
            item(key = "all-paused") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Pause,
                        title = stringResource(R.string.budgets_all_paused_title),
                        message = stringResource(R.string.budgets_all_paused_message),
                        action = { Button(onClick = onAdd) { Text(stringResource(R.string.budgets_add)) } },
                    )
                }
            }
        }

        items(state.active, key = { it.budget.id }) { card ->
            BudgetCard(
                card = card,
                onOpen = { onOpen(card.budget) },
                onEdit = { onEdit(card.budget) },
                onPause = { onPause(card.budget) },
                onResume = { onResume(card.budget) },
                onDelete = { onDelete(card.budget) },
            )
        }

        if (state.paused.isNotEmpty()) {
            item(key = "paused-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pausedOpen = !pausedOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.budgets_paused_section, state.paused.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (pausedOpen) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.paused, key = { it.budget.id }) { card ->
                AnimatedVisibility(visible = pausedOpen) {
                    BudgetCard(
                        card = card,
                        onOpen = { onOpen(card.budget) },
                        onEdit = { onEdit(card.budget) },
                        onPause = { onPause(card.budget) },
                        onResume = { onResume(card.budget) },
                        onDelete = { onDelete(card.budget) },
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun BudgetsPreview() {
    ExpensifyTheme {
        BudgetsScreen(
            state = BudgetsUiState.Content(active = SampleBudgets.cards, paused = SampleBudgets.pausedCards, spendingError = null),
            onOpen = {}, onAdd = {}, onEdit = {}, onPause = {}, onResume = {}, onDelete = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun BudgetsEmptyPreview() {
    ExpensifyTheme {
        BudgetsScreen(
            state = BudgetsUiState.Content(active = emptyList(), paused = emptyList(), spendingError = null),
            onOpen = {}, onAdd = {}, onEdit = {}, onPause = {}, onResume = {}, onDelete = {},
        )
    }
}
