package com.example.expensify.feature.categories.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.ConfirmDialog
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType
import kotlinx.coroutines.launch

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    onAdd: (CategoryType) -> Unit,
    onEdit: (Category) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    CategoriesScreen(
        state = state,
        busy = dialogs.busy,
        onBack = onBack,
        onAdd = onAdd,
        onAddDefaults = viewModel::addDefaults,
        onEdit = onEdit,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onDelete = viewModel::requestDelete,
    )
    dialogs.deleteTarget?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.category_delete_dialog_title, category.name),
            message = stringResource(R.string.category_delete_dialog_message),
            confirmText = stringResource(R.string.category_delete_dialog_confirm),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialogs,
            destructive = true,
            busy = dialogs.busy,
        )
    }
}

/** Expense and income categories on their own tabs, archived ones under each (req CAT-01). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    busy: Boolean,
    onBack: () -> Unit,
    onAdd: (CategoryType) -> Unit,
    onAddDefaults: () -> Unit,
    onEdit: (Category) -> Unit,
    onArchive: (Category) -> Unit,
    onRestore: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The open tab, one page per type: a swipe or a tap on a tab moves between them, and "Add category" adds to the open one.
    val pager = rememberPagerState { CategoryType.entries.size }
    val scope = rememberCoroutineScope()
    val type = CategoryType.entries[pager.currentPage]
    val showFab = state is CategoriesUiState.Content && !state.isEmpty

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
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
                    onClick = { onAdd(type) },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.categories_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            CategoriesUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(44.dp)
                Skeleton(360.dp)
            }
            is CategoriesUiState.Error -> ErrorState(state.message.asString(), Modifier.padding(padding))
            is CategoriesUiState.Content -> if (state.isEmpty) {
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    AppCard {
                        EmptyState(
                            icon = AppIcons.Categories,
                            title = stringResource(R.string.categories_empty_title),
                            message = stringResource(R.string.categories_empty_message),
                            action = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onAddDefaults, enabled = !busy) {
                                        Text(stringResource(if (busy) R.string.action_saving else R.string.categories_add_defaults))
                                    }
                                    OutlinedButton(onClick = { onAdd(type) }, enabled = !busy) {
                                        Text(stringResource(R.string.categories_add))
                                    }
                                }
                            },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    TabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.background) {
                        CategoryType.entries.forEachIndexed { index, t ->
                            Tab(
                                selected = pager.currentPage == index,
                                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                text = { Text(stringResource(t.labelRes())) },
                            )
                        }
                    }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        key = { CategoryType.entries[it].name },
                    ) { page ->
                        val pageType = CategoryType.entries[page]
                        CategoryLists(
                            type = pageType,
                            lists = state.of(pageType),
                            onAdd = { onAdd(pageType) },
                            onEdit = onEdit,
                            onArchive = onArchive,
                            onRestore = onRestore,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLists(
    type: CategoryType,
    lists: CategoryRules.Lists,
    onAdd: () -> Unit,
    onEdit: (Category) -> Unit,
    onArchive: (Category) -> Unit,
    onRestore: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    var archivedOpen by rememberSaveable(type) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lists.active.isEmpty()) {
            item(key = "empty-$type") {
                AppCard {
                    EmptyState(
                        icon = AppIcons.Categories,
                        title = stringResource(type.emptyTitleRes()),
                        message = stringResource(R.string.categories_type_empty_message),
                        action = { Button(onClick = onAdd) { Text(stringResource(R.string.categories_add)) } },
                    )
                }
            }
        } else {
            item(key = "active-$type") {
                AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    lists.active.forEachIndexed { index, category ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        CategoryRow(
                            category = category,
                            onEdit = { onEdit(category) },
                            onArchive = { onArchive(category) },
                            onRestore = { onRestore(category) },
                            onDelete = { onDelete(category) },
                        )
                    }
                }
            }
        }

        if (lists.archived.isNotEmpty()) {
            item(key = "archived-header-$type") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { archivedOpen = !archivedOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.categories_archived_section, lists.archived.size),
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
            item(key = "archived-$type") {
                AnimatedVisibility(visible = archivedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.categories_archived_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                            lists.archived.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                CategoryRow(
                                    category = category,
                                    onEdit = { onEdit(category) },
                                    onArchive = { onArchive(category) },
                                    onRestore = { onRestore(category) },
                                    onDelete = { onDelete(category) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One category on the Categories page: its icon and name, and a menu with its
 * actions. Tapping an active category edits it; an archived one only offers
 * restore and delete.
 */
@Composable
fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (category.archived) Modifier else Modifier.clickable(onClick = onEdit))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = IconMap.of(category.icon), color = dataColor(category.color), size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.More, contentDescription = stringResource(R.string.category_actions_for, category.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (category.archived) {
                    MenuItem(R.string.category_restore, AppIcons.Restore) { menuOpen = false; onRestore() }
                } else {
                    MenuItem(R.string.action_edit, AppIcons.Edit) { menuOpen = false; onEdit() }
                    MenuItem(R.string.category_archive, AppIcons.Archive) { menuOpen = false; onArchive() }
                }
                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}

@PreviewLightDark
@Composable
private fun CategoriesPreview() {
    ExpensifyTheme {
        CategoriesScreen(
            state = CategoriesUiState.Content(expense = SampleCategories.expense, income = SampleCategories.income),
            busy = false,
            onBack = {}, onAdd = {}, onAddDefaults = {}, onEdit = {}, onArchive = {}, onRestore = {}, onDelete = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun CategoriesEmptyPreview() {
    ExpensifyTheme {
        CategoriesScreen(
            state = CategoriesUiState.Content(
                expense = CategoryRules.Lists(emptyList(), emptyList()),
                income = CategoryRules.Lists(emptyList(), emptyList()),
            ),
            busy = false,
            onBack = {}, onAdd = {}, onAddDefaults = {}, onEdit = {}, onArchive = {}, onRestore = {}, onDelete = {},
        )
    }
}

@Suppress("unused")
private val previewSpacer = Modifier.height(0.dp)
