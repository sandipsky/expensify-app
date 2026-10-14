package com.example.expensify.core.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 * the way the web keeps it in localStorage.
 */
@Singleton
class DeviceState @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
    }

    /** The account the last entry was saved on here; null until one is. */
    val lastAccountId: Flow<String?> = context.deviceStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { it[Keys.LAST_ACCOUNT_ID] }
        .distinctUntilChanged()

    suspend fun setLastAccountId(id: String) {
        try {
            context.deviceStore.edit { it[Keys.LAST_ACCOUNT_ID] = id }
        } catch (_: IOException) {
            // Storage trouble: the next entry starts on the first account instead.
        }
    }
}
