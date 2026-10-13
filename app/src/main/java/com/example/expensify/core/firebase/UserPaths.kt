package com.example.expensify.core.firebase

import com.example.expensify.core.error.AppError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/** The collections under users/{uid}, in the order deleting an account removes them (req §9.3). */
enum class UserCollection(val path: String) {
    Transactions("transactions"),
    RecurringRules("recurringRules"),
    Budgets("budgets"),
    Accounts("accounts"),
    Categories("categories"),
}

/**
 * Paths under the logged-in user's document. Nothing the app sends can name
 * another user: the uid always comes from the session.
 */
@Singleton
class UserPaths @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    /** The logged-in user's id. Throws while logged out. */
    val uid: String
        get() = auth.currentUser?.uid ?: throw AppError.SignedOut()

    fun user(uid: String = this.uid): DocumentReference = db.collection("users").document(uid)

    fun collection(name: UserCollection, uid: String = this.uid): CollectionReference =
        user(uid).collection(name.path)

    fun document(name: UserCollection, id: String, uid: String = this.uid): DocumentReference =
        collection(name, uid).document(id)

    /** The public username → email lookup, readable before anyone is logged in. */
    fun username(username: String): DocumentReference = db.collection("usernames").document(username)
}
