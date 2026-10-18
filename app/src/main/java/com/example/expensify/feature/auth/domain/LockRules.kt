package com.example.expensify.feature.auth.domain

import java.util.Locale

/**
 * When the app lock (Settings › Security) asks for the MPIN, fingerprint or
 * face again, and how long a run of wrong MPINs closes the keypad. Pure, so
 * the timing is unit-tested without an activity.
 */
object LockRules {
    /** How long the app may be off screen before coming back to it asks again. */
    const val RELOCK_AFTER_MS: Long = 60_000

    /** Wrong MPINs in a row that cost nothing; the next one starts a wait. */
    const val FREE_TRIES = 5

    /** The first wait, after [FREE_TRIES] wrong MPINs in a row. Each further wrong one doubles it, up to [MAX_WAIT_MS]. */
    const val FIRST_WAIT_MS: Long = 30_000
    const val MAX_WAIT_MS: Long = 5 * 60_000

    /** Coming back after this long away asks again; a quick switch to another app does not. */
    fun locksOnReturn(awayMs: Long): Boolean = awayMs >= RELOCK_AFTER_MS

    /** Wrong MPINs still allowed before a wait starts, after [failures] wrong ones in a row. */
    fun triesLeft(failures: Int): Int = (FREE_TRIES - failures).coerceAtLeast(0)

    /**
     * How long the keypad stays closed after [failures] wrong MPINs in a row:
     * nothing through the fifth, then 30 s, 1 min, 2 min, 4 min and 5 min
     * from there on. Ten thousand codes at that pace take years.
     */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0
        val doublings = (failures - FREE_TRIES).coerceAtMost(MAX_DOUBLINGS)
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }

    /** How much of the wait after [failures] wrong MPINs, the last at [lastFailureAt], is still to go at [now]. */
    fun remainingWait(failures: Int, lastFailureAt: Long, now: Long): Long =
        (lastFailureAt + waitAfter(failures) - now).coerceIn(0, MAX_WAIT_MS)

    /** The wait as the lock screen counts it down: `m:ss`, so "0:30" and "4:59". ASCII digits whatever the locale. */
    fun waitText(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

    /** Enough doublings to pass [MAX_WAIT_MS]; more would only overflow. */
    private const val MAX_DOUBLINGS = 8
}
