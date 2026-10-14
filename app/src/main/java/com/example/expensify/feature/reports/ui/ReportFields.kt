package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.components.AppDatePickerDialog
import com.example.expensify.designsystem.components.PickerRow
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.feature.reports.domain.PeriodState
import com.example.expensify.feature.reports.domain.ReportPreset
import java.time.LocalDate

// Port of ../expensify/src/features/reports/components/ReportFields.tsx for a
// phone: each filter is a chip that opens a bottom sheet, or a segmented
// control when it has three short options.

/** An account or category as a multi-select filter offers it. */
data class PickItem(val id: String, val name: UiText, val archived: Boolean = false)

/** A chip with a dropdown arrow that opens a sheet. */
@Composable
private fun DropdownChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(AppIcons.ExpandMore, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        colors = if (active) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

/** A sheet's title and its Done button. */
@Composable
private fun SheetHeader(title: String, onDone: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
    }
}

/**
 * The period: a chip naming the preset (or the custom dates), and a sheet
 * with every preset; "Custom range" shows two date fields, starting from
 * whatever the report showed (req RPT-03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodChip(
    state: PeriodState,
    onPreset: (ReportPreset) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val label = if (state.preset == ReportPreset.Custom) DateFormats.period(state.range) else stringResource(ReportLabels.presetRes(state.preset))
    DropdownChip(label = label, onClick = { open = true }, modifier = modifier)

    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetHeader(stringResource(R.string.report_field_period), onDone = { open = false })
                for (preset in ReportPreset.entries) {
                    val selected = preset == state.preset
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onPreset(preset)
                                    if (preset != ReportPreset.Custom) open = false
                                },
                            )
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(ReportLabels.presetRes(preset)), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (preset == ReportPreset.Custom && selected) {
                        Row(
                            Modifier.padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PickerRow(
                                label = stringResource(R.string.report_field_from),
                                value = DateFormats.shortDate(state.custom.start),
                                placeholder = "",
                                onClick = { picking = "from" },
                                modifier = Modifier.weight(1f),
                            )
                            PickerRow(
                                label = stringResource(R.string.report_field_to),
                                value = DateFormats.shortDate(state.custom.end),
                                placeholder = "",
                                onClick = { picking = "to" },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    when (picking) {
        "from" -> AppDatePickerDialog(
            initial = state.custom.start,
            onPick = { onCustom(it, state.custom.end) },
            onDismiss = { picking = null },
        )
        "to" -> AppDatePickerDialog(
            initial = state.custom.end,
            onPick = { onCustom(state.custom.start, it) },
            onDismiss = { picking = null },
        )
    }
}

/** A labelled segmented control for a filter with two or three short options: Group by, Expense or Income. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    value: T,
    labelOf: @Composable (T) -> String,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.heightIn(max = 32.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == value,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(labelOf(option), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
            )
        }
    }
}

/**
 * Accounts or categories to narrow the report to, archived ones too, since
 * their history still counts. The chip names the pick; the sheet lists every
 * item with a checkbox and a Clear button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPickChip(
    title: String,
    placeholder: String,
    items: List<PickItem>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    countLabel: @Composable (Int) -> String,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = when (selected.size) {
        0 -> placeholder
        1 -> items.firstOrNull { it.id == selected[0] }?.name?.asString() ?: countLabel(1)
        else -> countLabel(selected.size)
    }
    DropdownChip(label = label, onClick = { open = true }, modifier = modifier, active = selected.isNotEmpty())

    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding()) {
                SheetHeader(
                    title,
                    onDone = { open = false },
                    trailing = {
                        if (selected.isNotEmpty()) {
                            TextButton(onClick = { onChange(emptyList()) }) { Text(stringResource(R.string.report_field_clear)) }
                        }
                    },
                )
                if (items.isEmpty()) {
                    Text(
                        stringResource(R.string.report_field_nothing_to_pick),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(items, key = { it.id }) { item ->
                        val checked = item.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { onChange(if (it) selected + item.id else selected - item.id) },
                                )
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (item.archived) stringResource(R.string.tx_form_archived_name, item.name.asString()) else item.name.asString(),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One choice from a list: the budget, or how far back to look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SinglePickChip(
    title: String,
    options: List<T>,
    value: T,
    labelOf: @Composable (T) -> String,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    DropdownChip(label = labelOf(value), onClick = { open = true }, modifier = modifier, active = active)
    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding()) {
                SheetHeader(title, onDone = { open = false })
                LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(options.size) { index ->
                        val option = options[index]
                        val selected = option == value
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
                            Text(labelOf(option), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A switch that sits among the filter chips: "Paused budgets", "Periods before a budget started". */
@Composable
fun ToggleChip(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = checked,
        onClick = { onChange(!checked) },
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (checked) {
            { Icon(AppIcons.Check, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** A full-width action under an empty state. */
@Composable
fun EmptyAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(label) }
}
