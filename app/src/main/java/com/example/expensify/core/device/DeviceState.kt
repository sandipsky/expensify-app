package com.example.expensify.core.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(name = "device_state")

/**
 * What stays on this device rather than in the profile (architecture §4.5):
 * the account the last entry used, so the next one starts there (req TXN-02),
 * and the profile's theme, so the first frame and the login screen already
 * use it (req SET-02), the way the web keeps both in localStorage.
 */
@Singleton
class DeviceState @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val THEME = stringPreferencesKey("theme")
    }

    private val data: Flow<Preferences> = context.deviceStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    /** The account the last entry was saved on here; null until one is. */
    val lastAccountId: Flow<String?> = data.map { it[Keys.LAST_ACCOUNT_ID] }.distinctUntilChanged()

    /** The stored theme ("light", "dark" or "system") the profile last asked for; null until a profile loaded. */
    val theme: Flow<String?> = data.map { it[Keys.THEME] }.distinctUntilChanged()

    suspend fun setLastAccountId(id: String) = write { it[Keys.LAST_ACCOUNT_ID] = id }

    suspend fun setTheme(stored: String) = write { it[Keys.THEME] = stored }

    /** Forgets what belonged to the login that just left, as deleting the account does. The theme stays for the login screen. */
    suspend fun clearUserState() = write { it.remove(Keys.LAST_ACCOUNT_ID) }

    private suspend fun write(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.deviceStore.edit { block(it) }
        } catch (_: IOException) {
            // Storage trouble: the app falls back to its defaults next time.
        }
    }
}
