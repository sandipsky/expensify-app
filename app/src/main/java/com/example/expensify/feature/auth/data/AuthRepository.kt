package com.example.expensify.feature.auth.data

import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.commitInBatches
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.auth.domain.Preferences
import com.example.expensify.feature.auth.domain.PreferencesPatch
import com.example.expensify.feature.auth.domain.Profile
import com.example.expensify.feature.auth.domain.ThemePref
import com.example.expensify.feature.auth.domain.Usernames
import com.example.expensify.feature.categories.data.addDefaultsTo
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface AuthRepository {
    /** Logs in with the username or the email (req AUTH-05). Refuses as `WrongPassword`. */
    suspend fun login(usernameOrEmail: String, password: String)

    /**
     * Creates the login, then its profile, its username and the default
     * categories in one batch, and stays logged in (req AUTH-04, US-08). When
     * the batch fails, the login is deleted again, so the same email can sign
     * up once more. A taken username or email is `Taken` naming the field.
     */
    suspend fun register(name: String, username: String, email: String, password: String)

    suspend fun logout()

    /** The logged-in user's profile, live; null while logged out or until the document exists. */
    fun profile(): Flow<Profile?>

    /**
     * What the web does on every profile load (req §9.3): reloads the login
     * to pick up an email changed through its link, writes a profile for a
     * login whose signup batch never landed, and follows the login's email on
     * the profile and the username lookup. Safe to call often; offline it
     * does nothing.
     */
    suspend fun syncLogin()

    /** Saves the name; the login's display name follows it (req §9.3). */
    suspend fun updateName(name: String)

    /** Saves a photo as a `data:image/jpeg;base64,…` URL, or removes it with null. */
    suspend fun updatePhoto(photoUrl: String?)

    /** Saves the preferences in the patch; the others stay as they are (req SET-02, SET-03). */
    suspend fun updatePreferences(patch: PreferencesPatch)

    /**
     * Sends a verification link to the new email after the password proves
     * it's the user (`WrongPassword`); the login switches once the link is
     * opened. The current email again cancels a pending change. A taken email
     * is `Taken(Email)`.
     */
    suspend fun changeEmail(email: String, password: String)

    /** Refuses as `WrongPassword` when the current password is wrong. */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /**
     * Deletes everything the user entered, then their username, their profile
     * and their login, once their password proves it's them (req AUTH-13,
     * US-09). The session ends by itself afterwards.
     */
    suspend fun deleteAccount(password: String)
}

/** The codes Firebase answers a wrong email or password with; anything else passes through. */
private val BAD_CREDENTIALS = setOf(
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_EMAIL",
)

/** What a wrong current password reads as on Settings (errors.ts). */
private const val WRONG_PASSWORD = "That password isn't right"

// What signup is refused with (auth.api.ts); each goes on its own field.
private const val USERNAME_UNUSABLE = "That username can't be used. Try another one."
private const val USERNAME_TAKEN = "That username is taken. Try another one."
private const val EMAIL_TAKEN = "There's already an account with this email. Log in instead."

/** What a new profile's preferences start as. */
private val DEFAULT_PREFERENCES = Preferences()

/** Port of ../expensify/src/features/auth/auth.api.ts: login, signup, logout, the profile and the Settings writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    private val session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : AuthRepository {

    private val sharedProfile: Flow<Profile?> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                paths.user(uid).snapshots().map { snapshot ->
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

    override suspend fun register(name: String, username: String, email: String, password: String) {
        val handle = Usernames.normalize(username)
        if (!Usernames.isUsable(handle)) throw AppError.Taken(AppError.Taken.Field.Username, USERNAME_UNUSABLE)
        if (paths.username(handle).get().await().exists()) throw AppError.Taken(AppError.Taken.Field.Username, USERNAME_TAKEN)

        // Firebase signs the new login in at once, before its profile exists. Held, the session stays signed
        // out until the batch below has landed, so the shell doesn't open the dashboard, and the profile repair
        // (syncLogin) doesn't write a profile without a username, on a half-made account.
        session.holdingSession {
            val user = try {
                auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                    ?: throw IllegalStateException("Sign-up returned no user")
            } catch (error: FirebaseAuthException) {
                if (error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") throw AppError.Taken(AppError.Taken.Field.Email, EMAIL_TAKEN)
                throw error
            }

            try {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
                val batch = db.batch()
                batch.set(paths.user(user.uid), newProfileFields(name = name.trim(), username = handle, email = user.email))
                batch.set(paths.username(handle), mapOf("uid" to user.uid, "email" to user.email, "pendingEmail" to null))
                addDefaultsTo(batch, paths.collection(UserCollection.Categories, user.uid), emptyList())
                batch.commit().await()
            } catch (error: Throwable) {
                // The login goes again so the same email can retry; even when the signup was cancelled.
                withContext(NonCancellable) { runCatching { user.delete().await() } }
                // Someone took the username a moment ago, and the rules refused to overwrite it.
                val refused = error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (refused && runCatching { paths.username(handle).get().await().exists() }.getOrDefault(false)) {
                    throw AppError.Taken(AppError.Taken.Field.Username, USERNAME_TAKEN)
                }
                throw error
            }
            // The session was read before the display name was set; the greeting wants the name.
            session.refresh()
        }
    }

    override suspend fun logout() {
        session.expectEnd()
        auth.signOut()
    }

    private fun currentUser(): FirebaseUser = auth.currentUser ?: throw AppError.SignedOut()

    override suspend fun syncLogin() {
        val user = currentUser()
        // Picks up an email changed through its link. Offline, the cached login will do.
        runCatching { user.reload().await() }
        val snapshot = paths.user(user.uid).get().await()
        if (!snapshot.exists()) {
            createMissingProfile(user)
            return
        }
        val profile = snapshot.toProfile(user.uid)
        val email = user.email
        if (!email.isNullOrEmpty() && email != profile.email) followLoginEmail(user, profile, email)
    }

    /** users/{uid} as signup writes it (req §8): no pending email, no photo, the default preferences. */
    private fun newProfileFields(name: String, username: String?, email: String?): Map<String, Any?> = mapOf(
        "name" to name,
        "username" to username,
        "email" to email,
        "pendingEmail" to null,
        "photoUrl" to null,
        "preferences" to DEFAULT_PREFERENCES.toFields(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /**
     * A login whose signup stopped before its batch was written, and whose
     * login couldn't be deleted either, gets its profile and categories now.
     * It has no username, so it logs in with its email.
     */
    private suspend fun createMissingProfile(user: FirebaseUser) {
        val batch = db.batch()
        batch.set(
            paths.user(user.uid),
            newProfileFields(
                name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@') ?: "You",
                username = null,
                email = user.email,
            ),
        )
        val categories = paths.collection(UserCollection.Categories, user.uid)
        if (categories.get().await().isEmpty) addDefaultsTo(batch, categories, emptyList())
        batch.commit().await()
    }

    /** Follows the login's email once its verification link was opened: the profile and the lookup take it, and nothing is pending. */
    private suspend fun followLoginEmail(user: FirebaseUser, profile: Profile, email: String) {
        // The rules check the username's email against the session's, so it must be fresh.
        user.getIdToken(true).await()
        val batch = db.batch()
        batch.update(paths.user(user.uid), mapOf("email" to email, "pendingEmail" to null, "updatedAt" to FieldValue.serverTimestamp()))
        profile.username?.let { batch.update(paths.username(it), mapOf("email" to email, "pendingEmail" to null)) }
        batch.commit().await()
    }

    override suspend fun updateName(name: String) {
        val user = currentUser()
        val trimmed = name.trim()
        paths.user(user.uid).update(mapOf("name" to trimmed, "updatedAt" to FieldValue.serverTimestamp())).await()
        runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()).await() }
            .onSuccess { session.refresh() }
    }

    override suspend fun updatePhoto(photoUrl: String?) {
        paths.user(currentUser().uid).update(mapOf("photoUrl" to photoUrl, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun updatePreferences(patch: PreferencesPatch) {
        val changes = mutableMapOf<String, Any?>("updatedAt" to FieldValue.serverTimestamp())
        patch.theme?.let { changes["preferences.theme"] = it.stored }
        patch.dailyReminder?.let { changes["preferences.notifications.dailyReminder"] = it }
        patch.reminderTime?.let { changes["preferences.notifications.reminderTime"] = it }
        patch.budgetAlerts?.let { changes["preferences.notifications.budgetAlerts"] = it }
        patch.billReminders?.let { changes["preferences.notifications.billReminders"] = it }
        paths.user(currentUser().uid).update(changes).await()
    }

    /** Proves it's really the user before a sensitive change; Firebase then counts the login as recent. */
    private suspend fun reauthenticate(user: FirebaseUser, password: String) {
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
        } catch (error: FirebaseAuthException) {
            if (error.errorCode in BAD_CREDENTIALS) throw AppError.WrongPassword(WRONG_PASSWORD)
            throw error
        }
    }

    /** Marks `pendingEmail` on the profile and the username's lookup, or clears it with null. */
    private suspend fun savePendingEmail(user: FirebaseUser, pendingEmail: String?) {
        val profile = paths.user(user.uid).get().await().toProfile(user.uid)
        val batch = db.batch()
        batch.update(paths.user(user.uid), mapOf("pendingEmail" to pendingEmail, "updatedAt" to FieldValue.serverTimestamp()))
        profile.username?.let { batch.update(paths.username(it), mapOf("pendingEmail" to pendingEmail)) }
        batch.commit().await()
    }

    override suspend fun changeEmail(email: String, password: String) {
        val user = currentUser()
        val next = email.trim().lowercase()
        reauthenticate(user, password)
        if (next == user.email?.lowercase()) {
            savePendingEmail(user, null)
            return
        }
        try {
            user.verifyBeforeUpdateEmail(next).await()
        } catch (error: FirebaseAuthException) {
            if (error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                throw AppError.Taken(AppError.Taken.Field.Email, "That email belongs to another account.")
            }
            throw error
        }
        savePendingEmail(user, next)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = currentUser()
        reauthenticate(user, currentPassword)
        user.updatePassword(newPassword).await()
    }

    override suspend fun deleteAccount(password: String) {
        val user = currentUser()
        reauthenticate(user, password)

        // Every document the user entered, in batches, in the order req §9.3 gives.
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (collection in UserCollection.entries) {
            for (doc in paths.collection(collection, user.uid).get().await().documents) {
                writes += { batch -> batch.delete(doc.reference) }
            }
        }
        db.commitInBatches(writes)

        val snapshot = paths.user(user.uid).get().await()
        val username = if (snapshot.exists()) snapshot.toProfile(user.uid).username else null
        val batch = db.batch()
        if (username != null && paths.username(username).get().await().exists()) batch.delete(paths.username(username))
        batch.delete(paths.user(user.uid))
        batch.commit().await()

        // The session ends on purpose here; the shell says "Your account was deleted", not "Your session ended".
        session.expectEnd()
        user.delete().await()
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

/** `preferences` as stored (req §8): the theme and the four notification settings, nothing else. */
internal fun Preferences.toFields(): Map<String, Any> = mapOf(
    "theme" to theme.stored,
    "notifications" to mapOf(
        "dailyReminder" to notifications.dailyReminder,
        "reminderTime" to notifications.reminderTime,
        "budgetAlerts" to notifications.budgetAlerts,
        "billReminders" to notifications.billReminders,
    ),
)
