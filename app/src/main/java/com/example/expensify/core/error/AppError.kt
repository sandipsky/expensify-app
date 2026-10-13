package com.example.expensify.core.error

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Why a read or write was refused, where the screen does something other
 * than show the message (req §9.1): a wrong password goes on the password
 * field, a taken username or email on its own field, and a recurring date
 * another device already handled isn't reported at all.
 */
sealed class AppError(message: String) : Exception(message) {
    class WrongPassword(message: String = "Wrong username, email or password.") : AppError(message)
    class Taken(val field: Field, message: String) : AppError(message) {
        enum class Field { Username, Email, Name }
    }
    class Stale : AppError("Already handled, perhaps on another device")
    class NotFound(message: String) : AppError(message)
    class SignedOut : AppError("You're logged out. Log in again.")
}

/**
 * A sentence for whatever a read or write threw, the same ones the web app
 * shows (../expensify/src/lib/errors.ts). Firebase's own messages never
 * reach the screen.
 */
object ErrorMessages {
    private const val OFFLINE = "Can't reach the server. Check your connection and try again."
    private const val BAD_CREDENTIALS = "Wrong email or password."

    private val authMessages = mapOf(
        "ERROR_INVALID_CREDENTIAL" to BAD_CREDENTIALS,
        "ERROR_WRONG_PASSWORD" to BAD_CREDENTIALS,
        "ERROR_USER_NOT_FOUND" to BAD_CREDENTIALS,
        "ERROR_INVALID_EMAIL" to "That email address doesn't look right.",
        "ERROR_MISSING_PASSWORD" to "Enter your password.",
        "ERROR_EMAIL_ALREADY_IN_USE" to "There's already an account with this email.",
        "ERROR_WEAK_PASSWORD" to "That password is too weak. Use at least 8 characters.",
        "ERROR_USER_DISABLED" to "This login has been switched off.",
        "ERROR_OPERATION_NOT_ALLOWED" to "Email and password login isn't turned on in Firebase yet.",
        "ERROR_REQUIRES_RECENT_LOGIN" to "For safety, log in again before doing this.",
        "ERROR_USER_TOKEN_EXPIRED" to "Your session ended. Log in again.",
        "ERROR_USER_MISMATCH" to "That's a different account. Log in again with this one.",
    )

    private val firestoreMessages = mapOf(
        FirebaseFirestoreException.Code.PERMISSION_DENIED to "The server refused this change. Try again in a moment.",
        FirebaseFirestoreException.Code.UNAUTHENTICATED to "You're logged out. Log in again.",
        FirebaseFirestoreException.Code.UNAVAILABLE to OFFLINE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED to "The server took too long to answer. Try again.",
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED to "The database is over its limit for now. Try again later.",
        FirebaseFirestoreException.Code.ABORTED to "Something else changed this at the same moment. Try again.",
        FirebaseFirestoreException.Code.NOT_FOUND to "That no longer exists. It may have been deleted on another device.",
    )

    fun of(error: Throwable, fallback: String = "Something went wrong"): String = when (error) {
        is AppError -> error.message ?: fallback
        is FirebaseNetworkException -> OFFLINE
        is FirebaseTooManyRequestsException -> "Too many attempts. Wait a few minutes, then try again."
        is FirebaseAuthException -> authMessages[error.errorCode] ?: fallback
        is FirebaseFirestoreException -> firestoreMessages[error.code] ?: fallback
        else -> fallback
    }
}
