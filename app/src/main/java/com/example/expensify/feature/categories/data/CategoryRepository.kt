package com.example.expensify.feature.categories.data

import com.example.expensify.core.firebase.ApplicationScope
import com.example.expensify.core.firebase.UserCollection
import com.example.expensify.core.firebase.UserPaths
import com.example.expensify.core.firebase.instant
import com.example.expensify.core.firebase.settled
import com.example.expensify.feature.auth.data.SessionRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.feature.categories.domain.categoryOrder
import com.google.firebase.firestore.DocumentSnapshot
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

interface CategoryRepository {
    /** Every category, archived ones too, in list order, live. */
    fun categories(): Flow<List<Category>>
}

/** Port of the reads in ../expensify/src/features/categories/categories.api.ts. Writes come with the Categories feature. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreCategoryRepository @Inject constructor(
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
                paths.collection(UserCollection.Categories, uid).snapshots().settled()
                    .map { snapshot -> snapshot.documents.map { it.toCategory() }.sortedWith(categoryOrder) }
            }
        }
        .retryWhen { _, attempt ->
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun categories(): Flow<List<Category>> = shared
}

internal fun DocumentSnapshot.toCategory(): Category = Category(
    id = id,
    name = getString("name").orEmpty(),
    type = CategoryType.fromStored(getString("type")),
    icon = getString("icon") ?: "category",
    color = getString("color") ?: "#64748B",
    archived = getBoolean("archived") ?: false,
    sortOrder = getLong("sortOrder") ?: 0,
    createdAt = instant("createdAt"),
    updatedAt = instant("updatedAt"),
)
