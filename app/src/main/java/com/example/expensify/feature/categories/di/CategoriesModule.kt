package com.example.expensify.feature.categories.di

import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.data.FirestoreCategoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CategoriesModule {
    @Binds
    abstract fun categoryRepository(impl: FirestoreCategoryRepository): CategoryRepository
}
