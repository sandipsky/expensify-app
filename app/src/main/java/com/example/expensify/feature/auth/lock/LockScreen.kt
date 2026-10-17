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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import com.example.expensify.R
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import kotlinx.coroutines.launch

/**
 * The lock screen over the signed-in shell (Settings › Security). It is its
 * own window, so it also covers a sheet or dialog that was open when the app
 * was put away, and the shell underneath keeps its state for when it opens
 * again. The phone's prompt shows as soon as the lock engages; a cancelled
 * prompt waits for the Unlock button. Back puts the app away instead of
 * reaching the shell.
 */
@Composable
fun LockRoute(viewModel: LockViewModel = hiltViewModel()) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val generation by viewModel.generation.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    var inFlight by remember { mutableStateOf(false) }

    suspend fun ask() {
        if (activity == null || inFlight) return
        inFlight = true
        try {
            if (viewModel.biometrics.availability() == Biometrics.Availability.NoScreenLock) {
                viewModel.skip()
            } else {
                viewModel.finished(viewModel.biometrics.authenticate(activity, title, subtitle))
            }
        } finally {
            inFlight = false
        }
    }

    // Once each time the lock engages, as soon as the activity is on screen (the prompt needs it to be).
    LaunchedEffect(generation) {
        lifecycle.withResumed { }
        ask()
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
        LockScreen(note = note, onUnlock = { scope.launch { ask() } }, onLogout = viewModel::logout)
    }
}

@Composable
fun LockScreen(note: UiText?, onUnlock: () -> Unit, onLogout: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
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
                    stringResource(R.string.lock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(AppIcons.Fingerprint, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lock_unlock))
                }
                if (note != null) {
                    Text(
                        note.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(onClick = onLogout) { Text(stringResource(R.string.more_log_out)) }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LockScreenPreview() {
    ExpensifyTheme {
        LockScreen(note = plain("Too many attempts · Wait a moment, then try again."), onUnlock = {}, onLogout = {})
    }
}
