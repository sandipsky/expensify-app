package com.example.expensify.feature.auth.lock

import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.feature.auth.data.Session
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.auth.domain.LockRules
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Whether the app lock is on for the login the shell shows (`uid`), as this phone remembers it. */
data class LockEnabled(val uid: String?, val on: Boolean)

/**
 * The app lock (Settings › Security): with it on, Expensify asks for the
 * phone's fingerprint, face or screen lock when it opens and when it comes
 * back after [LockRules.RELOCK_AFTER_MS] away, the way banking apps gate
 * their screens. The Firebase session itself stays on the device (req
 * AUTH-06); the lock only keeps the signed-in shell behind the lock screen.
 * The preference lives on this phone, keyed to the login that turned it on,
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

    /** Whether the lock is on for the current login; null until the device preference has been read. */
    val enabled: StateFlow<LockEnabled?> = combine(device.appLockUid, session.uid) { owner, uid ->
        LockEnabled(uid, on = uid != null && owner == uid)
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

    /** The user proved who they are: with the phone's lock, or by typing their password on the login screen. */
    fun unlock() {
        _generation.value = 0
    }

    /** Turns the lock on or off for the current login; they have just confirmed it is them, so the shell stays open. */
    suspend fun setEnabled(on: Boolean) {
        val uid = (session.session.value as? Session.SignedIn)?.uid ?: return
        device.setAppLockUid(if (on) uid else null)
        _generation.value = 0
    }
}
