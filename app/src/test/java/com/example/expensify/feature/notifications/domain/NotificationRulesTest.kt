package com.example.expensify.feature.notifications.domain

import com.example.expensify.core.money.Money
import com.example.expensify.feature.notifications.domain.NotificationRules.BillReminderKind
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringTemplate
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

/** Req NTF-02, NTF-03, NTF-06 and the pure parts of notification.domain.ts and notification.store.ts. */
class NotificationRulesTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    private val template = RecurringTemplate(TxType.Expense, Money.rupees(18_000), "bank", null, "rent", "Rent")

    private fun rule(
        nextDueDate: String,
        mode: RuleMode = RuleMode.Confirm,
        active: Boolean = true,
        endType: EndType = EndType.Never,
        maxCount: Int? = null,
        occurrences: Int = 0,
        id: String = "r1",
    ) = RecurringRule(
        id = id,
        template = template,
        frequency = Frequency.Monthly,
        interval = 1,
        weekdays = emptyList(),
        dayOfMonth = 14,
        startDate = d("2026-08-14"),
        endType = endType,
        endDate = null,
        maxCount = maxCount,
        mode = mode,
        nextDueDate = d(nextDueDate),
        occurrences = occurrences,
        active = active,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun tx(ruleId: String?) = Transaction(
        id = "t-$ruleId",
        type = TxType.Expense,
        amount = Money.rupees(100),
        currency = "NPR",
        accountId = "a1",
        toAccountId = null,
        categoryId = "food",
        date = d("2026-10-14"),
        note = null,
        recurringRuleId = ruleId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun alert(id: String, at: Long, read: Boolean = false) =
        AppAlert(id, AlertKind.Budget, AlertTone.Warn, "Food: 80% used", "Rs 100.00 left", AlertLinks.budget("b1"), at, read)

    @Test
    fun `ms until a time counts to it on today's date, and goes to zero or below once it has passed`() {
        val now = LocalDateTime.of(2026, 10, 14, 19, 30)
        assertThat(NotificationRules.msUntilTime("20:00", now)).isEqualTo(30L * 60 * 1000)
        assertThat(NotificationRules.msUntilTime("19:30", now)).isEqualTo(0L)
        assertThat(NotificationRules.msUntilTime("09:00", now)).isLessThan(0L)
        assertThat(NotificationRules.msUntilTime("25:00", now)).isNull()
        assertThat(NotificationRules.nextTime(LocalTime.of(20, 0), now)).isEqualTo(LocalDateTime.of(2026, 10, 14, 20, 0))
        assertThat(NotificationRules.nextTime(LocalTime.of(9, 0), now)).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 0))
    }

    @Test
    fun `NTF-02 the daily reminder is due once the time has come, nothing is logged and it hasn't shown today`() {
        val base = NotificationRules.DailyReminderState(
            enabled = true,
            time = "20:00",
            now = LocalDateTime.of(2026, 10, 14, 20, 5),
            loggedToday = false,
            lastShown = null,
        )
        assertThat(NotificationRules.dailyReminderDue(base)).isTrue()
        assertThat(NotificationRules.dailyReminderDue(base.copy(enabled = false))).isFalse()
        assertThat(NotificationRules.dailyReminderDue(base.copy(now = LocalDateTime.of(2026, 10, 14, 19, 59)))).isFalse()
        assertThat(NotificationRules.dailyReminderDue(base.copy(loggedToday = true))).isFalse()
        assertThat(NotificationRules.dailyReminderDue(base.copy(lastShown = d("2026-10-14")))).isFalse()
        assertThat(NotificationRules.dailyReminderDue(base.copy(lastShown = d("2026-10-13")))).isTrue()
        assertThat(NotificationRules.dailyReminderDue(base.copy(time = "8pm"))).isFalse()
    }

    @Test
    fun `NTF-02 entries a recurring rule added don't count as logging`() {
        assertThat(NotificationRules.loggedByHand(emptyList())).isFalse()
        assertThat(NotificationRules.loggedByHand(listOf(tx("r1")))).isFalse()
        assertThat(NotificationRules.loggedByHand(listOf(tx("r1"), tx(null)))).isTrue()
    }

    @Test
    fun `NTF-03 an ask-first rule reminds the day before, then once its entries wait`() {
        val today = d("2026-10-14")
        assertThat(NotificationRules.billReminder(rule("2026-10-15"), today))
            .isEqualTo(NotificationRules.BillReminder("r1", BillReminderKind.Upcoming, d("2026-10-15"), 1))
        assertThat(NotificationRules.billReminder(rule("2026-10-14"), today))
            .isEqualTo(NotificationRules.BillReminder("r1", BillReminderKind.Due, d("2026-10-14"), 1))
        // A catch-up of several missed dates is one reminder, named after the latest.
        assertThat(NotificationRules.billReminder(rule("2026-08-14"), today))
            .isEqualTo(NotificationRules.BillReminder("r1", BillReminderKind.Due, d("2026-10-14"), 3))
        assertThat(NotificationRules.billReminder(rule("2026-10-16"), today)).isNull()
    }

    @Test
    fun `NTF-03 automatic, paused and ended rules get no reminder`() {
        val today = d("2026-10-14")
        assertThat(NotificationRules.billReminder(rule("2026-10-15", mode = RuleMode.Auto), today)).isNull()
        assertThat(NotificationRules.billReminder(rule("2026-10-15", active = false), today)).isNull()
        assertThat(NotificationRules.billReminder(rule("2026-10-15", endType = EndType.Count, maxCount = 2, occurrences = 2), today)).isNull()
    }

    @Test
    fun `reminder keys name the rule, kind and date, and old ones are forgotten after 60 days`() {
        val reminder = NotificationRules.BillReminder("r1", BillReminderKind.Due, d("2026-10-14"), 2)
        assertThat(NotificationRules.billReminderKey(reminder)).isEqualTo("r1|due|2026-10-14")
        val kept = NotificationRules.pruneShownKeys(
            listOf("r1|due|2026-10-14", "r1|upcoming|2026-08-15", "r2|due|2026-08-14", "junk"),
            today = d("2026-10-14"),
        )
        assertThat(kept).containsExactly("r1|due|2026-10-14", "r1|upcoming|2026-08-15")
    }

    @Test
    fun `NTF-06 the list keeps at most 50 alerts for 30 days, newest first, and the same id replaces the old one`() {
        val now = 1_000_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val many = (0 until 60).map { alert("a$it", now - it * 1000) }
        assertThat(NotificationRules.prune(many, now)).hasSize(50)
        val old = alert("old", now - 31 * day)
        val recent = alert("recent", now - 29 * day)
        assertThat(NotificationRules.prune(listOf(old, recent), now)).containsExactly(recent)

        val added = NotificationRules.add(listOf(alert("x", now - 5000, read = true), alert("y", now - 1000)), alert("x", 0), now)
        assertThat(added.map { it.id }).containsExactly("x", "y").inOrder()
        assertThat(added[0].at).isEqualTo(now)
        assertThat(added[0].read).isFalse()
        assertThat(NotificationRules.unreadCount(added)).isEqualTo(2)
        assertThat(NotificationRules.unreadCount(NotificationRules.markAllRead(added))).isEqualTo(0)
    }

    @Test
    fun `alert links round-trip through the expensify scheme`() {
        assertThat(AlertLinks.parse(AlertLinks.budget("b1"))).isEqualTo(AlertTarget.Budget("b1"))
        assertThat(AlertLinks.parse(AlertLinks.RECURRING)).isEqualTo(AlertTarget.Recurring)
        assertThat(AlertLinks.parse(AlertLinks.TRANSACTIONS)).isEqualTo(AlertTarget.Transactions)
        assertThat(AlertLinks.parse("expensify://transactions?from=2026-10-01")).isEqualTo(AlertTarget.Transactions)
        assertThat(AlertLinks.parse("https://example.com/budgets/b1")).isNull()
        assertThat(AlertLinks.parse(null)).isNull()
    }
}
