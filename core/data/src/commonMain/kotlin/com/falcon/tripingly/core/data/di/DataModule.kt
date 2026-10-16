package com.falcon.tripingly.core.data.di

import com.falcon.tripingly.core.common.di.ApplicationScope
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.AccountRepositoryImpl
import com.falcon.tripingly.core.data.account.DeveloperSignIn
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.account.SessionRepositoryImpl
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.config.DefaultAppConfigRepository
import com.falcon.tripingly.core.data.config.FakeAppConfigRepository
import com.falcon.tripingly.core.data.photos.DefaultPhotoRepository
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.photos.PlatformPhotoPreparer
import com.falcon.tripingly.core.data.places.DefaultPlaceRepository
import com.falcon.tripingly.core.data.places.FakePlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.core.data.push.PushNotifications
import com.falcon.tripingly.core.data.push.PushRegistration
import com.falcon.tripingly.core.data.realtime.LiveUpdates
import com.falcon.tripingly.core.data.realtime.RealtimeSync
import com.falcon.tripingly.core.data.social.DefaultSocialRepository
import com.falcon.tripingly.core.data.social.FakeSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
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
import com.falcon.tripingly.core.data.users.DefaultUserRepository
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.network.ApiConfig
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

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
                realtime = get(),
                currentUserId = { get<SessionRepository>().currentUserId() },
            )
        }
    }
    single<MarkerRepository> {
        if (get<ApiConfig>().useFakeApi) {
            get<FakeTripBackend>()
        } else {
            DefaultMarkerRepository(
                api = get(),
                trips = get(),
                local = get(),
                pending = get(),
                scope = get(ApplicationScope),
                currentUserId = { get<SessionRepository>().currentUserId() },
            )
        }
    }
    single<TripInviteRepository> {
        if (get<ApiConfig>().useFakeApi) get<FakeTripBackend>() else DefaultTripInviteRepository(get(), get())
    }
    single<UserRepository> {
        if (get<ApiConfig>().useFakeApi) FakeUserRepository() else DefaultUserRepository(get(), get())
    }
    single<SocialRepository> {
        if (get<ApiConfig>().useFakeApi) {
            FakeSocialRepository()
        } else {
            DefaultSocialRepository(
                api = get(),
                local = get(),
                realtime = get(),
                currentUserId = { get<SessionRepository>().currentUserId() },
            )
        }
    }
    single<AppConfigRepository> {
        if (get<ApiConfig>().useFakeApi) FakeAppConfigRepository() else DefaultAppConfigRepository(get(), get(ApplicationScope))
    }
    single {
        DefaultPhotoRepository(
            api = get(),
            markersApi = get(),
            storage = get(),
            local = get(),
            config = get(),
            social = get(),
            preparer = PlatformPhotoPreparer,
            scope = get(ApplicationScope),
        )
    }
    single<PhotoRepository> {
        if (get<ApiConfig>().useFakeApi) FakePhotoRepository() else get<DefaultPhotoRepository>()
    }
    single<PlaceRepository> {
        if (get<ApiConfig>().useFakeApi) FakePlaceRepository() else DefaultPlaceRepository(get(), get(), get(ApplicationScope))
    }

    single {
        RealtimeSync(
            realtime = get(),
            trips = get(),
            local = get(),
            pending = get(),
            photos = get(),
            currentUserId = { get<SessionRepository>().currentUserId() },
            scope = get(ApplicationScope),
        )
    }
    single<LiveUpdates> { get<RealtimeSync>() }

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
            unregisterPush = { get<PushRegistration>().unregister() },
            scope = get(ApplicationScope),
        )
    }
    single {
        val config = get<ApiConfig>()
        PushRegistration(
            // The app binds the platform's push where it is set up; the fake API has none.
            messaging = if (config.useFakeApi) null else getOrNull<PushMessaging>(),
            api = get(),
            store = get(),
            config = config,
            language = get(),
            currentUserId = { get<SessionRepository>().currentUserId() },
            scope = get(ApplicationScope),
        )
    }
    single<PushNotifications> { get<PushRegistration>() }
    single {
        FakeAccountBackend(
            today = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
            cleaners = { getAll<LocalDataCleaner>() },
        )
    }

    // Galleries and uploads in progress belong to whoever was signed in.
    single(named("photos")) {
        val photos = get<DefaultPhotoRepository>()
        LocalDataCleaner { photos.clear() }
    }

    // The next account registers this device for push again.
    single(named("push")) {
        val push = get<PushRegistration>()
        LocalDataCleaner { push.clear() }
    }

    // The next account's first connection loads nothing extra.
    single(named("realtime")) {
        val sync = get<RealtimeSync>()
        LocalDataCleaner { sync.clear() }
    }

    // The cached trips belong to whoever was signed in.
    single(named("tripsCache")) {
        val trips = get<TripLocalDataSource>()
        LocalDataCleaner { trips.clear() }
    }
}

private fun SessionRepository.currentUserId(): String? = when (val session = session.value) {
    is SessionState.SignedIn -> session.account.id
    is SessionState.Onboarding -> session.account.id
    else -> null
}
