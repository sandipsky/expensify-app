package com.example.expensify.feature.settings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The checks the settings forms make (req SET-02…SET-05, AUTH-13, BR-15). */
class SettingsRulesTest {

    @Test
    fun `reminder times are every half hour, with an odd current time slotted in`() {
        assertThat(SettingsRules.HALF_HOURS).hasSize(48)
        assertThat(SettingsRules.HALF_HOURS.first()).isEqualTo("00:00")
        assertThat(SettingsRules.HALF_HOURS.last()).isEqualTo("23:30")
        assertThat(SettingsRules.reminderTimeOptions("20:00")).isEqualTo(SettingsRules.HALF_HOURS)
        val options = SettingsRules.reminderTimeOptions("20:15")
        assertThat(options).hasSize(49)
        assertThat(options.indexOf("20:15")).isEqualTo(options.indexOf("20:00") + 1)
        assertThat(SettingsRules.reminderTimeOptions("nope")).isEqualTo(SettingsRules.HALF_HOURS)
    }

    @Test
    fun `a time of day is HH colon mm on a 24 hour clock`() {
        assertThat(SettingsRules.isTimeOfDay("00:00")).isTrue()
        assertThat(SettingsRules.isTimeOfDay("23:59")).isTrue()
        assertThat(SettingsRules.isTimeOfDay("24:00")).isFalse()
        assertThat(SettingsRules.isTimeOfDay("9:00")).isFalse()
        assertThat(SettingsRules.isTimeOfDay(null)).isFalse()
    }

    @Test
    fun `names are required and at most 60 characters after trimming`() {
        assertThat(SettingsRules.validateName("  ")).isEqualTo(SettingsRules.NameError.Required)
        assertThat(SettingsRules.validateName("a".repeat(61))).isEqualTo(SettingsRules.NameError.TooLong)
        assertThat(SettingsRules.validateName(" " + "a".repeat(60) + " ")).isNull()
    }

    @Test
    fun `emails need a mailbox, a host and a dot, and compare ignoring case`() {
        assertThat(SettingsRules.validateEmail("")).isEqualTo(SettingsRules.EmailError.Required)
        assertThat(SettingsRules.validateEmail("sita@example")).isEqualTo(SettingsRules.EmailError.Invalid)
        assertThat(SettingsRules.validateEmail(" Sita@Example.com ")).isNull()
        assertThat(SettingsRules.emailKey(" Sita@Example.com ")).isEqualTo("sita@example.com")
    }

    @Test
    fun `a new password has at least 8 characters and differs from the current one`() {
        assertThat(SettingsRules.validateNewPassword("short", "x")).isEqualTo(SettingsRules.PasswordError.TooShort)
        assertThat(SettingsRules.validateNewPassword("password123", "password123")).isEqualTo(SettingsRules.PasswordError.SameAsCurrent)
        assertThat(SettingsRules.validateNewPassword("password456", "password123")).isNull()
    }

    @Test
    fun `deleting needs the exact phrase and a password`() {
        assertThat(SettingsRules.canDelete("DELETE", "pw")).isTrue()
        assertThat(SettingsRules.canDelete(" DELETE ", "pw")).isTrue()
        assertThat(SettingsRules.canDelete("delete", "pw")).isFalse()
        assertThat(SettingsRules.canDelete("DELETE", "")).isFalse()
    }

    @Test
    fun `initials take the first letters of the first two words`() {
        assertThat(SettingsRules.initialsOf("Sita Rai")).isEqualTo("SR")
        assertThat(SettingsRules.initialsOf("  sita  ")).isEqualTo("S")
        assertThat(SettingsRules.initialsOf("")).isEqualTo("?")
    }
}
