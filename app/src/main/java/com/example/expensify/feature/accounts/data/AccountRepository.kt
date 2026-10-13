package com.example.expensify.feature.accounts.data

import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.money
import com.example.expensify.core.firebase.settled
import com.example.expensify.core.firebase.settledDocument
import com.example.expensify.core.money.Money
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.accounts.domain.AccountBody
import com.example.expensify.feature.accounts.domain.AccountRules
import com.example.expensify.feature.accounts.domain.AccountType
import com.example.expensify.feature.auth.data.SessionRepository
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

interface AccountRepository {
    /** Every account, archived ones too, in list order, live. */
    fun accounts(): Flow<List<Account>>

    /** One account, live. Fails with `AppError.NotFound` once it is gone. */
    fun account(id: String): Flow<Account>

    suspend fun get(id: String): Account

    suspend fun create(body: AccountBody): Account

    /** A new opening balance shifts the current balance by the difference, in the same write. */
    suspend fun update(id: String, body: AccountBody): Account

    suspend fun setArchived(id: String, archived: Boolean)

    /** Sets the current balance to what the bank, card or wallet really shows. Nothing else is recorded. */
    suspend fun reconcile(id: String, actualBalance: Money)

    /** Its entries stay, and show it as a deleted account. */
    suspend fun delete(id: String)
}

/** Port of ../expensify/src/features/accounts/accounts.api.ts (req §9.4). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreAccountRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : AccountRepository {

    private val shared: Flow<List<Account>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                paths.collection(UserCollection.Accounts, uid).snapshots().settled()
                    .map { snapshot -> snapshot.documents.map { it.toAccount() }.sortedBy { it.sortOrder } }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val sessionUid = session.uid

    override fun accounts(): Flow<List<Account>> = shared

    override fun account(id: String): Flow<Account> = sessionUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf()
        } else {
            paths.document(UserCollection.Accounts, id, uid).snapshots().settledDocument().map { snapshot ->
                if (!snapshot.exists()) throw notFound()
                snapshot.toAccount()
            }
        }
    }

    override suspend fun get(id: String): Account {
        val snapshot = paths.document(UserCollection.Accounts, id).get().await()
        if (!snapshot.exists()) throw notFound()
        return snapshot.toAccount()
    }

    override suspend fun create(body: AccountBody): Account {
        val existing = paths.collection(UserCollection.Accounts).get().await().documents.map { it.toAccount() }
        val ref = paths.collection(UserCollection.Accounts).document()
        ref.set(
            body.toFields() + mapOf(
                "currentBalance" to body.openingBalance.toStored(),
                "archived" to false,
                "sortOrder" to AccountRules.nextSortOrder(existing),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return get(ref.id)
    }

    override suspend fun update(id: String, body: AccountBody): Account {
        val ref = paths.document(UserCollection.Accounts, id)
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (!snapshot.exists()) throw notFound()
            val delta = body.openingBalance - (snapshot.money("openingBalance") ?: Money.ZERO)
            val fields = body.toFields().toMutableMap()
            fields["updatedAt"] = FieldValue.serverTimestamp()
            if (!delta.isZero) fields["currentBalance"] = FieldValue.increment(delta.toStored())
            tx.update(ref, fields)
        }.await()
        return get(id)
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        paths.document(UserCollection.Accounts, id)
            .update(mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun reconcile(id: String, actualBalance: Money) {
        paths.document(UserCollection.Accounts, id)
            .update(mapOf("currentBalance" to actualBalance.toStored(), "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun delete(id: String) {
        paths.document(UserCollection.Accounts, id).delete().await()
    }

    private fun notFound() = AppError.NotFound("Account not found")
}

internal fun DocumentSnapshot.toAccount(): Account = Account(
    id = id,
    name = getString("name").orEmpty(),
    type = AccountType.fromStored(getString("type")),
    currency = getString("currency") ?: AccountRules.DEFAULT_CURRENCY,
    openingBalance = money("openingBalance") ?: Money.ZERO,
    currentBalance = money("currentBalance") ?: Money.ZERO,
    creditLimit = money("creditLimit"),
    icon = getString("icon") ?: AccountType.Other.defaultIcon,
    color = getString("color") ?: AccountType.Other.defaultColor,
    includeInTotal = getBoolean("includeInTotal") ?: true,
    archived = getBoolean("archived") ?: false,
    sortOrder = getLong("sortOrder") ?: 0,
    createdAt = instant("createdAt"),
    updatedAt = instant("updatedAt"),
)

/** The fields a form may set, every one written, null when it doesn't apply (req §8). */
internal fun AccountBody.toFields(): Map<String, Any?> = mapOf(
    "name" to name.trim(),
    "type" to type.stored,
    "currency" to currency,
    "openingBalance" to openingBalance.toStored(),
    // A zero limit means none; only credit cards keep one.
    "creditLimit" to creditLimit?.takeIf { type == AccountType.CreditCard && it.isPositive }?.toStored(),
    "icon" to icon,
    "color" to color,
    "includeInTotal" to includeInTotal,
)
