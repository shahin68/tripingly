package com.falcon.tripingly.core.data.di

import com.falcon.tripingly.core.common.di.ApplicationScope
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.AccountRepositoryImpl
import com.falcon.tripingly.core.data.account.DeveloperSignIn
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.account.SessionRepositoryImpl
import com.falcon.tripingly.core.data.places.DefaultPlaceRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.DefaultMarkerRepository
import com.falcon.tripingly.core.data.trips.DefaultTripInviteRepository
import com.falcon.tripingly.core.data.trips.FakeTripBackend
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.OfflineFirstTripRepository
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.data.trips.RoomTripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripInviteRepository
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.network.ApiConfig
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<TripLocalDataSource> { RoomTripLocalDataSource(get()) }
    single { FakeTripBackend() }
    single { PendingTripWrites() }
    single<TripRepository> {
        if (get<ApiConfig>().useFakeApi) {
            get<FakeTripBackend>()
        } else {
            OfflineFirstTripRepository(
                api = get(),
                local = get(),
                pending = get(),
                currentUserId = {
                    when (val session = get<SessionRepository>().session.value) {
                        is SessionState.SignedIn -> session.account.id
                        is SessionState.Onboarding -> session.account.id
                        else -> null
                    }
                },
            )
        }
    }
    single<MarkerRepository> {
        if (get<ApiConfig>().useFakeApi) {
            get<FakeTripBackend>()
        } else {
            DefaultMarkerRepository(api = get(), trips = get(), local = get(), pending = get(), scope = get(ApplicationScope))
        }
    }
    single<TripInviteRepository> {
        if (get<ApiConfig>().useFakeApi) get<FakeTripBackend>() else DefaultTripInviteRepository(get(), get())
    }
    single<PlaceRepository> {
        if (get<ApiConfig>().useFakeApi) FakePlaceRepository() else DefaultPlaceRepository(get())
    }

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

    // The cached trips belong to whoever was signed in.
    single(named("tripsCache")) {
        val trips = get<TripLocalDataSource>()
        LocalDataCleaner { trips.clear() }
    }
}
