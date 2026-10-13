package com.example.expensify.feature.auth.di

import com.example.expensify.feature.auth.data.AuthRepository
import com.example.expensify.feature.auth.data.FirebaseAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun authRepository(impl: FirebaseAuthRepository): AuthRepository
}
