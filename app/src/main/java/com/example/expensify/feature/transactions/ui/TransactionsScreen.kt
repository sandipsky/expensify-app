package com.example.expensify.feature.transactions.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.PeriodPreset
import com.example.expensify.core.date.Periods
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.AppDatePickerDialog
import com.example.expensify.designsystem.components.DayHeader
import com.example.expensify.designsystem.components.EmptyState
import com.example.expensify.designsystem.components.EntryRow
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.MoneyField
import com.example.expensify.designsystem.components.SectionLabel
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.ui.labelRes
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TransactionRules.Filter
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate

@Composable
fun TransactionsRoute(
    onOpenEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionsScreen(
        state = state,
        onPreset = viewModel::setPreset,
        onCustomStart = viewModel::setCustomStart,
        onCustomEnd = viewModel::setCustomEnd,
        onSearch = viewModel::setSearch,
        onApplyFilter = viewModel::applyFilter,
        onRemoveChip = viewModel::removeChip,
        onClearFilters = viewModel::clearFilters,
        onOpen = onOpenEntry,
        onAdd = onAddEntry,
    )
}

/**
 * The period's entries newest first with income, expense and net of what's
 * shown, filters that combine, and search (req LST-01…LST-04, LST-08). A row
 * opens the entry's page; the FAB adds one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onPreset: (PeriodPreset) -> Unit,
    onCustomStart: (LocalDate) -> Unit,
    onCustomEnd: (LocalDate) -> Unit,
    onSearch: (String) -> Unit,
    onApplyFilter: (Filter) -> Unit,
    onRemoveChip: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val toolbar = state.toolbar

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tx_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "toolbar") {
                Toolbar(
                    toolbar = toolbar,
                    onPreset = onPreset,
                    onCustomStart = onCustomStart,
                    onCustomEnd = onCustomEnd,
                    onSearch = onSearch,
                    onOpenFilters = { filtersOpen = true },
                    onRemoveChip = onRemoveChip,
                    onClearFilters = onClearFilters,
                )
            }
            when (state) {
                is TransactionsUiState.Loading -> item(key = "loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Skeleton(84.dp)
                        Skeleton(200.dp)
                        Skeleton(140.dp)
                    }
                }
                is TransactionsUiState.Error -> item(key = "error") { ErrorState(state.message.asString()) }
                is TransactionsUiState.Content -> {
                    item(key = "totals") { TotalsStrip(state.totals) }
                    if (state.isEmpty) {
                        item(key = "empty") {
                            EmptyList(toolbar, onAdd = onAdd, onClearFilters = onClearFilters, onThisMonth = { onPreset(PeriodPreset.ThisMonth) })
                        }
                    } else {
                        item(key = "count") {
                            Text(
                                pluralStringResource(R.plurals.transactions_count, state.count, state.count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.days.forEach { day ->
                            item(key = "day-${day.date}") {
                                AppCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    DayHeader(label = day.label.asString(), net = day.net)
                                    day.rows.forEachIndexed { index, row ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                        EntryRow(row, onClick = { onOpen(row.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        TransactionFiltersSheet(
            filter = toolbar.filter,
            options = toolbar.options,
            onApply = { onApplyFilter(it); filtersOpen = false },
            onDismiss = { filtersOpen = false },
        )
    }
}

@StringRes
private fun PeriodPreset.labelRes(): Int = when (this) {
    PeriodPreset.ThisMonth -> R.string.period_this_month
    PeriodPreset.LastMonth -> R.string.period_last_month
    PeriodPreset.ThisYear -> R.string.period_this_year
    PeriodPreset.Custom -> R.string.period_custom
}


/** The period chips, the custom range, search, the filter button with its count, and the active filters as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    toolbar: TxToolbarUi,
    onPreset: (PeriodPreset) -> Unit,
    onCustomStart: (LocalDate) -> Unit,
    onCustomEnd: (LocalDate) -> Unit,
    onSearch: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveChip: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodPreset.entries.forEach { preset ->
                FilterChip(
                    selected = toolbar.preset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(stringResource(preset.labelRes())) },
                )
            }
        }
        Text(
            toolbar.periodLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (toolbar.preset == PeriodPreset.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(stringResource(R.string.tx_custom_from, DateFormats.shortDate(toolbar.custom.start))) { picking = "start" }
                DateChip(stringResource(R.string.tx_custom_to, DateFormats.shortDate(toolbar.custom.end))) { picking = "end" }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = toolbar.filter.search,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.tx_search_placeholder)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = if (toolbar.filter.search.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.tx_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
            val count = toolbar.filter.activeCount
            BadgedBox(
                badge = { if (count > 0) Badge { Text(count.toString()) } },
            ) {
                IconButton(onClick = onOpenFilters) {
                    Icon(
                        AppIcons.Filter,
                        contentDescription = if (count > 0) {
                            stringResource(R.string.tx_filters_set, count)
                        } else {
                            stringResource(R.string.tx_filters)
                        },
                    )
                }
            }
        }

        if (toolbar.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                toolbar.chips.forEach { chip ->
                    val label = chip.label.asString()
                    InputChip(
                        selected = true,
                        onClick = { onRemoveChip(chip.key) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                AppIcons.Close,
                                contentDescription = stringResource(R.string.tx_filter_remove, label),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                TextButton(onClick = onClearFilters) { Text(stringResource(R.string.tx_filters_clear_all)) }
            }
        }
    }

    when (picking) {
        "start" -> AppDatePickerDialog(initial = toolbar.custom.start, onPick = onCustomStart, onDismiss = { picking = null })
        "end" -> AppDatePickerDialog(initial = toolbar.custom.end, onPick = onCustomEnd, onDismiss = { picking = null })
    }
}

@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** Income, expense and net of what's on screen; transfers don't count (req LST-04). */
@Composable
private fun TotalsStrip(totals: TransactionRules.Totals) {
    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row {
            TotalCell(stringResource(R.string.tx_total_income), totals.income, AmountKind.Income, Modifier.weight(1f))
            TotalCell(stringResource(R.string.tx_total_expense), -totals.expense, AmountKind.Expense, Modifier.weight(1f))
            TotalCell(
                stringResource(R.string.tx_total_net),
                totals.net,
                when {
                    totals.net.isPositive -> AmountKind.Income
                    totals.net.isNegative -> AmountKind.Expense
                    else -> AmountKind.Neutral
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TotalCell(label: String, amount: Money, kind: AmountKind, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(
            money = amount,
            kind = kind,
            sign = Sign.ExceptZero,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Start,
        )
    }
}

/** "No matches" with a way to clear, or "No transactions in {period}" with Add and "Show this month" (req LST-08). */
@Composable
private fun EmptyList(toolbar: TxToolbarUi, onAdd: () -> Unit, onClearFilters: () -> Unit, onThisMonth: () -> Unit) {
    AppCard {
        if (toolbar.filter.isFiltering) {
            EmptyState(
                icon = AppIcons.Search,
                title = stringResource(R.string.tx_empty_no_matches_title),
                message = stringResource(R.string.tx_empty_no_matches_message, toolbar.periodLabel),
                action = { Button(onClick = onClearFilters) { Text(stringResource(R.string.tx_empty_clear)) } },
            )
        } else {
            EmptyState(
                icon = AppIcons.Transactions,
                title = stringResource(R.string.tx_empty_title, toolbar.periodLabel),
                message = stringResource(R.string.tx_empty_message),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdd) { Text(stringResource(R.string.tx_add)) }
                        if (toolbar.preset != PeriodPreset.ThisMonth) {
                            OutlinedButton(onClick = onThisMonth) { Text(stringResource(R.string.tx_empty_show_this_month)) }
                        }
                    }
                },
            )
        }
    }
}

/**
 * The list's filters besides the period and search (req LST-02): type,
 * accounts, categories of the chosen type and an amount range. They apply
 * on Apply; the search text passes through untouched.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFiltersSheet(
    filter: Filter,
    options: FilterOptions,
    onApply: (Filter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(filter.type) }
    val accountIds = remember { mutableStateListOf(*filter.accountIds.toTypedArray()) }
    val categoryIds = remember { mutableStateListOf(*filter.categoryIds.toTypedArray()) }
    var minText by remember { mutableStateOf(filter.minAmount?.let(::moneyText).orEmpty()) }
    var maxText by remember { mutableStateOf(filter.maxAmount?.let(::moneyText).orEmpty()) }

    /** Categories of the chosen type (both when none is); transfers have none. */
    val allowedTypes = when (type) {
        null -> CategoryType.entries.toList()
        TxType.Transfer -> emptyList()
        else -> listOfNotNull(type?.categoryType())
    }

    fun selectType(next: TxType?) {
        type = next
        val allowed = options.categories.filter { next == null || it.type == next.categoryType() }.map { it.id }.toSet()
        categoryIds.retainAll { it in allowed }
    }

    fun apply() {
        var min = Money.parse(minText)
        var max = Money.parse(maxText)
        if (min != null && max != null && min > max) min = max.also { max = min }
        onApply(
            Filter(
                type = type,
                accountIds = accountIds.toList(),
                categoryIds = if (type == TxType.Transfer) emptyList() else categoryIds.toList(),
                minAmount = min,
                maxAmount = max,
                search = filter.search,
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tx_filters), style = MaterialTheme.typography.titleLarge)

            SectionLabel(stringResource(R.string.tx_filter_type))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val choices: List<TxType?> = listOf(null) + TxType.entries
                choices.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = type == choice,
                        onClick = { selectType(choice) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                        label = { Text(stringResource(choice?.labelRes() ?: R.string.tx_filter_type_all)) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.tx_filter_accounts))
            if (options.accounts.isEmpty()) {
                Text(stringResource(R.string.tx_form_no_accounts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.accounts.forEach { account ->
                    val selected = account.id in accountIds
                    FilterChip(
                        selected = selected,
                        onClick = { if (selected) accountIds.remove(account.id) else accountIds.add(account.id) },
                        label = { Text(if (account.archived) stringResource(R.string.tx_form_archived_name, account.name) else account.name) },
                        leadingIcon = {
                            Icon(
                                if (selected) AppIcons.Check else IconMap.of(account.icon),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else dataColor(account.color),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            allowedTypes.forEach { categoryType ->
                val ofType = options.categories.filter { it.type == categoryType }
                if (ofType.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.tx_filter_categories_of, stringResource(categoryType.labelRes())))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ofType.forEach { category ->
                            val selected = category.id in categoryIds
                            FilterChip(
                                selected = selected,
                                onClick = { if (selected) categoryIds.remove(category.id) else categoryIds.add(category.id) },
                                label = { Text(if (category.archived) stringResource(R.string.tx_form_archived_name, category.name) else category.name) },
                                leadingIcon = {
                                    Icon(
                                        if (selected) AppIcons.Check else IconMap.of(category.icon),
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else dataColor(category.color),
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.tx_filter_amount))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(value = minText, onValueChange = { minText = it }, label = stringResource(R.string.tx_filter_amount_from), modifier = Modifier.weight(1f))
                MoneyField(value = maxText, onValueChange = { maxText = it }, label = stringResource(R.string.tx_filter_amount_to), modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        type = null
                        accountIds.clear()
                        categoryIds.clear()
                        minText = ""
                        maxText = ""
                    },
                ) {
                    Text(stringResource(R.string.tx_filters_clear_all))
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { apply() }) { Text(stringResource(R.string.tx_filters_apply)) }
            }
        }
    }
}

/** An amount as a filter field prefills it: "1200" or "1200.50". */
private fun moneyText(money: Money): String {
    val rupees = money.paisa / 100
    val paisa = money.paisa % 100
    return if (paisa == 0L) rupees.toString() else "$rupees.${paisa.toString().padStart(2, '0')}"
}

@Suppress("unused")
private val previewWidth = Modifier.width(0.dp)

private fun previewToolbar(filter: Filter = Filter()): TxToolbarUi {
    val range = Periods.month(SampleTransactions.today)
    return TxToolbarUi(
        preset = PeriodPreset.ThisMonth,
        custom = range,
        range = range,
        periodLabel = DateFormats.period(range),
        filter = filter,
        chips = emptyList(),
        options = FilterOptions(emptyList(), emptyList()),
    )
}

@PreviewLightDark
@Composable
private fun TransactionsPreview() {
    ExpensifyTheme {
        TransactionsScreen(
            state = TransactionsUiState.Content(
                toolbar = previewToolbar(),
                totals = TransactionRules.totalsOf(SampleTransactions.all),
                days = SampleTransactions.days,
                count = SampleTransactions.all.size,
            ),
            onPreset = {}, onCustomStart = {}, onCustomEnd = {}, onSearch = {}, onApplyFilter = {},
            onRemoveChip = {}, onClearFilters = {}, onOpen = {}, onAdd = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TransactionsEmptyPreview() {
    ExpensifyTheme {
        TransactionsScreen(
            state = TransactionsUiState.Content(
                toolbar = previewToolbar(),
                totals = TransactionRules.Totals(Money.ZERO, Money.ZERO),
                days = emptyList(),
                count = 0,
            ),
            onPreset = {}, onCustomStart = {}, onCustomEnd = {}, onSearch = {}, onApplyFilter = {},
            onRemoveChip = {}, onClearFilters = {}, onOpen = {}, onAdd = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TransactionsLoadingPreview() {
    ExpensifyTheme {
        TransactionsScreen(
            state = TransactionsUiState.Loading(previewToolbar()),
            onPreset = {}, onCustomStart = {}, onCustomEnd = {}, onSearch = {}, onApplyFilter = {},
            onRemoveChip = {}, onClearFilters = {}, onOpen = {}, onAdd = {},
        )
    }
}

@Suppress("unused")
private val previewRange: DateRange = Periods.month(SampleTransactions.today)
