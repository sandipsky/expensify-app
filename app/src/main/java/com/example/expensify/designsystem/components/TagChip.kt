package com.example.expensify.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.expensify.designsystem.theme.ExpensifyTheme

enum class TagTone { Neutral, Info, Warn, Error, Success }

/** A small read-only label: "Archived", "Not in total", "Upcoming", a budget state (with its icon, never color alone). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.Neutral, icon: ImageVector? = null) {
    val colors = ExpensifyTheme.colors
    val (background, foreground) = when (tone) {
        TagTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        TagTone.Info -> colors.infoContainer to colors.info
        TagTone.Warn -> colors.warnContainer to colors.warn
        TagTone.Error -> colors.expenseContainer to colors.expense
        TagTone.Success -> colors.incomeContainer to colors.income
    }
    Row(
        modifier = modifier
            .background(background, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Suppress("unused")
private val Unused = Color.Unspecified
