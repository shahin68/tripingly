package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassButtonDivider
import com.falcon.tripingly.core.designsystem.component.GlassButtonRow
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.generated.resources.map_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_join_trip
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.generated.resources.map_report_trip
import com.falcon.tripingly.feature.map.generated.resources.map_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_search
import com.falcon.tripingly.feature.map.generated.resources.map_search_clear
import com.falcon.tripingly.feature.map.generated.resources.map_search_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_stop_count
import com.falcon.tripingly.feature.map.generated.resources.map_trip_members
import com.falcon.tripingly.feature.map.generated.resources.map_trip_menu
import com.falcon.tripingly.feature.map.generated.resources.map_unsave_trip
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.SearchState
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreview
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreviewStates
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The map's header, three glass pills: back, the trip's name over its dates and stops, and search joined to the
 * trip's ⋮ menu. While searching, one pill: the search field, still joined to ⋮. Over the native map, so no blur.
 * [showDayActions]: the day has no plan sheet, so its day actions are in ⋮ instead.
 */
@Composable
internal fun MapHeader(
    header: HeaderState,
    search: SearchState?,
    showDayActions: Boolean,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (search == null) {
            GlassIconButton(onClick = { onAction(MapAction.Header.Back) }, blur = false) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
            }
            TripTitle(header, Modifier.weight(1f))
        }
        GlassButtonRow(modifier = if (search != null) Modifier.weight(1f) else Modifier, blur = false) {
            if (search != null) {
                SearchField(search, onAction, Modifier.weight(1f))
            } else {
                IconButton(onClick = { onAction(MapAction.Search.Open) }) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.map_search))
                }
            }
            val actions = tripMenuActions(header, showDayActions, onAction)
            if (actions.isNotEmpty()) {
                GlassButtonDivider()
                TripMenuButton(actions)
            }
        }
    }
}

/** The trip's name, and its dates, stop count and (someone else's trip) owner under it. */
@Composable
private fun TripTitle(header: HeaderState, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.heightIn(min = GlassButtonDefaults.Size), shape = MaterialTheme.shapes.extraLarge, blur = false) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small - 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(header.tripName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(
                    header.dates,
                    pluralStringResource(Res.plurals.map_stop_count, header.stopCount, header.stopCount),
                    header.ownerLabel?.asString(),
                ).joinToString(" · ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (header.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * The trip's ⋮ menu: the members first (with the requests to join as a badge), then a viewer's save, join, owner and
 * report, or an editor's day actions while the day has no plan sheet.
 */
@Composable
private fun tripMenuActions(header: HeaderState, showDayActions: Boolean, onAction: (MapAction) -> Unit): List<DropdownAction> {
    val members = header.members?.let {
        DropdownAction(
            label = stringResource(Res.string.map_trip_members, it.count),
            icon = Icons.Outlined.Group,
            badge = it.requestCount.takeIf { count -> count > 0 }?.toString(),
        ) { onAction(MapAction.Header.OpenMembers) }
    }
    val viewer = header.viewerMenu?.let { menu ->
        listOfNotNull(
            DropdownAction(
                label = stringResource(if (menu.isSavedByMe) Res.string.map_unsave_trip else Res.string.map_save_trip),
                icon = if (menu.isSavedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            ) { onAction(MapAction.Header.ToggleSave) },
            // Once the owner declined, there's nothing more to ask.
            when (menu.myJoinRequest) {
                null -> DropdownAction(stringResource(Res.string.map_join_trip), icon = Icons.Outlined.PersonAdd) { onAction(MapAction.Header.RequestToJoin) }
                JoinRequestStatus.PENDING -> DropdownAction(stringResource(Res.string.map_cancel_join), icon = Icons.Outlined.PersonRemove) {
                    onAction(MapAction.Header.CancelJoinRequest)
                }
                JoinRequestStatus.DECLINED -> null
            },
            menu.ownerUsername?.let { owner ->
                DropdownAction(stringResource(Res.string.map_owner_profile, owner), icon = Icons.Outlined.Person) { onAction(MapAction.Header.OpenOwnerProfile) }
            },
            DropdownAction(stringResource(Res.string.map_report_trip), isDestructive = true, icon = Icons.Outlined.Flag) { onAction(MapAction.Header.ReportTrip) },
        )
    }.orEmpty()
    val days = header.editorMenu?.takeIf { showDayActions }?.let { menu ->
        listOfNotNull(
            DropdownAction(stringResource(Res.string.map_add_day), icon = Icons.Outlined.Add) { onAction(MapAction.Header.AddDay) },
            DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true, icon = Icons.Outlined.Delete) { onAction(MapAction.Header.DeleteDay) }
                .takeIf { menu.canDeleteDay },
        )
    }.orEmpty()
    return listOfNotNull(members) + viewer + days
}

@Composable
private fun TripMenuButton(actions: List<DropdownAction>) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.map_trip_menu))
        }
        AppDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, actions = actions)
    }
}

/** Replaces the back button and the trip's name while searching; back closes the search. */
@Composable
private fun SearchField(search: SearchState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(MapAction.Search.Close) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
        BasicTextField(
            value = search.query,
            onValueChange = { onAction(MapAction.Search.QueryChanged(it)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (search.query.isEmpty()) {
                        Text(
                            stringResource(Res.string.map_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    field()
                }
            },
        )
        when {
            search.isSearching -> CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall).size(20.dp),
                strokeWidth = 2.dp,
            )
            search.query.isNotEmpty() -> IconButton(onClick = { onAction(MapAction.Search.QueryChanged("")) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_search_clear))
            }
        }
    }
}

/** The search's results under the header, with the credits for OpenStreetMap and Photon. */
@Composable
internal fun SearchResults(
    results: ImmutableList<PlaceSearchResult>,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, blur = false) {
        Column(modifier = Modifier.heightIn(max = SearchResultsMaxHeight).verticalScroll(rememberScrollState()).padding(vertical = MaterialTheme.spacing.small)) {
            results.forEach { result ->
                SuggestionItem(
                    title = result.name,
                    subtitle = result.address,
                    onClick = { onAction(MapAction.Search.ResultClick(result)) },
                )
            }
            OsmAttribution(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small))
        }
    }
}

private val SearchResultsMaxHeight = 360.dp

@Preview
@Composable
private fun MapHeaderPreview(@PreviewParameter(MapHeaderPreviewStates::class) preview: MapHeaderPreview) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MapHeader(header = preview.header, search = preview.search, showDayActions = false, onAction = {})
        }
    }
}

@Preview
@Composable
private fun MapHeaderDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        val preview = MapHeaderPreviewStates().values.last()
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MapHeader(header = preview.header, search = null, showDayActions = false, onAction = {})
            SearchResults(results = preview.search!!.results, onAction = {}, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun SearchResultsPreview() {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
            SearchResults(results = MapHeaderPreviewStates().values.last().search!!.results, onAction = {})
        }
    }
}
