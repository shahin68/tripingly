package com.falcon.tripingly.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.falcon.tripingly.core.data.local.TripinglyDatabase
import com.falcon.tripingly.feature.map.data.datasource.AndroidLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { AndroidLocationDataSource(get()) } bind LocationDataSource::class
    single {
        val dbFile = get<Context>().getDatabasePath("tripingly.db")
        Room.databaseBuilder<TripinglyDatabase>(
            context = get(),
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }
}
