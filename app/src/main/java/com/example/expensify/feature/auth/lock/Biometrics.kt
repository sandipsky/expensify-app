package com.example.expensify.feature.auth.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The phone's fingerprint or face check, through the system `BiometricPrompt`.
 * Biometrics only: the phone's PIN, pattern or password is never offered, the
 * app lock's own MPIN is the other way in. Nothing is stored here; the lock
 * only needs a yes.
 */
@Singleton
class Biometrics @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Availability {
        /** A fingerprint or face is enrolled and the sensor is ready. */
        Available,

        /** The phone can take one but none is set up, so only the MPIN can unlock. */
        NoneEnrolled,

        /** No sensor, a sensor that is busy, or a security update pending. The MPIN still works. */
        Unavailable,
    }

    sealed interface Outcome {
        data object Success : Outcome

        /** Dismissed, or "Use MPIN" tapped. */
        data object Cancelled : Outcome

        /** Too many wrong fingers or faces; the phone refuses for a while. */
        data object LockedOut : Outcome
        data object Failed : Outcome
    }

    fun availability(): Availability = when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NoneEnrolled
        else -> Availability.Unavailable
    }

    /**
     * Shows the prompt and waits for its answer. [negativeText] is the prompt's
     * other button ("Use MPIN"), which the system requires when the phone's
     * credential is not an option. Leaving the coroutine dismisses it.
     */
    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, negativeText: String): Outcome =
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
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }

    private companion object {
        /**
         * Any biometric the phone trusts for its own unlock, and nothing else:
         * no `DEVICE_CREDENTIAL`, so the phone's PIN, pattern or password never
         * opens the app. Weak (class 2) face unlock counts: there is no key to
         * protect, only a gate to pass.
         */
        const val AUTHENTICATORS = BIOMETRIC_WEAK
    }
}
