package com.falcon.tripingly.core.network.di

import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.account.AccountApi
import com.falcon.tripingly.core.network.account.createAccountApi
import com.falcon.tripingly.core.network.auth.AuthApi
import com.falcon.tripingly.core.network.auth.createAuthApi
import com.falcon.tripingly.core.network.auth.SecureTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
import com.falcon.tripingly.core.network.places.PlacesApi
import com.falcon.tripingly.core.network.places.createPlacesApi
import com.falcon.tripingly.core.network.trips.InvitesApi
import com.falcon.tripingly.core.network.trips.MarkersApi
import com.falcon.tripingly.core.network.trips.TripsApi
import com.falcon.tripingly.core.network.trips.createInvitesApi
import com.falcon.tripingly.core.network.trips.createMarkersApi
import com.falcon.tripingly.core.network.trips.createTripsApi
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
    single<PlacesApi> { get<Ktorfit>().createPlacesApi() }
}

internal expect val networkPlatformModule: Module
