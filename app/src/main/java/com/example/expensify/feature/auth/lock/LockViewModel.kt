package com.example.expensify.feature.auth.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.ui.AppMessage
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.res
import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.domain.LockRules
import com.example.expensify.feature.auth.domain.MpinRules
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

data class LockUiState(
    /** The MPIN digits typed so far; checked as soon as it is complete. */
    val entered: String = "",
    /** Why the last try did not pass, or how long the wait still is. */
    val note: UiText? = null,
    /** Seconds the keypad is still closed after too many wrong MPINs; 0 while it is open. */
    val waitSeconds: Int = 0,
    /** A complete MPIN is being checked. */
    val checking: Boolean = false,
) {
    val padOpen: Boolean get() = waitSeconds == 0 && !checking
}

sealed interface LockEvent {
    /** A wrong MPIN, for the haptic. */
    data object Wrong : LockEvent
}

/**
 * The lock screen over the signed-in shell: takes the MPIN digit by digit
 * and checks them when four are in, takes the phone's fingerprint or face
 * answer, counts the wait down after too many wrong MPINs, and offers the
 * way out for someone who forgot it: log out, which also drops the lock,
 * since the account password is then the proof of who they are.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLock: AppLock,
    private val auth: AuthRepository,
    private val messages: Messages,
    val biometrics: Biometrics,
) : ViewModel() {

    /** Counts up each time the lock engages, so the screen asks for a biometric once more each time. */
    val generation: StateFlow<Int> = appLock.generation

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private val _events = Channel<LockEvent>(Channel.BUFFERED)
    val events: Flow<LockEvent> = _events.receiveAsFlow()

    private var wait: Job? = null

    init {
        // A wait that was running when the process ended still runs.
        viewModelScope.launch { startWait(appLock.remainingWait()) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.padOpen) return
        val next = current.entered + digit
        if (!MpinRules.isPartial(next)) return
        _state.update { it.copy(entered = next, note = null) }
        if (MpinRules.isComplete(next)) check(next)
    }

    fun onBackspace() {
        val current = _state.value
        if (!current.padOpen || current.entered.isEmpty()) return
        _state.update { it.copy(entered = it.entered.dropLast(1)) }
    }

    private fun check(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            when (val result = appLock.unlockWithPin(pin)) {
                PinCheck.Correct -> _state.update { it.copy(entered = "", checking = false, note = null) }
                is PinCheck.Wrong -> {
                    _state.update {
                        it.copy(entered = "", checking = false, note = UiText.Plural(R.plurals.lock_wrong_pin, result.triesLeft, listOf(result.triesLeft)))
                    }
                    _events.trySend(LockEvent.Wrong)
                }
                is PinCheck.Wait -> {
                    _state.update { it.copy(entered = "", checking = false) }
                    _events.trySend(LockEvent.Wrong)
                    startWait(result.remainingMs)
                }
            }
        }
    }

    /** Closes the keypad for [ms], saying for how long, then opens it again. Nothing for 0. */
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

    /** What the phone's fingerprint or face prompt came to. A wait on the MPIN does not stop a biometric. */
    fun biometricFinished(outcome: Biometrics.Outcome) {
        when (outcome) {
            Biometrics.Outcome.Success -> {
                _state.update { it.copy(entered = "", checking = false, note = if (it.waitSeconds > 0) it.note else null) }
                appLock.unlock()
            }
            Biometrics.Outcome.Cancelled -> Unit
            Biometrics.Outcome.LockedOut -> _state.update { it.copy(note = res(R.string.lock_biometrics_locked_out)) }
            Biometrics.Outcome.Failed -> _state.update { it.copy(note = res(R.string.lock_biometrics_failed)) }
        }
    }

    /**
     * Forgot the MPIN: the lock is dropped and the session ended, so the
     * account password on the login screen is what lets them back in. Said
     * over the login screen so they know to set a new one.
     */
    fun forgot() {
        viewModelScope.launch {
            appLock.disable()
            auth.logout()
            messages.show(AppMessage(res(R.string.lock_forgot_done), long = true))
        }
    }
}
