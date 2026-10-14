package com.example.expensify.feature.transactions.data

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.money
import com.example.expensify.core.firebase.settled
import com.example.expensify.core.money.Money
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface TransactionRepository {
    /** Entries dated within the range, both ends included, newest first, live (req §9.7). */
    fun inRange(range: DateRange): Flow<List<Transaction>>

    /** Entries touching the account, on either side of a transfer, newest first, live (req ACC-09). */
    fun forAccount(accountId: String): Flow<List<Transaction>>
}

/**
 * Port of the reads in ../expensify/src/features/transactions/transactions.api.ts.
 * One filter per query, so Firestore's single-field indexes serve it (req §9.7).
 * Writes come with the Transactions feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreTransactionRepository @Inject constructor(
    private val paths: UserPaths,
    private val session: SessionRepository,
) : TransactionRepository {

    override fun inRange(range: DateRange): Flow<List<Transaction>> = live { collection ->
        collection
            .whereGreaterThanOrEqualTo("date", range.start.ymd())
            .whereLessThanOrEqualTo("date", range.end.ymd())
    }

    override fun forAccount(accountId: String): Flow<List<Transaction>> = live { collection ->
        collection.whereArrayContains("accountIds", accountId)
    }

    /** The query on the user's collection, sorted newest first on the device; empty while logged out. */
    private fun live(query: (Query) -> Query): Flow<List<Transaction>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                query(paths.collection(UserCollection.Transactions, uid))
                    .snapshots()
                    .settled()
                    .map { snapshot ->
                        snapshot.documents.map { it.toTransaction() }.sortedWith(TransactionRules.newestFirst)
                    }
            }
        }
}

private val UNDATED: LocalDate = LocalDate.of(1970, 1, 1)

internal fun DocumentSnapshot.toTransaction(): Transaction = Transaction(
    id = id,
    type = TxType.fromStored(getString("type")),
    amount = money("amount") ?: Money.ZERO,
    currency = getString("currency") ?: "NPR",
    accountId = getString("accountId").orEmpty(),
    toAccountId = getString("toAccountId"),
    categoryId = getString("categoryId"),
    date = getString("date")?.toLocalDateOrNull() ?: UNDATED,
    note = getString("note")?.takeIf { it.isNotBlank() },
    recurringRuleId = getString("recurringRuleId"),
    createdAt = instant("createdAt"),
    updatedAt = instant("updatedAt"),
)
