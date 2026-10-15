package com.example.expensify.feature.notifications.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What raised an alert: a budget, an ask-first recurring entry, the daily reminder (notification.types.ts). */
@Serializable
enum class AlertKind {
    @SerialName("budget")
    Budget,

    @SerialName("bill")
    Bill,

    @SerialName("reminder")
    Reminder,
}

/** How the alert reads: `Warn` and `Error` for budget thresholds, `Info` for reminders. */
@Serializable
enum class AlertTone {
    @SerialName("info")
    Info,

    @SerialName("success")
    Success,

    @SerialName("warn")
    Warn,

    @SerialName("error")
    Error,
}

/**
 * One alert in the list behind the bell (req NTF-06). Kept on this device as
 * JSON, the way the web keeps `expensify.alerts.v1` in localStorage; the
 * titles are stored already written, so the list reads the same later.
 */
@Serializable
data class AppAlert(
    /** Names what it's about, so the same alert raised again replaces the old one. */
    val id: String,
    val kind: AlertKind,
    val tone: AlertTone,
    val title: String,
    val message: String,
    /** The app link it leads to, e.g. `expensify://budgets/4`, or null. */
    val link: String?,
    /** When it was raised, in ms since the epoch. */
    val at: Long,
    val read: Boolean,
)

/** Where an alert leads: the web's `/budgets/:id`, `/recurring` and `/transactions` links. */
sealed interface AlertTarget {
    data class Budget(val id: String) : AlertTarget
    data object Recurring : AlertTarget
    data object Transactions : AlertTarget
}

/**
 * The app links alerts carry, as `expensify://` URIs so a system notification
 * can open them too (architecture §8). Anything else parses to null and the
 * row just opens nothing.
 */
object AlertLinks {
    const val SCHEME = "expensify"
    const val RECURRING = "$SCHEME://recurring"
    const val TRANSACTIONS = "$SCHEME://transactions"

    private val BUDGET = Regex("^$SCHEME://budgets/([A-Za-z0-9]{1,64})$")

    fun budget(id: String): String = "$SCHEME://budgets/$id"

    fun parse(link: String?): AlertTarget? {
        if (link == null) return null
        val trimmed = link.trim().trimEnd('/')
        return when {
            trimmed == RECURRING -> AlertTarget.Recurring
            trimmed == TRANSACTIONS || trimmed.startsWith("$TRANSACTIONS?") -> AlertTarget.Transactions
            else -> BUDGET.matchEntire(trimmed)?.let { AlertTarget.Budget(it.groupValues[1]) }
        }
    }
}
