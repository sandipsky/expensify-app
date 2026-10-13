package com.example.expensify.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.expensify.designsystem.theme.ExpensifyTheme

enum class BannerTone { Error, Warn, Info, Success }

/** An inline notice inside a page or form: a failed save, an archived account's note. */
@Composable
fun Banner(text: String, tone: BannerTone, modifier: Modifier = Modifier) {
    val colors = ExpensifyTheme.colors
    val (background, foreground) = when (tone) {
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BannerTone.Warn -> colors.warnContainer to MaterialTheme.colorScheme.onSurface
        BannerTone.Info -> colors.infoContainer to MaterialTheme.colorScheme.onSurface
        BannerTone.Success -> colors.incomeContainer to MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = foreground,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
