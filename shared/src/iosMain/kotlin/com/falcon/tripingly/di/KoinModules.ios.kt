package com.falcon.tripingly.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.falcon.tripingly.core.data.local.TripinglyDatabase
import com.falcon.tripingly.core.util.IosShareManager
import com.falcon.tripingly.core.util.ShareManager
import com.falcon.tripingly.feature.map.data.datasource.IosLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformModule(): Module = module {
    singleOf(::IosLocationDataSource) bind LocationDataSource::class
    single<ShareManager> { IosShareManager() }
    single {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val dbFilePath = documentDirectory?.path + "/tripingly.db"
        Room.databaseBuilder<TripinglyDatabase>(
            name = dbFilePath,
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }
}
