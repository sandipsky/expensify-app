package com.example.expensify.feature.notifications.ui

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.NotificationRules.BillReminder
import com.example.expensify.feature.notifications.domain.NotificationRules.BillReminderKind
import com.example.expensify.feature.recurring.ui.RuleLabels
import java.time.LocalDate
import java.util.Locale

/** Port of ../expensify/src/features/notifications/notification.labels.ts: how alerts read on screen. */
object AlertLabels {

    /** The icon each kind of alert shows in the list. */
    fun icon(kind: AlertKind): ImageVector = when (kind) {
        AlertKind.Budget -> AppIcons.BudgetAlert
        AlertKind.Bill -> AppIcons.Recurring
        AlertKind.Reminder -> AppIcons.Schedule
    }

    /** The bell's name for screen readers: "Alerts" or "Alerts, 3 new". */
    fun bellLabel(unread: Int): UiText =
        if (unread > 0) res(R.string.dashboard_alerts_bell_new, unread) else res(R.string.dashboard_alerts_bell)

    /** A title and a message, as a reminder is written. */
    data class Text(val title: UiText, val message: UiText)

    /** "Rent is due tomorrow" · "−Rs 20,000.00 · Bank. You'll be asked to confirm it." */
    fun reminderText(name: UiText, amount: String, account: UiText, reminder: BillReminder, today: LocalDate): Text = when {
        reminder.kind == BillReminderKind.Upcoming -> Text(
            res(R.string.reminder_upcoming_title, name),
            res(R.string.reminder_upcoming_message, amount, account),
        )
        reminder.count > 1 -> Text(
            res(R.string.reminder_waiting_many_title, name, RuleLabels.entryCount(reminder.count)),
            res(R.string.reminder_waiting_many_message, amount, account),
        )
        reminder.date == today -> Text(
            res(R.string.reminder_due_today_title, name),
            res(R.string.reminder_due_today_message, amount, account),
        )
        else -> Text(
            res(R.string.reminder_waiting_title, name),
            res(R.string.reminder_waiting_message, DateFormats.date(reminder.date), amount),
        )
    }

    private val UNITS = listOf(
        365L * 24 * 60 * 60 * 1000 to RelativeDateTimeFormatter.RelativeUnit.YEARS,
        30L * 24 * 60 * 60 * 1000 to RelativeDateTimeFormatter.RelativeUnit.MONTHS,
        7L * 24 * 60 * 60 * 1000 to RelativeDateTimeFormatter.RelativeUnit.WEEKS,
        24L * 60 * 60 * 1000 to RelativeDateTimeFormatter.RelativeUnit.DAYS,
        60L * 60 * 1000 to RelativeDateTimeFormatter.RelativeUnit.HOURS,
        60L * 1000 to RelativeDateTimeFormatter.RelativeUnit.MINUTES,
    )

    /** "3 minutes ago", "2 days ago", in the device's language; "Just now" under a minute (the web's `formatTimeAgo`). */
    fun timeAgo(at: Long, now: Long, locale: Locale = Locale.getDefault()): UiText {
        val elapsed = (now - at).coerceAtLeast(0)
        val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
        for ((ms, unit) in UNITS) {
            if (elapsed >= ms) {
                return UiText.Plain(formatter.format((elapsed / ms).toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit))
            }
        }
        return res(R.string.alerts_just_now)
    }
}
