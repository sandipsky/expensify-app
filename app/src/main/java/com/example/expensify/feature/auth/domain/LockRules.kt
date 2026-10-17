package com.example.expensify.feature.auth.domain

/**
 * When the app lock (Settings › Security) asks for the phone's fingerprint,
 * face or screen lock again. Pure, so the timing is unit-tested without an
 * activity.
 */
object LockRules {
    /** How long the app may be off screen before coming back to it asks again. */
    const val RELOCK_AFTER_MS: Long = 60_000

    /** Coming back after this long away asks again; a quick switch to another app does not. */
    fun locksOnReturn(awayMs: Long): Boolean = awayMs >= RELOCK_AFTER_MS
}
