package com.example.expensify.feature.auth.data

import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.feature.auth.domain.Usernames
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    /** Logs in with the username or the email (req AUTH-05). Refuses as `WrongPassword`. */
    suspend fun login(usernameOrEmail: String, password: String)

    suspend fun logout()
}

/** The codes Firebase answers a wrong email or password with; anything else passes through. */
private val BAD_CREDENTIALS = setOf(
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND",
    "ERROR_INVALID_EMAIL",
)

/** Port of ../expensify/src/features/auth/auth.api.ts (login and logout for now). */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val paths: UserPaths,
) : AuthRepository {

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
