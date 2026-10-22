package com.falcon.tripingly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.push.PushNotifications
import com.falcon.tripingly.core.data.realtime.LiveUpdates
import com.falcon.tripingly.core.data.trips.MarkerRepository
import com.falcon.tripingly.core.designsystem.browser.rememberInAppUriHandler
import com.falcon.tripingly.core.designsystem.component.MessageBannerHost
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.navigation.Home
import com.falcon.tripingly.core.navigation.NavConfig
import com.falcon.tripingly.core.navigation.Paywall
import com.falcon.tripingly.core.navigation.People
import com.falcon.tripingly.core.navigation.Profile
import com.falcon.tripingly.core.navigation.Settings
import com.falcon.tripingly.core.navigation.TripMap
import com.falcon.tripingly.core.network.photos.StorageClient
import com.falcon.tripingly.deeplink.DeepLink
import com.falcon.tripingly.deeplink.DeepLinks
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGate
import com.falcon.tripingly.feature.map.presentation.screen.MapRoute
import com.falcon.tripingly.feature.paywall.presentation.PaywallRoute
import com.falcon.tripingly.feature.settings.presentation.SettingsRoute
import com.falcon.tripingly.feature.social.presentation.people.PeopleRoute
import com.falcon.tripingly.feature.social.presentation.profile.ProfileRoute
import com.falcon.tripingly.feature.social.presentation.social.SocialRoute
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersRoute
import com.falcon.tripingly.feature.trips.presentation.screen.HomeRoute
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
            // Every screen's errors and notes show in one floating banner (see MessageBanner).
            Box(Modifier.fillMaxSize()) {
                AuthGate { MainNavigation() }
                MessageBannerHost(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                )
            }
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
    // Purchases made from here on belong to this account.
    val pro = koinInject<ProRepository>()
    LaunchedEffect(pro) { pro.start() }
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
        // Each screen's ViewModels live while it is on the back stack; leaving it, or signing out
        // (this whole display goes away), clears them. Rotation keeps them.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // The paywall opens over the screen it came from, which stays as it was (the trip map isn't drawn again).
        sceneStrategies = listOf(DialogSceneStrategy()),
    ) { route ->
        val metadata = if (route is Paywall) DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false)) else emptyMap()
        NavEntry(route, metadata = metadata) { key ->
            when (key) {
                is Home -> {
                    HomeRoute(
                        onNavigateToMap = { tripId ->
                            backStack.add(TripMap(tripId))
                        },
                        onOpenMyProfile = { backStack.add(Profile(username = null)) },
                        socialContent = { query ->
                            SocialRoute(
                                query = query,
                                onOpenTrip = { backStack.add(TripMap(it)) },
                                onOpenProfile = { backStack.add(Profile(it)) },
                            )
                        },
                    )
                }
                is TripMap -> {
                    MapRoute(
                        tripId = key.tripId,
                        onNavigateBack = { goBack() },
                        onOpenProfile = { backStack.add(Profile(it)) },
                        onOpenPaywall = { backStack.add(Paywall) },
                        membersContent = { tripId, onDismiss -> TripMembersRoute(tripId = tripId, onDismiss = onDismiss) },
                    )
                }
                is Profile -> {
                    ProfileRoute(
                        username = key.username,
                        onBack = { goBack() },
                        onOpenTrip = { backStack.add(TripMap(it)) },
                        onOpenList = { backStack.add(People(it.name)) },
                        onOpenSettings = { backStack.add(Settings) },
                    )
                }
                is People -> {
                    PeopleRoute(
                        list = PeopleList.valueOf(key.list),
                        onBack = { goBack() },
                        onOpenProfile = { backStack.add(Profile(it)) },
                    )
                }
                is Settings -> SettingsRoute(onBack = { goBack() }, onOpenPro = { backStack.add(Paywall) })
                is Paywall -> Box(Modifier.fillMaxSize()) {
                    PaywallRoute(onBack = { goBack() })
                    // A dialog is a window above the app's banner.
                    MessageBannerHost(
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    )
                }
            }
        }
    }
}
