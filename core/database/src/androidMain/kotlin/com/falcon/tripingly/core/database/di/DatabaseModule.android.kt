package com.falcon.tripingly.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.falcon.tripingly.core.database.TripinglyDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val databasePlatformModule: Module = module {
    single {
        val dbFile = get<Context>().getDatabasePath(DATABASE_NAME)
        Room.databaseBuilder<TripinglyDatabase>(
            context = get(),
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }
}
