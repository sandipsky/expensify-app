package com.example.expensify.feature.auth.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.domain.MpinRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One dot per MPIN digit, filled as they are typed; in the error colour after a wrong one. */
@Composable
fun PinDots(filled: Int, error: Boolean, modifier: Modifier = Modifier, length: Int = MpinRules.LENGTH) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.lock_dots_entered, filled, length)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(length) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (on) color else Color.Transparent)
                    .border(2.dp, if (on) color else empty, CircleShape),
            )
        }
    }
}

/**
 * The MPIN keypad: the digits, a fingerprint key when the phone can take one
 * (always live, since a biometric still unlocks during an MPIN wait) and
 * backspace. Its own keys rather than the system keyboard, so it works the
 * same over the lock window and never offers suggestions.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { digit -> DigitKey(digit, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            if (onBiometric != null) {
                IconKey(AppIcons.Fingerprint, stringResource(R.string.lock_use_biometrics), enabled = true, onClick = onBiometric)
            } else {
                Spacer(Modifier.size(KEY_SIZE))
            }
            DigitKey('0', enabled) { onDigit('0') }
            IconKey(AppIcons.Backspace, stringResource(R.string.lock_backspace), enabled = enabled, onClick = onBackspace)
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Key(enabled, onClick) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IconKey(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    Key(enabled, onClick) {
        Icon(icon, contentDescription = contentDescription, Modifier.size(28.dp))
    }
}

@Composable
private fun Key(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private val KEY_SIZE = 72.dp
private val KEY_GAP = 20.dp

/** Counts [ms] down into [onTick] a second at a time (the seconds left, then 0), for the wait after wrong MPINs. */
internal fun CoroutineScope.countDown(ms: Long, onTick: (Int) -> Unit): Job = launch {
    var left = ((ms + 999) / 1000).toInt()
    while (left > 0) {
        onTick(left)
        delay(1_000)
        left--
    }
    onTick(0)
}

@PreviewLightDark
@Composable
private fun PinPadPreview() {
    ExpensifyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                PinDots(filled = 2, error = false)
                PinPad(onDigit = {}, onBackspace = {}, onBiometric = {}, enabled = true)
            }
        }
    }
}
