package com.example.expensify.feature.auth.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.example.expensify.R
import kotlinx.coroutines.launch

/**
 * The phone's fingerprint or face prompt from a screen: shown once on its own
 * each time [autoKey] changes while [auto] is true (as soon as the activity
 * is resumed, which the prompt needs), and again whenever the returned
 * function is called, with "Use MPIN" as its other button. One prompt at a
 * time; [onOutcome] gets each answer.
 */
@Composable
fun rememberBiometricAsk(
    biometrics: Biometrics,
    auto: Boolean,
    autoKey: Any?,
    title: String,
    subtitle: String?,
    onOutcome: (Biometrics.Outcome) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current as? FragmentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val negative = stringResource(R.string.lock_prompt_use_pin)
    val latestOutcome by rememberUpdatedState(onOutcome)
    var inFlight by remember { mutableStateOf(false) }

    suspend fun ask() {
        if (activity == null || inFlight) return
        inFlight = true
        try {
            latestOutcome(biometrics.authenticate(activity, title, subtitle, negative))
        } finally {
            inFlight = false
        }
    }

    LaunchedEffect(autoKey, auto) {
        if (auto) {
            lifecycle.withResumed { }
            ask()
        }
    }
    return { scope.launch { ask() } }
}
