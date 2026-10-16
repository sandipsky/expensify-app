package com.example.expensify.feature.auth.domain

/** Username rules (req AUTH-02). Kept in step with firestore.rules and auth.types.ts. */
object Usernames {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30
    private val PATTERN = Regex("^[a-z0-9._-]+$")
    private val RESERVED = Regex("^__.*__$")

    /** Trimmed and lowercased, so "Sita.R" and "sita.r" are one login. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    /** Only lowercase letters, numbers, dots, dashes and underscores. */
    fun hasAllowedCharacters(username: String): Boolean = PATTERN.matches(username)

    /** Whether it can be a Firestore document id too: the signup rules, and not `__…__`. */
    fun isUsable(username: String): Boolean =
        username.length in MIN_LENGTH..MAX_LENGTH && hasAllowedCharacters(username) && !RESERVED.matches(username)
}
