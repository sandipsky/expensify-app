package com.example.expensify.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.expensify.designsystem.theme.ExpensifyTheme

/** Accent under 80 %, warning from 80 %, error from 100 % (req ACC-04, BR-08). */
enum class MeterTone { Accent, Warn, Error }

fun meterToneOf(percent: Double): MeterTone = when {
    percent >= 100 -> MeterTone.Error
    percent >= 80 -> MeterTone.Warn
    else -> MeterTone.Accent
}

/** A thin rounded bar: credit used, budget spent. */
@Composable
fun Meter(
    fraction: Float,
    tone: MeterTone,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = ExpensifyTheme.colors
    val fill = when (tone) {
        MeterTone.Accent -> MaterialTheme.colorScheme.primary
        MeterTone.Warn -> colors.warn
        MeterTone.Error -> colors.expense
    }
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = clamped, label = "meter")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                if (label != null) contentDescription = label
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(fill),
        )
    }
}
