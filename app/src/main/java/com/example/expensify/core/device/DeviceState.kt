package com.example.expensify.core.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(name = "device_state")

/** The app lock's wrong MPINs in a row ([count]) and when the last was typed ([lastAt], epoch ms; null if none). */
data class AppLockFailures(val count: Int, val lastAt: Long?)

/**
 * What stays on this device rather than in the profile (architecture §4.5):
 * the account the last entry used, so the next one starts there (req TXN-02);
 * the profile's theme, so the first frame and the login screen already use it
 * (req SET-02); and what this phone remembers about notifications, the alert
 * list behind the bell and the reminders it already showed (req NTF-06,
 * NTF-07), the way the web keeps all of it in localStorage. The app lock
 * (Settings › Security) is here too: the login it is on for, the MPIN's
 * verifier (never the digits) and the wrong tries so far; it has no web
 * counterpart.
 */
@Singleton
class DeviceState @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val THEME = stringPreferencesKey("theme")

        /** The alert list as JSON, ≤ 50, ≤ 30 days (`expensify.alerts.v1`). */
        val ALERTS_JSON = stringPreferencesKey("alerts_json")

        /** The last day the daily reminder showed, `YYYY-MM-DD` (`expensify.daily-reminder.v1`). */
        val DAILY_REMINDER_LAST = stringPreferencesKey("daily_reminder_last")

        /** Recurring reminders already shown, as a JSON list of keys (`expensify.bill-reminders.v1`). */
        val BILL_REMINDERS_JSON = stringPreferencesKey("bill_reminders_json")

        /** Whose alert and reminder state the three above are (`expensify.notifications-owner.v1`). */
        val OWNER_UID = stringPreferencesKey("owner_uid")

        /** The login the app lock is on for; absent while it is off. */
        val APP_LOCK_UID = stringPreferencesKey("app_lock_uid")

        /** The MPIN's verifier (`MpinRules.verifier`); the lock is only on while both it and the uid are here. */
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")

        /** Wrong MPINs in a row, and when the last one was typed, so the wait outlives the process. */
        val APP_LOCK_FAILURES = intPreferencesKey("app_lock_failures")
        val APP_LOCK_FAILED_AT = longPreferencesKey("app_lock_failed_at")
    }

    private val data: Flow<Preferences> = context.deviceStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    /** The account the last entry was saved on here; null until one is. */
    val lastAccountId: Flow<String?> = data.map { it[Keys.LAST_ACCOUNT_ID] }.distinctUntilChanged()

    /** The stored theme ("light", "dark" or "system") the profile last asked for; null until a profile loaded. */
    val theme: Flow<String?> = data.map { it[Keys.THEME] }.distinctUntilChanged()

    /** The alert list as stored; null until an alert was ever kept. */
    val alertsJson: Flow<String?> = data.map { it[Keys.ALERTS_JSON] }.distinctUntilChanged()

    /** The last day the daily reminder showed, as `YYYY-MM-DD`; null if never. */
    val dailyReminderLast: Flow<String?> = data.map { it[Keys.DAILY_REMINDER_LAST] }.distinctUntilChanged()

    /** The recurring reminders already shown, as stored; null until one was. */
    val billRemindersJson: Flow<String?> = data.map { it[Keys.BILL_REMINDERS_JSON] }.distinctUntilChanged()

    /** The login the app lock is on for; null while it is off. */
    val appLockUid: Flow<String?> = data.map { it[Keys.APP_LOCK_UID] }.distinctUntilChanged()

    /** The MPIN's verifier; null while none is set, which also means the lock is off. */
    val appLockPin: Flow<String?> = data.map { it[Keys.APP_LOCK_PIN] }.distinctUntilChanged()

    suspend fun setLastAccountId(id: String) = write { it[Keys.LAST_ACCOUNT_ID] = id }

    suspend fun setTheme(stored: String) = write { it[Keys.THEME] = stored }

    /** Turns the app lock on for a login with its MPIN's verifier, in one write so it is never on without an MPIN. */
    suspend fun setAppLock(uid: String, pinVerifier: String) = write { prefs ->
        prefs[Keys.APP_LOCK_UID] = uid
        prefs[Keys.APP_LOCK_PIN] = pinVerifier
        clearAppLockFailureKeys(prefs)
    }

    /** A new MPIN for the lock as it is. */
    suspend fun setAppLockPin(pinVerifier: String) = write { prefs ->
        prefs[Keys.APP_LOCK_PIN] = pinVerifier
        clearAppLockFailureKeys(prefs)
    }

    /** Turns the app lock off and forgets its MPIN. */
    suspend fun clearAppLock() = write { clearAppLockKeys(it) }

    /** Wrong MPINs in a row so far and when the last one was typed (epoch ms); zero and null after a right one. */
    suspend fun appLockFailures(): AppLockFailures {
        val prefs = data.first()
        return AppLockFailures(count = prefs[Keys.APP_LOCK_FAILURES] ?: 0, lastAt = prefs[Keys.APP_LOCK_FAILED_AT])
    }

    suspend fun setAppLockFailures(count: Int, lastAt: Long) = write { prefs ->
        prefs[Keys.APP_LOCK_FAILURES] = count
        prefs[Keys.APP_LOCK_FAILED_AT] = lastAt
    }

    suspend fun clearAppLockFailures() = write { clearAppLockFailureKeys(it) }

    /** Changes the alert list in one read-modify-write, so two alerts raised together both land. */
    suspend fun updateAlertsJson(transform: (String?) -> String?) = write { prefs ->
        val next = transform(prefs[Keys.ALERTS_JSON])
        if (next == null) prefs.remove(Keys.ALERTS_JSON) else prefs[Keys.ALERTS_JSON] = next
    }

    suspend fun setDailyReminderLast(ymd: String) = write { it[Keys.DAILY_REMINDER_LAST] = ymd }

    suspend fun updateBillRemindersJson(transform: (String?) -> String?) = write { prefs ->
        val next = transform(prefs[Keys.BILL_REMINDERS_JSON])
        if (next == null) prefs.remove(Keys.BILL_REMINDERS_JSON) else prefs[Keys.BILL_REMINDERS_JSON] = next
    }

    /**
     * Starts this phone's alerts and reminders over when someone other than
     * the user they were kept for logs in, so nobody sees another's alerts
     * (req NTF-07). Returns whether anything was cleared.
     */
    suspend fun claimNotifications(uid: String): Boolean {
        var cleared = false
        write { prefs ->
            if (prefs[Keys.OWNER_UID] != uid) {
                clearNotificationKeys(prefs)
                prefs[Keys.OWNER_UID] = uid
                cleared = true
            }
        }
        return cleared
    }

    /** Forgets every alert and reminder this phone showed, as deleting the account does. */
    suspend fun clearNotificationState() = write { prefs ->
        clearNotificationKeys(prefs)
        prefs.remove(Keys.OWNER_UID)
    }

    /** Forgets what belonged to the login that just left, as deleting the account does. The theme stays for the login screen. */
    suspend fun clearUserState() = write { prefs ->
        prefs.remove(Keys.LAST_ACCOUNT_ID)
        clearNotificationKeys(prefs)
        prefs.remove(Keys.OWNER_UID)
        clearAppLockKeys(prefs)
    }

    private fun clearAppLockKeys(prefs: MutablePreferences) {
        prefs.remove(Keys.APP_LOCK_UID)
        prefs.remove(Keys.APP_LOCK_PIN)
        clearAppLockFailureKeys(prefs)
    }

    private fun clearAppLockFailureKeys(prefs: MutablePreferences) {
        prefs.remove(Keys.APP_LOCK_FAILURES)
        prefs.remove(Keys.APP_LOCK_FAILED_AT)
    }

    private fun clearNotificationKeys(prefs: MutablePreferences) {
        prefs.remove(Keys.ALERTS_JSON)
        prefs.remove(Keys.DAILY_REMINDER_LAST)
        prefs.remove(Keys.BILL_REMINDERS_JSON)
    }

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        try {
            context.deviceStore.edit { block(it) }
        } catch (_: IOException) {
            // Storage trouble: the app falls back to its defaults next time.
        }
    }
}
