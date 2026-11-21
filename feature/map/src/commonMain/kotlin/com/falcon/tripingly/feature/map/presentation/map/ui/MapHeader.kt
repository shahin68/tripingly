package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.core.animateFloatAsState
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.updateTransition
import com.falcon.tripingly.core.designsystem.component.verticalFadingEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassButtonDivider
import com.falcon.tripingly.core.designsystem.component.GlassButtonRow
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.SearchKeyboard
import com.falcon.tripingly.core.designsystem.component.SuggestionItem
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.ui.rememberOpeningFocus
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.generated.resources.map_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_join_trip
import com.falcon.tripingly.feature.map.generated.resources.map_report_trip
import com.falcon.tripingly.feature.map.generated.resources.map_share_trip
import com.falcon.tripingly.feature.map.generated.resources.map_leave_trip
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Share
import com.falcon.tripingly.feature.map.generated.resources.map_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_search
import com.falcon.tripingly.feature.map.generated.resources.map_search_placeholder
import com.falcon.tripingly.feature.map.generated.resources.map_trip_members
import com.falcon.tripingly.feature.map.generated.resources.map_trip_menu
import com.falcon.tripingly.feature.map.generated.resources.map_unsave_trip
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.SearchResultItem
import com.falcon.tripingly.feature.map.presentation.map.SearchState
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreview
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreviewStates
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * The map's header, one row of glass pills of the same height: a round back button, the trip's owner and name, and
 * search joined to the trip's ⋮ menu. Tapping search grows that pill to the left over the whole
 * row, pushing the back button and the title away while ⋮ stays put, as on the design canvas; closing it shrinks
 * back. Tapping the title opens the trip card ([onTitleClick]; [onTitlePlaced] gives the pill's bounds inside the
 * header's padding, where the card grows out of it; the pill hides meanwhile, [isTitleHidden]). As the card grows
 * ([cardProgress], 0 to 1) it pushes the back button out to the start and search and ⋮ out to the end, cut at the
 * header's sides (the left half in landscape). Over the native map, so no blur.
 */
@Composable
internal fun MapHeader(
    header: HeaderState,
    search: SearchState?,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
    isTitleHidden: Boolean = false,
    onTitleClick: () -> Unit = {},
    onTitlePlaced: (Rect) -> Unit = {},
    cardProgress: () -> Float = { 0f },
) {
    val progress by animateFloatAsState(if (search != null) 1f else 0f, tween(SEARCH_MOTION_MILLIS))
    val actions = tripMenuActions(header, onAction)
    val gap = MaterialTheme.spacing.small
    // How far past the header's padding the trip card pushes the buttons: off the screen, or out of the left half.
    val pushedOut = MaterialTheme.spacing.medium
    // The search pill's width with just its buttons, kept while the field is open.
    val collapsedWidth = remember { intArrayOf(0) }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            // Only the sides: what the trip card pushes out goes no further, the buttons' shadows still show.
            .drawWithContent { clipRect(top = -size.height, bottom = size.height * 2) { this@drawWithContent.drawContent() } }
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .height(GlassButtonDefaults.HeaderSize),
        content = {
            Row(
                // Cut while search grows over it; not while the trip card pushes the back button off the screen.
                modifier = Modifier.graphicsLayer { alpha = 1f - progress }.then(if (search != null || progress > 0f) Modifier.clipToBounds() else Modifier),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = { onAction(MapAction.Header.Back) },
                    blur = false,
                    size = GlassButtonDefaults.HeaderSize,
                    modifier = Modifier.graphicsLayer { translationX = -cardProgress() * (size.width + pushedOut.toPx()) },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
                }
                TripTitle(
                    header = header,
                    onClick = onTitleClick,
                    onOwnerClick = { onAction(MapAction.Header.OpenOwnerProfile) },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = if (isTitleHidden) 0f else 1f }
                        .onGloballyPositioned { onTitlePlaced(it.boundsInParent()) },
                )
            }
            GlassButtonRow(blur = false, height = GlassButtonDefaults.HeaderSize) {
                if (search != null) {
                    SearchField(search, onAction, Modifier.weight(1f))
                } else {
                    // At the end while the pill shrinks back, so search and ⋮ stay put and don't glide.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        IconButton(onClick = { onAction(MapAction.Search.Open) }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.map_search))
                        }
                    }
                }
                if (actions.isNotEmpty()) {
                    GlassButtonDivider()
                    TripMenuButton(actions)
                }
            }
        },
    ) { (title, pill), constraints ->
        val width = constraints.maxWidth
        val height = GlassButtonDefaults.HeaderSize.roundToPx()
        if (search == null) collapsedWidth[0] = pill.maxIntrinsicWidth(height)
        val pillWidth = lerp(collapsedWidth[0], width, progress)
        val titleWidth = (width - pillWidth - gap.roundToPx()).coerceAtLeast(0)
        val titlePlaceable = title.measure(Constraints.fixed(titleWidth, height))
        val pillPlaceable = pill.measure(Constraints.fixed(pillWidth, height))
        layout(width, height) {
            titlePlaceable.place(0, 0)
            pillPlaceable.placeWithLayer(width - pillWidth, 0) { translationX = cardProgress() * (pillWidth + pushedOut.toPx()) }
        }
    }
}

/** How long the search pill takes to grow over the header or shrink back. */
private const val SEARCH_MOTION_MILLIS = 300

/**
 * The trip's ⋮ menu: the members first (with the requests to join as a badge), then a viewer's save, join, owner and
 * report, or an editor's day actions while the day has no plan sheet.
 */
@Composable
private fun tripMenuActions(header: HeaderState, onAction: (MapAction) -> Unit): List<DropdownAction> {
    // ⋮ is about the whole trip: who is on it, saving it, asking to join, reporting it. The open day's plan has its own ⋯
    // in the plan sheet, a day comes with + by the day tabs and goes with a long press on its tab (Shahin, 2026-11-19).
    // The owner's profile opens from their face in the title. Share trip for everyone, Leave trip for editors.
    val haptics = rememberHaptics()
    val members = header.members?.let {
        DropdownAction(
            label = stringResource(Res.string.map_trip_members, it.count),
            icon = Icons.Outlined.Group,
            badge = it.requestCount.takeIf { count -> count > 0 }?.toString(),
        ) { onAction(MapAction.Header.OpenMembers) }
    }
    val share = DropdownAction(stringResource(Res.string.map_share_trip), icon = Icons.Outlined.Share) { onAction(MapAction.Header.ShareTrip) }
        .takeIf { header.canShare }
    // An editor's way out; the owner deletes the trip from My Trips.
    val leave = DropdownAction(stringResource(Res.string.map_leave_trip), isDestructive = true, icon = Icons.AutoMirrored.Outlined.Logout) {
        onAction(MapAction.Header.LeaveTrip)
    }.takeIf { header.canLeave }
    val viewer = header.viewerMenu?.let { menu ->
        listOfNotNull(
            DropdownAction(
                label = stringResource(if (menu.isSavedByMe) Res.string.map_unsave_trip else Res.string.map_save_trip),
                icon = if (menu.isSavedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            ) {
                haptics.toggle(!menu.isSavedByMe)
                onAction(MapAction.Header.ToggleSave)
            },
            // Once the owner declined, there's nothing more to ask.
            when (menu.myJoinRequest) {
                null -> DropdownAction(stringResource(Res.string.map_join_trip), icon = Icons.Outlined.PersonAdd) { onAction(MapAction.Header.RequestToJoin) }
                JoinRequestStatus.PENDING -> DropdownAction(stringResource(Res.string.map_cancel_join), icon = Icons.Outlined.PersonRemove) {
                    onAction(MapAction.Header.CancelJoinRequest)
                }
                JoinRequestStatus.DECLINED -> null
            },
            DropdownAction(stringResource(Res.string.map_report_trip), isDestructive = true, icon = Icons.Outlined.Flag) { onAction(MapAction.Header.ReportTrip) },
        )
    }.orEmpty()
    return listOfNotNull(members, share) + viewer + listOfNotNull(leave)
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

/** Replaces the back button and the trip's name while searching; its ✕ (or the system back) closes the search. */
@Composable
private fun SearchField(search: SearchState, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = rememberOpeningFocus()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppTextField(
            state = search.query,
            placeholder = stringResource(Res.string.map_search_placeholder),
            onTextChange = { onAction(MapAction.Search.QueryChanged(it)) },
            // ✕ closes the search: its results, the places named so and the keyboard go, the map is as before.
            onClose = { onAction(MapAction.Search.Close) },
            // The keyboard's search key, no result picked: the map shows only the places named so.
            onImeAction = { onAction(MapAction.Search.Submit) },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            style = AppTextFieldStyle.Inline,
            trailing = if (search.isSearching) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            keyboardOptions = SearchKeyboard,
        )
    }
}

/**
 * The search's results under the header: as tall as they are, at most [maxHeight], then they scroll with faded edges;
 * the credits for OpenStreetMap and Photon stay at the bottom. The card grows down out of the header's edge when results
 * come and folds back up when they go, like the trip card but only vertically: the glass (its shadow, edge and clip) is
 * one shape whose bottom moves, and the content is uncovered, not faded. It is laid out once at full size, so each
 * frame only draws again. While typing, each result fades in or out, the rest glide to their places, and the card's
 * height glides with them (Shahin, 2026-11-21).
 */
@Composable
internal fun SearchResults(
    results: ImmutableList<SearchResultItem>,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = SearchResultsMaxHeight,
) {
    val transition = updateTransition(results.isNotEmpty(), label = "search results")
    if (!transition.currentState && !transition.targetState) return
    val reveal = transition.animateFloat(
        transitionSpec = { tween(if (false isTransitioningTo true) RESULTS_OPEN_MILLIS else RESULTS_CLOSE_MILLIS, easing = Decelerate) },
        label = "search results reveal",
    ) { shown -> if (shown) 1f else 0f }
    val corners = MaterialTheme.shapes.large
    val tint = GlassDefaults.opaqueTint
    val edge = MaterialTheme.extendedColors.glassEdge
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shape = RevealShape(corners, reveal.value)
                clip = true
                shadowElevation = GlassDefaults.ShadowElevation.toPx()
                ambientShadowColor = GlassDefaults.Shadow
                spotShadowColor = GlassDefaults.Shadow
            }
            .drawBehind {
                val outline = RevealShape(corners, reveal.value).createOutline(size, layoutDirection, this)
                drawOutline(outline, tint)
                drawOutline(outline, edge, style = Stroke(GlassDefaults.Edge.toPx()))
            }
            .heightIn(max = maxHeight)
            .animateContentSize(tween(RESULTS_OPEN_MILLIS, easing = Emphasized)),
    ) {
        // Drawn by hand rather than with GlassSurface, so it sets the glass's content color itself.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).verticalFadingEdge(listState),
                contentPadding = PaddingValues(top = MaterialTheme.spacing.small),
            ) {
                items(results, key = { it.key }) { (result, distanceMeters) ->
                    SuggestionItem(
                        title = result.name,
                        // No address (OpenStreetMap has none): how far it is from the map's centre.
                        subtitle = result.address?.takeIf { it.isNotBlank() } ?: distanceMeters?.let { distanceText(it).asString() },
                        onClick = { onAction(MapAction.Search.ResultClick(result)) },
                        icon = result.icon,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            // Always in sight under the list, as far from the card's sides and bottom as the first result's text from its top.
            OsmAttribution(
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.medium,
                    top = MaterialTheme.spacing.small,
                    bottom = MaterialTheme.spacing.medium,
                ),
            )
        }
    }
}

/** [base] cut to the top [progress] of the card's height: its bottom edge moves, its corners stay round. */
private class RevealShape(private val base: Shape, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        base.createOutline(Size(size.width, size.height * progress), layoutDirection, density)
}

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** As the trip card's: fast at first, easing into place, both ways. */
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val RESULTS_OPEN_MILLIS = 320
private const val RESULTS_CLOSE_MILLIS = 260

/** Previews only: on the map the room down to the plan sheet decides. */
private val SearchResultsMaxHeight = 360.dp

@Preview
@Composable
private fun MapHeaderPreview(@PreviewParameter(MapHeaderPreviewStates::class) preview: MapHeaderPreview) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MapHeader(header = preview.header, search = preview.search, onAction = {})
        }
    }
}

@Preview
@Composable
private fun MapHeaderDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        val preview = MapHeaderPreviewStates().values.last()
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            MapHeader(header = preview.header, search = null, onAction = {})
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
