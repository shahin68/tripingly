package com.falcon.tripingly.core.network.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual val networkPlatformModule: Module = module {
    single<NetworkPlatform> { IosNetworkPlatform }
}

private object IosNetworkPlatform : NetworkPlatform {
    override val name = "ios"
    override fun languageTag(): String =
        NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    override fun engine(): HttpClientEngine = Darwin.create()
}
