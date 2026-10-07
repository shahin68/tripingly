package com.falcon.tripingly.core.network.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.falcon.tripingly.core.network.baseLanguageTag
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Locale
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val networkPlatformModule: Module = module {
    single<NetworkPlatform> { AndroidNetworkPlatform(get<Context>()) }
}

private class AndroidNetworkPlatform(context: Context) : NetworkPlatform {
    override val name = "android"
    override fun languageTag(): String = Locale.getDefault().toLanguageTag().baseLanguageTag()
    override fun engine(): HttpClientEngine = OkHttp.create()
    override val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
