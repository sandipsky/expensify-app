package com.example.expensify.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.Sign

/** One column of a report table: a header, a width, and how a row and the totals row fill it. */
@Immutable
class TableColumn<T>(
    val key: String,
    val header: String,
    val width: Dp,
    /** Amounts and counts line up on the right. */
    val numeric: Boolean = false,
    val cell: @Composable (T) -> Unit,
    /** The totals row's cell; the row shows when any column has one. */
    val total: (@Composable () -> Unit)? = null,
)

/**
 * A report's table on a phone: the first column stays put while the rest
 * scroll sideways together, numbers line up on the right, a totals row
 * closes it, and a row opens something when the caller says so.
 */
@Composable
fun <T> ReportTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    rowKey: (T) -> Any,
    modifier: Modifier = Modifier,
    onRowClick: ((T) -> Unit)? = null,
    /** Whether a row opens; all of them do when left out. */
    canOpen: (T) -> Boolean = { true },
    /** Marks a row, such as the selected one. */
    isCurrent: (T) -> Boolean = { false },
    /** Dims a row, such as a period from before a budget started. */
    isMuted: (T) -> Boolean = { false },
) {
    val scroll = rememberScrollState()
    val first = columns.first()
    val rest = columns.drop(1)
    val hasTotals = columns.any { it.total != null }
    val hairline = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxWidth()) {
        TableRow(firstWidth = first.width, scroll = scroll, minHeight = 40.dp, first = { HeaderText(first.header, first.numeric) }) {
            for (column in rest) Box(Modifier.width(column.width)) { HeaderText(column.header, column.numeric) }
        }
        HorizontalDivider(color = hairline)
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = hairline)
            val opens = onRowClick != null && canOpen(row)
            val current = isCurrent(row)
            TableRow(
                firstWidth = first.width,
                scroll = scroll,
                modifier = Modifier
                    .then(if (opens) Modifier.clickable(onClick = { onRowClick?.invoke(row) }) else Modifier)
                    .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                    .alpha(if (isMuted(row)) 0.6f else 1f)
                    .semantics {
                        if (opens) role = Role.Button
                        if (current) selected = true
                    },
                first = { first.cell(row) },
            ) {
                for (column in rest) Box(Modifier.width(column.width), contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart) { column.cell(row) }
            }
        }
        if (hasTotals) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            TableRow(firstWidth = first.width, scroll = scroll, first = { first.total?.invoke() }) {
                for (column in rest) Box(Modifier.width(column.width), contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart) { column.total?.invoke() }
            }
        }
    }
}

@Composable
private fun TableRow(
    firstWidth: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    first: @Composable () -> Unit,
    rest: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(firstWidth)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { first() }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            rest()
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun HeaderText(text: String, numeric: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(end = if (numeric) 8.dp else 0.dp),
    )
}

/** Plain text in a cell: one line, ellipsized, right-aligned when numeric. */
@Composable
fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    emphasis: Boolean = false,
    color: Color = LocalContentColor.current,
) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(end = if (numeric) 8.dp else 0.dp),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFeatureSettings = if (numeric) "tnum" else null,
            fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
    )
}

/** A money cell: its sign and color beside the figure, never color alone. */
@Composable
fun TableMoneyCell(
    money: Money,
    modifier: Modifier = Modifier,
    kind: AmountKind = AmountKind.Neutral,
    sign: Sign = Sign.Auto,
    emphasis: Boolean = false,
) {
    AmountText(
        money = money,
        kind = kind,
        sign = sign,
        modifier = modifier.fillMaxWidth().padding(end = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal),
    )
}

/** A cell with a caption under its text: a period and when it ends, a name and its type. */
@Composable
fun TableTextWithCaption(text: String, caption: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!caption.isNullOrEmpty()) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A figure with a smaller one under it: an amount and its change, a limit and what was carried. */
@Composable
fun TableFigureWithSub(modifier: Modifier = Modifier, figure: @Composable () -> Unit, sub: String?) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        figure()
        if (!sub.isNullOrEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
