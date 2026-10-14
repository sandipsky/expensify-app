package com.example.expensify.feature.auth.data

import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.settledDocument
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.auth.domain.Preferences
import com.example.expensify.feature.auth.domain.Profile
import com.example.expensify.feature.auth.domain.ThemePref
import com.example.expensify.feature.auth.domain.Usernames
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    /** Logs in with the username or the email (req AUTH-05). Refuses as `WrongPassword`. */
    suspend fun login(usernameOrEmail: String, password: String)

    suspend fun logout()

    /** The logged-in user's profile, live; null while logged out or until the document exists. */
    fun profile(): Flow<Profile?>
}

/** The codes Firebase answers a wrong email or password with; anything else passes through. */
private val BAD_CREDENTIALS = setOf(
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_EMAIL",
)

/** Port of ../expensify/src/features/auth/auth.api.ts (login, logout and the profile read for now). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val paths: UserPaths,
    session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    private val sharedProfile: Flow<Profile?> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                paths.user(uid).snapshots().settledDocument().map { snapshot ->
                    if (snapshot.exists()) snapshot.toProfile(uid) else null
                }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun profile(): Flow<Profile?> = sharedProfile

    override suspend fun login(usernameOrEmail: String, password: String) {
        val value = Usernames.normalize(usernameOrEmail)
        // Usernames can't hold "@", so the value says which it is.
        val emails = if ("@" in value) listOf(value) else emailsFor(value)
        for (email in emails) {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                return
            } catch (error: FirebaseAuthException) {
                if (error.errorCode !in BAD_CREDENTIALS) throw error
            }
        }
        throw AppError.WrongPassword()
    }

    /** The emails a username logs in with: its login's, then a new one still waiting on its link. */
    private suspend fun emailsFor(username: String): List<String> {
        if (!Usernames.isUsable(username)) return emptyList()
        val lookup = paths.username(username).get().await()
        return listOfNotNull(lookup.getString("email"), lookup.getString("pendingEmail")).filter { it.isNotEmpty() }
    }

    override suspend fun logout() {
        auth.signOut()
    }
}

/** users/{uid} as the app reads it; missing preference fields take the defaults a new profile gets. */
internal fun DocumentSnapshot.toProfile(uid: String): Profile {
    val preferences = get("preferences") as? Map<*, *>
    val notifications = preferences?.get("notifications") as? Map<*, *>
    val defaults = NotificationPrefs()
    return Profile(
        uid = uid,
        name = getString("name").orEmpty(),
        username = getString("username"),
        email = getString("email").orEmpty(),
        pendingEmail = getString("pendingEmail"),
        photoUrl = getString("photoUrl"),
        preferences = Preferences(
            theme = ThemePref.fromStored(preferences?.get("theme") as? String),
            notifications = NotificationPrefs(
                dailyReminder = notifications?.get("dailyReminder") as? Boolean ?: defaults.dailyReminder,
                reminderTime = notifications?.get("reminderTime") as? String ?: defaults.reminderTime,
                budgetAlerts = notifications?.get("budgetAlerts") as? Boolean ?: defaults.budgetAlerts,
                billReminders = notifications?.get("billReminders") as? Boolean ?: defaults.billReminders,
            ),
        ),
    )
}
