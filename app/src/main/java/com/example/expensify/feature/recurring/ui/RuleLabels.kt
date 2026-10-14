package com.example.expensify.feature.recurring.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.money.Money
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.recurring.domain.RuleState
import com.example.expensify.feature.recurring.domain.RuleStatus
import com.example.expensify.feature.recurring.domain.Schedule
import com.example.expensify.feature.transactions.domain.TxType
import com.example.expensify.feature.transactions.ui.TxRows
import com.example.expensify.feature.transactions.ui.amountKind
import com.example.expensify.feature.transactions.ui.signed
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

// Port of ../expensify/src/features/recurring/recurring.labels.ts: how rules read on screen.

@StringRes
fun Frequency.labelRes(): Int = when (this) {
    Frequency.Daily -> R.string.rule_frequency_daily
    Frequency.Weekly -> R.string.rule_frequency_weekly
    Frequency.Monthly -> R.string.rule_frequency_monthly
    Frequency.Yearly -> R.string.rule_frequency_yearly
}

/** "day" or "days", for the "Every N …" field's suffix. */
@StringRes
fun Frequency.unitRes(plural: Boolean): Int = when (this) {
    Frequency.Daily -> if (plural) R.string.rule_unit_days else R.string.rule_unit_day
    Frequency.Weekly -> if (plural) R.string.rule_unit_weeks else R.string.rule_unit_week
    Frequency.Monthly -> if (plural) R.string.rule_unit_months else R.string.rule_unit_month
    Frequency.Yearly -> if (plural) R.string.rule_unit_years else R.string.rule_unit_year
}

@StringRes
fun EndType.labelRes(): Int = when (this) {
    EndType.Never -> R.string.rule_end_never
    EndType.Count -> R.string.rule_end_count
    EndType.Until -> R.string.rule_end_until
}

@StringRes
fun RuleMode.labelRes(): Int = when (this) {
    RuleMode.Auto -> R.string.rule_mode_auto
    RuleMode.Confirm -> R.string.rule_mode_confirm
}

/** A rule as the recurring screens show it. */
data class RuleView(
    val rule: RecurringRule,
    /** The note, else the category, else "Transfer": what entries it adds are called. */
    val name: UiText,
    /** The same, as plain text for sorting. */
    val sortName: String,
    /** The category's icon and color, or neutral ones for transfers and missing categories. */
    val icon: String,
    val color: String?,
    /** "Every month on the 1st". */
    val schedule: UiText,
    /** "Bank", or "Bank → Cash" for a transfer. */
    val accountLabel: UiText,
    val kind: AmountKind,
    /** + for income, − for expense, unsigned for a transfer. */
    val amount: Money,
    val status: RuleStatus,
    /** The next date an entry is due, or null once the rule has ended. */
    val next: LocalDate?,
    /** Entries due by today and not yet added or skipped, oldest first. */
    val due: List<LocalDate>,
) {
    val id: String get() = rule.id
    val asksFirst: Boolean get() = rule.mode == RuleMode.Confirm
}

/** The screens' groups of rules. */
data class RuleGroups(
    val all: List<RuleView>,
    /** Running rules, and those waiting for an account to be fixed. */
    val active: List<RuleView>,
    val paused: List<RuleView>,
    val ended: List<RuleView>,
    /** Ask-first rules with entries waiting for Confirm or Skip, oldest due first. */
    val waiting: List<RuleView>,
) {
    val isEmpty: Boolean get() = all.isEmpty()
}

object RuleLabels {

    /** "1st", "22nd", "30th". */
    fun ordinal(day: Int): String {
        val teen = day % 100 in 11..13
        val suffix = if (teen) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** A weekday's name in the device's language: "Mon" or "Monday". */
    fun weekdayName(isoDay: Int, style: TextStyle = TextStyle.SHORT, locale: Locale = Locale.getDefault()): String =
        DayOfWeek.of(isoDay).getDisplayName(style, locale)

    /** "Mon", "Mon and Thu", "Mon, Wed and Fri". */
    fun joinList(items: List<String>): UiText = when (items.size) {
        0 -> plain("")
        1 -> plain(items[0])
        else -> res(R.string.list_two, items.dropLast(1).joinToString(", "), items.last())
    }

    /**
     * The schedule in words: "Every day", "Every 2 weeks on Mon and Thu",
     * "Every month on the 1st", "Every month on the last day", "Every year on 25 Dec".
     */
    fun scheduleLabel(schedule: Schedule, locale: Locale = Locale.getDefault()): UiText {
        val n = max(1, schedule.interval)
        val every: UiText = when (schedule.frequency) {
            Frequency.Daily -> if (n == 1) res(R.string.rule_every_day) else res(R.string.rule_every_n_days, n)
            Frequency.Weekly -> if (n == 1) res(R.string.rule_every_week) else res(R.string.rule_every_n_weeks, n)
            Frequency.Monthly -> if (n == 1) res(R.string.rule_every_month) else res(R.string.rule_every_n_months, n)
            Frequency.Yearly -> if (n == 1) res(R.string.rule_every_year) else res(R.string.rule_every_n_years, n)
        }
        return when (schedule.frequency) {
            Frequency.Daily -> every
            Frequency.Weekly -> res(R.string.rule_on, every, joinList(RecurringRules.weekdaysOf(schedule).map { weekdayName(it, TextStyle.SHORT, locale) }))
            Frequency.Yearly -> res(R.string.rule_on, every, DateTimeFormatter.ofPattern("d MMM", locale).format(schedule.startDate))
            Frequency.Monthly -> {
                val day = RecurringRules.dayOfMonthOf(schedule)
                res(R.string.rule_on_the, every, if (day == RecurringRules.LAST_DAY) res(R.string.rule_last_day) else plain(ordinal(day)))
            }
        }
    }

    /** "1 entry", "12 entries", as a resource with its count. */
    fun entryCount(count: Int): UiText = UiText.Plural(R.plurals.entries_count, count, listOf(count))

    /** How the rule ends, or null for one that never does. */
    fun endLabel(rule: RuleState): UiText? {
        val maxCount = rule.maxCount
        val endDate = rule.endDate
        return when {
            rule.endType == EndType.Count && maxCount != null ->
                res(R.string.rule_ends_after, entryCount(maxCount), max(0, maxCount - rule.occurrences))
            rule.endType == EndType.Until && endDate != null -> res(R.string.rule_ends_on, DateFormats.date(endDate))
            else -> null
        }
    }

    fun toView(rule: RecurringRule, lookups: TxRows.Lookups): RuleView {
        val template = rule.template
        val transfer = template.type == TxType.Transfer
        val category = TxRows.categoryOf(lookups, template.type, template.categoryId)
        val status = RecurringRules.statusOf(rule) { lookups.accountsById.containsKey(it) }
        val ended = status == RuleStatus.Ended || RecurringRules.isEnded(rule)
        val note = template.note?.trim().orEmpty()
        val sortName = note.ifEmpty { template.categoryId?.let { lookups.categoriesById[it]?.name } ?: if (transfer) "Transfer" else "" }
        return RuleView(
            rule = rule,
            name = if (note.isNotEmpty()) plain(note) else category.name,
            sortName = sortName,
            icon = category.icon,
            color = category.color,
            schedule = scheduleLabel(rule),
            accountLabel = TxRows.accountLabel(lookups, template.type, template.accountId, template.toAccountId),
            kind = template.type.amountKind(),
            amount = template.type.signed(template.amount),
            status = status,
            next = if (ended) null else rule.nextDueDate,
            due = if (status == RuleStatus.Active) RecurringRules.dueDates(rule, lookups.today) else emptyList(),
        )
    }

    /** Soonest next date first, ended rules last, then by name. */
    val viewOrder: Comparator<RuleView> =
        compareBy<RuleView> { it.next ?: LocalDate.MAX }.thenBy { it.sortName.lowercase() }

    fun group(rules: List<RecurringRule>, lookups: TxRows.Lookups): RuleGroups {
        val all = rules.map { toView(it, lookups) }.sortedWith(viewOrder)
        return RuleGroups(
            all = all,
            active = all.filter { it.status == RuleStatus.Active || it.status == RuleStatus.MissingAccount },
            paused = all.filter { it.status == RuleStatus.Paused },
            ended = all.filter { it.status == RuleStatus.Ended },
            waiting = all.filter { it.asksFirst && it.due.isNotEmpty() }.sortedBy { it.due[0] },
        )
    }

    /** "Next 1 Oct", "Due", "Paused", "Ended". */
    fun nextLabel(view: RuleView, today: LocalDate, locale: Locale = Locale.getDefault()): UiText {
        val next = view.next
        return when {
            view.status == RuleStatus.Paused -> res(R.string.rule_next_paused)
            next == null -> res(R.string.rule_next_ended)
            view.due.isNotEmpty() -> res(R.string.rule_next_due)
            else -> {
                val pattern = if (next.year == today.year) "d MMM" else "d MMM yyyy"
                res(R.string.rule_next, DateTimeFormatter.ofPattern(pattern, locale).format(next))
            }
        }
    }

    /** "Due Thu, 1 Oct 2026 · Bank", with how many more wait after it. */
    fun dueCaption(view: RuleView): UiText {
        val first = view.due.firstOrNull() ?: return view.accountLabel
        val due = res(R.string.rule_due_caption, DateFormats.date(first), view.accountLabel)
        val more = view.due.size - 1
        return if (more > 0) res(R.string.rule_due_then_more, due, entryCount(more)) else due
    }

    /** "Spends", "Receives" or "Moves": the caption over the amount on the rule's page. */
    @StringRes
    fun captionRes(type: TxType): Int = when (type) {
        TxType.Expense -> R.string.rule_caption_spends
        TxType.Income -> R.string.rule_caption_receives
        TxType.Transfer -> R.string.rule_caption_moves
    }
}
