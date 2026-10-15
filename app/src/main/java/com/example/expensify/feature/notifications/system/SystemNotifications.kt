package com.example.expensify.feature.notifications.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.expensify.MainActivity
import com.example.expensify.R
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.AppAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One channel per kind of alert (architecture §9), so the phone's settings can mute each on its own. */
object NotificationChannels {
    const val BUDGET_ALERTS = "budget_alerts"
    const val BILL_REMINDERS = "bill_reminders"
    const val DAILY_REMINDER = "daily_reminder"

    fun of(kind: AlertKind): String = when (kind) {
        AlertKind.Budget -> BUDGET_ALERTS
        AlertKind.Bill -> BILL_REMINDERS
        AlertKind.Reminder -> DAILY_REMINDER
    }

    /** Creating a channel again is a no-op, so this runs on every app start. */
    fun create(context: Context) {
        val channels = listOf(
            channel(context, BUDGET_ALERTS, R.string.channel_budget_alerts, R.string.channel_budget_alerts_description),
            channel(context, BILL_REMINDERS, R.string.channel_bill_reminders, R.string.channel_bill_reminders_description),
            channel(context, DAILY_REMINDER, R.string.channel_daily_reminder, R.string.channel_daily_reminder_description),
        )
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }

    private fun channel(context: Context, id: String, name: Int, description: Int): NotificationChannelCompat =
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(name))
            .setDescription(context.getString(description))
            .build()
}

/**
 * System notifications (port of browser-notifications.ts): shown while the
 * app is in the background, when the phone allows them. Tapping one opens
 * the alert's page through the activity's `expensify://` link.
 */
@Singleton
class SystemNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /** Whether the phone lets this app post: the app-level switch, and on Android 13+ the runtime permission. */
    fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Shows a system notification; false where it can't, so the caller keeps
     * the alert in the app instead. The alert's id is its tag, so the same
     * alert raised again replaces the earlier one.
     */
    @SuppressLint("MissingPermission")
    fun post(alert: AppAlert): Boolean {
        if (!canPost()) return false
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = alert.link?.toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.of(alert.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(alert.id, alert.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            // The permission was taken away between the check and the post.
            false
        }
    }
}
