package com.example.expensify.feature.transactions.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.date.Near
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.AmountKind
import com.example.expensify.designsystem.components.EntryRowUi
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.domain.categoryOrder
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate

@StringRes
fun TxType.labelRes(): Int = when (this) {
    TxType.Expense -> R.string.tx_type_expense
    TxType.Income -> R.string.tx_type_income
    TxType.Transfer -> R.string.tx_type_transfer
}

/** The color an amount of this type takes; always beside a sign and an icon (req NFR-06). */
fun TxType.amountKind(): AmountKind = when (this) {
    TxType.Expense -> AmountKind.Expense
    TxType.Income -> AmountKind.Income
    TxType.Transfer -> AmountKind.Transfer
}

/** The chip tone a type reads in: expense red, income green, transfer neutral. */
fun TxType.chipTone(): TagTone = when (this) {
    TxType.Expense -> TagTone.Error
    TxType.Income -> TagTone.Success
    TxType.Transfer -> TagTone.Neutral
}

/** The category type an entry of this type uses; null for transfers. */
fun TxType.categoryType(): CategoryType? = when (this) {
    TxType.Expense -> CategoryType.Expense
    TxType.Income -> CategoryType.Income
    TxType.Transfer -> null
}

/** + for income, − for expense, unsigned for a transfer: how a row and a page write the amount. */
fun TxType.signed(amount: Money): Money = if (this == TxType.Expense) -amount else amount

/** One day of a grouped list, newest first. */
data class DayUi(val date: LocalDate, val label: UiText, val net: Money, val rows: List<EntryRowUi>)

/** An account as a picker offers it; archived ones only when the entry already uses them (req TXN-03). */
data class AccountPick(val id: String, val name: String, val icon: String, val color: String, val archived: Boolean)

/** A category tile in the picker grid. */
data class CategoryPick(val id: String, val name: String, val icon: String, val color: String, val archived: Boolean, val type: CategoryType)

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
    fun categoryOf(lookups: Lookups, type: TxType, categoryId: String?): CategoryView {
        if (type == TxType.Transfer) return CategoryView(res(R.string.tx_type_transfer), TRANSFER_ICON, null)
        val category = categoryId?.let { lookups.categoriesById[it] }
        if (category != null) return CategoryView(plain(category.name), category.icon, category.color)
        return CategoryView(res(R.string.tx_deleted_category), MISSING_CATEGORY_ICON, null)
    }

    fun categoryOf(lookups: Lookups, tx: Transaction): CategoryView = categoryOf(lookups, tx.type, tx.categoryId)

    /** "Cash", or "Bank → Cash" for a transfer. */
    fun accountLabel(lookups: Lookups, type: TxType, accountId: String, toAccountId: String?): UiText {
        val account = accountName(lookups, accountId)
        return if (type == TxType.Transfer) res(R.string.tx_transfer_arrow, account, accountName(lookups, toAccountId)) else account
    }

    fun toRow(tx: Transaction, lookups: Lookups): EntryRowUi {
        val category = categoryOf(lookups, tx)
        val accountLabel = accountLabel(lookups, tx.type, tx.accountId, tx.toAccountId)
        val note = tx.note?.trim().orEmpty()
        return EntryRowUi(
            id = tx.id,
            title = if (note.isNotEmpty()) plain(note) else category.name,
            // With a note on top, the category goes in the detail line; otherwise it's already the title.
            subtitle = if (note.isNotEmpty()) res(R.string.tx_subtitle_with_category, category.name, accountLabel) else accountLabel,
            iconName = category.icon,
            colorHex = category.color,
            kind = tx.type.amountKind(),
            amount = tx.type.signed(tx.amount),
            upcoming = TransactionRules.isUpcoming(tx, lookups.today),
        )
    }

    /** Entries already sorted newest first, as day groups with each day's net and label. */
    fun days(txs: List<Transaction>, lookups: Lookups): List<DayUi> =
        TransactionRules.groupByDay(txs) { it.date }.map { day ->
            DayUi(
                date = day.date,
                label = dayLabel(day.date, lookups.today),
                net = TransactionRules.totalsOf(day.items).net,
                rows = day.items.map { toRow(it, lookups) },
            )
        }

    /** The text the search box looks through: note, category and account names (req LST-03). */
    fun searchTextOf(tx: Transaction, lookups: Lookups): String {
        val category = tx.categoryId?.let { lookups.categoriesById[it]?.name }.orEmpty()
        val accounts = TransactionRules.accountIdsOf(tx).mapNotNull { lookups.accountsById[it]?.name }
        return (listOf(tx.note.orEmpty(), category) + accounts).joinToString("\n")
    }

    /** "Rs 250.00 · Food and dining", "Rs 250.00 · Bank → Cash", for the snackbar after a save. */
    fun summary(
        type: TxType,
        amount: Money,
        accountId: String,
        toAccountId: String?,
        categoryId: String?,
        accounts: List<Account>,
        categories: List<Category>,
    ): UiText {
        val money = MoneyFormat.full(amount)
        val what: String? = if (type == TxType.Transfer) {
            val from = accounts.firstOrNull { it.id == accountId }?.name.orEmpty()
            val to = accounts.firstOrNull { it.id == toAccountId }?.name.orEmpty()
            "$from → $to"
        } else {
            categories.firstOrNull { it.id == categoryId }?.name
        }
        return if (what.isNullOrBlank()) plain(money) else res(R.string.tx_subtitle_with_category, money, what)
    }

    /** Account picker items: active accounts, plus an archived one the entry already uses (req TXN-03). */
    fun accountPicks(accounts: List<Account>, used: List<String?>): List<AccountPick> =
        accounts.filter { !it.archived || it.id in used }
            .map { AccountPick(it.id, it.name, it.icon, it.color, it.archived) }

    /** Category tiles: the type's active categories in list order, plus the chosen one when it's archived. */
    fun categoryPicks(categories: List<Category>, type: TxType, chosen: String?): List<CategoryPick> {
        val categoryType = type.categoryType() ?: return emptyList()
        return categories.filter { it.type == categoryType && (!it.archived || it.id == chosen) }
            .sortedWith(categoryOrder)
            .map { CategoryPick(it.id, it.name, it.icon, it.color, it.archived, it.type) }
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
