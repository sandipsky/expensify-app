package com.example.expensify.feature.recurring.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.designsystem.components.AppDatePickerDialog
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.designsystem.components.MoneyField
import com.example.expensify.designsystem.components.PickerRow
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.accounts.ui.SampleAccounts
import com.example.expensify.feature.categories.ui.SampleCategories
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.AccountField
import com.example.expensify.feature.transactions.ui.CategoryGrid
import com.example.expensify.feature.transactions.ui.TxRows
import com.example.expensify.feature.transactions.ui.labelRes
import java.time.LocalDate

@Composable
fun RuleFormRoute(
    onDone: () -> Unit,
    viewModel: RuleFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RuleFormEvent.Saved -> onDone()
            }
        }
    }
    RuleFormScreen(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onAmountChange = viewModel::onAmountChange,
        onAccountChange = viewModel::onAccountChange,
        onToAccountChange = viewModel::onToAccountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onNoteChange = viewModel::onNoteChange,
        onFrequencyChange = viewModel::onFrequencyChange,
        onIntervalChange = viewModel::onIntervalChange,
        onToggleWeekday = viewModel::toggleWeekday,
        onDayOfMonthChange = viewModel::onDayOfMonthChange,
        onStartDateChange = viewModel::onStartDateChange,
        onEndTypeChange = viewModel::onEndTypeChange,
        onMaxCountChange = viewModel::onMaxCountChange,
        onEndDateChange = viewModel::onEndDateChange,
        onModeChange = viewModel::onModeChange,
        onSave = viewModel::save,
        onClose = onDone,
    )
}

/** Full screen: the entry it adds, then the schedule, the end, the mode and a preview of the next dates. Save stays pinned. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleFormScreen(
    state: RuleFormUiState,
    onTypeChange: (TxType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onFrequencyChange: (Frequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    onToggleWeekday: (Int) -> Unit,
    onDayOfMonthChange: (Int) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndTypeChange: (EndType) -> Unit,
    onMaxCountChange: (String) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onModeChange: (RuleMode) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.rule_form_edit_title else R.string.rule_form_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = onSave,
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                state.saving -> R.string.action_saving
                                state.isEdit -> R.string.action_save
                                else -> R.string.rule_form_submit_add
                            },
                        ),
                    )
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
            if (state.isEdit) Banner(stringResource(R.string.rule_form_edit_notice), BannerTone.Info)

            Text(stringResource(R.string.rule_form_section_entry), style = MaterialTheme.typography.titleMedium)

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

            MoneyField(
                value = state.amountText,
                onValueChange = onAmountChange,
                label = stringResource(R.string.tx_form_amount),
                error = state.amountError?.asString(),
            )

            if (state.isTransfer) {
                AccountField(stringResource(R.string.tx_form_from_account), state.accounts, state.accountId, onAccountChange, error = state.accountError?.asString())
                AccountField(stringResource(R.string.tx_form_to_account), state.accounts, state.toAccountId, onToAccountChange, error = state.toAccountError?.asString())
            } else {
                if (state.categories.isEmpty()) {
                    Banner(stringResource(R.string.rule_form_no_categories, stringResource(state.type.labelRes()).lowercase()), BannerTone.Info)
                    state.categoryError?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                } else {
                    CategoryGrid(state.categories, state.categoryId, onCategoryChange, error = state.categoryError?.asString())
                }
                AccountField(stringResource(R.string.tx_form_account), state.accounts, state.accountId, onAccountChange, error = state.accountError?.asString())
            }

            AppTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.tx_form_note),
                placeholder = stringResource(R.string.rule_form_note_placeholder),
                error = state.noteError?.asString(),
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            )

            Text(stringResource(R.string.rule_form_section_schedule), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val frequencies = Frequency.entries
                frequencies.forEachIndexed { index, frequency ->
                    SegmentedButton(
                        selected = state.frequency == frequency,
                        onClick = { onFrequencyChange(frequency) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size),
                        label = { Text(stringResource(frequency.labelRes())) },
                    )
                }
            }

            val plural = state.intervalText.trim().toIntOrNull() != 1
            AppTextField(
                value = state.intervalText,
                onValueChange = onIntervalChange,
                label = stringResource(R.string.rule_form_every),
                error = state.intervalError?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                trailingIcon = {
                    Text(
                        stringResource(state.frequency.unitRes(plural)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )

            if (state.frequency == Frequency.Weekly) {
                SectionLabel(stringResource(R.string.rule_form_on))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = day in state.weekdays,
                            onClick = { onToggleWeekday(day) },
                            label = { Text(RuleLabels.weekdayName(day)) },
                        )
                    }
                }
                state.weekdaysError?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }

            if (state.frequency == Frequency.Monthly) {
                PickerRow(
                    label = stringResource(R.string.rule_form_on_the),
                    value = if (state.dayOfMonth == RecurringRules.LAST_DAY) stringResource(R.string.rule_day_last) else RuleLabels.ordinal(state.dayOfMonth),
                    placeholder = "",
                    onClick = { picking = "day" },
                )
                Text(
                    stringResource(R.string.rule_form_month_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PickerRow(
                label = stringResource(R.string.rule_form_starts),
                value = DateFormats.date(state.startDate),
                placeholder = "",
                onClick = { picking = "start" },
                leading = { Icon(AppIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            state.pastHint?.let {
                Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel(stringResource(R.string.rule_form_ends))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val ends = EndType.entries
                ends.forEachIndexed { index, end ->
                    SegmentedButton(
                        selected = state.endType == end,
                        onClick = { onEndTypeChange(end) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ends.size),
                        label = { Text(stringResource(end.labelRes())) },
                    )
                }
            }
            when (state.endType) {
                EndType.Count -> AppTextField(
                    value = state.maxCountText,
                    onValueChange = onMaxCountChange,
                    label = stringResource(R.string.rule_form_count),
                    error = state.countError?.asString(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    trailingIcon = {
                        Text(
                            stringResource(R.string.rule_form_count_suffix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                )
                EndType.Until -> PickerRow(
                    label = stringResource(R.string.rule_form_last_date),
                    value = state.endDate?.let { DateFormats.date(it) },
                    placeholder = stringResource(R.string.tx_form_pick_date),
                    onClick = { picking = "end" },
                    leading = { Icon(AppIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    error = state.endDateError?.asString(),
                )
                EndType.Never -> Unit
            }

            SectionLabel(stringResource(R.string.rule_form_on_due))
            RuleMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.mode == mode, onClick = { onModeChange(mode) }, role = Role.RadioButton)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.mode == mode, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(mode.labelRes()), style = MaterialTheme.typography.bodyLarge)
                }
            }

            state.preview?.let {
                Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            state.error?.let { Banner(it.asString(), BannerTone.Error) }
            Spacer(Modifier.height(8.dp))
        }
    }

    when (picking) {
        "start" -> AppDatePickerDialog(initial = state.startDate, onPick = onStartDateChange, onDismiss = { picking = null })
        "end" -> AppDatePickerDialog(
            initial = state.endDate ?: state.startDate,
            onPick = onEndDateChange,
            onDismiss = { picking = null },
            minDate = state.startDate,
        )
        "day" -> DayOfMonthDialog(selected = state.dayOfMonth, onPick = { onDayOfMonthChange(it); picking = null }, onDismiss = { picking = null })
    }
}

/** The 1st to the 30th, then "Last day" (31), as the web's monthly picker lists them. */
@Composable
private fun DayOfMonthDialog(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_form_on_the)) },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                items((1..31).toList()) { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = day == selected, onClick = { onPick(day) }, role = Role.RadioButton)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = day == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (day == RecurringRules.LAST_DAY) stringResource(R.string.rule_day_last) else RuleLabels.ordinal(day))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@PreviewLightDark
@Composable
private fun RuleFormPreview() {
    ExpensifyTheme {
        RuleFormScreen(
            state = RuleFormUiState(
                loading = false,
                today = SampleRules.today,
                startDate = SampleRules.today,
                amountText = "18000",
                accountId = SampleAccounts.everyday.id,
                categoryId = SampleCategories.all.first { it.name == "Housing and rent" }.id,
                note = "Rent",
                dayOfMonth = 14,
                weekdays = listOf(3),
                accounts = TxRows.accountPicks(SampleAccounts.active, emptyList()),
                categories = TxRows.categoryPicks(SampleCategories.all, TxType.Expense, null),
                preview = com.example.expensify.core.ui.plain("Next: Wed, 14 Oct 2026 · Sat, 14 Nov 2026 · Mon, 14 Dec 2026"),
            ),
            onTypeChange = {}, onAmountChange = {}, onAccountChange = {}, onToAccountChange = {}, onCategoryChange = {}, onNoteChange = {},
            onFrequencyChange = {}, onIntervalChange = {}, onToggleWeekday = {}, onDayOfMonthChange = {}, onStartDateChange = {},
            onEndTypeChange = {}, onMaxCountChange = {}, onEndDateChange = {}, onModeChange = {}, onSave = {}, onClose = {},
        )
    }
}
