package com.example.expensify.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations. Page state that should survive Back lives in the arguments, as the web keeps it in the URL. */
object Routes {
    @Serializable
    data object Home

    @Serializable
    data object Transactions

    @Serializable
    data object Accounts

    @Serializable
    data object Budgets

    @Serializable
    data object More

    @Serializable
    data class AccountDetail(val id: String)

    /** Add with no id; edit with one. */
    @Serializable
    data class AccountForm(val id: String? = null)
}
