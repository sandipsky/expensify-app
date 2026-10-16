package com.example.expensify.feature.auth.data

import com.example.expensify.core.firebase.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Who is logged in. `Unknown` until Firebase has restored the saved session, or found there is none. */
sealed interface Session {
    data object Unknown : Session
    data object SignedOut : Session
    data class SignedIn(val uid: String, val name: String, val email: String) : Session
}

/**
 * The Firebase Auth session as a StateFlow. Firebase keeps it on the device
 * until the user logs out (req AUTH-06); when it ends for any other reason,
 * the app shell sends the user to the login screen (AUTH-09).
 */
@Singleton
class SessionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @ApplicationScope scope: CoroutineScope,
) {
    /** What Firebase Auth last said. The listener fires on sign-in and sign-out, not on a changed display name. */
    private val authState = MutableStateFlow<Session>(Session.Unknown)

    /** How many signups are in flight (see [holdingSession]). */
    private val holds = MutableStateFlow(0)

    /** Set just before the app ends the session itself, so the shell doesn't read it as the session ending on it. */
    private val expectedEnd = AtomicBoolean(false)

    init {
        auth.addAuthStateListener { authState.value = it.currentUser.toSession() }
    }

    val session: StateFlow<Session> = combine(authState, holds) { state, held ->
        // A signup's login exists before its profile does; held, the app stays on the signup screen until both are written.
        if (held > 0 && state is Session.SignedIn) Session.SignedOut else state
    }.stateIn(scope, SharingStarted.Eagerly, Session.Unknown)

    /** The uid while signed in, null otherwise. Repositories bind their flows to it. */
    val uid: Flow<String?> = session.map { (it as? Session.SignedIn)?.uid }.distinctUntilChanged()

    /** Re-reads the login after its display name changed, which the listener doesn't report. */
    fun refresh() {
        if (authState.value != Session.Unknown) authState.value = auth.currentUser.toSession()
    }

    /**
     * Runs a signup with the session held signed out (req AUTH-04): the shell
     * opens the dashboard, and the profile repair runs, only once the profile,
     * the username and the default categories are written. A signup that fails
     * deletes its login again inside the hold, so nothing was ever signed in.
     */
    suspend fun <T> holdingSession(block: suspend () -> T): T {
        holds.update { it + 1 }
        try {
            return block()
        } finally {
            holds.update { it - 1 }
        }
    }

    /** The session is about to end on purpose: Log out, or Delete account. */
    fun expectEnd() {
        expectedEnd.set(true)
    }

    /** Whether the end that just happened was expected; asking clears it. */
    fun consumeExpectedEnd(): Boolean = expectedEnd.getAndSet(false)
}

private fun FirebaseUser?.toSession(): Session =
    if (this == null) {
        Session.SignedOut
    } else {
        Session.SignedIn(uid = uid, name = displayName.orEmpty().ifBlank { email.orEmpty() }, email = email.orEmpty())
    }
