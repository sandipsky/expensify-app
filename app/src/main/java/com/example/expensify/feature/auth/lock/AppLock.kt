package com.example.expensify.feature.auth.lock

import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.domain.LockRules
import com.example.expensify.feature.auth.domain.MpinRules
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Whether the app lock is on for the login the shell shows (`uid`), as this phone remembers it. */
data class LockEnabled(val uid: String?, val on: Boolean)

/** What typing an MPIN came to. */
sealed interface PinCheck {
    data object Correct : PinCheck

    /** Not it; [triesLeft] more before a wait starts. */
    data class Wrong(val triesLeft: Int) : PinCheck

    /** Too many wrong in a row: nothing is checked for another [remainingMs]. */
    data class Wait(val remainingMs: Long) : PinCheck
}

/**
 * The app lock (Settings › Security): with it on, Expensify asks for the
 * user's MPIN, or their fingerprint or face, when it opens and when it
 * comes back after [LockRules.RELOCK_AFTER_MS] away, the way banking apps
 * gate their screens. The phone's own PIN or password is never an option.
 * The Firebase session itself stays on the device (req AUTH-06); the lock
 * only keeps the signed-in shell behind the lock screen. The switch and the
 * MPIN's verifier live on this phone, keyed to the login that turned it on,
 * so another login on the same phone is not locked by it. The one activity
 * reports its starts and stops; a login typed just now, or a lock just
 * turned on, counts as unlocked.
 */
@Singleton
class AppLock @Inject constructor(
    private val device: DeviceState,
    private val session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
    private val clock: Clock,
) {
    /**
     * 0 while the shell may show. Otherwise the number of times the lock has
     * engaged since the process started, so the lock screen asks once more
     * each time it engages. A fresh process starts engaged.
     */
    private val _generation = MutableStateFlow(1)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** When the app went off screen, by the clock; null while it is on screen, or was never shown. */
    @Volatile
    private var hiddenAt: Long? = null

    /** One MPIN check at a time, so two quick submissions never both count as one wrong try. */
    private val checking = Mutex()

    /** Whether the lock is on for the current login; null until the device preference has been read. */
    val enabled: StateFlow<LockEnabled?> = combine(device.appLockUid, device.appLockPin, session.uid) { owner, pin, uid ->
        LockEnabled(uid, on = uid != null && owner == uid && pin != null)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The activity is on screen again. Called before its lifecycle reports it, so the first frame is already behind the lock. */
    fun onStart() {
        val hidden = hiddenAt ?: return
        hiddenAt = null
        if (LockRules.locksOnReturn(clock.millis() - hidden)) _generation.update { it + 1 }
    }

    fun onStop() {
        hiddenAt = clock.millis()
    }

    /** The user proved who they are: with their MPIN, fingerprint or face, or by typing their password on the login screen. */
    fun unlock() {
        _generation.value = 0
    }

    /** Turns the lock on for the current login with [pin] as its MPIN; they chose it just now, so the shell stays open. */
    suspend fun enable(pin: String) {
        val uid = (session.session.value as? Session.SignedIn)?.uid ?: return
        device.setAppLock(uid, hash(pin))
        _generation.value = 0
    }

    /** Turns the lock off and forgets the MPIN. The caller has just checked the MPIN or a biometric. */
    suspend fun disable() {
        device.clearAppLock()
        _generation.value = 0
    }

    /** Replaces the MPIN. The caller has just checked the current one or a biometric. */
    suspend fun changePin(pin: String) {
        device.setAppLockPin(hash(pin))
    }

    /** Whether [pin] is the current MPIN, counting a wrong one towards the wait (`LockRules`). Does not unlock. */
    suspend fun checkPin(pin: String): PinCheck = checking.withLock {
        val now = clock.millis()
        val failures = device.appLockFailures()
        val waiting = failures.lastAt?.let { LockRules.remainingWait(failures.count, it, now) } ?: 0
        if (waiting > 0) return PinCheck.Wait(waiting)
        val verifier = device.appLockPin.first() ?: return PinCheck.Correct
        val matches = withContext(Dispatchers.Default) { MpinRules.matches(pin, verifier) }
        if (matches) {
            device.clearAppLockFailures()
            return PinCheck.Correct
        }
        val count = failures.count + 1
        device.setAppLockFailures(count, now)
        val wait = LockRules.waitAfter(count)
        if (wait > 0) PinCheck.Wait(wait) else PinCheck.Wrong(LockRules.triesLeft(count))
    }

    /** [checkPin], and the shell opens on a match. */
    suspend fun unlockWithPin(pin: String): PinCheck = checkPin(pin).also { if (it == PinCheck.Correct) unlock() }

    /** How long the keypad is still closed after too many wrong MPINs; 0 when it is open. */
    suspend fun remainingWait(): Long {
        val failures = device.appLockFailures()
        val lastAt = failures.lastAt ?: return 0
        return LockRules.remainingWait(failures.count, lastAt, clock.millis())
    }

    private suspend fun hash(pin: String): String = withContext(Dispatchers.Default) { MpinRules.verifier(pin) }
}
