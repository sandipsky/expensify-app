package com.example.expensify.feature.auth.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The phone's own check of who is holding it, through the system
 * `BiometricPrompt`: a fingerprint or face where the phone has one, and its
 * PIN, pattern or password otherwise or as the fallback the prompt offers.
 * Nothing is stored here; the app lock only needs a yes.
 */
@Singleton
class Biometrics @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Availability {
        /** A fingerprint, face or screen lock is set up. */
        Available,

        /** The phone has no screen lock at all, so nothing can be checked. */
        NoScreenLock,

        /** Not right now: no hardware, a sensor that is busy, or a security update pending. The prompt may still work later. */
        Unavailable,
    }

    sealed interface Outcome {
        data object Success : Outcome
        data object Cancelled : Outcome

        /** Too many wrong tries; the phone refuses for a while. */
        data object LockedOut : Outcome
        data object Failed : Outcome
    }

    fun availability(): Availability = when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NoScreenLock
        else -> Availability.Unavailable
    }

    /** Shows the prompt and waits for its answer. Leaving the coroutine dismisses it. */
    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String?): Outcome =
        suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(Outcome.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> Outcome.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> Outcome.LockedOut
                        else -> Outcome.Failed
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                }

                // A wrong finger or face: the prompt stays up and lets them try again.
                override fun onAuthenticationFailed() = Unit
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .apply { if (subtitle != null) setSubtitle(subtitle) }
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }

    private companion object {
        /**
         * Any biometric the phone trusts for its own unlock, with the screen lock as the fallback.
         * Weak (class 2) face unlock counts: there is no key to protect, only a gate to pass, and
         * this pairing is the one the library supports on every Android version from 7 up.
         */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
