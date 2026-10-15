package com.example.expensify.feature.notifications.reminders

import com.example.expensify.R
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.auth.domain.NotificationPrefs
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.notifications.AlertInput
import com.example.expensify.feature.notifications.Notifier
import com.example.expensify.feature.notifications.domain.AlertKind
import com.example.expensify.feature.notifications.domain.AlertLinks
import com.example.expensify.feature.notifications.domain.AlertTone
import com.example.expensify.feature.notifications.domain.NotificationRules
import com.example.expensify.feature.notifications.ui.AlertLabels
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.recurring.ui.RuleLabels
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.ui.TxRows
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The reminder checks (port of DailyReminder.tsx and RecurringReminders.tsx),
 * given what they need to look at. The foreground observers and the
 * closed-app worker both call them, so a reminder is raised the same way and
 * at most once whichever path gets there first: what was shown is kept on
 * the device (req NTF-02, NTF-03, NTF-07).
 */
@Singleton
class ReminderChecks @Inject constructor(
    private val device: DeviceState,
    private val notifier: Notifier,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The daily reminder to log spending (req NTF-02): at the reminder time,
     * if nothing but recurring entries is dated today, once a day. Returns
     * whether it was delivered.
     */
    suspend fun dailyReminder(prefs: NotificationPrefs, todays: List<Transaction>, now: LocalDateTime): Boolean {
        val state = NotificationRules.DailyReminderState(
            enabled = prefs.dailyReminder,
            time = prefs.reminderTime,
            now = now,
            loggedToday = NotificationRules.loggedByHand(todays),
            lastShown = device.dailyReminderLast.first()?.toLocalDateOrNull(),
        )
        if (!NotificationRules.dailyReminderDue(state)) return false
        device.setDailyReminderLast(state.today.ymd())
        notifier.deliver(
            AlertInput(
                id = "reminder|${state.today.ymd()}",
                kind = AlertKind.Reminder,
                tone = AlertTone.Info,
                title = res(R.string.reminder_daily_title),
                message = res(R.string.reminder_daily_message),
                link = AlertLinks.TRANSACTIONS,
            ),
        )
        return true
    }

    /**
     * Reminders for ask-first recurring entries (req NTF-03): the day before
     * one is due, and once it's due and waiting for Confirm or Skip, each
     * once. Automatic rules need none; the runner adds their entries. Returns
     * how many were delivered.
     */
    suspend fun billReminders(
        enabled: Boolean,
        rules: List<RecurringRule>,
        accounts: List<Account>,
        categories: List<Category>,
        today: LocalDate,
    ): Int {
        if (!enabled) return 0
        val lookups = TxRows.Lookups(accounts, categories, today)
        val shown = decodeKeys(device.billRemindersJson.first()).toMutableSet()
        val added = mutableListOf<String>()

        for (rule in rules) {
            val view = RuleLabels.toView(rule, lookups)
            if (view.status != RuleStatus.Active) continue
            val reminder = NotificationRules.billReminder(rule, today) ?: continue
            val key = NotificationRules.billReminderKey(reminder)
            if (!shown.add(key)) continue
            added += key

            val amount = MoneyFormat.full(view.amount, if (view.kind == AmountKind.Transfer) Sign.Auto else Sign.ExceptZero)
            val text = AlertLabels.reminderText(view.name, amount, view.accountLabel, reminder, today)
            notifier.deliver(
                AlertInput(
                    id = "bill|$key",
                    kind = AlertKind.Bill,
                    tone = AlertTone.Info,
                    title = text.title,
                    message = text.message,
                    link = AlertLinks.RECURRING,
                ),
            )
        }

        if (added.isNotEmpty()) {
            // Keys end in their date, so old ones can be dropped.
            device.updateBillRemindersJson { raw ->
                json.encodeToString(NotificationRules.pruneShownKeys(decodeKeys(raw) + added, today))
            }
        }
        return added.size
    }

    private fun decodeKeys(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
