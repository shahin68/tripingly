package com.falcon.tripingly.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.designsystem.component.FloatingNavigationBar
import com.falcon.tripingly.core.designsystem.component.NavBarItem
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.feature.map.presentation.photos.PhotoViewerRoute
import com.falcon.tripingly.feature.social.presentation.explore.ExploreRoute
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyRoute
import com.falcon.tripingly.feature.social.presentation.profile.ProfileRoute
import com.falcon.tripingly.feature.trips.presentation.screen.HomeRoute
import com.falcon.tripingly.shared.generated.resources.Res
import com.falcon.tripingly.shared.generated.resources.tab_explore
import com.falcon.tripingly.shared.generated.resources.tab_nearby
import com.falcon.tripingly.shared.generated.resources.tab_profile
import com.falcon.tripingly.shared.generated.resources.tab_trips
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The four tabs, in the bottom bar's order. */
internal enum class MainTab(val label: StringResource, val icon: ImageVector) {
    Trips(Res.string.tab_trips, Icons.Outlined.Luggage),
    Explore(Res.string.tab_explore, Icons.Outlined.Explore),
    Nearby(Res.string.tab_nearby, Icons.Outlined.NearMe),
    Profile(Res.string.tab_profile, Icons.Outlined.Person),
}

/**
 * The tab shell: each tab's screen with its own floating top bar, over the floating bottom bar. Every tab keeps
 * its scroll position and view models while another is shown (they live as long as this screen); content scrolls
 * behind both bars.
 */
@Composable
internal fun MainTabs(
    onOpenTrip: (tripId: String) -> Unit,
    onOpenProfile: (username: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Trips) }
    val tabStates = rememberSaveableStateHolder()
    val items = MainTab.entries.map { NavBarItem(stringResource(it.label), it.icon) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = { FloatingNavigationBar(items = items, selectedIndex = tab.ordinal, onSelect = { tab = MainTab.entries[it] }) },
    ) { bottomBarPadding ->
        tabStates.SaveableStateProvider(tab.name) {
            when (tab) {
                MainTab.Trips -> HomeRoute(onNavigateToMap = onOpenTrip, onOpenSettings = onOpenSettings, bottomBarPadding = bottomBarPadding)
                MainTab.Explore -> ExploreRoute(
                    onOpenTrip = onOpenTrip,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
                    bottomBarPadding = bottomBarPadding,
                )
                MainTab.Nearby -> NearbyRoute(
                    onOpenTrip = onOpenTrip,
                    onOpenSettings = onOpenSettings,
                    placeImageContent = { placeId, imageId, onReport, onDismiss ->
                        PhotoViewerRoute(owner = PhotoOwner.Place(placeId), photoId = imageId, onReport = onReport, onDismiss = onDismiss)
                    },
                    bottomBarPadding = bottomBarPadding,
                )
                MainTab.Profile -> ProfileRoute(
                    username = null,
                    onBack = {},
                    onOpenTrip = onOpenTrip,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
                    bottomBarPadding = bottomBarPadding,
                )
            }
        }
    }
}
