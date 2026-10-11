package com.falcon.tripingly

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.falcon.tripingly.core.network.photos.StorageClient
import org.koin.compose.koinInject
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.push.PushNotifications
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.deeplink.DeepLink
import com.falcon.tripingly.deeplink.DeepLinks
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.NavEntry
import com.falcon.tripingly.core.data.realtime.LiveUpdates
import com.falcon.tripingly.core.navigation.NavConfig
import com.falcon.tripingly.core.navigation.Home
import com.falcon.tripingly.core.navigation.TripMap
import com.falcon.tripingly.core.designsystem.browser.rememberInAppUriHandler
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGate
import com.falcon.tripingly.feature.trips.presentation.screen.HomeRoute
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute

@Composable
fun App() {
    val storage = koinInject<StorageClient>()
    // Photos come from storage through signed URLs, so they're fetched without the API's token and headers.
    // They're cached by photo ID, not URL (see PhotoImage), in Coil's default memory and disk caches.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { storage.httpClient })) }
            .crossfade(true)
            .build()
    }
    TripinglyTheme {
        // Links (legal documents and others) open in an in-app browser, not the external one.
        CompositionLocalProvider(LocalUriHandler provides rememberInAppUriHandler()) {
            AuthGate { MainNavigation() }
        }
    }
}

@Composable
private fun MainNavigation() {
    // Signed in from here on: live updates while the app is in the foreground.
    val liveUpdates = koinInject<LiveUpdates>()
    LifecycleStartEffect(liveUpdates) {
        liveUpdates.start()
        onStopOrDispose { liveUpdates.stop() }
    }
    val push = koinInject<PushNotifications>()
    LaunchedEffect(push) { push.start() }
    val backStack = rememberNavBackStack(NavConfig, Home)
    val goBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }

    // A tapped push opens what it's about, once signed in.
    val deepLinks = koinInject<DeepLinks>()
    val markers = koinInject<MarkerRepository>()
    LaunchedEffect(deepLinks) {
        deepLinks.next.filterNotNull().collect {
            val link = deepLinks.take() ?: return@collect
            link.notificationId?.let { launch { push.opened(it) } }
            val tripId = when (link) {
                is DeepLink.Trip -> link.tripId
                is DeepLink.Marker -> (markers.tripIdOfMarker(link.markerId) as? AppResult.Success)?.data
                is DeepLink.Home -> null
            }
            if (tripId != null && backStack.lastOrNull() != TripMap(tripId)) backStack.add(TripMap(tripId))
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { goBack() },
    ) { route ->
        NavEntry(route) { key ->
            when (key) {
                is Home -> {
                    HomeRoute(
                        onNavigateToMap = { tripId ->
                            backStack.add(TripMap(tripId))
                        }
                    )
                }
                is TripMap -> {
                    MapRoute(
                        tripId = key.tripId,
                        onNavigateBack = { goBack() },
                    )
                }
            }
        }
    }
}
