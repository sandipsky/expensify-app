package com.example.expensify.feature.categories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.designsystem.components.AppTextField
import com.example.expensify.designsystem.components.Banner
import com.example.expensify.designsystem.components.BannerTone
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType

@Composable
fun CategoryFormRoute(
    onDone: () -> Unit,
    viewModel: CategoryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CategoryFormEvent.Saved -> onDone()
            }
        }
    }
    CategoryFormScreen(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onNameChange = viewModel::onNameChange,
        onIconChange = viewModel::onIconChange,
        onSave = viewModel::save,
        onClose = onDone,
    )
}

/** Full screen: type (when adding), name, and the 48 icon tiles in the color the category gets. Save stays pinned. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryFormScreen(
    state: CategoryFormUiState,
    onTypeChange: (CategoryType) -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.category_form_edit_title else R.string.category_form_add_title)) },
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
                                else -> R.string.category_form_submit_add
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
        val color = dataColor(state.color)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isEdit) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val types = CategoryType.entries
                    types.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                            label = { Text(stringResource(type.labelRes())) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = IconMap.of(state.icon), color = color, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                AppTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.category_form_name),
                    placeholder = stringResource(state.type.placeholderRes()),
                    error = state.nameError?.asString(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel(stringResource(R.string.category_form_icon))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 6,
            ) {
                // The current icon first when it isn't one of the tiles, so it stays picked.
                val icons = if (state.icon in CategoryRules.ICONS) CategoryRules.ICONS else listOf(state.icon) + CategoryRules.ICONS
                icons.forEach { name ->
                    IconTile(
                        name = name,
                        selected = state.icon == name,
                        color = color,
                        onClick = { onIconChange(name) },
                    )
                }
            }

            state.error?.let { Banner(it.asString(), BannerTone.Error) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One icon in the picker: a 52 dp circle, filled with the category's color when chosen. */
@Composable
private fun IconTile(name: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val label = iconLabel(name)
    val background = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(background, CircleShape)
            .then(if (selected) Modifier.border(2.dp, color, CircleShape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            IconMap.of(name),
            contentDescription = null,
            tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun CategoryFormPreview() {
    ExpensifyTheme {
        CategoryFormScreen(
            state = CategoryFormUiState(loading = false, type = CategoryType.Expense, name = "Coffee", icon = "cart", color = "#0D9488"),
            onTypeChange = {}, onNameChange = {}, onIconChange = {}, onSave = {}, onClose = {},
        )
    }
}
