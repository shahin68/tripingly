package com.falcon.tripingly.core.data.di

import com.falcon.tripingly.core.data.repository.TripRepository
import com.falcon.tripingly.core.data.repository.TripRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    singleOf(::TripRepositoryImpl) bind TripRepository::class
}
