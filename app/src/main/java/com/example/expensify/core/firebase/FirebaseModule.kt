package com.example.expensify.core.firebase

import com.example.expensify.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the app: shared repository flows and background observers. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/** The host machine as an Android emulator sees it. */
private const val EMULATOR_HOST = "10.0.2.2"
private const val AUTH_EMULATOR_PORT = 9099
private const val FIRESTORE_EMULATOR_PORT = 8080

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // The Java entry points on purpose: firebase-auth's Kotlin extensions are
    // compiled with a newer Kotlin than this project's compiler reads.
    // useEmulator must run before anything else touches the instance, and
    // both instances are only ever obtained through these providers.

    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (BuildConfig.USE_EMULATORS) useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
    }

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (BuildConfig.USE_EMULATORS) useEmulator(EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
