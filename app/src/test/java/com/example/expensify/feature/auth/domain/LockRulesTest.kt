package com.example.expensify.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When the app lock asks again (Settings › Security). */
class LockRulesTest {

    @Test
    fun `coming back after a minute away asks again, a quick switch to another app does not`() {
        assertThat(LockRules.locksOnReturn(0)).isFalse()
        assertThat(LockRules.locksOnReturn(LockRules.RELOCK_AFTER_MS - 1)).isFalse()
        assertThat(LockRules.locksOnReturn(LockRules.RELOCK_AFTER_MS)).isTrue()
        assertThat(LockRules.locksOnReturn(2 * 60 * 60 * 1000L)).isTrue()
    }

    @Test
    fun `a clock that went backwards never locks by itself`() {
        assertThat(LockRules.locksOnReturn(-5_000)).isFalse()
    }
}
