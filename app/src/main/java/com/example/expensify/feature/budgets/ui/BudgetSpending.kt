package com.example.expensify.feature.budgets.ui

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.transactions.data.TransactionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The transactions budget figures read, over one range, or why they couldn't be read. */
internal sealed interface Spending {
    data class Loaded(val txs: List<Transaction>) : Spending
    data class Failed(val message: UiText) : Spending
}

/**
 * The entries over the dates the budgets need, live. Nothing is read while
 * there is no range; a refused read reports instead of failing the screen.
 */
internal fun TransactionRepository.spendingOver(range: DateRange?): Flow<Spending> =
    if (range == null) {
        flowOf(Spending.Loaded(emptyList()))
    } else {
        inRange(range)
            .map<List<Transaction>, Spending> { Spending.Loaded(it) }
            .catch { emit(Spending.Failed(plain(ErrorMessages.of(it)))) }
    }
