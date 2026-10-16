package com.example.expensify.feature.auth.domain

/**
 * Port of the signup form's checks (../expensify/src/features/auth/components/SignupPage.tsx)
 * and the account rules in auth.types.ts (req AUTH-01, AUTH-02). The form
 * checks these before the account is created; a taken username or email is
 * only known once the repository asks.
 */
object SignupRules {

    /** Fewest characters a new password may have. Firebase itself asks for 6. */
    const val MIN_PASSWORD_LENGTH = 8

    /** Most characters a name may have. */
    const val MAX_NAME_LENGTH = 60

    enum class NameError { Required, TooLong }

    enum class UsernameError { Required, TooShort, TooLong, InvalidCharacters }

    enum class EmailError { Required, Invalid }

    enum class PasswordError { TooShort }

    /** A name is required and at most 60 characters once trimmed. */
    fun validateName(name: String): NameError? = when {
        name.isBlank() -> NameError.Required
        name.trim().length > MAX_NAME_LENGTH -> NameError.TooLong
        else -> null
    }

    /** Checked on the normalized username, as the form lowercases it before the rules run. */
    fun validateUsername(raw: String): UsernameError? {
        val username = Usernames.normalize(raw)
        return when {
            username.isEmpty() -> UsernameError.Required
            username.length < Usernames.MIN_LENGTH -> UsernameError.TooShort
            username.length > Usernames.MAX_LENGTH -> UsernameError.TooLong
            !Usernames.hasAllowedCharacters(username) -> UsernameError.InvalidCharacters
            else -> null
        }
    }

    // What the web's `z.email()` accepts, near enough: a mailbox, a host and a dot, no spaces.
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateEmail(email: String): EmailError? = when {
        email.isBlank() -> EmailError.Required
        !EMAIL.matches(email.trim()) -> EmailError.Invalid
        else -> null
    }

    /** Only the length is enforced (req AUTH-01); the checklist is a hint. */
    fun validatePassword(password: String): PasswordError? =
        if (password.length < MIN_PASSWORD_LENGTH) PasswordError.TooShort else null

    /** The password-requirements checklist shown under the field, as the web's password input shows it. */
    data class PasswordChecks(
        val length: Boolean = false,
        val mixedCase: Boolean = false,
        val special: Boolean = false,
        val number: Boolean = false,
    )

    fun passwordChecks(password: String): PasswordChecks = PasswordChecks(
        length = password.length >= MIN_PASSWORD_LENGTH,
        mixedCase = password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
        special = password.any { !it.isLetterOrDigit() },
        number = password.any { it.isDigit() },
    )
}
