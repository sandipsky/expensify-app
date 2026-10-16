package com.example.expensify.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Username rules (req AUTH-02), kept in step with firestore.rules and auth.api.ts's `isUsableUsername`. */
class UsernamesTest {

    @Test
    fun `AUTH-02 Sita dot R and sita dot r are one login`() {
        assertThat(Usernames.normalize("  Sita.R ")).isEqualTo("sita.r")
        assertThat(Usernames.normalize("sita.r")).isEqualTo("sita.r")
    }

    @Test
    fun `AUTH-02 a usable username is 3 to 30 allowed characters`() {
        assertThat(Usernames.isUsable("sita.r")).isTrue()
        assertThat(Usernames.isUsable("ram_thapa-01")).isTrue()
        assertThat(Usernames.isUsable("abc")).isTrue()
        assertThat(Usernames.isUsable("a".repeat(30))).isTrue()
        assertThat(Usernames.isUsable("ab")).isFalse()
        assertThat(Usernames.isUsable("a".repeat(31))).isFalse()
        assertThat(Usernames.isUsable("Sita")).isFalse()
        assertThat(Usernames.isUsable("sita rai")).isFalse()
        assertThat(Usernames.isUsable("sita@example.com")).isFalse()
    }

    @Test
    fun `AUTH-02 names Firestore keeps for itself are not usable`() {
        assertThat(Usernames.isUsable("__sita__")).isFalse()
        assertThat(Usernames.isUsable("____")).isFalse()
        assertThat(Usernames.isUsable("__sita")).isTrue()
        assertThat(Usernames.isUsable("sita__")).isTrue()
    }

    @Test
    fun `the allowed characters are lowercase letters, numbers, dots, dashes and underscores`() {
        assertThat(Usernames.hasAllowedCharacters("a.b-c_1")).isTrue()
        assertThat(Usernames.hasAllowedCharacters("A")).isFalse()
        assertThat(Usernames.hasAllowedCharacters("a b")).isFalse()
        assertThat(Usernames.hasAllowedCharacters("")).isFalse()
    }
}
