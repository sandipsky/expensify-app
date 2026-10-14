package com.example.expensify.feature.budgets.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.example.expensify.designsystem.components.LoadingScreen
import com.example.expensify.designsystem.components.MoneyField
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.categories.ui.SampleCategories

@Composable
fun BudgetFormRoute(
    onDone: () -> Unit,
    viewModel: BudgetFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BudgetFormEvent.Saved -> onDone()
            }
        }
    }
    BudgetFormScreen(
        state = state,
        onPeriodChange = viewModel::onPeriodChange,
        onAmountChange = viewModel::onAmountChange,
        onCategoryToggle = viewModel::onCategoryToggle,
        onNameChange = viewModel::onNameChange,
        onRolloverChange = viewModel::onRolloverChange,
        onAlertsChange = viewModel::onAlertsChange,
        onSave = viewModel::save,
        onClose = onDone,
    )
}

/** Full screen: period, limit, categories as chips, name, rollover and alerts. Save stays pinned at the bottom. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetFormScreen(
    state: BudgetFormUiState,
    onPeriodChange: (BudgetPeriod) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryToggle: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
    onAlertsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEdit) R.string.budget_form_edit_title else R.string.budget_form_add_title)) },
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
                                else -> R.string.budget_form_submit_add
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(stringResource(R.string.budget_form_period))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val periods = BudgetPeriod.entries
                periods.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { onPeriodChange(period) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        label = { Text(stringResource(period.labelRes())) },
                    )
                }
            }
            Hint(stringResource(R.string.budget_form_this_period, state.thisPeriod))

            MoneyField(
                value = state.amountText,
                onValueChange = onAmountChange,
                label = stringResource(R.string.budget_form_limit, stringResource(state.period.labelRes())),
                error = state.amountError?.asString(),
                imeAction = ImeAction.Next,
            )

            SectionLabel(stringResource(R.string.budget_form_categories))
            if (state.options.isEmpty()) {
                Hint(stringResource(R.string.budget_form_no_categories))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.options.forEach { option ->
                        val selected = option.id in state.categoryIds
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryToggle(option.id) },
                            label = {
                                Text(if (option.archived) stringResource(R.string.budget_form_category_archived, option.name) else option.name)
                            },
                            leadingIcon = {
                                Icon(
                                    if (selected) AppIcons.Check else IconMap.of(option.icon),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else dataColor(option.color),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                Hint(stringResource(R.string.budget_form_categories_hint))
            }

            AppTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.budget_form_name),
                error = state.nameError?.asString(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )

            SwitchRow(
                label = stringResource(R.string.budget_form_rollover),
                hint = stringResource(R.string.budget_form_rollover_hint, stringResource(state.period.previousRes())),
                checked = state.rollover,
                onCheckedChange = onRolloverChange,
            )
            SwitchRow(
                label = stringResource(R.string.budget_form_alerts),
                hint = stringResource(R.string.budget_form_alerts_hint),
                checked = state.alerts,
                onCheckedChange = onAlertsChange,
            )

            state.error?.let { Banner(it.asString(), BannerTone.Error) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Hint(hint)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@PreviewLightDark
@Composable
private fun BudgetFormPreview() {
    ExpensifyTheme {
        BudgetFormScreen(
            state = BudgetFormUiState(
                loading = false,
                thisPeriod = "October 2026",
                amountText = "15000",
                categoryIds = listOf(SampleCategories.food.id),
                options = SampleCategories.expense.active.map { CategoryOption(it.id, it.name, it.icon, it.color, false) },
                name = "Food and dining",
            ),
            onPeriodChange = {}, onAmountChange = {}, onCategoryToggle = {}, onNameChange = {},
            onRolloverChange = {}, onAlertsChange = {}, onSave = {}, onClose = {},
        )
    }
}
