package com.example.expensify.feature.recurring.data

import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.settled
import com.example.expensify.core.money.Money
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.recurring.domain.EndType
import com.example.expensify.feature.recurring.domain.Frequency
import com.example.expensify.feature.recurring.domain.RecurringRule
import com.example.expensify.feature.recurring.domain.RecurringRules
import com.example.expensify.feature.recurring.domain.RecurringTemplate
import com.example.expensify.feature.recurring.domain.RuleBody
import com.example.expensify.feature.recurring.domain.RuleMode
import com.example.expensify.feature.transactions.data.EntryWrites
import com.example.expensify.feature.transactions.domain.TransactionBody
import com.example.expensify.feature.transactions.domain.TransactionRules
import com.example.expensify.feature.transactions.domain.TxType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await

interface RecurringRepository {
    /** Every rule, paused and ended ones too, live. A handful at most, so one listener serves the app. */
    fun rules(): Flow<List<RecurringRule>>

    suspend fun get(id: String): RecurringRule

    /** Every rule as the server has it right now, past the on-device cache: what the background catch-up reads. */
    suspend fun fetchAll(): List<RecurringRule>

    suspend fun create(body: RuleBody): RecurringRule

    /** Reaches future entries only; the ones already added stay as they are (req REC-08). */
    suspend fun update(id: String, body: RuleBody): RecurringRule

    /** Resuming passes the date to pick up from, so dates passed while paused are left out (req REC-09). */
    suspend fun setActive(id: String, active: Boolean, nextDueDate: LocalDate? = null): RecurringRule

    /**
     * Adds the entries due on `dates` and moves the rule on, in one write. An
     * `entry` replaces the template for a single date, as edited before
     * confirming. Every entry moves its account balances, like any other.
     * Refuses as `AppError.Stale` when another device already handled the first date.
     */
    suspend fun addOccurrences(id: String, dates: List<LocalDate>, nextDueDate: LocalDate, entry: TransactionBody? = null)

    /** Passes over the entry due on `date`; refuses as `AppError.Stale` when it was already handled. */
    suspend fun skip(id: String, date: LocalDate, nextDueDate: LocalDate): RecurringRule

    /** The entries it already added stay. */
    suspend fun delete(id: String)
}

/**
 * Port of ../expensify/src/features/recurring/recurring.api.ts (req §9.9).
 * The device works out the dates; adding or skipping one happens in a
 * Firestore transaction that re-reads the rule, so a date is handled once
 * however many devices try at the same time (req BR-13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreRecurringRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : RecurringRepository {

    private val writes = EntryWrites(paths)

    private val shared: Flow<List<RecurringRule>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                paths.collection(UserCollection.RecurringRules, uid).snapshots().settled()
                    .map { snapshot -> snapshot.documents.map { it.toRule() } }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun rules(): Flow<List<RecurringRule>> = shared

    override suspend fun get(id: String): RecurringRule {
        val snapshot = paths.document(UserCollection.RecurringRules, id).get().await()
        if (!snapshot.exists()) throw notFound()
        return snapshot.toRule()
    }

    override suspend fun fetchAll(): List<RecurringRule> =
        paths.collection(UserCollection.RecurringRules).get(Source.SERVER).await().documents.map { it.toRule() }

    override suspend fun create(body: RuleBody): RecurringRule {
        val ref = paths.collection(UserCollection.RecurringRules).document()
        ref.set(
            body.toFields() + mapOf(
                "occurrences" to 0L,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return get(ref.id)
    }

    override suspend fun update(id: String, body: RuleBody): RecurringRule {
        paths.document(UserCollection.RecurringRules, id)
            .update(body.toFields() + mapOf("updatedAt" to FieldValue.serverTimestamp()))
            .await()
        return get(id)
    }

    override suspend fun setActive(id: String, active: Boolean, nextDueDate: LocalDate?): RecurringRule {
        val fields = mutableMapOf<String, Any>("active" to active, "updatedAt" to FieldValue.serverTimestamp())
        if (nextDueDate != null) fields["nextDueDate"] = nextDueDate.ymd()
        paths.document(UserCollection.RecurringRules, id).update(fields).await()
        return get(id)
    }

    /**
     * The rule, read inside the transaction. Occurrences are handled oldest
     * first, so a write must start at the rule's next due date; anything else
     * was confirmed, skipped or added already, on another device or by the runner.
     */
    private fun readDueRule(tx: com.google.firebase.firestore.Transaction, id: String, date: LocalDate): RecurringRule {
        val snapshot = tx.get(paths.document(UserCollection.RecurringRules, id))
        if (!snapshot.exists()) throw notFound()
        val rule = snapshot.toRule()
        if (!rule.active || rule.nextDueDate != date) throw AppError.Stale()
        return rule
    }

    override suspend fun addOccurrences(id: String, dates: List<LocalDate>, nextDueDate: LocalDate, entry: TransactionBody?) {
        require(dates.isNotEmpty()) { "At least one date" }
        val ruleRef = paths.document(UserCollection.RecurringRules, id)
        val refs = dates.map { paths.collection(UserCollection.Transactions).document() }
        db.runTransaction { tx ->
            val rule = readDueRule(tx, id, dates[0])
            // The template's category may have been deleted since; the entries
            // keep it, as other entries of a deleted category do.
            val entries: List<TransactionBody> = dates.map { date ->
                // An edited entry may move its own date; the schedule date still counts as handled.
                entry ?: RecurringRules.occurrenceOf(rule.template, date)
            }
            val source = entries[0]
            val accounts = writes.readAccounts(tx, TransactionRules.accountIdsOf(source))
            if (!writes.accountsExist(source.accountId, source.toAccountId, accounts)) {
                throw AppError.NotFound("An account this rule uses was deleted. Edit the rule to pick another one.")
            }
            val currency = writes.currencyOf(source.accountId, accounts)
            entries.forEachIndexed { i, body ->
                tx.set(
                    refs[i],
                    writes.transactionFields(body, currency) + mapOf(
                        "recurringRuleId" to id,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            val effects = TransactionRules.combineEffects(entries.map { TransactionRules.balanceEffects(it) })
            writes.moveBalances({ r, data -> tx.update(r, data) }, effects, writes.existing(accounts))
            tx.update(
                ruleRef,
                mapOf(
                    "occurrences" to FieldValue.increment(dates.size.toLong()),
                    "nextDueDate" to nextDueDate.ymd(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    override suspend fun skip(id: String, date: LocalDate, nextDueDate: LocalDate): RecurringRule {
        val ruleRef = paths.document(UserCollection.RecurringRules, id)
        db.runTransaction { tx ->
            readDueRule(tx, id, date)
            tx.update(ruleRef, mapOf("nextDueDate" to nextDueDate.ymd(), "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
        return get(id)
    }

    override suspend fun delete(id: String) {
        paths.document(UserCollection.RecurringRules, id).delete().await()
    }

    private fun notFound() = AppError.NotFound("Recurring rule not found")
}

private val UNDATED: LocalDate = LocalDate.of(1970, 1, 1)

internal fun DocumentSnapshot.toRule(): RecurringRule {
    val template = get("template") as? Map<*, *>
    return RecurringRule(
        id = id,
        template = RecurringTemplate(
            type = TxType.fromStored(template?.get("type") as? String),
            amount = (template?.get("amount") as? Number)?.toDouble()?.let(Money::fromStored) ?: Money.ZERO,
            accountId = (template?.get("accountId") as? String).orEmpty(),
            toAccountId = template?.get("toAccountId") as? String,
            categoryId = template?.get("categoryId") as? String,
            note = (template?.get("note") as? String)?.takeIf { it.isNotBlank() },
        ),
        frequency = Frequency.fromStored(getString("frequency")),
        interval = getLong("interval")?.toInt() ?: 1,
        weekdays = (get("weekdays") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
        dayOfMonth = getLong("dayOfMonth")?.toInt(),
        startDate = getString("startDate")?.toLocalDateOrNull() ?: UNDATED,
        endType = EndType.fromStored(getString("endType")),
        endDate = getString("endDate")?.toLocalDateOrNull(),
        maxCount = getLong("maxCount")?.toInt(),
        mode = RuleMode.fromStored(getString("mode")),
        nextDueDate = getString("nextDueDate")?.toLocalDateOrNull() ?: UNDATED,
        occurrences = getLong("occurrences")?.toInt() ?: 0,
        active = getBoolean("active") ?: true,
        createdAt = instant("createdAt"),
        updatedAt = instant("updatedAt"),
    )
}

/**
 * The fields a form may set (req §8, §9.9), with the ones other frequencies
 * and ends don't use cleared, and whole numbers written as such since the
 * rules check them with `is int`.
 */
internal fun RuleBody.toFields(): Map<String, Any?> {
    val transfer = template.type == TxType.Transfer
    return mapOf(
        "template" to mapOf(
            "type" to template.type.stored,
            "amount" to template.amount.toStored(),
            "accountId" to template.accountId,
            "toAccountId" to if (transfer) template.toAccountId else null,
            "categoryId" to if (transfer) null else template.categoryId,
            "note" to template.note?.trim()?.takeIf { it.isNotEmpty() },
        ),
        "frequency" to frequency.stored,
        "interval" to interval.toLong(),
        "weekdays" to if (frequency == Frequency.Weekly) weekdays.distinct().sorted().map { it.toLong() } else emptyList<Long>(),
        "dayOfMonth" to if (frequency == Frequency.Monthly) dayOfMonth?.toLong() else null,
        "startDate" to startDate.ymd(),
        "endType" to endType.stored,
        "endDate" to if (endType == EndType.Until) endDate?.ymd() else null,
        "maxCount" to if (endType == EndType.Count) maxCount?.toLong() else null,
        "mode" to mode.stored,
        "nextDueDate" to nextDueDate.ymd(),
    )
}
