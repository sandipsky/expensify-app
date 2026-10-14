package com.example.expensify.feature.auth.domain

/** The theme the profile asks for (req SET-02). */
enum class ThemePref(val stored: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        fun fromStored(value: String?): ThemePref = entries.firstOrNull { it.stored == value } ?: System
    }
}

/** `preferences.notifications` on users/{uid} (req §8), with the defaults a new profile gets. */
data class NotificationPrefs(
    val dailyReminder: Boolean = false,
    /** `HH:mm`, 24-hour. */
    val reminderTime: String = "20:00",
    val budgetAlerts: Boolean = true,
    val billReminders: Boolean = true,
)

data class Preferences(
    val theme: ThemePref = ThemePref.System,
    val notifications: NotificationPrefs = NotificationPrefs(),
)

/** Preferences to change; null fields stay as they are (the web's `IPreferencesBody`). */
data class PreferencesPatch(
    val theme: ThemePref? = null,
    val dailyReminder: Boolean? = null,
    val reminderTime: String? = null,
    val budgetAlerts: Boolean? = null,
    val billReminders: Boolean? = null,
)

/** users/{uid} (req §8). */
data class Profile(
    val uid: String,
    val name: String,
    /** Null only for a login whose signup batch never landed (req §9.3). */
    val username: String?,
    val email: String,
    /** A new email waiting on its verification link. */
    val pendingEmail: String?,
    /** A `data:image/…;base64,…` URL, at most 200,000 characters. */
    val photoUrl: String?,
    val preferences: Preferences,
)
