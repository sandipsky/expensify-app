package com.example.expensify.designsystem.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import kotlin.math.atan2
import kotlin.math.hypot

@Immutable
data class DonutSegment(val key: String, val label: String, val value: Money, val color: Color)

/**
 * A ring split by value, with a slot in the middle for the total and a
 * legend under it listing each slice with its share and amount. Tapping a
 * slice or its legend row opens it when the chart is clickable.
 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thickness: Dp = 22.dp,
    format: (Money) -> String = { MoneyFormat.full(it) },
    onSegmentClick: ((DonutSegment) -> Unit)? = null,
    center: @Composable () -> Unit = {},
) {
    val total = segments.sumOf { it.value.paisa.coerceAtLeast(0) }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val gapDegrees = if (segments.size > 1) 2f else 0f

    /** The slice under a point on the ring, if any: its angle from 12 o'clock, clockwise. */
    fun segmentAt(offset: Offset, canvas: Size): DonutSegment? {
        if (total <= 0) return null
        val centerX = canvas.width / 2
        val centerY = canvas.height / 2
        val distance = hypot(offset.x - centerX, offset.y - centerY)
        if (distance > canvas.minDimension / 2) return null
        var angle = Math.toDegrees(atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble())).toFloat() + 90f
        if (angle < 0) angle += 360f
        var start = 0f
        for (segment in segments) {
            val sweep = 360f * segment.value.paisa.coerceAtLeast(0) / total
            if (angle >= start && angle < start + sweep) return segment
            start += sweep
        }
        return null
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(size)
                    .semantics { this.contentDescription = contentDescription }
                    .pointerInput(segments, onSegmentClick) {
                        if (onSegmentClick == null) return@pointerInput
                        detectTapGestures { offset ->
                            segmentAt(offset, Size(this.size.width.toFloat(), this.size.height.toFloat()))?.let(onSegmentClick)
                        }
                    },
            ) {
                val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt)
                val inset = thickness.toPx() / 2
                val arcSize = Size(this.size.width - thickness.toPx(), this.size.height - thickness.toPx())
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                if (total <= 0) return@Canvas
                var start = -90f
                for (segment in segments) {
                    val sweep = 360f * segment.value.paisa.coerceAtLeast(0) / total
                    val drawn = (sweep - gapDegrees).coerceAtLeast(0f)
                    if (drawn > 0f) {
                        drawArc(segment.color, start + gapDegrees / 2, drawn, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(thickness + 8.dp)) { center() }
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.fillMaxWidth()) {
            for (segment in segments) {
                val share = if (total > 0) Math.floorDiv(segment.value.paisa * 200 + total, total * 2) else 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (onSegmentClick != null) Modifier.clickable { onSegmentClick(segment) } else Modifier)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendItem(label = segment.label, color = segment.color, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$share%",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                        maxLines = 1,
                    )
                    Text(
                        format(segment.value),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
