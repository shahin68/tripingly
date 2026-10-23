package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.feature.map.domain.model.TripOwnerColor
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_add_day
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.generated.resources.map_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_day_menu
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day
import com.falcon.tripingly.feature.map.generated.resources.map_join_trip
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.generated.resources.map_report_trip
import com.falcon.tripingly.feature.map.generated.resources.map_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_search
import com.falcon.tripingly.feature.map.generated.resources.map_search_clear
import com.falcon.tripingly.feature.map.generated.resources.map_search_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_trip_menu
import com.falcon.tripingly.feature.map.generated.resources.map_unsave_trip
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.ui.tooling.preview.PreviewParameter

/** The trip's name with back, search and the trip's menu; the search field while searching. */
@Composable
internal fun MapHeader(
    header: HeaderState,
    search: SearchState?,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        // A see-through surface doesn't get a content color of its own, so it's set here.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (search != null) {
            SearchField(search = search, onAction = onAction)
        } else {
            TripTitle(header = header, onAction = onAction)
        }
    }
}

@Composable
private fun TripTitle(header: HeaderState, onAction: (MapAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(MapAction.Header.Back) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
        Column(Modifier.weight(1f).padding(vertical = if (header.createdLabel == null) 16.dp else 8.dp)) {
            Text(
                text = header.tripName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            header.createdLabel?.let { created ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = created.asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    header.ownerLabel?.let { owner ->
                        Text(
                            text = owner.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(TripOwnerColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (header.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = { onAction(MapAction.Search.Open) }) {
            Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.map_search))
        }
        header.viewerMenu?.let { ViewerMenuButton(it, onAction) }
        header.editorMenu?.let { EditorMenuButton(it, onAction) }
    }
}

@Composable
private fun ViewerMenuButton(menu: ViewerMenu, onAction: (MapAction) -> Unit) {
    MenuButton(
        contentDescription = stringResource(Res.string.map_trip_menu),
        actions = listOfNotNull(
            DropdownAction(stringResource(if (menu.isSavedByMe) Res.string.map_unsave_trip else Res.string.map_save_trip)) {
                onAction(MapAction.Header.ToggleSave)
            },
            // Once the owner declined, there's nothing more to ask.
            when (menu.myJoinRequest) {
                null -> DropdownAction(stringResource(Res.string.map_join_trip)) { onAction(MapAction.Header.RequestToJoin) }
                JoinRequestStatus.PENDING -> DropdownAction(stringResource(Res.string.map_cancel_join)) { onAction(MapAction.Header.CancelJoinRequest) }
                JoinRequestStatus.DECLINED -> null
            },
            menu.ownerUsername?.let { owner ->
                DropdownAction(stringResource(Res.string.map_owner_profile, owner)) { onAction(MapAction.Header.OpenOwnerProfile) }
            },
            DropdownAction(stringResource(Res.string.map_report_trip)) { onAction(MapAction.Header.ReportTrip) },
        ),
    )
}

@Composable
private fun EditorMenuButton(menu: EditorMenu, onAction: (MapAction) -> Unit) {
    MenuButton(
        contentDescription = stringResource(Res.string.map_day_menu),
        actions = listOfNotNull(
            DropdownAction(stringResource(Res.string.map_add_day)) { onAction(MapAction.Header.AddDay) },
            DropdownAction(stringResource(Res.string.map_delete_day), isDestructive = true) { onAction(MapAction.Header.DeleteDay) }
                .takeIf { menu.canDeleteDay },
        ),
    )
}

@Composable
private fun MenuButton(contentDescription: String, actions: List<DropdownAction>) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = contentDescription)
        }
        AppDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, actions = actions)
    }
}

/** Replaces the trip name while searching; back closes the search. */
@Composable
private fun SearchField(search: SearchState, onAction: (MapAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(MapAction.Search.Close) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
        TextField(
            value = search.query,
            onValueChange = { onAction(MapAction.Search.QueryChanged(it)) },
            placeholder = { Text(stringResource(Res.string.map_search_placeholder)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        when {
            search.isSearching -> CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 14.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
            search.query.isNotEmpty() -> IconButton(onClick = { onAction(MapAction.Search.QueryChanged("")) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_search_clear))
            }
        }
    }
}

@Composable
internal fun SearchResults(
    results: ImmutableList<PlaceSearchResult>,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            results.forEach { result ->
                SuggestionItem(
                    title = result.name,
                    subtitle = result.address,
                    onClick = { onAction(MapAction.Search.ResultClick(result)) },
                )
            }
            OsmAttribution(modifier = Modifier.padding(MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun MapHeaderPreview(@PreviewParameter(MapHeaderPreviewStates::class) preview: MapHeaderPreview) {
    TripinglyTheme { MapHeader(header = preview.header, search = preview.search, onAction = {}) }
}

@Preview
@Composable
private fun MapHeaderDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        val preview = MapHeaderPreviewStates().values.last()
        Column {
            MapHeader(header = preview.header, search = null, onAction = {})
            SearchResults(results = preview.search!!.results, onAction = {})
        }
    }
}

@Preview
@Composable
private fun SearchResultsPreview() {
    TripinglyTheme { SearchResults(results = MapHeaderPreviewStates().values.last().search!!.results, onAction = {}) }
}
