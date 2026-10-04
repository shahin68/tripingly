package com.falcon.tripingly.core.database.di

import com.falcon.tripingly.core.database.TripinglyDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)
    single { get<TripinglyDatabase>().tripDao() }
}

internal expect val databasePlatformModule: Module

internal const val DATABASE_NAME = "tripingly.db"
