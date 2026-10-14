package com.example.expensify.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations. Page state that should survive Back lives in the arguments, as the web keeps it in the URL. */
object Routes {
    @Serializable
    data object Home

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
}
