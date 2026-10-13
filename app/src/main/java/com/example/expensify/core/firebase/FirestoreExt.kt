package com.example.expensify.core.firebase

import com.example.expensify.core.money.Money
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
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

/**
 * Drops the cache-only empty snapshot that can precede the first server
 * answer, so a list never flashes "nothing here" before it has loaded.
 * A cache snapshot with pending writes is the user's own change and passes.
 */
fun Flow<QuerySnapshot>.settled(): Flow<QuerySnapshot> =
    filterNot { it.metadata.isFromCache && it.isEmpty && !it.metadata.hasPendingWrites() }

/** The document form: a cache miss is not "deleted"; only the server, or a local write, says so. */
fun Flow<DocumentSnapshot>.settledDocument(): Flow<DocumentSnapshot> =
    filterNot { it.metadata.isFromCache && !it.exists() && !it.metadata.hasPendingWrites() }

/** Commits the writes in batches under Firestore's cap, one batch after another. */
suspend fun FirebaseFirestore.commitInBatches(writes: List<(WriteBatch) -> Unit>) {
    writes.chunked(MAX_BATCH_WRITES).forEach { chunk ->
        val batch = batch()
        chunk.forEach { it(batch) }
        batch.commit().await()
    }
}
