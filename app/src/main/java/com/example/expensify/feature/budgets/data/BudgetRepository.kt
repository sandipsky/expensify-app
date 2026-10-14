package com.example.expensify.feature.budgets.data

import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.money
import com.example.expensify.core.firebase.settled
import com.example.expensify.core.money.Money
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetAlert
import com.example.expensify.feature.budgets.domain.BudgetBody
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetRules
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
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

interface BudgetRepository {
    /** Every budget, paused ones too, in list order, live. */
    fun budgets(): Flow<List<Budget>>

    suspend fun get(id: String): Budget

    suspend fun create(body: BudgetBody): Budget

    /** Updates the form's fields; keeps `active` and `lastAlert`. */
    suspend fun update(id: String, body: BudgetBody): Budget

    /** Pausing keeps the settings; it only stops tracking and alerts (req BUD-06). */
    suspend fun setActive(id: String, active: Boolean)

    /**
     * Records that an alert was shown, so it isn't shown again that period,
     * whichever device shows it (req BR-11). A lower threshold than the
     * period already reached changes nothing.
     */
    suspend fun recordAlert(id: String, alert: BudgetAlert)

    /** Transactions aren't touched. */
    suspend fun delete(id: String)
}

/**
 * Port of ../expensify/src/features/budgets/budgets.api.ts (req §9.8).
 * Figures are worked out on the device from the transactions; a budget only
 * stores its settings and the last alert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreBudgetRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : BudgetRepository {

    private val shared: Flow<List<Budget>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                paths.collection(UserCollection.Budgets, uid).snapshots().settled()
                    .map { snapshot -> snapshot.documents.map { it.toBudget() }.sortedWith(BudgetRules.listOrder) }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun budgets(): Flow<List<Budget>> = shared

    override suspend fun get(id: String): Budget {
        val snapshot = paths.document(UserCollection.Budgets, id).get().await()
        if (!snapshot.exists()) throw notFound()
        return snapshot.toBudget()
    }

    override suspend fun create(body: BudgetBody): Budget {
        val ref = paths.collection(UserCollection.Budgets).document()
        ref.set(
            body.toFields() + mapOf(
                "lastAlert" to null,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return get(ref.id)
    }

    override suspend fun update(id: String, body: BudgetBody): Budget {
        paths.document(UserCollection.Budgets, id)
            .update(body.toFields() + mapOf("updatedAt" to FieldValue.serverTimestamp()))
            .await()
        return get(id)
    }

    override suspend fun setActive(id: String, active: Boolean) {
        paths.document(UserCollection.Budgets, id)
            .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun recordAlert(id: String, alert: BudgetAlert) {
        val ref = paths.document(UserCollection.Budgets, id)
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (!snapshot.exists()) throw notFound()
            val last = snapshot.toBudget().lastAlert
            val alreadyReached = last != null && last.periodStart == alert.periodStart && last.threshold >= alert.threshold
            if (!alreadyReached) {
                tx.update(
                    ref,
                    mapOf(
                        "lastAlert" to mapOf("periodStart" to alert.periodStart.ymd(), "threshold" to alert.threshold.toLong()),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        }.await()
    }

    override suspend fun delete(id: String) {
        paths.document(UserCollection.Budgets, id).delete().await()
    }

    private fun notFound() = AppError.NotFound("Budget not found")
}

internal fun DocumentSnapshot.toBudget(): Budget = Budget(
    id = id,
    name = getString("name").orEmpty(),
    amount = money("amount") ?: Money.ZERO,
    period = BudgetPeriod.fromStored(getString("period")),
    categoryIds = (get("categoryIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
    rollover = getBoolean("rollover") ?: false,
    // Whole numbers come back as Long; a Double from an older write still reads.
    alertThresholds = (get("alertThresholds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
    lastAlert = (get("lastAlert") as? Map<*, *>)?.let { map ->
        val start = (map["periodStart"] as? String)?.toLocalDateOrNull()
        val threshold = (map["threshold"] as? Number)?.toInt()
        if (start != null && threshold != null) BudgetAlert(start, threshold) else null
    },
    active = getBoolean("active") ?: true,
    createdAt = instant("createdAt"),
    updatedAt = instant("updatedAt"),
)

/**
 * The fields a form may set (req §8): the name trimmed, `categoryIds` without
 * duplicates, `alertThresholds` sorted without duplicates and written as whole
 * numbers, since the rules check them with `is int`.
 */
internal fun BudgetBody.toFields(): Map<String, Any?> = mapOf(
    "name" to name.trim(),
    "amount" to amount.toStored(),
    "period" to period.stored,
    "categoryIds" to categoryIds.distinct(),
    "rollover" to rollover,
    "alertThresholds" to alertThresholds.distinct().sorted().map { it.toLong() },
)
