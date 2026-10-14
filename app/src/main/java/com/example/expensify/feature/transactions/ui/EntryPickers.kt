package com.example.expensify.feature.transactions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.PickerRow
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.dataColor

/** Tiles shown before "Show all": two rows of four (req TXN-03). */
private const val COLLAPSED = 8
private const val COLUMNS = 4

/**
 * Pick one category from a grid of labelled tiles (port of
 * CategoryPicker.tsx). Long lists start collapsed to the first tiles, with
 * the chosen one kept in view, until "Show all N categories".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    options: List<CategoryPick>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val collapsed = !showAll && options.size > COLLAPSED + 1
    val visible = if (collapsed) {
        val shown = options.take(COLLAPSED)
        val chosen = options.firstOrNull { it.id == selectedId }
        if (chosen != null && chosen !in shown) shown.dropLast(1) + chosen else shown
    } else {
        options
    }
    val hidden = options.size - visible.size

    Column(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.tx_form_category))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = COLUMNS,
        ) {
            visible.forEach { option ->
                CategoryTile(option, selected = option.id == selectedId, onClick = { onSelect(option.id) }, modifier = Modifier.weight(1f))
            }
            // Fill the last row so every tile keeps the same width.
            repeat((COLUMNS - visible.size % COLUMNS) % COLUMNS) { Spacer(Modifier.weight(1f)) }
        }
        if (hidden > 0) {
            TextButton(onClick = { showAll = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.tx_form_show_all_categories, options.size))
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CategoryTile(option: CategoryPick, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = dataColor(option.color)
    val background = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier
            .background(background, MaterialTheme.shapes.small)
            .then(if (selected) Modifier.border(2.dp, color, MaterialTheme.shapes.small) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(icon = IconMap.of(option.icon), color = color, size = 36.dp)
        Text(
            if (option.archived) stringResource(R.string.tx_form_archived_name, option.name) else option.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An account field: the chosen account's badge and name; tapping opens the picker sheet. */
@Composable
fun AccountField(
    label: String,
    options: List<AccountPick>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    PickerRow(
        label = label,
        value = selected?.let { if (it.archived) stringResource(R.string.tx_form_archived_name, it.name) else it.name },
        placeholder = stringResource(R.string.tx_form_choose_account),
        onClick = { open = true },
        modifier = modifier,
        leading = selected?.let { { IconBadge(icon = IconMap.of(it.icon), color = dataColor(it.color), size = 32.dp) } },
        error = error,
    )
    if (open) {
        AccountPickerSheet(
            title = label,
            options = options,
            selectedId = selectedId,
            onPick = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/** The accounts to pick from, in a sheet: badge, name, "(archived)" where it applies, and a check on the chosen one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    title: String,
    options: List<AccountPick>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (options.isEmpty()) {
            Text(
                stringResource(R.string.tx_form_no_accounts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        LazyColumn(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            items(options, key = { it.id }) { option ->
                val selected = option.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = { onPick(option.id) }, role = Role.RadioButton)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(icon = IconMap.of(option.icon), color = dataColor(option.color), size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (option.archived) stringResource(R.string.tx_form_archived_name, option.name) else option.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
