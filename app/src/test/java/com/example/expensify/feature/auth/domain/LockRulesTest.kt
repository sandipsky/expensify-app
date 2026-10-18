package com.example.expensify.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When the app lock asks again, and the wait after wrong MPINs (Settings › Security). */
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

    @Test
    fun `five wrong MPINs cost nothing, the sixth starts a wait that doubles to five minutes`() {
        assertThat(LockRules.waitAfter(0)).isEqualTo(0)
        assertThat(LockRules.waitAfter(4)).isEqualTo(0)
        assertThat(LockRules.waitAfter(5)).isEqualTo(30_000)
        assertThat(LockRules.waitAfter(6)).isEqualTo(60_000)
        assertThat(LockRules.waitAfter(7)).isEqualTo(120_000)
        assertThat(LockRules.waitAfter(8)).isEqualTo(240_000)
        assertThat(LockRules.waitAfter(9)).isEqualTo(300_000)
        assertThat(LockRules.waitAfter(50)).isEqualTo(300_000)
        assertThat(LockRules.waitAfter(1_000_000)).isEqualTo(300_000)
    }

    @Test
    fun `tries left counts down to the wait and never below zero`() {
        assertThat(LockRules.triesLeft(0)).isEqualTo(5)
        assertThat(LockRules.triesLeft(1)).isEqualTo(4)
        assertThat(LockRules.triesLeft(5)).isEqualTo(0)
        assertThat(LockRules.triesLeft(9)).isEqualTo(0)
    }

    @Test
    fun `the remaining wait runs down from the last wrong try and is over once it has passed`() {
        val failedAt = 1_000_000L
        assertThat(LockRules.remainingWait(5, failedAt, now = failedAt)).isEqualTo(30_000)
        assertThat(LockRules.remainingWait(5, failedAt, now = failedAt + 12_000)).isEqualTo(18_000)
        assertThat(LockRules.remainingWait(5, failedAt, now = failedAt + 30_000)).isEqualTo(0)
        assertThat(LockRules.remainingWait(5, failedAt, now = failedAt + 90_000)).isEqualTo(0)
        assertThat(LockRules.remainingWait(2, failedAt, now = failedAt)).isEqualTo(0)
    }

    @Test
    fun `the wait counts down as minutes and seconds`() {
        assertThat(LockRules.waitText(30)).isEqualTo("0:30")
        assertThat(LockRules.waitText(5)).isEqualTo("0:05")
        assertThat(LockRules.waitText(299)).isEqualTo("4:59")
        assertThat(LockRules.waitText(300)).isEqualTo("5:00")
    }

    @Test
    fun `a clock set forward never asks for more than the longest wait`() {
        assertThat(LockRules.remainingWait(9, lastFailureAt = Long.MAX_VALUE / 2, now = 0)).isEqualTo(LockRules.MAX_WAIT_MS)
    }
}
