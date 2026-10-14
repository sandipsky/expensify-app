package com.example.expensify.feature.budgets.domain

import com.example.expensify.core.money.Money
import java.time.Instant
import java.time.LocalDate

/** Shortest first, as the period switcher lists them. */
enum class BudgetPeriod(val stored: String) {
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly"),
    ;

    companion object {
        fun fromStored(value: String?): BudgetPeriod = entries.firstOrNull { it.stored == value } ?: Monthly
    }
}

/** The highest threshold already alerted in the period that starts on `periodStart` (req BR-11). */
data class BudgetAlert(
    /** The first day of the period. */
    val periodStart: LocalDate,
    /** Percent used, e.g. 80. */
    val threshold: Int,
)

/** users/{uid}/budgets/{id} (req §8). */
data class Budget(
    val id: String,
    /** 1–40 characters; shown on the card. */
    val name: String,
    /** Limit per period. */
    val amount: Money,
    /** Weeks run Monday to Sunday; months and years follow the calendar. */
    val period: BudgetPeriod,
    /** Expense categories it counts; empty means every expense. */
    val categoryIds: List<String>,
    /** Carry the previous period's unspent or overspent amount into this one. */
    val rollover: Boolean,
    /** Percent-used levels that alert once per period: [80, 100], or empty for none. */
    val alertThresholds: List<Int>,
    val lastAlert: BudgetAlert?,
    /** False while paused: settings kept, no progress shown, no alerts. */
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** What a form saves; the repository sets the id, the alert record, the paused flag and the timestamps. */
data class BudgetBody(
    val name: String,
    val amount: Money,
    val period: BudgetPeriod,
    val categoryIds: List<String>,
    val rollover: Boolean,
    val alertThresholds: List<Int>,
)
