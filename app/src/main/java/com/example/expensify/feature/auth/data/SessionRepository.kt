package com.example.expensify.feature.auth.data

import com.example.expensify.core.firebase.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    auth: FirebaseAuth,
    @ApplicationScope scope: CoroutineScope,
) {
    val session: StateFlow<Session> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(scope, SharingStarted.Eagerly, Session.Unknown)

    /** The uid while signed in, null otherwise. Repositories bind their flows to it. */
    val uid: Flow<String?> = session.map { (it as? Session.SignedIn)?.uid }.distinctUntilChanged()
}

private fun FirebaseUser?.toSession(): Session =
    if (this == null) {
        Session.SignedOut
    } else {
        Session.SignedIn(uid = uid, name = displayName.orEmpty().ifBlank { email.orEmpty() }, email = email.orEmpty())
    }
