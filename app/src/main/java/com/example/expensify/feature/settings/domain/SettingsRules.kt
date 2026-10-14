package com.example.expensify.feature.settings.domain

/**
 * Port of ../expensify/src/features/settings/settings.labels.ts and the
 * checks its forms make (ProfileSettings, PasswordForm, DeleteAccountDialog):
 * pure rules the settings screens share.
 */
object SettingsRules {

    /** What the user types to confirm deleting their account. */
    const val DELETE_PHRASE = "DELETE"

    /** Profile photos are stored as a JPEG this many pixels a side: sharp at 64 dp on a 2x screen. */
    const val PHOTO_EDGE = 128

    /** The most characters a stored photo may take (req §8). */
    const val MAX_PHOTO_URL_LENGTH = 200_000

    /** Fewest characters a new password may have. Firebase itself asks for 6. */
    const val MIN_PASSWORD_LENGTH = 8

    /** Most characters a name may have (req BR-15). */
    const val MAX_NAME_LENGTH = 60

    /** Every half hour of the day, as `HH:mm`. */
    val HALF_HOURS: List<String> = (0 until 48).map { i -> "%02d:%02d".format(i / 2, (i % 2) * 30) }

    private val TIME_OF_DAY = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    /** A 24-hour local time of day, `HH:mm`. */
    fun isTimeOfDay(value: String?): Boolean = value != null && TIME_OF_DAY.matches(value)

    /** The reminder times to pick from: every half hour, plus `current` in its place when it's between them. */
    fun reminderTimeOptions(current: String): List<String> =
        if (current in HALF_HOURS || !isTimeOfDay(current)) HALF_HOURS else (HALF_HOURS + current).sorted()

    /** How emails compare: trimmed and ignoring case, as logins do. */
    fun emailKey(email: String): String = email.trim().lowercase()

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Whether the text looks like an email address, as the web's `z.email()` checks. */
    fun isEmail(value: String): Boolean = EMAIL.matches(value.trim())

    enum class NameError { Required, TooLong }

    fun validateName(name: String): NameError? = when {
        name.isBlank() -> NameError.Required
        name.trim().length > MAX_NAME_LENGTH -> NameError.TooLong
        else -> null
    }

    enum class EmailError { Required, Invalid }

    fun validateEmail(email: String): EmailError? = when {
        email.isBlank() -> EmailError.Required
        !isEmail(email) -> EmailError.Invalid
        else -> null
    }

    enum class PasswordError { TooShort, SameAsCurrent }

    /** A new password: at least 8 characters, and not the current one. */
    fun validateNewPassword(newPassword: String, currentPassword: String): PasswordError? = when {
        newPassword.length < MIN_PASSWORD_LENGTH -> PasswordError.TooShort
        newPassword == currentPassword -> PasswordError.SameAsCurrent
        else -> null
    }

    /** Whether the delete form is complete: the phrase typed exactly, and a password given. */
    fun canDelete(phrase: String, password: String): Boolean = phrase.trim() == DELETE_PHRASE && password.isNotEmpty()

    /** The initials an avatar shows without a photo: "Sita Rai" → "SR". */
    fun initialsOf(name: String): String =
        name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
