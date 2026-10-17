package com.example.expensify.feature.categories.data

import com.example.expensify.core.error.AppError
import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryBody
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.domain.categoryOrder
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
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

interface CategoryRepository {
    /** Every category, archived ones too, in list order, live. */
    fun categories(): Flow<List<Category>>

    suspend fun get(id: String): Category

    /** Refuses a name its type already has, ignoring case, as `AppError.Taken(Name)`. Goes to the end of its type. */
    suspend fun create(body: CategoryBody): Category

    /** The type can't change (req CAT-03); the same name check as create. */
    suspend fun update(id: String, body: CategoryBody): Category

    /** Archived categories leave pickers; entries that use them keep them (req CAT-04). */
    suspend fun setArchived(id: String, archived: Boolean)

    /** Adds the defaults the user doesn't have yet (req CAT-06). Returns how many were added; nothing is written for 0. */
    suspend fun addDefaults(): Int

    /** Entries, rules and budgets keep its id and show it as deleted (req CAT-05). */
    suspend fun delete(id: String)
}

/**
 * Port of ../expensify/src/features/categories/categories.api.ts (req §9.5).
 * The rules check each document's fields; what needs the other categories,
 * like unique names, is checked here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreCategoryRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val paths: UserPaths,
    session: SessionRepository,
    @ApplicationScope scope: CoroutineScope,
) : CategoryRepository {

    // One listener for the whole app: the collection is small and every
    // transaction row needs it.
    private val shared: Flow<List<Category>> = session.uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                paths.collection(UserCollection.Categories, uid).snapshots()
                    .map { snapshot -> snapshot.documents.map { it.toCategory() }.sortedWith(categoryOrder) }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun categories(): Flow<List<Category>> = shared

    /** A one-shot read of the whole list, for the checks a write needs. */
    private suspend fun fetchAll(): List<Category> =
        paths.collection(UserCollection.Categories).get().await().documents.map { it.toCategory() }.sortedWith(categoryOrder)

    override suspend fun get(id: String): Category {
        val snapshot = paths.document(UserCollection.Categories, id).get().await()
        if (!snapshot.exists()) throw notFound()
        return snapshot.toCategory()
    }

    override suspend fun create(body: CategoryBody): Category {
        val categories = fetchAll()
        ensureNameFree(categories, body, null)
        val ref = paths.collection(UserCollection.Categories).document()
        ref.set(
            body.toFields() + mapOf(
                "archived" to false,
                "sortOrder" to CategoryRules.nextSortOrder(categories, body.type),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return get(ref.id)
    }

    override suspend fun update(id: String, body: CategoryBody): Category {
        val categories = fetchAll()
        val existing = categories.firstOrNull { it.id == id } ?: throw notFound()
        require(body.type == existing.type) { "A category's type can't be changed" }
        ensureNameFree(categories, body, id)
        paths.document(UserCollection.Categories, id)
            .update(body.toFields() + mapOf("updatedAt" to FieldValue.serverTimestamp()))
            .await()
        return get(id)
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        paths.document(UserCollection.Categories, id)
            .update(mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun addDefaults(): Int {
        val batch = db.batch()
        val added = addDefaultsTo(batch, paths.collection(UserCollection.Categories), fetchAll())
        if (added > 0) batch.commit().await()
        return added
    }

    override suspend fun delete(id: String) {
        paths.document(UserCollection.Categories, id).delete().await()
    }

    private fun notFound() = AppError.NotFound("Category not found")

    /** Names are unique per type, ignoring case; archived categories count too (req CAT-02). */
    private fun ensureNameFree(categories: List<Category>, body: CategoryBody, id: String?) {
        if (CategoryRules.isNameTaken(categories, body.name, body.type, id)) {
            throw AppError.Taken(AppError.Taken.Field.Name, takenMessage(body.type))
        }
    }
}

/** The sentence the web shows for a duplicate name (categories.api.ts). */
internal fun takenMessage(type: CategoryType) = "You already have an ${type.stored} category with this name"

/**
 * Adds the default categories missing from `existing` to `batch`, matched by
 * type and name, each at the end of its type's list. Signup seeds a new
 * account with them in its own batch. Returns how many were added.
 */
internal fun addDefaultsTo(batch: WriteBatch, collection: CollectionReference, existing: List<Category>): Int {
    val missing = CategoryRules.missingDefaults(existing)
    for (placed in missing) {
        batch.set(
            collection.document(),
            mapOf(
                "name" to placed.entry.name,
                "type" to placed.entry.type.stored,
                "icon" to placed.entry.icon,
                "color" to placed.entry.color,
                "archived" to false,
                "sortOrder" to placed.sortOrder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }
    return missing.size
}

internal fun DocumentSnapshot.toCategory(): Category = Category(
    id = id,
    name = getString("name").orEmpty(),
    type = CategoryType.fromStored(getString("type")),
    icon = getString("icon") ?: CategoryRules.DEFAULT_ICON,
    color = getString("color") ?: "#64748B",
    archived = getBoolean("archived") ?: false,
    sortOrder = getLong("sortOrder") ?: 0,
    createdAt = instant("createdAt"),
    updatedAt = instant("updatedAt"),
)

/** The fields a form may set (req §8); the archived flag, sort order and timestamps belong to the repository. */
internal fun CategoryBody.toFields(): Map<String, Any?> = mapOf(
    "name" to name.trim(),
    "type" to type.stored,
    "icon" to icon,
    "color" to color,
)
