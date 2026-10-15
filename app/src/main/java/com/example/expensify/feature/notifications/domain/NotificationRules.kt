package com.example.expensify.feature.notifications.domain

import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.settings.domain.SettingsRules
import com.example.expensify.feature.transactions.domain.Transaction
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Port of ../expensify/src/features/notifications/notification.domain.ts and
 * notification.store.ts: when reminders fall due, as pure functions on local
 * dates and `HH:mm` times, and how the alert list is kept.
 */
object NotificationRules {

    /** The list keeps this many alerts, for this long (req NTF-06). */
    const val MAX_ALERTS = 50
    const val ALERT_MAX_AGE_MS: Long = 30L * 24 * 60 * 60 * 1000

    /** Reminders already shown are remembered this long after their date, then forgotten. */
    const val KEEP_DAYS = 60L

    /** The hour the day-before and waiting reminders are checked while the app is closed. */
    val BILL_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    /** A 24-hour local time of day, `HH:mm`. */
    fun isTimeOfDay(value: String?): Boolean = SettingsRules.isTimeOfDay(value)

    /** `time` as a time of day, or null when it isn't one. */
    fun timeOfDay(time: String): LocalTime? {
        if (!isTimeOfDay(time)) return null
        return try {
            LocalTime.parse(time)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Milliseconds from `now` until `time` on now's local date: positive while
     * it's still ahead, 0 or less once it has passed. Null for a malformed time.
     */
    fun msUntilTime(time: String, now: LocalDateTime): Long? {
        val at = timeOfDay(time) ?: return null
        return Duration.between(now, now.toLocalDate().atTime(at)).toMillis()
    }

    /** The next moment `time` comes round from `now`: today if still ahead, otherwise tomorrow. */
    fun nextTime(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    data class DailyReminderState(
        val enabled: Boolean,
        /** The reminder time, `HH:mm`. */
        val time: String,
        val now: LocalDateTime,
        /** Whether anything is logged for today already. */
        val loggedToday: Boolean,
        /** The last date the reminder was shown, if ever. */
        val lastShown: LocalDate?,
    ) {
        val today: LocalDate get() = now.toLocalDate()
    }

    /**
     * Whether the daily reminder to log spending is due (req NTF-02): it's on,
     * today's reminder time has come, nothing is logged for today yet, and it
     * hasn't been shown today. Once a day at most.
     */
    fun dailyReminderDue(state: DailyReminderState): Boolean {
        if (!state.enabled) return false
        val ms = msUntilTime(state.time, state.now) ?: return false
        return ms <= 0 && !state.loggedToday && state.lastShown != state.today
    }

    /** Entries a recurring rule added don't count as logging (req NTF-02). */
    fun loggedByHand(todays: List<Transaction>): Boolean = todays.any { it.recurringRuleId == null }

    /** `Upcoming`: an entry is due tomorrow. `Due`: entries are due and wait for Confirm or Skip. */
    enum class BillReminderKind(val stored: String) {
        Upcoming("upcoming"),
        Due("due"),
    }

    data class BillReminder(
        val ruleId: String,
        val kind: BillReminderKind,
        /** The entry's date: tomorrow's for `Upcoming`, the latest waiting one for `Due`. */
        val date: LocalDate,
        /** How many entries wait; 1 for `Upcoming`. */
        val count: Int,
    )

    /**
     * The reminder an ask-first rule has now (req NTF-03): `Due` once entries
     * wait for Confirm or Skip, else `Upcoming` when the next one is due
     * tomorrow. Automatic, paused and ended rules get none. A catch-up of
     * several missed dates is one reminder, named after the latest of them.
     */
    fun billReminder(rule: RecurringRule, today: LocalDate): BillReminder? {
        if (rule.mode != RuleMode.Confirm || !rule.active || RecurringRules.isEnded(rule)) return null
        val due = RecurringRules.dueDates(rule, today)
        if (due.isNotEmpty()) return BillReminder(rule.id, BillReminderKind.Due, due.last(), due.size)
        val tomorrow = today.plusDays(1)
        val next = RecurringRules.pendingDates(rule, 1, tomorrow).firstOrNull()
        return if (next == tomorrow) BillReminder(rule.id, BillReminderKind.Upcoming, next, 1) else null
    }

    /** Names a reminder so each is shown once: per rule, kind and date. Ends in the date. */
    fun billReminderKey(reminder: BillReminder): String = "${reminder.ruleId}|${reminder.kind.stored}|${reminder.date}"

    /** Keys end in their date, so ones older than `KEEP_DAYS` before `today` are dropped. */
    fun pruneShownKeys(keys: Collection<String>, today: LocalDate): List<String> {
        val oldest = today.minusDays(KEEP_DAYS).toString()
        return keys.filter { it.length >= 10 && it.takeLast(10) >= oldest }
    }

    /** Newest first, at most `MAX_ALERTS`, none older than `ALERT_MAX_AGE_MS` (req NTF-06). */
    fun prune(alerts: List<AppAlert>, now: Long): List<AppAlert> =
        alerts.filter { now - it.at < ALERT_MAX_AGE_MS }.sortedByDescending { it.at }.take(MAX_ALERTS)

    /** Adds an unread alert at the top, replacing one with the same id, and prunes. */
    fun add(alerts: List<AppAlert>, alert: AppAlert, now: Long): List<AppAlert> =
        prune(listOf(alert.copy(at = now, read = false)) + alerts.filter { it.id != alert.id }, now)

    fun markAllRead(alerts: List<AppAlert>): List<AppAlert> = alerts.map { if (it.read) it else it.copy(read = true) }

    fun unreadCount(alerts: List<AppAlert>): Int = alerts.count { !it.read }
}
