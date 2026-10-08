package com.falcon.tripingly.core.network.di

import com.falcon.tripingly.core.network.baseLanguageTag
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
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
        (NSLocale.preferredLanguages.firstOrNull() as? String ?: "en").baseLanguageTag()
    override fun engine(): HttpClientEngine = Darwin.create()

    @OptIn(ExperimentalNativeApi::class)
    override val isDebugBuild = Platform.isDebugBinary
}
