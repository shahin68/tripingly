package com.falcon.tripingly.core.network.di

import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.auth.InMemoryTokenStore
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.createHttpClient
import com.falcon.tripingly.core.network.createKtorfit
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
}

val networkModule = module {
    includes(networkPlatformModule)
    single { ApiConfig.fromBuildConfig(get<NetworkPlatform>().name) }
    single<TokenStore> { InMemoryTokenStore() }
    single { SessionEvents() }
    single<HttpClient> {
        val platform = get<NetworkPlatform>()
        createHttpClient(
            engine = platform.engine(),
            config = get(),
            tokenStore = get(),
            sessionEvents = get(),
            languageTag = platform::languageTag,
        )
    }
    single<Ktorfit> { createKtorfit(get(), get()) }
}

internal expect val networkPlatformModule: Module
