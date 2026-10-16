package com.example.expensify.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The checks the signup form makes before an account is created (req AUTH-01, AUTH-02). */
class SignupRulesTest {

    @Test
    fun `AUTH-01 a name is required and at most 60 characters once trimmed`() {
        assertThat(SignupRules.validateName("")).isEqualTo(SignupRules.NameError.Required)
        assertThat(SignupRules.validateName("   ")).isEqualTo(SignupRules.NameError.Required)
        assertThat(SignupRules.validateName("a".repeat(61))).isEqualTo(SignupRules.NameError.TooLong)
        assertThat(SignupRules.validateName(" " + "a".repeat(60) + " ")).isNull()
        assertThat(SignupRules.validateName("Sita Rai")).isNull()
    }

    @Test
    fun `AUTH-02 a username is 3 to 30 of letters, numbers, dots, dashes and underscores`() {
        assertThat(SignupRules.validateUsername("")).isEqualTo(SignupRules.UsernameError.Required)
        assertThat(SignupRules.validateUsername("  ")).isEqualTo(SignupRules.UsernameError.Required)
        assertThat(SignupRules.validateUsername("ab")).isEqualTo(SignupRules.UsernameError.TooShort)
        assertThat(SignupRules.validateUsername("a".repeat(31))).isEqualTo(SignupRules.UsernameError.TooLong)
        assertThat(SignupRules.validateUsername("sita rai")).isEqualTo(SignupRules.UsernameError.InvalidCharacters)
        assertThat(SignupRules.validateUsername("sita@r")).isEqualTo(SignupRules.UsernameError.InvalidCharacters)
        assertThat(SignupRules.validateUsername("ram.thapa_1-2")).isNull()
    }

    @Test
    fun `AUTH-02 the username is checked lowercased and trimmed, as it is stored`() {
        assertThat(SignupRules.validateUsername(" Sita.R ")).isNull()
        assertThat(SignupRules.validateUsername("SITA")).isNull()
    }

    @Test
    fun `an email needs a mailbox, a host and a dot`() {
        assertThat(SignupRules.validateEmail("")).isEqualTo(SignupRules.EmailError.Required)
        assertThat(SignupRules.validateEmail("sita")).isEqualTo(SignupRules.EmailError.Invalid)
        assertThat(SignupRules.validateEmail("sita@example")).isEqualTo(SignupRules.EmailError.Invalid)
        assertThat(SignupRules.validateEmail("sita rai@example.com")).isEqualTo(SignupRules.EmailError.Invalid)
        assertThat(SignupRules.validateEmail(" Sita@Example.com ")).isNull()
    }

    @Test
    fun `AUTH-01 only the password length is enforced, at least 8 characters`() {
        assertThat(SignupRules.validatePassword("")).isEqualTo(SignupRules.PasswordError.TooShort)
        assertThat(SignupRules.validatePassword("1234567")).isEqualTo(SignupRules.PasswordError.TooShort)
        assertThat(SignupRules.validatePassword("12345678")).isNull()
        assertThat(SignupRules.validatePassword("password")).isNull()
    }

    @Test
    fun `the checklist reports length, mixed case, a special character and a number apart`() {
        assertThat(SignupRules.passwordChecks("")).isEqualTo(SignupRules.PasswordChecks())
        assertThat(SignupRules.passwordChecks("password")).isEqualTo(SignupRules.PasswordChecks(length = true))
        assertThat(SignupRules.passwordChecks("Pass")).isEqualTo(SignupRules.PasswordChecks(mixedCase = true))
        assertThat(SignupRules.passwordChecks("pass!")).isEqualTo(SignupRules.PasswordChecks(special = true))
        assertThat(SignupRules.passwordChecks("pass1")).isEqualTo(SignupRules.PasswordChecks(number = true))
        assertThat(SignupRules.passwordChecks("Password1!"))
            .isEqualTo(SignupRules.PasswordChecks(length = true, mixedCase = true, special = true, number = true))
    }
}
