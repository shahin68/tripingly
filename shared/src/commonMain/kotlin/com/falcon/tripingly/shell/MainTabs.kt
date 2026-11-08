package com.falcon.tripingly.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.data.push.NotificationCenterRepository
import com.falcon.tripingly.core.designsystem.component.AnimatedTopBar
import com.falcon.tripingly.core.designsystem.component.FloatingNavigationBar
import com.falcon.tripingly.core.designsystem.component.NavBarItem
import com.falcon.tripingly.core.designsystem.component.SearchSettingsPill
import com.falcon.tripingly.core.designsystem.component.safeSides
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.deeplink.DeepLinks
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
import org.koin.compose.koinInject

/** The four tabs, in the bottom bar's order, with the title each shows in the top bar. */
internal enum class MainTab(val label: StringResource, val icon: ImageVector, val title: StringResource = label, val hasSearch: Boolean = false) {
    Trips(Res.string.tab_trips, Icons.Outlined.Luggage, Res.string.tab_trips_title, hasSearch = true),
    Explore(Res.string.tab_explore, Icons.Outlined.Explore),
    Nearby(Res.string.tab_nearby, Icons.Outlined.NearMe),
    Profile(Res.string.tab_profile, Icons.Outlined.Person, hasSearch = true),
}

/**
 * The tab shell: one floating top bar and one floating bottom bar over the tabs, which the user swipes between
 * like pages or picks in the bottom bar (tap, or drag its selection; a tap on a far tab jumps there without passing the tabs in between). The top bar animates to the page shown: its
 * title, the page's own action (Explore's period) and the search button in the Settings pill (Trips, Profile).
 * The button opens the tab's search field with the keyboard; the field's ✕ closes both. A tab keeps its search open
 * while another is shown, like its scroll position. The Add Trip button
 * shrinks away off the Trips tab. Every tab keeps its scroll position and view models while another is shown
 * (they live as long as this screen); content scrolls behind both bars. Profile has a red dot while something new
 * waits in the notification center.
 */
@Composable
internal fun MainTabs(
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val tabStates = rememberSaveableStateHolder()
    val notificationCenter = koinInject<NotificationCenterRepository>()
    val hasUnseenNotifications by notificationCenter.hasUnseen.collectAsStateWithLifecycle()
    val deepLinks = koinInject<DeepLinks>()
    // A link about several new followers: the Profile tab shows them.
    val showFollowers by deepLinks.followersRequested.collectAsStateWithLifecycle()
    LaunchedEffect(showFollowers) { if (showFollowers) pagerState.scrollToPage(MainTab.Profile.ordinal) }
    val items = tabs.map { NavBarItem(stringResource(it.label), it.icon, hasBadge = it == MainTab.Profile && hasUnseenNotifications) }
    val shown = tabs[pagerState.currentPage]
    var searching by rememberSaveable { mutableStateOf(emptySet<MainTab>()) }
    // Only a tap on the search button brings the keyboard, not a swipe back to a tab whose search is open.
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }
    LaunchedEffect(focusSearch) {
        if (!focusSearch) return@LaunchedEffect
        withFrameNanos {} // the Scaffold composes the top bar while laying out, so the field exists a frame later
        searchFocus.requestFocus()
        focusSearch = false
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedTopBar(
                page = shown,
                title = { stringResource(it.title) },
                pageActions = { if (it == MainTab.Explore) ExplorePeriodAction() },
                actions = {
                    val canSearch = shown.hasSearch && shown !in searching
                    SearchSettingsPill(onSearch = if (canSearch) ({ searching += shown; focusSearch = true }) else null, onSettings = onOpenSettings)
                },
                search = { tab ->
                    val onClose = { searching -= tab }
                    when (tab) {
                        MainTab.Trips -> { { TripsSearchField(onClose, Modifier.focusRequester(searchFocus)) } }
                        MainTab.Profile -> { { PeopleSearchField(onClose, Modifier.focusRequester(searchFocus)) } }
                        else -> null
                    }
                },
                isSearchOpen = { it in searching },
            )
        },
        bottomBar = {
            FloatingNavigationBar(items = items, pagerState = pagerState)
        },
        // 8 dp closer to the bottom bar than the Scaffold's spacing (Shahin, 2026-11-08).
        floatingActionButton = {
            AddTripButton(visible = shown == MainTab.Trips, Modifier.windowInsetsPadding(WindowInsets.safeSides).offset(y = MaterialTheme.spacing.small))
        },
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
                        onOpenNotifications = onOpenNotifications,
                        hasUnseenNotifications = hasUnseenNotifications,
                        showFollowers = showFollowers,
                        onFollowersShown = deepLinks::followersShown,
                    )
                }
            }
        }
    }
}
