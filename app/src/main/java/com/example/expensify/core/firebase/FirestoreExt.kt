package com.example.expensify.core.firebase

import com.example.expensify.core.money.Money
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.time.Instant
import kotlinx.coroutines.tasks.await

/** Firestore caps a commit at 500 writes; bulk writes stay under it. */
const val MAX_BATCH_WRITES = 450

/**
 * A stored timestamp as an Instant. A write the server hasn't stamped yet
 * reads as now, so a fresh entry sorts where it will end up.
 */
fun DocumentSnapshot.instant(field: String): Instant =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) }
        ?: Instant.now()

/** A stored amount. `getDouble` coerces the Long that Firestore hands back for whole values. */
fun DocumentSnapshot.money(field: String): Money? = getDouble(field)?.let(Money::fromStored)

/*
 * Listener flows are `Query.snapshots()` / `DocumentReference.snapshots()` exactly as the SDK
 * raises them: nothing filters a snapshot on `metadata.isFromCache`.
 *
 * The SDK already holds the first event back while it is online and the cache has nothing to
 * show (QueryListener.shouldRaiseInitialEvent), so a list never flashes "nothing here" ahead of
 * the server. The two cases where it does raise an empty from-cache snapshot, a query the cache
 * knows was empty at its last sync and a client that has gone offline, are the cases where that
 * snapshot is the only event coming: with the default MetadataChanges.EXCLUDE the server's later
 * "still empty" is a metadata-only change and is never delivered. A filter that dropped such
 * snapshots left Home, Transactions and Budgets on their skeletons for good whenever one of their
 * queries was empty (no budgets, no rules, nothing in last month…); removed 17 October 2026.
 */

/** Commits the writes in batches under Firestore's cap, one batch after another. */
suspend fun FirebaseFirestore.commitInBatches(writes: List<(WriteBatch) -> Unit>) {
    writes.chunked(MAX_BATCH_WRITES).forEach { chunk ->
        val batch = batch()
        chunk.forEach { it(batch) }
        batch.commit().await()
    }
}
