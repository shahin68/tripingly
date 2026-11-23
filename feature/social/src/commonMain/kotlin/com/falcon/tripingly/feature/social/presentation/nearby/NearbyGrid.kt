package com.falcon.tripingly.feature.social.presentation.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.falcon.tripingly.core.designsystem.component.BannerHotButton
import com.falcon.tripingly.core.designsystem.component.FaceSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtonSlot
import com.falcon.tripingly.core.designsystem.component.JoinedButtons
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

/*
 * Nearby's grid (the Nearby canvas v18, Shahin 2026-11-23): the people's faces on top, then the spots in rows of
 * 2+1, 1+1+1 and 1+2, repeated. Every tile is one column's width high, so a row never changes height when a photo
 * comes, and a wide tile is as high as the one beside it.
 */

/** A spot in the grid, [span] columns wide (1, or 2 for a spot with a photo). */
internal data class Tile(val place: NearbyPlace, val span: Int)

/** The rows' patterns, repeated. */
private val RowPatterns = listOf(listOf(2, 1), listOf(1, 1, 1), listOf(1, 2))

/**
 * The spots as tiles in rows of 2+1, 1+1+1, 1+2, repeated. A wide slot takes the next spot with a photo among the
 * next few ([WIDE_LOOKAHEAD], so a row only depends on spots near it and a new page only adds rows below); without
 * one in reach the row is 1+1+1. Spots without a photo are always one column wide.
 */
internal fun arrangeTiles(places: List<NearbyPlace>): List<Tile> {
    val left = places.toMutableList()
    val tiles = mutableListOf<Tile>()
    var row = 0
    while (left.isNotEmpty()) {
        val pattern = RowPatterns[row++ % RowPatterns.size]
        val photo = if (2 in pattern) left.take(WIDE_LOOKAHEAD).indexOfFirst { it.coverImageId != null } else -1
        if (photo < 0 || left.size < pattern.size) {
            repeat(minOf(3, left.size)) { tiles += Tile(left.removeAt(0), span = 1) }
            continue
        }
        val wide = left.removeAt(photo)
        for (span in pattern) tiles += if (span == 2) Tile(wide, span = 2) else Tile(left.removeAt(0), span = 1)
    }
    return tiles
}

private const val WIDE_LOOKAHEAD = 6

/** The grid's columns: three, or three more for each [TileGroupWidth] a wide screen (landscape) has room for. */
private fun columnsFor(width: Dp): Int = 3 * (width / TileGroupWidth).toInt().coerceAtLeast(1)

private val TileGroupWidth = 400.dp

/**
 * Nearby's grid: [header] items (the faces) on top across the whole width, then [places] as tiles, then
 * [footer] items (the OpenStreetMap credit). Tiles open their spot ([onOpen]) and mark it hot ([onMarkHot]).
 */
@Composable
internal fun SpotGrid(
    places: List<NearbyPlace>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (NearbyPlace) -> Unit,
    onMarkHot: (NearbyPlace) -> Unit,
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
        val tileHeight = (width - gap * (columns - 1)) / columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().glassSource(),
        ) {
            header()
            items(tiles, key = { it.place.id }, span = { GridItemSpan(it.span) }) { tile ->
                NearbyTile(tile.place, onOpen = { onOpen(tile.place) }, onMarkHot = { onMarkHot(tile.place) }, Modifier.height(tileHeight).animateItem())
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
internal fun NearbyTile(place: NearbyPlace, onOpen: () -> Unit, onMarkHot: () -> Unit, modifier: Modifier = Modifier) {
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
        trailing = { JoinedButtons(listOf<JoinedButtonSlot>({ BannerHotButton(place.hotByMe, place.hotCount, onMarkHot, it) })) },
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
