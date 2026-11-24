package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.NumberUtils
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.BannerCount
import com.falcon.tripingly.core.designsystem.component.FaceSlot
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.component.PersonFace
import com.falcon.tripingly.core.designsystem.component.PersonFaceDefaults
import com.falcon.tripingly.core.designsystem.component.PhotoBanner
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.PinMark
import com.falcon.tripingly.core.designsystem.component.glassSource
import com.falcon.tripingly.core.designsystem.component.label
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.social.PeoplePage
import com.falcon.tripingly.core.model.social.Person
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_km
import com.falcon.tripingly.feature.social.generated.resources.social_nearby_distance_m
import com.falcon.tripingly.feature.social.generated.resources.social_people
import com.falcon.tripingly.feature.social.generated.resources.social_people_more
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel.PeopleState
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.social.presentation.component.previewNearbyPeople
import com.falcon.tripingly.feature.social.presentation.component.previewNearbyPlaces

/*
 * Nearby's grid (the Nearby canvas v18, Shahin 2026-11-23): the faces of the people a search found on top, then the
 * spots in rows of 2+1, 1.5+1.5 and 1+2 (Shahin, 2026-11-24), repeated. The grid counts in half columns
 * ([UNITS_PER_COLUMN]), so a 1.5 tile is whole: a group of three columns is six units. Every tile is [TileHeightRatio]
 * times one column's width high (so a name, its distance and the flame fit without covering each other, Shahin
 * 2026-11-24): a row never changes height when a photo comes, and wider tiles are as high as the ones beside them.
 */

/** A spot in the grid, [span] half columns wide: [NARROW], [HALF] or [WIDE]. */
internal data class Tile(val place: NearbyPlace, val span: Int)

/** How many grid units one column is: halves, so a tile can be one and a half columns wide. */
private const val UNITS_PER_COLUMN = 2
internal const val NARROW = UNITS_PER_COLUMN
internal const val HALF = UNITS_PER_COLUMN * 3 / 2
internal const val WIDE = UNITS_PER_COLUMN * 2

/** The rows' patterns, repeated: 2+1, 1.5+1.5, 1+2. */
private val RowPatterns = listOf(listOf(WIDE, NARROW), listOf(HALF, HALF), listOf(NARROW, WIDE))

/**
 * The spots as tiles in rows of 2+1, 1.5+1.5, 1+2, repeated. A wide slot takes the next spot with a photo among the
 * next few ([WIDE_LOOKAHEAD], so a row only depends on spots near it and a new page only adds rows below); without
 * one in reach the row is 1+1+1. The 1.5+1.5 row takes the next two spots, photo or not. Spots without a photo are
 * never wide.
 */
internal fun arrangeTiles(places: List<NearbyPlace>): List<Tile> {
    val left = places.toMutableList()
    val tiles = mutableListOf<Tile>()
    var row = 0
    while (left.isNotEmpty()) {
        val pattern = RowPatterns[row++ % RowPatterns.size]
        val photo = if (WIDE in pattern) left.take(WIDE_LOOKAHEAD).indexOfFirst { it.coverImageId != null } else -1
        when {
            left.size < pattern.size || (WIDE in pattern && photo < 0) ->
                repeat(minOf(3, left.size)) { tiles += Tile(left.removeAt(0), span = NARROW) }
            WIDE in pattern -> {
                val wide = left.removeAt(photo)
                for (span in pattern) tiles += if (span == WIDE) Tile(wide, span) else Tile(left.removeAt(0), span)
            }
            else -> for (span in pattern) tiles += Tile(left.removeAt(0), span)
        }
    }
    return tiles
}

private const val WIDE_LOOKAHEAD = 6

/** The grid's columns: three, or three more for each [TileGroupWidth] a wide screen (landscape) has room for. */
private fun columnsFor(width: Dp): Int = 3 * (width / TileGroupWidth).toInt().coerceAtLeast(1)

private val TileGroupWidth = 400.dp

/** A tile's height to one column's width. */
private const val TileHeightRatio = 1.4f

/**
 * Nearby's grid: [header] items (the faces) on top across the whole width, then [places] as tiles, then
 * [footer] items (the OpenStreetMap credit). Tiles open their spot ([onOpen]); their flames only show (Shahin, 2026-11-24).
 */
@Composable
internal fun SpotGrid(
    places: List<NearbyPlace>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (NearbyPlace) -> Unit,
    modifier: Modifier = Modifier,
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
    val tiles = remember(places) { arrangeTiles(places) }
    val gap = MaterialTheme.spacing.small
    BoxWithConstraints(modifier.fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val width = maxWidth - contentPadding.calculateStartPadding(direction) - contentPadding.calculateEndPadding(direction)
        val columns = columnsFor(width)
        // In half columns: a column is two units and the gap between them.
        val units = columns * UNITS_PER_COLUMN
        val unit = (width - gap * (units - 1)) / units
        val tileHeight = (unit * UNITS_PER_COLUMN + gap * (UNITS_PER_COLUMN - 1)) * TileHeightRatio
        LazyVerticalGrid(
            columns = GridCells.Fixed(units),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().glassSource(),
        ) {
            header()
            items(tiles, key = { it.place.id }, span = { GridItemSpan(it.span) }) { tile ->
                NearbyTile(tile.place, onOpen = { onOpen(tile.place) }, Modifier.height(tileHeight).animateItem())
            }
            footer()
        }
    }
}

/** A grid item across the whole width. */
internal fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** The OpenStreetMap credit under the spots, when the server lists OpenStreetMap places. */
internal fun LazyGridScope.osmCredit(isShown: Boolean) {
    if (isShown) fullWidthItem("osm") { OsmAttribution(Modifier.padding(vertical = MaterialTheme.spacing.small)) }
}

/**
 * A spot: the pin card's photo banner (Shahin, 2026-11-23), its name and distance (or city, or kind) at the bottom
 * start and its flame at the bottom end; without a photo, the tinted panel with its kind's mark.
 */
@Composable
internal fun NearbyTile(place: NearbyPlace, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    PhotoBanner(
        title = place.name,
        subtitle = AnnotatedString(place.distanceMeters?.let { distanceText(it).asString() } ?: place.city ?: place.category.label()),
        image = coverOf(place),
        modifier = modifier.skeleton(MaterialTheme.shapes.large),
        height = Dp.Unspecified,
        titleStyle = MaterialTheme.typography.titleSmall,
        titleMaxLines = 2,
        onClick = onOpen,
        mark = { PinMark(place.category, isStop = false) },
        trailing = { if (place.hotCount > 0) BannerCount(Icons.Outlined.LocalFireDepartment, place.hotCount) },
    )
}

/** The spot's cover filling its tile; null without one. */
private fun coverOf(place: NearbyPlace): (@Composable BoxScope.() -> Unit)? = place.coverImageId?.let { id ->
    { PhotoImage(photoId = id, url = place.coverThumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.fillMaxSize()) }
}

/** "350 m" or "2.4 km" from me. */
internal fun distanceText(meters: Int): UiText =
    if (meters < 1000) {
        UiText.Resource(Res.string.social_nearby_distance_m, meters)
    } else {
        UiText.Resource(Res.string.social_nearby_distance_km, NumberUtils.kilometers(meters))
    }

/**
 * As many faces as fit (tapped: their profile), then, when there are more, a circle with how many (up to "999+")
 * that opens the people sheet. A full row spreads across the width, so it lines up with the tiles' edges.
 */
@Composable
internal fun PeopleFaces(people: PeopleState, onOpenProfile: (Person) -> Unit, onOpenSheet: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = MaterialTheme.spacing.extraSmall
        val fit = ((maxWidth + gap) / (PersonFaceDefaults.Width + gap)).toInt().coerceAtLeast(1)
        val hasMore = people.total > fit || people.people.size > fit
        val faces = people.people.take(if (hasMore) fit - 1 else fit)
        val isFull = faces.size + (if (hasMore) 1 else 0) == fit
        Row(horizontalArrangement = if (isFull) Arrangement.SpaceBetween else Arrangement.spacedBy(gap)) {
            faces.forEach { person -> PersonFace(person.user, onClick = { onOpenProfile(person) }) }
            if (hasMore) MoreFace(total = people.total, shown = faces.size, onClick = onOpenSheet)
        }
    }
}

/** How many more people there are, in a circle the size of a face, with "People" under it. */
@Composable
private fun MoreFace(total: Int, shown: Int, onClick: () -> Unit) {
    val more = total - shown
    val count = if (total >= PeoplePage.TOTAL_CAP) "${PeoplePage.TOTAL_CAP}+" else "+$more"
    val description = stringResource(Res.string.social_people_more, count)
    FaceSlot(name = stringResource(Res.string.social_people), onClick = onClick, onClickLabel = description) {
        Box(
            Modifier.size(AvatarSize.Medium).skeleton(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The grid as a search shows it: the people's faces on top, the spots, the OpenStreetMap credit. */
@Composable
private fun SpotGridPreviewContent() {
    SpotGrid(
        places = previewNearbyPlaces,
        gridState = rememberLazyGridState(),
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        onOpen = {},
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        header = { fullWidthItem("people") { PeopleFaces(previewNearbyPeople, onOpenProfile = {}, onOpenSheet = {}) } },
        footer = { osmCredit(isShown = true) },
    )
}

@Preview(heightDp = 720)
@Composable
private fun SpotGridPreview() {
    TripinglyTheme { SpotGridPreviewContent() }
}

@Preview(heightDp = 720)
@Composable
private fun SpotGridDarkPreview() {
    TripinglyTheme(darkTheme = true) { SpotGridPreviewContent() }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SpotGridLandscapePreview() {
    TripinglyTheme { SpotGridPreviewContent() }
}

/** A tile with a photo's place, one without, and the faces with a few people and with more than fit. */
@Composable
private fun NearbyPartsPreviewContent() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            previewNearbyPlaces.take(3).forEach { place ->
                NearbyTile(place, onOpen = {}, modifier = Modifier.size(width = 112.dp, height = 157.dp))
            }
        }
        PeopleFaces(previewNearbyPeople.copy(total = previewNearbyPeople.people.size), onOpenProfile = {}, onOpenSheet = {})
        PeopleFaces(previewNearbyPeople, onOpenProfile = {}, onOpenSheet = {})
    }
}

@Preview
@Composable
private fun NearbyPartsPreview() {
    TripinglyTheme { NearbyPartsPreviewContent() }
}

@Preview
@Composable
private fun NearbyPartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { NearbyPartsPreviewContent() }
}
