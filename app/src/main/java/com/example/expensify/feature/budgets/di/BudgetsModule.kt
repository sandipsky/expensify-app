package com.example.expensify.feature.budgets.di

import com.example.expensify.feature.budgets.data.BudgetRepository
import com.example.expensify.feature.budgets.data.FirestoreBudgetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BudgetsModule {
    @Binds
    abstract fun budgetRepository(impl: FirestoreBudgetRepository): BudgetRepository
}
