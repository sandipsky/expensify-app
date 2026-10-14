package com.example.expensify.feature.recurring.di

import com.example.expensify.feature.recurring.data.FirestoreRecurringRepository
import com.example.expensify.feature.recurring.data.RecurringRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecurringModule {
    @Binds
    abstract fun recurringRepository(impl: FirestoreRecurringRepository): RecurringRepository
}
