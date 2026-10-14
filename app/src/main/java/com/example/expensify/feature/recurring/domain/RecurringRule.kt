package com.example.expensify.feature.recurring.domain

import com.example.expensify.core.money.Money
import com.example.expensify.feature.transactions.domain.TxType
import java.time.Instant
import java.time.LocalDate

enum class Frequency(val stored: String) {
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly"),
    ;

    companion object {
        fun fromStored(value: String?): Frequency = entries.firstOrNull { it.stored == value } ?: Monthly
    }
}

/** How a rule ends. */
enum class EndType(val stored: String) {
    Never("never"),
    Count("count"),
    Until("until"),
    ;

    companion object {
        fun fromStored(value: String?): EndType = entries.firstOrNull { it.stored == value } ?: Never
    }
}

/** What happens on the due date: add the entry, or ask first. */
enum class RuleMode(val stored: String) {
    Auto("auto"),
    Confirm("confirm"),
    ;

    companion object {
        fun fromStored(value: String?): RuleMode = entries.firstOrNull { it.stored == value } ?: Auto
    }
}

/** What each entry the rule adds is written with. */
data class RecurringTemplate(
    val type: TxType,
    /** Positive; `type` gives the direction. */
    val amount: Money,
    /** Source account. */
    val accountId: String,
    /** Transfers only. */
    val toAccountId: String?,
    /** Null for transfers. */
    val categoryId: String?,
    val note: String?,
)

/** The schedule part of a rule, which the date functions read (req BR-12). */
interface Schedule {
    val frequency: Frequency

    /** 1 or more: "every 2 weeks" is 2. */
    val interval: Int

    /** Weekly rules: ISO weekdays, 1 = Monday … 7 = Sunday. Empty for other frequencies. */
    val weekdays: List<Int>

    /** Monthly rules: 1–31; 29–31 fall on the last day of shorter months. Null otherwise. */
    val dayOfMonth: Int?

    /** The first entry falls on the first schedule date on or after it. */
    val startDate: LocalDate
}

/** The schedule, how the rule ends and where it stands: what listing its pending dates needs (req BR-13). */
interface RuleState : Schedule {
    val endType: EndType

    /** `until` rules: the last date an entry may fall on. */
    val endDate: LocalDate?

    /** `count` rules: how many entries it adds in all. */
    val maxCount: Int?

    /** Entries added so far; skipped ones don't count. */
    val occurrences: Int

    /** The next schedule date not yet added or skipped. */
    val nextDueDate: LocalDate
}

/** A schedule on its own, as a form builds one. */
data class ScheduleSpec(
    override val frequency: Frequency,
    override val interval: Int,
    override val weekdays: List<Int>,
    override val dayOfMonth: Int?,
    override val startDate: LocalDate,
) : Schedule

/** A rule as a form previews it before it is saved. */
data class RuleSpec(
    override val frequency: Frequency,
    override val interval: Int,
    override val weekdays: List<Int>,
    override val dayOfMonth: Int?,
    override val startDate: LocalDate,
    override val endType: EndType,
    override val endDate: LocalDate?,
    override val maxCount: Int?,
    override val occurrences: Int,
    override val nextDueDate: LocalDate,
) : RuleState

/** users/{uid}/recurringRules/{id} (req §8). */
data class RecurringRule(
    val id: String,
    val template: RecurringTemplate,
    override val frequency: Frequency,
    override val interval: Int,
    override val weekdays: List<Int>,
    override val dayOfMonth: Int?,
    override val startDate: LocalDate,
    override val endType: EndType,
    override val endDate: LocalDate?,
    override val maxCount: Int?,
    val mode: RuleMode,
    override val nextDueDate: LocalDate,
    override val occurrences: Int,
    /** False while paused. */
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) : RuleState

/**
 * What a form saves when creating or updating; the repository sets the id,
 * the count of entries added, the paused flag and the timestamps. The device
 * works out `nextDueDate` from the schedule.
 */
data class RuleBody(
    val template: RecurringTemplate,
    override val frequency: Frequency,
    override val interval: Int,
    override val weekdays: List<Int>,
    override val dayOfMonth: Int?,
    override val startDate: LocalDate,
    val endType: EndType,
    val endDate: LocalDate?,
    val maxCount: Int?,
    val mode: RuleMode,
    val nextDueDate: LocalDate,
) : Schedule
