package com.example.expensify.feature.notifications.data

import com.example.expensify.core.device.DeviceState
import com.example.expensify.feature.notifications.domain.AppAlert
import com.example.expensify.feature.notifications.domain.NotificationRules
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Recent alerts on this phone, newest first, behind the bell on Home (port
 * of notification.store.ts, req NTF-06). Kept in DataStore through
 * `DeviceState`, so a restart shows the same list; it belongs to the user who
 * last logged in here and starts over for another (req NTF-07).
 */
@Singleton
class AlertStore @Inject constructor(
    private val device: DeviceState,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Newest first, at most 50, none older than 30 days. */
    val alerts: Flow<List<AppAlert>> = device.alertsJson.map { decode(it) }.distinctUntilChanged()

    /** How many alerts nobody has seen yet, for the bell's badge. */
    val unreadCount: Flow<Int> = alerts.map { NotificationRules.unreadCount(it) }.distinctUntilChanged()

    /** Adds an unread alert at the top, replacing one with the same id. */
    suspend fun add(alert: AppAlert) = update { NotificationRules.add(it, alert, clock.millis()) }

    suspend fun markAllRead() = update { NotificationRules.markAllRead(it) }

    suspend fun remove(id: String) = update { list -> list.filter { it.id != id } }

    suspend fun clear() = update { emptyList() }

    /** Starts the list and the reminder state over when a different user logs in (req NTF-07). */
    suspend fun claim(uid: String): Boolean = device.claimNotifications(uid)

    private suspend fun update(transform: (List<AppAlert>) -> List<AppAlert>) {
        device.updateAlertsJson { raw -> json.encodeToString(transform(decode(raw))) }
    }

    /** What is stored, pruned; anything unreadable counts as an empty list rather than failing the bell. */
    private fun decode(raw: String?): List<AppAlert> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            NotificationRules.prune(json.decodeFromString<List<AppAlert>>(raw), clock.millis())
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
