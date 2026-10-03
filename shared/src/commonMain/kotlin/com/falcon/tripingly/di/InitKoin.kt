package com.falcon.tripingly.di

import com.falcon.tripingly.core.common.di.commonModule
import com.falcon.tripingly.core.data.di.dataModule
import com.falcon.tripingly.core.database.di.databaseModule
import com.falcon.tripingly.feature.map.di.mapModule
import com.falcon.tripingly.feature.trips.di.tripsModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            commonModule,
            databaseModule,
            dataModule,
            tripsModule,
            mapModule,
        )
    }
}
