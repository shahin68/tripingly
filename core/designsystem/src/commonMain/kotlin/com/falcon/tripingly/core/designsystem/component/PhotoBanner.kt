package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceCategory

/*
 * The photo banner of the map's pin cards (the "Final" board of the pin card canvas, Shahin, 2026-11-12), shared with
 * Nearby's spot tiles (Shahin, 2026-11-23) so a spot looks the same in the grid and on its map.
 */

/**
 * A photo [image] filling a rounded banner (tapped: [onClick]) with the [title] (wrapping) and [subtitle] in white over
 * a dark fade at the bottom, [afterSubtitle] on its line (e.g. who added it), [trailing] on their right at the bottom
 * (a stop's flame and comments to tap, or a place's flames only counted; Shahin 2026-11-14), and a [topEnd] button.
 * Without an image ([image] null) it is a tinted panel with the pin icon at the top start and the text dark. [height]
 * is the pin cards' banner height; [Dp.Unspecified] leaves it to [modifier] (a grid cell).
 */
@Composable
fun PhotoBanner(
    title: String,
    subtitle: AnnotatedString,
    image: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    height: Dp = PhotoBannerDefaults.Height,
    /** The title's style and lines: a small tile takes a smaller title cut after two lines. */
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    titleMaxLines: Int = Int.MAX_VALUE,
    /** The name is still coming: a small spinner in its place ([LoadingTitle]). */
    isTitleLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    afterSubtitle: @Composable FlowRowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    topEnd: @Composable () -> Unit = {},
    /** Without an image: the pin's own mark ([PinMark]), mirroring [topEnd]. */
    mark: @Composable () -> Unit = {},
) {
    val textColor = if (image != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(if (image != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (image != null) {
            image()
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        } else {
            // The pin's mark, mirroring the top end button: the same size and inset.
            Box(Modifier.padding(BannerButtonInset)) { mark() }
        }
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Box(Modifier.align(Alignment.TopEnd).padding(BannerButtonInset)) { topEnd() }
            // The title on its own line, then the details with the pills at their end (Shahin, 2026-11-23). The text keeps
            // its inset from the start; the pills, one joined group, sit as far from the end and the bottom as ⌄ from its
            // corner, with nothing before them, so the details get all the room left.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = BannerInset, end = BannerButtonInset, bottom = BannerButtonInset)) {
                LoadingTitle(
                    title,
                    isTitleLoading,
                    titleStyle,
                    Modifier.padding(end = BannerInset - BannerButtonInset),
                    maxLines = titleMaxLines,
                    overflow = if (titleMaxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                // A pill high with or without them, so the details sit at the same place on every card.
                Row(Modifier.heightIn(min = BannerPillHeight), verticalAlignment = Alignment.CenterVertically) {
                    // Who added it goes on as long as there's room, then whole onto the next line (Shahin, 2026-11-14).
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                        afterSubtitle()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
                }
            }
        }
    }
}

object PhotoBannerDefaults {
    /** The pin cards' banner. */
    val Height = 180.dp
}

/**
 * A pin's title, or while its name is still coming ([isLoading]) a small spinner a line high in its place, fading
 * into the name when it comes (Shahin, 2026-11-17). Only drawn state: nothing keeps running once the card goes.
 */
@Composable
fun LoadingTitle(
    text: String,
    isLoading: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
        label = "title loading",
    ) { loading ->
        if (loading) {
            val lineHeight = with(LocalDensity.current) { style.lineHeight.takeIf { it.isSpecified }?.toDp() ?: TitleSpinnerSize }
            Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(TitleSpinnerSize), strokeWidth = TitleSpinnerStroke, color = LocalContentColor.current)
            }
        } else {
            Text(text, style = style, maxLines = maxLines, overflow = overflow)
        }
    }
}

private val TitleSpinnerSize = 12.dp
private val TitleSpinnerStroke = 1.5.dp

/**
 * The mark of a card without an image: its place's category icon, on the place colour or on stop green for a stop
 * ([isStop]; its number is in the bold "Stop 2" under the title, Shahin 2026-11-13).
 */
@Composable
fun PinMark(category: PlaceCategory?, isStop: Boolean, modifier: Modifier = Modifier.size(JoinedButtonDefaults.Height)) {
    val fill = if (isStop) MaterialTheme.extendedColors.stop else MaterialTheme.extendedColors.place
    Box(modifier.background(fill, CircleShape), contentAlignment = Alignment.Center) {
        Icon((category ?: PlaceCategory.Other).icon(), contentDescription = null, tint = if (isStop) MaterialTheme.extendedColors.onStop else MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
    }
}

/**
 * A count on a banner that only shows (a place's flames before it's on a trip): no pill, only buttons get one (Shahin,
 * 2026-11-23); a pill's height and inset, so it sits where the pill would.
 */
@Composable
fun BannerCount(icon: ImageVector, count: Int) {
    CountLabel(icon, count, Modifier.height(BannerPillHeight).padding(BannerPillPadding), size = CountLabelSize.Large)
}

/**
 * The see-through glass of a banner's buttons, a tint of the banner's text colour: white on a photo, a dark tint on the
 * panel without one, so it shows on both (Shahin, 2026-11-13).
 */
@Composable
fun bannerGlass(): Color {
    val content = LocalContentColor.current
    // White on a busy photo needs a stronger glass than a dark tint on the plain panel.
    return content.copy(alpha = if (content.luminance() > 0.5f) ON_PHOTO_TINT else ON_PANEL_TINT)
}

/** A banner's count button as a pill of [fill] in [shape] (its place in a joined group), [BannerPillHeight] high. */
fun Modifier.bannerPill(fill: Color, shape: Shape): Modifier = clip(shape).background(fill).height(BannerPillHeight)

/** 12 dp lower than ⌄ (Shahin, 2026-11-23): the pills shouldn't outweigh the text beside them. */
private val BannerPillHeight = 36.dp

/** A banner pill's sides: the icon as far in from the start as from the top and bottom. */
val BannerPillPadding = PaddingValues(horizontal = (BannerPillHeight - JoinedButtonDefaults.IconSize) / 2)

/** The flame as a pill on a banner, in [shape] (its place in the joined group): glass, or filled with the flame's own colour once I marked it hot. */
@Composable
fun BannerHotButton(hot: Boolean, count: Int, onToggle: () -> Unit, shape: Shape) {
    val fill by animateColorAsState(if (hot) MaterialTheme.extendedColors.attention else bannerGlass())
    val content by animateColorAsState(if (hot) MaterialTheme.extendedColors.onAttention else LocalContentColor.current)
    CompositionLocalProvider(LocalContentColor provides content) {
        HotButton(hot, count, onToggle, Modifier.bannerPill(fill, shape), tint = content, contentPadding = BannerPillPadding)
    }
}

/** A count to tap (a stop's comments) as a glass pill on a banner, in [shape] (its place in the joined group). */
@Composable
fun BannerCountButton(icon: ImageVector, contentDescription: String, count: Int, onClick: () -> Unit, isNew: Boolean = false, shape: Shape) {
    CountButton(icon, contentDescription, count, onClick, Modifier.bannerPill(bannerGlass(), shape), isNew = isNew, contentPadding = BannerPillPadding)
}

/** A round button on a photo banner (⌄ collapse), in the banner's text colour on its [bannerGlass]. */
@Composable
fun BannerButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(JoinedButtonDefaults.Height), shape = CircleShape, color = bannerGlass(), contentColor = LocalContentColor.current) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(JoinedButtonDefaults.IconSize)) }
    }
}

private val BannerInset = 14.dp
private val BannerButtonInset = 8.dp
private const val ON_PHOTO_TINT = 0.26f
private const val ON_PANEL_TINT = 0.12f

@Composable
private fun PhotoBannerPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium).width(340.dp), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        PhotoBanner(title = "Hofburg", subtitle = AnnotatedString("Historic site"), image = null, mark = { PinMark(PlaceCategory("historic"), isStop = false) }, trailing = { BannerCount(Icons.Outlined.LocalFireDepartment, 40) })
        PhotoBanner(
            title = "Lunch at Anna's, a long name that wraps",
            subtitle = AnnotatedString("Custom spot"),
            image = null,
            mark = { PinMark(PlaceCategory("restaurant"), isStop = true) },
            topEnd = { BannerButton({}, Icons.Outlined.ExpandMore, "Collapse") },
            trailing = {
                JoinedButtons(
                    listOf<JoinedButtonSlot>(
                        { BannerHotButton(hot = true, count = 12, onToggle = {}, shape = it) },
                        { BannerCountButton(Icons.AutoMirrored.Outlined.Comment, "Comments", 3, onClick = {}, isNew = true, shape = it) },
                    ),
                )
            },
        )
    }
}

@Preview
@Composable
private fun PhotoBannerPreview() {
    TripinglyTheme { PhotoBannerPreviewContent() }
}

@Preview
@Composable
private fun PhotoBannerDarkPreview() {
    TripinglyTheme(darkTheme = true) { PhotoBannerPreviewContent() }
}
