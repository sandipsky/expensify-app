package com.example.expensify.feature.transactions.di

import com.example.expensify.feature.transactions.data.FirestoreTransactionRepository
import com.example.expensify.feature.transactions.data.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionsModule {
    @Binds
    abstract fun transactionRepository(impl: FirestoreTransactionRepository): TransactionRepository
}
