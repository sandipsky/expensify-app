package com.example.expensify.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The app lock's MPIN (Settings › Security): shape and verifier. */
class MpinRulesTest {

    @Test
    fun `an MPIN is exactly four ASCII digits`() {
        assertThat(MpinRules.isComplete("0000")).isTrue()
        assertThat(MpinRules.isComplete("4821")).isTrue()
        assertThat(MpinRules.isComplete("482")).isFalse()
        assertThat(MpinRules.isComplete("48210")).isFalse()
        assertThat(MpinRules.isComplete("48a1")).isFalse()
        // Devanagari digits are digits to isDigit(), but the keypad only types ASCII ones.
        assertThat(MpinRules.isComplete("४८२१")).isFalse()
        assertThat(MpinRules.isComplete("")).isFalse()
    }

    @Test
    fun `a partial MPIN is what the keypad may still finish`() {
        assertThat(MpinRules.isPartial("")).isTrue()
        assertThat(MpinRules.isPartial("48")).isTrue()
        assertThat(MpinRules.isPartial("4821")).isTrue()
        assertThat(MpinRules.isPartial("48210")).isFalse()
        assertThat(MpinRules.isPartial("4a")).isFalse()
    }

    @Test
    fun `the verifier matches its MPIN and no other`() {
        val verifier = MpinRules.verifier("4821")
        assertThat(MpinRules.matches("4821", verifier)).isTrue()
        assertThat(MpinRules.matches("4822", verifier)).isFalse()
        assertThat(MpinRules.matches("1284", verifier)).isFalse()
        assertThat(MpinRules.matches("", verifier)).isFalse()
        assertThat(MpinRules.matches("48211", verifier)).isFalse()
    }

    @Test
    fun `the verifier never holds the digits, and two for the same MPIN differ by their salt`() {
        val a = MpinRules.verifier("4821")
        val b = MpinRules.verifier("4821")
        assertThat(a).doesNotContain("4821")
        assertThat(a).isNotEqualTo(b)
        assertThat(MpinRules.matches("4821", a)).isTrue()
        assertThat(MpinRules.matches("4821", b)).isTrue()
    }

    @Test
    fun `the stored form is versioned with its rounds and salt`() {
        val salt = ByteArray(16) { it.toByte() }
        val verifier = MpinRules.verifier("1234", salt, iterations = 10_000)
        assertThat(verifier).isEqualTo(
            "v1:10000:000102030405060708090a0b0c0d0e0f:91ea059bae0333a2969fc8ccd7c77851dda43f5adb96baeea246a624cdf489ec",
        )
        assertThat(MpinRules.matches("1234", verifier)).isTrue()
    }

    @Test
    fun `pbkdf2 agrees with the RFC 7914 test vector`() {
        // PBKDF2-HMAC-SHA-256 ("passwd", "salt", c = 1), first 32 bytes.
        val out = MpinRules.pbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1)
        assertThat(out.joinToString("") { "%02x".format(it) })
            .isEqualTo("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc")
    }

    @Test
    fun `a verifier with other rounds still matches, so the rounds can grow later`() {
        val verifier = MpinRules.verifier("4821", iterations = 3)
        assertThat(verifier).startsWith("v1:3:")
        assertThat(MpinRules.matches("4821", verifier)).isTrue()
    }

    @Test
    fun `a malformed or foreign verifier matches nothing`() {
        assertThat(MpinRules.matches("4821", "")).isFalse()
        assertThat(MpinRules.matches("4821", "4821")).isFalse()
        assertThat(MpinRules.matches("4821", "v2:10000:00:00")).isFalse()
        assertThat(MpinRules.matches("4821", "v1:0:00:00")).isFalse()
        assertThat(MpinRules.matches("4821", "v1:10000:zz:00")).isFalse()
        assertThat(MpinRules.matches("4821", "v1:10000:00")).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a verifier is never made from an incomplete MPIN`() {
        MpinRules.verifier("482")
    }
}
