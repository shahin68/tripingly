package com.falcon.tripingly.core.data.di

import com.falcon.tripingly.core.common.di.ApplicationScope
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.AccountRepositoryImpl
import com.falcon.tripingly.core.data.account.DeveloperSignIn
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.account.SessionRepositoryImpl
import com.falcon.tripingly.core.data.repository.TripRepository
import com.falcon.tripingly.core.data.repository.TripRepositoryImpl
import com.falcon.tripingly.core.database.dao.MarkerDao
import com.falcon.tripingly.core.database.dao.TripDao
import com.falcon.tripingly.core.network.ApiConfig
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    singleOf(::TripRepositoryImpl) bind TripRepository::class

    single<SessionRepository> {
        if (get<ApiConfig>().useFakeApi) {
            get<FakeAccountBackend>()
        } else {
            get<SessionRepositoryImpl>()
        }
    }
    single<AccountRepository> {
        if (get<ApiConfig>().useFakeApi) {
            get<FakeAccountBackend>()
        } else {
            AccountRepositoryImpl(api = get(), session = get())
        }
    }
    single {
        val config = get<ApiConfig>()
        SessionRepositoryImpl(
            authApi = get(),
            accountApi = get(),
            tokenStore = get(),
            sessionEvents = get(),
            developerSignIn = if (config.developerSignIn) DeveloperSignIn(config.devAuthSecret) else null,
            cleaners = { getAll<LocalDataCleaner>() },
            scope = get(ApplicationScope),
        )
    }
    single {
        FakeAccountBackend(
            today = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
            cleaners = { getAll<LocalDataCleaner>() },
        )
    }

    // The local trips and markers belong to whoever was signed in.
    single(named("tripsCache")) {
        val trips = get<TripDao>()
        val markers = get<MarkerDao>()
        LocalDataCleaner {
            markers.deleteAll()
            trips.deleteAll()
        }
    }
}
