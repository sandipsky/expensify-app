package com.example.expensify.feature.auth.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.R
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme

/**
 * The lock screen over the signed-in shell (Settings › Security). It is its
 * own window, so it also covers a sheet or dialog that was open when the app
 * was put away, and the shell underneath keeps its state for when it opens
 * again. The MPIN keypad is always there; where the phone has a fingerprint
 * or face, its prompt shows as soon as the lock engages and again from the
 * keypad's fingerprint key. Back puts the app away instead of reaching the
 * shell.
 */
@Composable
fun LockRoute(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generation by viewModel.generation.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val haptics = LocalHapticFeedback.current
    // Read once per lock: enrolling a fingerprint means leaving the app, which engages the lock again.
    val biometrics = remember(generation) { viewModel.biometrics.availability() == Biometrics.Availability.Available }
    val askBiometric = rememberBiometricAsk(
        biometrics = viewModel.biometrics,
        auto = biometrics,
        autoKey = generation,
        title = stringResource(R.string.lock_prompt_title),
        subtitle = stringResource(R.string.lock_prompt_subtitle),
        onOutcome = viewModel::biometricFinished,
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LockEvent.Wrong -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler { activity?.moveTaskToBack(true) }
        // Its own window: no dim, and system bar icons that suit the theme, as the activity's edge-to-edge setup gives the shell.
        val view = LocalView.current
        val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        LockScreen(
            state = state,
            biometrics = biometrics,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onBiometric = askBiometric,
            onForgot = viewModel::forgot,
        )
    }
}

@Composable
fun LockScreen(
    state: LockUiState,
    biometrics: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // What this is and how it is going, centred in whatever room the keypad leaves.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(icon = AppIcons.Lock, color = MaterialTheme.colorScheme.primary, size = 64.dp)
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(
                        stringResource(if (biometrics) R.string.lock_subtitle_biometrics else R.string.lock_subtitle_pin),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    PinDots(filled = state.entered.length, error = state.note != null)
                    // Room kept for the note, so the dots and keypad never jump when it comes and goes.
                    Box(Modifier.heightIn(min = 40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.note != null) {
                            Text(
                                state.note.asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            PinPad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = if (biometrics) onBiometric else null,
                enabled = state.padOpen,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgot) { Text(stringResource(R.string.lock_forgot)) }
        }
    }
}

@PreviewLightDark
@Composable
private fun LockScreenPreview() {
    ExpensifyTheme {
        LockScreen(
            state = LockUiState(entered = "48", note = plain("Wrong MPIN · 3 tries left")),
            biometrics = true,
            onDigit = {}, onBackspace = {}, onBiometric = {}, onForgot = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun LockScreenWaitingPreview() {
    ExpensifyTheme {
        LockScreen(
            state = LockUiState(waitSeconds = 27, note = plain("Too many wrong tries · Try again in 0:27")),
            biometrics = false,
            onDigit = {}, onBackspace = {}, onBiometric = {}, onForgot = {},
        )
    }
}
