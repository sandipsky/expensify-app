package com.example.expensify.designsystem.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.theme.ExpensifyTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What a series stands for, which picks its color: green income or assets, red expense or liabilities, the accent, or grey. */
enum class ChartTone { Income, Expense, Accent, Neutral }

@Immutable
data class ChartSeries(val key: String, val label: String, val tone: ChartTone)

/** One column of a bar chart: its short axis label, a fuller title, and a value per series. */
@Immutable
data class BarGroup(
    val key: String,
    val label: String,
    val title: String,
    val values: Map<String, Money>,
    /** A figure written over the column, such as the period's net; only when there are few columns. */
    val caption: String? = null,
)

/** Captions over the columns only fit under a handful of them on a phone. */
private const val MOST_CAPTIONS = 6

@Composable
private fun ChartTone.color(): Color = when (this) {
    ChartTone.Income -> ExpensifyTheme.colors.income
    ChartTone.Expense -> ExpensifyTheme.colors.expense
    ChartTone.Accent -> MaterialTheme.colorScheme.primary
    ChartTone.Neutral -> MaterialTheme.colorScheme.outline
}

/** A tick step that reads well: 1, 2, 2.5 or 5 times a power of ten, at least `raw`. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/**
 * Grouped bars on a Canvas: one column per group, one bar per series, with
 * a compact money axis, four grid lines, thinned labels and a legend. A tap
 * opens the group when the chart is clickable, otherwise it reads the
 * group's values out under the chart, since a phone has no hover.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarChart(
    groups: List<BarGroup>,
    series: List<ChartSeries>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    format: (Money) -> String = { MoneyFormat.full(it) },
    tickFormat: (Money) -> String = { MoneyFormat.compact(it) },
    selectedKey: String? = null,
    onGroupClick: ((BarGroup) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val captionStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val colors = series.associate { it.key to it.tone.color() }
    var tapped by remember(groups) { mutableStateOf<String?>(null) }

    // Scale: the tallest bar, on a step that reads well, so every grid line is a round figure.
    val maxPaisa = groups.maxOfOrNull { g -> g.values.values.maxOfOrNull { it.paisa } ?: 0L } ?: 0L
    val step = niceStep(maxPaisa / 100.0 / 4)
    val top = maxOf(step, ceil(maxPaisa / 100.0 / step) * step)
    val ticks = (0..(top / step).roundToInt()).map { Money((it * step * 100).roundToLong()) }
    val axisWidthPx = ticks.maxOf { textMeasurer.measure(tickFormat(it), labelStyle).size.width } + with(density) { 8.dp.toPx() }
    val showCaptions = groups.size <= MOST_CAPTIONS && groups.any { it.caption != null }
    // Every nth label keeps its text so the widest one never runs into its neighbour.
    val widestLabelPx = groups.maxOfOrNull { textMeasurer.measure(it.label, labelStyle, maxLines = 1).size.width } ?: 0

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(groups, onGroupClick) {
                    detectTapGestures { offset ->
                        val plotLeft = axisWidthPx
                        val groupWidth = (size.width - plotLeft) / groups.size.coerceAtLeast(1)
                        val index = ((offset.x - plotLeft) / groupWidth).toInt()
                        if (index !in groups.indices) return@detectTapGestures
                        val group = groups[index]
                        if (onGroupClick != null) onGroupClick(group) else tapped = if (tapped == group.key) null else group.key
                    }
                },
        ) {
            val labelHeight = textMeasurer.measure("Sep", labelStyle).size.height
            val captionHeight = if (showCaptions) textMeasurer.measure("0", captionStyle).size.height + 4.dp.toPx() else 0f
            val plotLeft = axisWidthPx
            val plotRight = size.width - 4.dp.toPx()
            val plotTop = captionHeight + 4.dp.toPx()
            val plotBottom = size.height - labelHeight - 6.dp.toPx()
            val plotHeight = plotBottom - plotTop
            val scale: Float = if (top > 0) (plotHeight / (top * 100)).toFloat() else 0f

            // Grid and axis.
            ticks.forEachIndexed { i, tick ->
                val y = plotBottom - tick.paisa * scale
                drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1f)
                val measured = textMeasurer.measure(tickFormat(tick), labelStyle)
                val textY = when (i) {
                    0 -> y - measured.size.height
                    else -> (y - measured.size.height / 2).coerceAtLeast(0f)
                }
                drawText(measured, topLeft = Offset(plotLeft - measured.size.width - 4.dp.toPx(), textY))
            }

            if (groups.isEmpty()) return@Canvas
            val groupWidth = (plotRight - plotLeft) / groups.size
            val labelStep = ceil((widestLabelPx + 8.dp.toPx()) / groupWidth).toInt().coerceAtLeast(1)
            val inner = groupWidth * 0.72f
            val barWidth = (inner / series.size.coerceAtLeast(1)).coerceAtLeast(2f)
            val radius = CornerRadius(2.dp.toPx())

            groups.forEachIndexed { i, group ->
                val left = plotLeft + i * groupWidth
                val highlighted = group.key == selectedKey || group.key == tapped
                if (highlighted) {
                    drawRoundRect(highlight, Offset(left + 1.dp.toPx(), plotTop), Size(groupWidth - 2.dp.toPx(), plotBottom - plotTop), CornerRadius(4.dp.toPx()))
                }
                var x = left + (groupWidth - barWidth * series.size) / 2
                for (s in series) {
                    val value = group.values[s.key] ?: Money.ZERO
                    val barHeight = (value.paisa.coerceAtLeast(0) * scale).coerceAtMost(plotHeight)
                    if (barHeight > 0f) {
                        drawRoundRect(colors.getValue(s.key), Offset(x, plotBottom - barHeight), Size(barWidth, barHeight), radius)
                    }
                    x += barWidth
                }
                if (i % labelStep == 0 && group.label.isNotEmpty()) {
                    val measured = textMeasurer.measure(group.label, labelStyle, overflow = TextOverflow.Clip, maxLines = 1)
                    if (measured.size.width <= groupWidth * labelStep) {
                        drawText(measured, topLeft = Offset(left + (groupWidth - measured.size.width) / 2, plotBottom + 4.dp.toPx()))
                    }
                }
                if (showCaptions && group.caption != null) {
                    val measured = textMeasurer.measure(group.caption, captionStyle, maxLines = 1)
                    if (measured.size.width + 4.dp.toPx() <= groupWidth) {
                        drawText(measured, topLeft = Offset(left + (groupWidth - measured.size.width) / 2, 0f))
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (s in series) LegendItem(label = s.label, color = colors.getValue(s.key))
        }

        val readout = groups.firstOrNull { it.key == tapped }
        if (readout != null) {
            Text(
                text = readout.title + " · " + series.joinToString(" · ") { "${it.label} ${format(readout.values[it.key] ?: Money.ZERO)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A colored dot and a label, as every chart's legend reads. */
@Composable
fun LegendItem(label: String, color: Color, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.labelMedium) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
