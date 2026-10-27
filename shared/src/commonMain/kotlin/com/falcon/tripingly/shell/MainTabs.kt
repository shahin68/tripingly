package com.falcon.tripingly.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.designsystem.component.AnimatedTopBar
import com.falcon.tripingly.core.designsystem.component.FloatingNavigationBar
import com.falcon.tripingly.core.designsystem.component.NavBarItem
import com.falcon.tripingly.core.designsystem.component.SettingsAction
import com.falcon.tripingly.core.designsystem.component.safeSides
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.feature.map.presentation.photos.PhotoViewerRoute
import com.falcon.tripingly.feature.social.presentation.explore.ExplorePeriodAction
import com.falcon.tripingly.feature.social.presentation.explore.ExploreRoute
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyRoute
import com.falcon.tripingly.feature.social.presentation.profile.PeopleSearchField
import com.falcon.tripingly.feature.social.presentation.profile.ProfileRoute
import com.falcon.tripingly.feature.trips.presentation.screen.AddTripButton
import com.falcon.tripingly.feature.trips.presentation.screen.HomeRoute
import com.falcon.tripingly.feature.trips.presentation.screen.TripsSearchField
import com.falcon.tripingly.shared.generated.resources.Res
import com.falcon.tripingly.shared.generated.resources.tab_explore
import com.falcon.tripingly.shared.generated.resources.tab_nearby
import com.falcon.tripingly.shared.generated.resources.tab_profile
import com.falcon.tripingly.shared.generated.resources.tab_trips
import com.falcon.tripingly.shared.generated.resources.tab_trips_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The four tabs, in the bottom bar's order, with the title each shows in the top bar. */
internal enum class MainTab(val label: StringResource, val icon: ImageVector, val title: StringResource = label) {
    Trips(Res.string.tab_trips, Icons.Outlined.Luggage, Res.string.tab_trips_title),
    Explore(Res.string.tab_explore, Icons.Outlined.Explore),
    Nearby(Res.string.tab_nearby, Icons.Outlined.NearMe),
    Profile(Res.string.tab_profile, Icons.Outlined.Person),
}

/**
 * The tab shell: one floating top bar and one floating bottom bar over the tabs, which the user swipes between
 * like pages or picks in the bottom bar (tap, or drag its selection; a tap on a far tab jumps there without passing the tabs in between). The top bar animates to the page shown: its
 * title, the page's own action (Explore's period) and its search field (Trips, Profile). The Add Trip button
 * shrinks away off the Trips tab. Every tab keeps its scroll position and view models while another is shown
 * (they live as long as this screen); content scrolls behind both bars.
 */
@Composable
internal fun MainTabs(
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val tabStates = rememberSaveableStateHolder()
    val items = tabs.map { NavBarItem(stringResource(it.label), it.icon) }
    val shown = tabs[pagerState.currentPage]
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedTopBar(
                page = shown,
                title = { stringResource(it.title) },
                pageActions = { if (it == MainTab.Explore) ExplorePeriodAction() },
                actions = { SettingsAction(onOpenSettings) },
                search = { tab ->
                    when (tab) {
                        MainTab.Trips -> { { TripsSearchField() } }
                        MainTab.Profile -> { { PeopleSearchField() } }
                        else -> null
                    }
                },
            )
        },
        bottomBar = {
            FloatingNavigationBar(items = items, pagerState = pagerState)
        },
        floatingActionButton = { AddTripButton(visible = shown == MainTab.Trips, Modifier.windowInsetsPadding(WindowInsets.safeSides)) },
    ) { barsPadding ->
        HorizontalPager(state = pagerState, key = { tabs[it].name }) { page ->
            val tab = tabs[page]
            tabStates.SaveableStateProvider(tab.name) {
                when (tab) {
                    MainTab.Trips -> HomeRoute(onNavigateToMap = onOpenTrip, barsPadding = barsPadding)
                    MainTab.Explore -> ExploreRoute(onOpenTrip = onOpenTrip, onOpenProfile = onOpenProfile, barsPadding = barsPadding)
                    MainTab.Nearby -> NearbyRoute(
                        onOpenTrip = onOpenTrip,
                        placeImageContent = { placeId, imageId, onReport, onDismiss ->
                            PhotoViewerRoute(owner = PhotoOwner.Place(placeId), photoId = imageId, onReport = onReport, onDismiss = onDismiss)
                        },
                        barsPadding = barsPadding,
                    )
                    MainTab.Profile -> ProfileRoute(
                        username = null,
                        onBack = {},
                        onOpenTrip = onOpenTrip,
                        onOpenProfile = onOpenProfile,
                        barsPadding = barsPadding,
                    )
                }
            }
        }
    }
}
