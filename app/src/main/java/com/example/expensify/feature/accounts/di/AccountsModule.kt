package com.example.expensify.feature.accounts.di

import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.data.FirestoreAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountsModule {
    @Binds
    abstract fun accountRepository(impl: FirestoreAccountRepository): AccountRepository
}
