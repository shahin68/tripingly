package com.falcon.tripingly.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.falcon.tripingly.core.common.di.commonModule
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.di.dataModule
import com.falcon.tripingly.core.database.di.databaseModule
import com.falcon.tripingly.core.location.di.locationModule
import com.falcon.tripingly.core.network.di.networkModule
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.core.storage.di.storageModule
import com.falcon.tripingly.deeplink.DeepLinks
import com.falcon.tripingly.feature.auth.di.authModule
import com.falcon.tripingly.feature.map.di.mapModule
import com.falcon.tripingly.feature.paywall.di.paywallModule
import com.falcon.tripingly.feature.settings.di.settingsModule
import com.falcon.tripingly.feature.social.di.socialModule
import com.falcon.tripingly.feature.trips.di.tripsModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            commonModule,
            databaseModule,
            storageModule,
            networkModule,
            dataModule,
            locationModule,
            authModule,
            tripsModule,
            mapModule,
            socialModule,
            settingsModule,
            paywallModule,
            appModule,
        )
    }
}

private val appModule = module {
    single { DeepLinks() }
    // Photos come from storage through signed URLs, so they're fetched without the API's token and headers.
    // They're cached by photo ID, not URL (see PhotoImage), in Coil's default memory and disk caches.
    single {
        val storage = get<StorageClient>()
        ImageLoader.Builder(get<PlatformContext>())
            .components { add(KtorNetworkFetcherFactory(httpClient = { storage.httpClient })) }
            .crossfade(true)
            .build()
    }
    // The cached photos, private trips' included, belong to whoever was signed in.
    single(named("imageCache")) {
        val images = get<ImageLoader>()
        LocalDataCleaner {
            images.memoryCache?.clear()
            withContext(Dispatchers.IO) { images.diskCache?.clear() }
        }
    }
}
