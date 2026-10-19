package com.falcon.tripingly.core.network.di

import com.falcon.tripingly.core.common.di.ApplicationScope
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.AppLanguage
import com.falcon.tripingly.core.network.account.AccountApi
import com.falcon.tripingly.core.network.account.createAccountApi
import com.falcon.tripingly.core.network.auth.AuthApi
import com.falcon.tripingly.core.network.auth.SecureTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.auth.createAuthApi
import com.falcon.tripingly.core.network.config.AppConfigApi
import com.falcon.tripingly.core.network.config.createAppConfigApi
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.photos.PhotosApi
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.core.network.photos.createPhotosApi
import com.falcon.tripingly.core.network.places.PlacesApi
import com.falcon.tripingly.core.network.places.createPlacesApi
import com.falcon.tripingly.core.network.push.PushApi
import com.falcon.tripingly.core.network.push.createPushApi
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.SocketIoRealtimeClient
import com.falcon.tripingly.core.network.routing.RoutingApi
import com.falcon.tripingly.core.network.routing.createRoutingApi
import com.falcon.tripingly.core.network.social.SocialApi
import com.falcon.tripingly.core.network.social.createSocialApi
import com.falcon.tripingly.core.network.trips.InvitesApi
import com.falcon.tripingly.core.network.trips.MarkersApi
import com.falcon.tripingly.core.network.trips.TripsApi
import com.falcon.tripingly.core.network.trips.createInvitesApi
import com.falcon.tripingly.core.network.trips.createMarkersApi
import com.falcon.tripingly.core.network.trips.createTripsApi
import com.falcon.tripingly.core.network.users.UsersApi
import com.falcon.tripingly.core.network.users.createUsersApi
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/** What the network layer needs from the platform. */
internal interface NetworkPlatform {
    /** `android` or `ios`, the first part of `X-Client`. */
    val name: String

    /** The app's current language as a BCP 47 tag, sent as `Accept-Language`. */
    fun languageTag(): String

    fun engine(): HttpClientEngine

    /** A debug build (Android debuggable, iOS debug binary); only these log HTTP calls. */
    val isDebugBuild: Boolean
}

val networkModule = module {
    includes(networkPlatformModule)
    single { ApiConfig.fromBuildConfig(get<NetworkPlatform>().name) }
    single<TokenStore> { SecureTokenStore(get()) }
    single { SessionEvents() }
    single<HttpClient> {
        val platform = get<NetworkPlatform>()
        createHttpClient(
            engine = platform.engine(),
            config = get(),
            tokenStore = get(),
            sessionEvents = get(),
            languageTag = platform::languageTag,
            logHttp = platform.isDebugBuild,
        )
    }
    single<Ktorfit> { createKtorfit(get(), get()) }
    single<AuthApi> { get<Ktorfit>().createAuthApi() }
    single<AccountApi> { get<Ktorfit>().createAccountApi() }
    single<TripsApi> { get<Ktorfit>().createTripsApi() }
    single<MarkersApi> { get<Ktorfit>().createMarkersApi() }
    single<InvitesApi> { get<Ktorfit>().createInvitesApi() }
    single<UsersApi> { get<Ktorfit>().createUsersApi() }
    single<PlacesApi> { get<Ktorfit>().createPlacesApi() }
    single<RoutingApi> { get<Ktorfit>().createRoutingApi() }
    single<SocialApi> { get<Ktorfit>().createSocialApi() }
    single<AppConfigApi> { get<Ktorfit>().createAppConfigApi() }
    single<PhotosApi> { get<Ktorfit>().createPhotosApi() }
    single { StorageClient(get<NetworkPlatform>().engine()) }
    single<PushApi> { get<Ktorfit>().createPushApi() }
    single { AppLanguage(get<NetworkPlatform>()::languageTag) }
    single<RealtimeClient> {
        val config = get<ApiConfig>()
        if (config.useFakeApi) {
            FakeRealtimeClient()
        } else {
            SocketIoRealtimeClient(
                engine = get<NetworkPlatform>().engine(),
                config = config,
                apiClient = get(),
                tokenStore = get(),
                sessionEvents = get(),
                scope = get(ApplicationScope),
            )
        }
    }
}

internal expect val networkPlatformModule: Module
