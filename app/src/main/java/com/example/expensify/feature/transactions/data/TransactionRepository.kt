package com.example.expensify.feature.transactions.data

import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.MAX_BATCH_WRITES
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.money
import com.example.expensify.core.money.Money
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TransactionBody
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface TransactionRepository {
    /** Entries dated within the range, both ends included, newest first, live (req §9.7). */
    fun inRange(range: DateRange): Flow<List<Transaction>>

    /**
     * Entries dated on or after `start`, future ones too, newest first, live.
     * The accounts report works balances back from today's, so it needs every
     * later entry (req RPT-07, BR-17). Still one filter on `date` (req §9.7).
     */
    fun fromDate(start: LocalDate): Flow<List<Transaction>>

    /**
     * The range's entries read from the server once, for a check while the
     * app is closed: an entry logged on the web counts. The same one filter
     * on `date` as `inRange` (req §9.7).
     */
    suspend fun fetchInRange(range: DateRange): List<Transaction>

    /** Entries touching the account, on either side of a transfer, newest first, live (req ACC-09). */
    fun forAccount(accountId: String): Flow<List<Transaction>>

    /** Entries a recurring rule added, newest first, live (req REC-12). */
    fun ofRule(ruleId: String): Flow<List<Transaction>>

    /** One entry, live. Fails with `AppError.NotFound` once it is gone. */
    fun transaction(id: String): Flow<Transaction>

    suspend fun get(id: String): Transaction

    /** Writes the entry and moves every affected account balance in the same write (req §9.6). */
    suspend fun create(body: TransactionBody): Transaction

    /** An edit reverses what the old entry did to balances and applies the new one (req TXN-06). */
    suspend fun update(id: String, body: TransactionBody): Transaction

    /** Deletes the entry and takes what it did off the balances (req TXN-07). */
    suspend fun delete(id: String)

    /** How many entries there are, for the "Delete all" confirmation. */
    suspend fun count(): Int

    /**
     * Deletes every transaction, undoing each one's effect on balances, so
     * every account ends at its opening balance. Returns how many went.
     */
    suspend fun deleteAll(): Int
}

/**
 * Port of ../expensify/src/features/transactions/transactions.api.ts (req
 * §9.6, §9.7). One filter per query, so Firestore's single-field indexes
 * serve it. Every write moves the balances of the accounts it touches with
 * `increment()`, in the same atomic write, so a balance is always its opening
 * balance plus its entries (req §4). An account deleted since an entry was
 * written has no balance to move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreTransactionRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    private val session: SessionRepository,
) : TransactionRepository {

    private val writes = EntryWrites(paths)

    override fun inRange(range: DateRange): Flow<List<Transaction>> = live { collection ->
        collection
            .whereGreaterThanOrEqualTo("date", range.start.ymd())
            .whereLessThanOrEqualTo("date", range.end.ymd())
    }

    override fun fromDate(start: LocalDate): Flow<List<Transaction>> = live { collection ->
        collection.whereGreaterThanOrEqualTo("date", start.ymd())
    }

    override suspend fun fetchInRange(range: DateRange): List<Transaction> =
        paths.collection(UserCollection.Transactions)
            .whereGreaterThanOrEqualTo("date", range.start.ymd())
            .whereLessThanOrEqualTo("date", range.end.ymd())
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.toTransaction() }
            .sortedWith(TransactionRules.newestFirst)

    override fun forAccount(accountId: String): Flow<List<Transaction>> = live { collection ->
        collection.whereArrayContains("accountIds", accountId)
    }

    override fun ofRule(ruleId: String): Flow<List<Transaction>> = live { collection ->
        collection.whereEqualTo("recurringRuleId", ruleId)
    }

    override fun transaction(id: String): Flow<Transaction> = session.uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf()
        } else {
            paths.document(UserCollection.Transactions, id, uid).snapshots().map { snapshot ->
                if (!snapshot.exists()) throw notFound()
                snapshot.toTransaction()
            }
        }
    }

    /** The query on the user's collection, sorted newest first on the device; empty while logged out. */
    private fun live(query: (Query) -> Query): Flow<List<Transaction>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                query(paths.collection(UserCollection.Transactions, uid))
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.map { it.toTransaction() }.sortedWith(TransactionRules.newestFirst)
                    }
            }
        }

    override suspend fun get(id: String): Transaction {
        val snapshot = paths.document(UserCollection.Transactions, id).get().await()
        if (!snapshot.exists()) throw notFound()
        return snapshot.toTransaction()
    }

    override suspend fun create(body: TransactionBody): Transaction {
        val ref = paths.collection(UserCollection.Transactions).document()
        db.runTransaction { tx ->
            val accounts = writes.readAccounts(tx, TransactionRules.accountIdsOf(body))
            val currency = writes.checkEntry(tx, body, accounts)
            tx.set(
                ref,
                writes.transactionFields(body, currency) + mapOf(
                    "recurringRuleId" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            writes.moveBalances({ r, data -> tx.update(r, data) }, TransactionRules.balanceEffects(body), writes.existing(accounts))
        }.await()
        return get(ref.id)
    }

    override suspend fun update(id: String, body: TransactionBody): Transaction {
        val ref = paths.document(UserCollection.Transactions, id)
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (!snapshot.exists()) throw notFound()
            val before = snapshot.toTransaction()
            val accounts = writes.readAccounts(tx, TransactionRules.accountIdsOf(before) + TransactionRules.accountIdsOf(body))
            val currency = writes.checkEntry(tx, body, accounts)
            tx.update(ref, writes.transactionFields(body, currency) + mapOf("updatedAt" to FieldValue.serverTimestamp()))
            val effects = TransactionRules.combineEffects(
                listOf(TransactionRules.balanceEffects(before, -1), TransactionRules.balanceEffects(body)),
            )
            writes.moveBalances({ r, data -> tx.update(r, data) }, effects, writes.existing(accounts))
        }.await()
        return get(id)
    }

    override suspend fun delete(id: String) {
        val ref = paths.document(UserCollection.Transactions, id)
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (snapshot.exists()) {
                val entry = snapshot.toTransaction()
                val accounts = writes.readAccounts(tx, TransactionRules.accountIdsOf(entry))
                tx.delete(ref)
                writes.moveBalances({ r, data -> tx.update(r, data) }, TransactionRules.balanceEffects(entry, -1), writes.existing(accounts))
            }
        }.await()
    }

    override suspend fun count(): Int = paths.collection(UserCollection.Transactions).get().await().size()

    override suspend fun deleteAll(): Int {
        val all = paths.collection(UserCollection.Transactions).get().await().documents.map { it.toTransaction() }
        val accountIds = paths.collection(UserCollection.Accounts).get().await().documents.map { it.id }.toSet()
        // Room in each batch for one balance update per account. A batch that
        // fails leaves the balances matching the entries that are left.
        val size = maxOf(50, MAX_BATCH_WRITES - accountIds.size)
        for (chunk in all.chunked(size)) {
            val batch = db.batch()
            for (tx in chunk) batch.delete(paths.document(UserCollection.Transactions, tx.id))
            val effects = TransactionRules.combineEffects(chunk.map { TransactionRules.balanceEffects(it, -1) })
            writes.moveBalances({ r, data -> batch.update(r, data) }, effects, accountIds)
            batch.commit().await()
        }
        return all.size
    }

    private fun notFound() = AppError.NotFound("Transaction not found")
}

/**
 * The pieces of an entry write that transactions and recurring occurrences
 * share: reading the accounts inside a Firestore transaction, the checks an
 * entry needs, its stored fields, and the balance moves.
 */
internal class EntryWrites(private val paths: UserPaths) {

    /** The accounts' snapshots read inside the transaction; a deleted one reads as not existing. */
    fun readAccounts(tx: com.google.firebase.firestore.Transaction, ids: Iterable<String>): Map<String, DocumentSnapshot> =
        ids.toSet().associateWith { id -> tx.get(paths.document(UserCollection.Accounts, id)) }

    /** The ids of the accounts that still exist, from `readAccounts`. */
    fun existing(accounts: Map<String, DocumentSnapshot>): Set<String> = accounts.filterValues { it.exists() }.keys

    /** Whether the entry's accounts all exist: the source, and the target of a transfer. */
    fun accountsExist(accountId: String, toAccountId: String?, accounts: Map<String, DocumentSnapshot>): Boolean {
        val source = accounts[accountId]?.exists() == true
        val target = toAccountId == null || accounts[toAccountId]?.exists() == true
        return source && target
    }

    /** The source account's currency, as read inside the transaction. */
    fun currencyOf(accountId: String, accounts: Map<String, DocumentSnapshot>): String =
        accounts[accountId]?.getString("currency") ?: "NPR"

    /**
     * Checks what an entry needs before it's written: its accounts, and a
     * category of its own type. Returns the source account's currency.
     */
    fun checkEntry(tx: com.google.firebase.firestore.Transaction, body: TransactionBody, accounts: Map<String, DocumentSnapshot>): String {
        val transfer = body.type == TxType.Transfer
        if (!accountsExist(body.accountId, if (transfer) body.toAccountId else null, accounts)) {
            throw AppError.NotFound("That account was deleted. Pick another one.")
        }
        if (!transfer) {
            val category = body.categoryId?.let { tx.get(paths.document(UserCollection.Categories, it)) }?.takeIf { it.exists() }
                ?: throw AppError.NotFound("That category was deleted. Pick another one.")
            if (category.getString("type") != body.type.stored) {
                throw AppError.NotFound("Pick an ${body.type.stored} category")
            }
        }
        return currencyOf(body.accountId, accounts)
    }

    /** The fields an entry is stored with (req §8). The currency is the source account's. */
    fun transactionFields(body: TransactionBody, currency: String): Map<String, Any?> {
        val transfer = body.type == TxType.Transfer
        val toAccountId = if (transfer) body.toAccountId else null
        return mapOf(
            "type" to body.type.stored,
            "amount" to body.amount.toStored(),
            "currency" to currency,
            "accountId" to body.accountId,
            "toAccountId" to toAccountId,
            "categoryId" to if (transfer) null else body.categoryId,
            "date" to body.date.ymd(),
            "note" to body.note?.trim()?.takeIf { it.isNotEmpty() },
            "accountIds" to TransactionRules.accountIdsOf(body.accountId, toAccountId),
        )
    }

    /** Adds the balance moves for the accounts that still exist to a transaction or batch. */
    fun moveBalances(
        update: (DocumentReference, Map<String, Any>) -> Unit,
        effects: Map<String, Money>,
        accounts: Set<String>,
    ) {
        for ((id, delta) in effects) {
            if (delta.isZero || id !in accounts) continue
            update(
                paths.document(UserCollection.Accounts, id),
                mapOf(
                    "currentBalance" to FieldValue.increment(delta.toStored()),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
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
