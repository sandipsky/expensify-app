package com.example.expensify.feature.auth.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.domain.LockRules
import com.example.expensify.feature.auth.domain.MpinRules
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › Security opened the MPIN screen for; `stored` is the route argument. */
enum class AppLockPinPurpose(val stored: String) {
    /** Choose an MPIN, then the lock is on. */
    TurnOn("on"),

    /** Prove the current MPIN (or a biometric), then choose a new one. */
    Change("change"),

    /** Prove the current MPIN (or a biometric), then the lock is off. */
    TurnOff("off"),
    ;

    companion object {
        fun fromStored(value: String?): AppLockPinPurpose? = entries.firstOrNull { it.stored == value }
    }
}

enum class PinStep {
    /** The current MPIN, or the phone's fingerprint or face. */
    Verify,

    /** A new MPIN. */
    Enter,

    /** The new MPIN again. */
    Confirm,
}

data class AppLockPinUiState(
    val purpose: AppLockPinPurpose,
    val step: PinStep,
    val entered: String = "",
    val note: UiText? = null,
    /** Seconds the keypad is still closed after too many wrong MPINs; 0 while it is open. */
    val waitSeconds: Int = 0,
    /** An MPIN is being checked or saved. */
    val busy: Boolean = false,
) {
    val padOpen: Boolean get() = waitSeconds == 0 && !busy
}

sealed interface AppLockPinEvent {
    data object Done : AppLockPinEvent

    /** A wrong or mismatched MPIN, for the haptic. */
    data object Wrong : AppLockPinEvent
}

/**
 * Settings › Security › the MPIN screen: turn the lock on by choosing an MPIN
 * twice, change it after proving the current one, or turn the lock off after
 * proving it. Proving takes the MPIN or the phone's fingerprint or face,
 * never its own PIN or password. A wrong current MPIN counts towards the
 * same wait as on the lock screen.
 */
@HiltViewModel
class AppLockPinViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val appLock: AppLock,
    private val messages: Messages,
    val biometrics: Biometrics,
) : ViewModel() {

    private val purpose = AppLockPinPurpose.fromStored(savedState.toRoute<Routes.AppLockPin>().purpose) ?: AppLockPinPurpose.TurnOn

    private val _state = MutableStateFlow(
        AppLockPinUiState(purpose, step = if (purpose == AppLockPinPurpose.TurnOn) PinStep.Enter else PinStep.Verify),
    )
    val state: StateFlow<AppLockPinUiState> = _state.asStateFlow()

    private val _events = Channel<AppLockPinEvent>(Channel.BUFFERED)
    val events: Flow<AppLockPinEvent> = _events.receiveAsFlow()

    /** The new MPIN as first typed, waiting for its confirmation. */
    private var chosen: String? = null
    private var wait: Job? = null

    init {
        if (purpose != AppLockPinPurpose.TurnOn) viewModelScope.launch { startWait(appLock.remainingWait()) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.padOpen) return
        val next = current.entered + digit
        if (!MpinRules.isPartial(next)) return
        _state.update { it.copy(entered = next, note = null) }
        if (MpinRules.isComplete(next)) complete(next)
    }

    fun onBackspace() {
        val current = _state.value
        if (!current.padOpen || current.entered.isEmpty()) return
        _state.update { it.copy(entered = it.entered.dropLast(1)) }
    }

    private fun complete(pin: String) {
        when (_state.value.step) {
            PinStep.Verify -> verify(pin)
            PinStep.Enter -> {
                chosen = pin
                _state.update { it.copy(step = PinStep.Confirm, entered = "", note = null) }
            }
            PinStep.Confirm -> {
                if (pin == chosen) {
                    finish(pin)
                } else {
                    chosen = null
                    _state.update { it.copy(step = PinStep.Enter, entered = "", note = res(R.string.pin_mismatch)) }
                    _events.trySend(AppLockPinEvent.Wrong)
                }
            }
        }
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = appLock.checkPin(pin)) {
                PinCheck.Correct -> verified()
                is PinCheck.Wrong -> {
                    _state.update {
                        it.copy(entered = "", busy = false, note = UiText.Plural(R.plurals.lock_wrong_pin, result.triesLeft, listOf(result.triesLeft)))
                    }
                    _events.trySend(AppLockPinEvent.Wrong)
                }
                is PinCheck.Wait -> {
                    _state.update { it.copy(entered = "", busy = false) }
                    _events.trySend(AppLockPinEvent.Wrong)
                    startWait(result.remainingMs)
                }
            }
        }
    }

    /** It is them: the lock goes off, or the new MPIN is next. */
    private fun verified() {
        when (purpose) {
            AppLockPinPurpose.TurnOff -> viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                appLock.disable()
                messages.show(res(R.string.settings_app_lock_off))
                _events.trySend(AppLockPinEvent.Done)
            }
            AppLockPinPurpose.Change, AppLockPinPurpose.TurnOn -> _state.update { it.copy(step = PinStep.Enter, entered = "", busy = false, note = null) }
        }
    }

    private fun finish(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            if (purpose == AppLockPinPurpose.TurnOn) {
                appLock.enable(pin)
                messages.show(res(R.string.settings_app_lock_on))
            } else {
                appLock.changePin(pin)
                messages.show(res(R.string.settings_app_lock_pin_changed))
            }
            _events.trySend(AppLockPinEvent.Done)
        }
    }

    private fun startWait(ms: Long) {
        if (ms <= 0) return
        wait?.cancel()
        wait = viewModelScope.countDown(ms) { seconds ->
            _state.update {
                if (seconds > 0) it.copy(entered = "", waitSeconds = seconds, note = res(R.string.lock_wait, LockRules.waitText(seconds)))
                else it.copy(waitSeconds = 0, note = null)
            }
        }
    }

    /** What the phone's fingerprint or face prompt came to, on the Verify step. */
    fun biometricFinished(outcome: Biometrics.Outcome) {
        if (_state.value.step != PinStep.Verify) return
        when (outcome) {
            Biometrics.Outcome.Success -> verified()
            Biometrics.Outcome.Cancelled -> Unit
            Biometrics.Outcome.LockedOut -> _state.update { it.copy(note = res(R.string.lock_biometrics_locked_out)) }
            Biometrics.Outcome.Failed -> _state.update { it.copy(note = res(R.string.lock_biometrics_failed)) }
        }
    }
}

@Composable
fun AppLockPinRoute(onDone: () -> Unit, viewModel: AppLockPinViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val biometrics = remember { viewModel.biometrics.availability() == Biometrics.Availability.Available }
    val askBiometric = rememberBiometricAsk(
        biometrics = viewModel.biometrics,
        auto = biometrics && state.step == PinStep.Verify,
        autoKey = state.step,
        title = stringResource(R.string.lock_confirm_title),
        subtitle = null,
        onOutcome = viewModel::biometricFinished,
    )
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AppLockPinEvent.Done -> onDone()
                AppLockPinEvent.Wrong -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }
    AppLockPinScreen(
        state = state,
        biometrics = biometrics,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBiometric = askBiometric,
        onClose = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockPinScreen(
    state: AppLockPinUiState,
    biometrics: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (state.purpose) {
                                AppLockPinPurpose.TurnOn -> R.string.pin_title_on
                                AppLockPinPurpose.Change -> R.string.pin_title_change
                                AppLockPinPurpose.TurnOff -> R.string.pin_title_off
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when (state.step) {
                            PinStep.Verify -> stringResource(R.string.pin_step_verify)
                            PinStep.Enter -> stringResource(R.string.pin_step_enter, MpinRules.LENGTH)
                            PinStep.Confirm -> stringResource(R.string.pin_step_confirm)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    val hint = when (state.step) {
                        PinStep.Verify -> if (biometrics) stringResource(R.string.pin_step_verify_hint_biometrics) else null
                        PinStep.Enter -> stringResource(R.string.pin_step_enter_hint)
                        PinStep.Confirm -> stringResource(R.string.pin_step_confirm_hint)
                    }
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PinDots(filled = state.entered.length, error = state.note != null)
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
                onBiometric = if (biometrics && state.step == PinStep.Verify) onBiometric else null,
                enabled = state.padOpen,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@PreviewLightDark
@Composable
private fun AppLockPinScreenPreview() {
    ExpensifyTheme {
        AppLockPinScreen(
            state = AppLockPinUiState(AppLockPinPurpose.TurnOn, PinStep.Confirm, entered = "4", note = plain("They didn't match · Choose your MPIN again.")),
            biometrics = false,
            onDigit = {}, onBackspace = {}, onBiometric = {}, onClose = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AppLockPinScreenVerifyPreview() {
    ExpensifyTheme {
        AppLockPinScreen(
            state = AppLockPinUiState(AppLockPinPurpose.TurnOff, PinStep.Verify, entered = "48"),
            biometrics = true,
            onDigit = {}, onBackspace = {}, onBiometric = {}, onClose = {},
        )
    }
}
