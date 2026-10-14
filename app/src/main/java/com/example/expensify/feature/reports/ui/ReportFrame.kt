package com.example.expensify.feature.reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.ErrorState
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.reports.domain.ReportKey

/**
 * A report page (port of ReportFrame.tsx): Back to Reports, the report's
 * name and what it shows, Reset while the filters differ from the defaults,
 * the filters as chips, a line saying what the report covers, then the
 * report itself. Skeletons stand in while it loads, and an error replaces
 * it when a query fails.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportFrame(
    key: ReportKey,
    filtered: Boolean,
    summary: String,
    loading: Boolean,
    error: UiText?,
    onBack: () -> Unit,
    onReset: () -> Unit,
    filters: @Composable FlowRowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val info = ReportLabels.info(key)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(info.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.report_back_to_reports))
                    }
                },
                actions = {
                    if (filtered) {
                        IconButton(onClick = onReset) {
                            Icon(AppIcons.Reset, contentDescription = stringResource(R.string.report_reset))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "report-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = IconMap.of(info.icon), color = dataColor(info.colorHex), size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(info.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "report-filters") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        content = filters,
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                error != null -> item(key = "report-error") { AppCard { ErrorState(error.asString()) } }
                loading -> item(key = "report-loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Skeleton(84.dp)
                        Skeleton(260.dp)
                        Skeleton(320.dp)
                    }
                }
                else -> content()
            }
        }
    }
}

/** How a tile's value reads: income green or expense red, always with its sign in the text (req NFR-06). */
enum class TileTone { Income, Expense }

/** One headline figure: a label, the value, and a line under it such as an average or the change. */
@Immutable
data class ReportTile(val label: String, val value: String, val tone: TileTone? = null, val note: String? = null)

/** The report's headline figures, two to a row (port of ReportTiles.tsx). */
@Composable
fun ReportTiles(tiles: List<ReportTile>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (tile in pair) {
                    AppCard(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tile.value,
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                            color = when (tile.tone) {
                                TileTone.Income -> ExpensifyTheme.colors.income
                                TileTone.Expense -> ExpensifyTheme.colors.expense
                                null -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (tile.note != null) {
                            Text(
                                tile.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A card with a title line, as the charts and tables sit in. */
@Composable
fun ReportCard(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    AppCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.padding(contentPadding)) { content() }
    }
}

/** A line of small print under a report, such as how balances are worked out. */
@Composable
fun ReportNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
