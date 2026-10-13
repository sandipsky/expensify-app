package com.example.expensify.feature.transactions.ui

import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Near
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate

/** Port of ../expensify/src/features/transactions/transaction.labels.ts: what a row is called. */
object TxRows {

    /** What a transfer's badge draws, since a transfer has no category. */
    const val TRANSFER_ICON = "refresh"

    /** What an entry whose category no longer exists draws. */
    const val MISSING_CATEGORY_ICON = "help"

    /** Names the rows need, looked up by id. */
    data class Lookups(
        val accountsById: Map<String, Account>,
        val categoriesById: Map<String, Category>,
        val today: LocalDate,
    ) {
        constructor(accounts: List<Account>, categories: List<Category>, today: LocalDate) : this(
            accounts.associateBy { it.id },
            categories.associateBy { it.id },
            today,
        )
    }

    data class CategoryView(val name: UiText, val icon: String, val color: String?)

    fun accountName(lookups: Lookups, id: String?): UiText =
        id?.let { lookups.accountsById[it]?.name }?.let(::plain) ?: res(R.string.tx_deleted_account)

    /** Archived categories still name their entries. */
    fun categoryOf(lookups: Lookups, tx: Transaction): CategoryView {
        if (tx.type == TxType.Transfer) return CategoryView(res(R.string.tx_type_transfer), TRANSFER_ICON, null)
        val category = tx.categoryId?.let { lookups.categoriesById[it] }
        if (category != null) return CategoryView(plain(category.name), category.icon, category.color)
        return CategoryView(res(R.string.tx_deleted_category), MISSING_CATEGORY_ICON, null)
    }

    fun toRow(tx: Transaction, lookups: Lookups): EntryRowUi {
        val account = accountName(lookups, tx.accountId)
        val category = categoryOf(lookups, tx)
        val accountLabel = if (tx.type == TxType.Transfer) {
            res(R.string.tx_transfer_arrow, account, accountName(lookups, tx.toAccountId))
        } else {
            account
        }
        val note = tx.note?.trim().orEmpty()
        return EntryRowUi(
            id = tx.id,
            title = if (note.isNotEmpty()) plain(note) else category.name,
            // With a note on top, the category goes in the detail line; otherwise it's already the title.
            subtitle = if (note.isNotEmpty()) res(R.string.tx_subtitle_with_category, category.name, accountLabel) else accountLabel,
            iconName = category.icon,
            colorHex = category.color,
            kind = when (tx.type) {
                TxType.Expense -> AmountKind.Expense
                TxType.Income -> AmountKind.Income
                TxType.Transfer -> AmountKind.Transfer
            },
            amount = if (tx.type == TxType.Expense) -tx.amount else tx.amount,
            upcoming = TransactionRules.isUpcoming(tx, lookups.today),
        )
    }

    /** A day header: "Today · Sat, 26 Sep 2026", or just the date. */
    fun dayLabel(date: LocalDate, today: LocalDate): UiText {
        val formatted = plain(DateFormats.date(date))
        val near = when (DateFormats.nearOf(date, today)) {
            Near.Today -> R.string.day_today
            Near.Yesterday -> R.string.day_yesterday
            Near.Tomorrow -> R.string.day_tomorrow
            null -> return formatted
        }
        return res(R.string.day_near_label, res(near), formatted)
    }
}
