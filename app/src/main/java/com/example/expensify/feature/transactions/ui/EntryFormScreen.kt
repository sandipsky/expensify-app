package com.example.expensify.feature.transactions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.designsystem.components.AmountInput
import com.example.expensify.designsystem.components.AppDatePickerDialog
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.budgets.ui.MenuItem
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate

@Composable
fun EntryFormRoute(
    onDone: () -> Unit,
    onDuplicate: (String) -> Unit,
    onMakeRecurring: (String) -> Unit,
    onOpenRule: (String) -> Unit,
    viewModel: EntryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EntryFormEvent.Saved, EntryFormEvent.Left -> onDone()
                EntryFormEvent.FocusAmount -> amountFocus.requestFocus()
                is EntryFormEvent.Duplicate -> onDuplicate(event.id)
                is EntryFormEvent.MakeRecurring -> onMakeRecurring(event.id)
                is EntryFormEvent.OpenRule -> onOpenRule(event.ruleId)
            }
        }
    }
    EntryFormScreen(
        state = state,
        amountFocus = amountFocus,
        onTypeChange = viewModel::onTypeChange,
        onAmountChange = viewModel::onAmountChange,
        onAccountChange = viewModel::onAccountChange,
        onToAccountChange = viewModel::onToAccountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onDaysAgo = viewModel::setDaysAgo,
        onNoteChange = viewModel::onNoteChange,
        onAddDefaults = viewModel::addDefaults,
        onSave = { viewModel.save() },
        onSaveAndAddAnother = { viewModel.save(another = true) },
        onClose = onDone,
        onDuplicate = viewModel::duplicate,
        onMakeRecurring = viewModel::makeRecurring,
        onOpenRule = viewModel::openRule,
        onDelete = viewModel::requestDeleteEditing,
    )
    TransactionDialogsHost(dialogs = dialogs, onConfirmDelete = viewModel::confirmDelete, onDismiss = viewModel::dismissDialogs)
}

/**
 * Full screen (CLAUDE.md, Add entry): the type at top, the large amount with
 * the keypad up at once, the category grid, the account (or both of a
 * transfer), date chips and the note. Save stays pinned at the bottom, with
 * "Save and add another" beside it on a new entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    state: EntryFormUiState,
    amountFocus: FocusRequester,
    onTypeChange: (TxType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onDaysAgo: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onAddDefaults: () -> Unit,
    onSave: () -> Unit,
    onSaveAndAddAnother: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onMakeRecurring: () -> Unit,
    onOpenRule: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val mode = state.mode

    // The keypad comes up as soon as a new entry opens (CLAUDE.md, goals: under five seconds).
    LaunchedEffect(state.loading, mode) {
        if (!state.loading && mode == EntryFormMode.Add) amountFocus.requestFocus()
    }

    val save = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onSave()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (mode) {
                                is EntryFormMode.Edit -> R.string.tx_form_edit_title
                                is EntryFormMode.Confirm -> R.string.tx_form_confirm_title
                                EntryFormMode.Add -> R.string.tx_form_add_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (mode is EntryFormMode.Edit) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                MenuItem(R.string.tx_duplicate, AppIcons.Duplicate) { menuOpen = false; onDuplicate() }
                                if (mode.ruleId != null) {
                                    MenuItem(R.string.tx_open_rule, AppIcons.Recurring) { menuOpen = false; onOpenRule() }
                                } else {
                                    MenuItem(R.string.tx_make_recurring, AppIcons.Recurring) { menuOpen = false; onMakeRecurring() }
                                }
                                MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode == EntryFormMode.Add) {
                        TextButton(
                            onClick = onSaveAndAddAnother,
                            enabled = !state.saving && !state.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.tx_form_save_and_add_another))
                        }
                    }
                    Button(
                        onClick = save,
                        enabled = !state.saving && !state.loading,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    state.saving -> R.string.action_saving
                                    mode is EntryFormMode.Confirm -> R.string.tx_form_submit_confirm
                                    else -> R.string.action_save
                                },
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode is EntryFormMode.Confirm) {
                Banner(stringResource(R.string.tx_form_confirm_hint, DateFormats.date(mode.date)), BannerTone.Info)
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = TxType.entries
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }

            AmountInput(
                value = state.amountText,
                onValueChange = onAmountChange,
                error = state.amountError?.asString(),
                focusRequester = amountFocus,
                imeAction = ImeAction.Done,
                onImeAction = save,
            )

            if (!state.hasAccounts) {
                Banner(stringResource(R.string.tx_form_no_accounts_banner), BannerTone.Warn)
            }

            if (state.isTransfer) {
                AccountField(
                    label = stringResource(R.string.tx_form_from_account),
                    options = state.accounts,
                    selectedId = state.accountId,
                    onSelect = onAccountChange,
                    error = state.accountError?.asString(),
                )
                AccountField(
                    label = stringResource(R.string.tx_form_to_account),
                    options = state.accounts,
                    selectedId = state.toAccountId,
                    onSelect = onToAccountChange,
                    error = state.toAccountError?.asString(),
                )
            } else {
                if (state.categories.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Banner(stringResource(R.string.tx_form_no_categories, stringResource(state.type.labelRes()).lowercase()), BannerTone.Info)
                        OutlinedButton(onClick = onAddDefaults, enabled = !state.addingDefaults, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.categories_add_defaults))
                        }
                        state.categoryError?.let {
                            Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    CategoryGrid(
                        options = state.categories,
                        selectedId = state.categoryId,
                        onSelect = onCategoryChange,
                        error = state.categoryError?.asString(),
                    )
                }
                AccountField(
                    label = stringResource(R.string.tx_form_account),
                    options = state.accounts,
                    selectedId = state.accountId,
                    onSelect = onAccountChange,
                    error = state.accountError?.asString(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(R.string.tx_form_date))
                val isToday = state.date == state.today
                val isYesterday = state.date == state.today.minusDays(1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = isToday, onClick = { onDaysAgo(0) }, label = { Text(stringResource(R.string.day_today)) })
                    FilterChip(selected = isYesterday, onClick = { onDaysAgo(1) }, label = { Text(stringResource(R.string.day_yesterday)) })
                    AssistChip(
                        onClick = { datePickerOpen = true },
                        label = { Text(if (isToday || isYesterday) stringResource(R.string.tx_form_pick_date) else DateFormats.shortDate(state.date)) },
                        leadingIcon = { Icon(AppIcons.Calendar, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
                Text(
                    DateFormats.date(state.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.upcoming) {
                    Text(
                        stringResource(R.string.tx_form_upcoming_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AppTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.tx_form_note),
                placeholder = stringResource(R.string.tx_form_note_placeholder),
                error = state.noteError?.asString(),
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            )

            state.error?.let { Banner(it.asString(), BannerTone.Error) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (datePickerOpen) {
        AppDatePickerDialog(initial = state.date, onPick = onDateChange, onDismiss = { datePickerOpen = false })
    }
}

@Suppress("unused")
private val previewSpacing = Modifier.width(0.dp)

@PreviewLightDark
@Composable
private fun EntryFormPreview() {
    ExpensifyTheme {
        EntryFormScreen(
            state = EntryFormUiState(
                loading = false,
                date = SampleTransactions.today,
                today = SampleTransactions.today,
                amountText = "450",
                accountId = SampleAccounts.cash.id,
                categoryId = SampleCategories.food.id,
                accounts = TxRows.accountPicks(SampleAccounts.active, emptyList()),
                categories = TxRows.categoryPicks(SampleCategories.all, TxType.Expense, null),
            ),
            amountFocus = androidx.compose.runtime.remember { FocusRequester() },
            onTypeChange = {}, onAmountChange = {}, onAccountChange = {}, onToAccountChange = {}, onCategoryChange = {},
            onDateChange = {}, onDaysAgo = {}, onNoteChange = {}, onAddDefaults = {}, onSave = {}, onSaveAndAddAnother = {},
            onClose = {}, onDuplicate = {}, onMakeRecurring = {}, onOpenRule = {}, onDelete = {},
        )
    }
}
