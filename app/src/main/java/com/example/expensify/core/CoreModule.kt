package com.example.expensify.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    /** The device clock and zone: what "today" means (req BR-05). Tests pin a fixed one. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
