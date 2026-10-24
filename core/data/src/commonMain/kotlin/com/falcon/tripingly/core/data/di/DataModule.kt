package com.falcon.tripingly.core.data.di

import com.falcon.tripingly.core.common.di.ApplicationScope
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.AccountRepositoryImpl
import com.falcon.tripingly.core.data.account.DeveloperSignIn
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.account.SessionRepositoryImpl
import com.falcon.tripingly.core.data.billing.DefaultProRepository
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.billing.StorePurchases
import com.falcon.tripingly.core.data.config.AppConfigRepository
import com.falcon.tripingly.core.data.config.DefaultAppConfigRepository
import com.falcon.tripingly.core.data.photos.DefaultPhotoRepository
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.data.photos.PlatformPhotoPreparer
import com.falcon.tripingly.core.data.places.DefaultPlaceRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.core.data.push.PushNotifications
import com.falcon.tripingly.core.data.push.PushRegistration
import com.falcon.tripingly.core.data.realtime.LiveUpdates
import com.falcon.tripingly.core.data.realtime.RealtimeSync
import com.falcon.tripingly.core.data.routing.DefaultRouteRepository
import com.falcon.tripingly.core.data.routing.RouteRepository
import com.falcon.tripingly.core.data.social.DefaultSocialRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.DataStoreTripMapViewRepository
import com.falcon.tripingly.core.data.trips.DefaultMarkerRepository
import com.falcon.tripingly.core.data.trips.DefaultTripInviteRepository
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.data.trips.OfflineFirstTripRepository
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.data.trips.RoomTripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripInviteRepository
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.users.DefaultUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.network.ApiConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<TripLocalDataSource> { RoomTripLocalDataSource(get()) }
    single { PendingTripWrites() }
    single<TripRepository> {
        OfflineFirstTripRepository(
            api = get(),
            local = get(),
            pending = get(),
            realtime = get(),
            mapViews = get(),
            currentUserId = { get<SessionRepository>().currentUserId() },
        )
    }
    single<TripMapViewRepository> { DataStoreTripMapViewRepository(get()) }
    single<MarkerRepository> {
        DefaultMarkerRepository(
            api = get(),
            trips = get(),
            local = get(),
            pending = get(),
            scope = get(ApplicationScope),
            currentUserId = { get<SessionRepository>().currentUserId() },
        )
    }
    single<TripInviteRepository> { DefaultTripInviteRepository(get(), get()) }
    single<UserRepository> { DefaultUserRepository(get(), get()) }
    single<SocialRepository> {
        DefaultSocialRepository(
            api = get(),
            local = get(),
            realtime = get(),
            currentUserId = { get<SessionRepository>().currentUserId() },
        )
    }
    single<AppConfigRepository> { DefaultAppConfigRepository(get(), get(ApplicationScope)) }
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
    single<PhotoRepository> { get<DefaultPhotoRepository>() }
    single<PlaceRepository> { DefaultPlaceRepository(get(), get(), get(ApplicationScope)) }
    single { DefaultRouteRepository(get(), get()) }
    single<RouteRepository> { get<DefaultRouteRepository>() }

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

    single<SessionRepository> { get<SessionRepositoryImpl>() }
    single<AccountRepository> { AccountRepositoryImpl(api = get(), session = get()) }
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
            // The app binds the platform's push where it is set up.
            messaging = getOrNull<PushMessaging>(),
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
        DefaultProRepository(
            // The app binds the store where a RevenueCat key is set.
            store = getOrNull<StorePurchases>(),
            session = get(),
            scope = get(ApplicationScope),
        )
    }
    single<ProRepository> { get<DefaultProRepository>() }
    // Galleries and uploads in progress belong to whoever was signed in.
    single(named("photos")) {
        val photos = get<DefaultPhotoRepository>()
        LocalDataCleaner { photos.clear() }
    }

    // Places along the way were for whoever was signed in.
    single(named("routes")) {
        val routes = get<DefaultRouteRepository>()
        LocalDataCleaner { routes.clear() }
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

    // How each trip's map was left belongs to whoever was signed in.
    single(named("tripMapViews")) {
        val views = get<TripMapViewRepository>()
        LocalDataCleaner { views.clear() }
    }

    // Purchases made next belong to the next account.
    single(named("purchases")) {
        val pro = get<DefaultProRepository>()
        LocalDataCleaner { pro.clear() }
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
