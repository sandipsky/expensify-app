package com.example.expensify.feature.accounts.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.accounts.domain.Account

@Composable
fun AccountsRoute(
    onOpenAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    AccountsScreen(
        state = state,
        onOpen = { onOpenAccount(it.id) },
        onAdd = onAddAccount,
        onEdit = { onEditAccount(it.id) },
        onReconcile = viewModel::openReconcile,
        onArchive = viewModel::requestArchive,
        onRestore = viewModel::restore,
        onDelete = viewModel::requestDelete,
    )
    AccountDialogsHost(
        dialogs = dialogs,
        onConfirmArchive = viewModel::confirmArchive,
        onConfirmDelete = viewModel::confirmDelete,
        onReconcileInput = viewModel::onReconcileInput,
        onSaveReconcile = viewModel::saveReconcile,
        onDismiss = viewModel::dismissDialogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onOpen: (Account) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Account) -> Unit,
    onReconcile: (Account) -> Unit,
    onArchive: (Account) -> Unit,
    onRestore: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val showFab = state is AccountsUiState.Content && !state.isEmpty

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
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
                    text = { Text(stringResource(R.string.accounts_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            AccountsUiState.Loading -> LoadingList(Modifier.padding(padding))
            is AccountsUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is AccountsUiState.Content -> AccountsContent(
                state = state,
                modifier = Modifier.padding(padding),
                onOpen = onOpen,
                onAdd = onAdd,
                onEdit = onEdit,
                onReconcile = onReconcile,
                onArchive = onArchive,
                onRestore = onRestore,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun LoadingList(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(104.dp)
        repeat(3) { Skeleton(140.dp) }
    }
}

@Composable
private fun AccountsContent(
    state: AccountsUiState.Content,
    modifier: Modifier,
    onOpen: (Account) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Account) -> Unit,
    onReconcile: (Account) -> Unit,
    onArchive: (Account) -> Unit,
    onRestore: (Account) -> Unit,
    onDelete: (Account) -> Unit,
) {
    var archivedOpen by rememberSaveable { mutableStateOf(false) }

    if (state.isEmpty) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            AppCard {
                EmptyState(
                    icon = AppIcons.Accounts,
                    title = stringResource(R.string.accounts_empty_title),
                    message = stringResource(R.string.accounts_empty_message),
                    action = { Button(onClick = onAdd) { Text(stringResource(R.string.accounts_add)) } },
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
        item(key = "total") { TotalCard(state) }

        if (state.active.isEmpty()) {
            item(key = "all-archived") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Archive,
                        title = stringResource(R.string.accounts_all_archived_title),
                        message = stringResource(R.string.accounts_all_archived_message),
                    )
                }
            }
        }

        items(state.active, key = { it.id }) { account ->
            AccountCard(
                account = account,
                onOpen = { onOpen(account) },
                onEdit = { onEdit(account) },
                onReconcile = { onReconcile(account) },
                onArchive = { onArchive(account) },
                onRestore = { onRestore(account) },
                onDelete = { onDelete(account) },
            )
        }

        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { archivedOpen = !archivedOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.accounts_archived_section, state.archived.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (archivedOpen) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.archived, key = { it.id }) { account ->
                AnimatedVisibility(visible = archivedOpen) {
                    AccountCard(
                        account = account,
                        onOpen = { onOpen(account) },
                        onEdit = { onEdit(account) },
                        onReconcile = { onReconcile(account) },
                        onArchive = { onArchive(account) },
                        onRestore = { onRestore(account) },
                        onDelete = { onDelete(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalCard(state: AccountsUiState.Content) {
    val across = pluralStringResource(R.plurals.accounts_total_across, state.includedCount, state.includedCount)
    val note = if (state.excludedCount > 0) {
        stringResource(R.string.accounts_total_excluded, across, state.excludedCount)
    } else {
        across
    }
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Text(
            stringResource(R.string.accounts_total_balance),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        AmountText(
            money = state.total,
            style = MaterialTheme.typography.displaySmall,
            color = if (state.total.isNegative) ExpensifyTheme.colors.expense else Color.Unspecified,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(4.dp))
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@PreviewLightDark
@Composable
private fun AccountsPreview() {
    ExpensifyTheme {
        AccountsScreen(
            state = AccountsUiState.Content(
                currency = "NPR",
                total = com.example.expensify.core.money.Money.rupees(1_234_567, 50),
                includedCount = 3,
                excludedCount = 1,
                active = SampleAccounts.active,
                archived = SampleAccounts.archived,
            ),
            onOpen = {}, onAdd = {}, onEdit = {}, onReconcile = {}, onArchive = {}, onRestore = {}, onDelete = {},
        )
    }
}

@Suppress("unused")
private val previewTotal = MoneyFormat.SYMBOL
