package com.example.expensify.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations. Page state that should survive Back lives in the arguments, as the web keeps it in the URL. */
object Routes {
    // Signed out (req AUTH-05, AUTH-01): the two screens of AuthNavHost.

    @Serializable
    data object Login

    @Serializable
    data object Signup

    /**
     * The dashboard, with its period (req DSH-01): `period` is a
     * `DashboardPreset`, `from`/`to` the custom range as `YYYY-MM-DD`. Kept in
     * the arguments so Back from a drill-down returns to the same period.
     */
    @Serializable
    data class Home(
        val period: String? = null,
        val from: String? = null,
        val to: String? = null,
    )

    /**
     * The list, with what a link into it can preset (req LST-06): a period as
     * `YYYY-MM-DD` dates, a type, and categories or accounts as one id or
     * several joined by commas. Invalid values are dropped.
     */
    @Serializable
    data class Transactions(
        val from: String? = null,
        val to: String? = null,
        val type: String? = null,
        val category: String? = null,
        val account: String? = null,
    )

    @Serializable
    data object Accounts

    @Serializable
    data object Budgets

    @Serializable
    data object More

    /** The list of recent alerts behind the bell on Home (req NTF-06). */
    @Serializable
    data object Alerts

    @Serializable
    data class TransactionDetail(val id: String)

    /**
     * Add with nothing set; edit with `editId`; duplicate with `duplicateOf`
     * (req TXN-08); add on a given account with `accountId`; or confirm the
     * recurring entry of `ruleId` due on `dueDate` as edited (req REC-07).
     */
    @Serializable
    data class EntryForm(
        val editId: String? = null,
        val duplicateOf: String? = null,
        val accountId: String? = null,
        val ruleId: String? = null,
        val dueDate: String? = null,
    )

    @Serializable
    data class AccountDetail(val id: String)

    /** Add with no id; edit with one. */
    @Serializable
    data class AccountForm(val id: String? = null)

    @Serializable
    data object Categories

    /** Add to the `type` list ("expense" or "income") with no id; edit with one. */
    @Serializable
    data class CategoryForm(val type: String, val id: String? = null)

    @Serializable
    data class BudgetDetail(val id: String)

    /** Add with no id; edit with one. */
    @Serializable
    data class BudgetForm(val id: String? = null)

    @Serializable
    data object Recurring

    @Serializable
    data class RuleDetail(val id: String)

    /** Add with nothing set; edit with `id`; or "Make recurring" from the transaction `fromTransaction` (req TXN-09). */
    @Serializable
    data class RuleForm(val id: String? = null, val fromTransaction: String? = null)

    // Reports (req RPT-02): every filter is an argument, as the web keeps them in the URL,
    // and the ViewModels write changes back to the same saved-state keys.

    @Serializable
    data object Reports

    /** `period` is a `ReportPreset`; `from`/`to` the custom range; `by` a `Granularity`; `accounts` ids joined by commas. */
    @Serializable
    data class CashFlowReport(
        val period: String? = null,
        val from: String? = null,
        val to: String? = null,
        val by: String? = null,
        val accounts: String? = null,
    )

    /** `type` is "expense" or "income". */
    @Serializable
    data class CategoryReport(
        val period: String? = null,
        val from: String? = null,
        val to: String? = null,
        val type: String? = null,
        val accounts: String? = null,
    )

    @Serializable
    data class CategoryTrendsReport(
        val period: String? = null,
        val from: String? = null,
        val to: String? = null,
        val type: String? = null,
        val accounts: String? = null,
        val categories: String? = null,
    )

    @Serializable
    data class AccountsReport(
        val period: String? = null,
        val from: String? = null,
        val to: String? = null,
        val by: String? = null,
        val accounts: String? = null,
    )

    /** One budget's periods with `budget`; `periods` is 6, 12 or 24. */
    @Serializable
    data class BudgetReport(
        val budget: String? = null,
        val periods: Int? = null,
        val early: Boolean = false,
        val paused: Boolean = false,
    )

    // Settings (req SET-01…SET-05)

    @Serializable
    data object Settings

    @Serializable
    data object ProfileForm

    @Serializable
    data object ChangePassword

    /** Settings › Security: `purpose` is an `AppLockPinPurpose` ("on", "change" or "off"). */
    @Serializable
    data class AppLockPin(val purpose: String)

    @Serializable
    data object DeleteAccount
}
