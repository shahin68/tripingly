package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlin.math.max
import kotlin.math.min

/**
 * One entry of an [AppDropdownMenu]. An optional [icon] leads it; a [badge] (e.g. how many join requests wait)
 * sits at the end in the accent color, and a [selected] entry (the filter in use) ends with a check mark.
 */
@Immutable
data class DropdownAction(
    val label: String,
    val isDestructive: Boolean = false,
    val icon: ImageVector? = null,
    val badge: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The app's overflow menu (⋮, long press), in map glass with each icon in a soft tile (Shahin, 2026-11-21, look I).
 * It opens next to what opened it, never over it: below, or above when there's no room below (the plan sheet's ⋯,
 * the day tabs), lined up with its end on the right half of the screen and its start on the left. It blooms out of
 * the corner nearest that button while its rows slide in from that side, nearest first; closing plays it back.
 * One [Animatable] read only in graphicsLayer and drawing, so nothing is measured again while it moves. The popup
 * keeps room around the card, so its slight shadow is never cut, and the rows reach the card's edges, so the first
 * and last ripples fill its rounded corners. Popups open in their own window, where glass blur can't reach, so
 * the glass is the map's near-opaque tint. Put it in a `Box` together with the button that opens it.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<DropdownAction>,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    var isShown by remember { mutableStateOf(expanded) }
    // Opening eases out as each part arrives; closing from open starts fast and eases out as each part leaves
    // (Shahin, 2026-11-22). A turn back halfway keeps the curve it's on, so nothing jumps.
    var isClosingCurve by remember { mutableStateOf(false) }
    val isOpen by rememberUpdatedState(expanded)
    LaunchedEffect(expanded) {
        if (expanded) {
            if (progress.value == 0f) isClosingCurve = false
            isShown = true
            progress.animateTo(1f, tween(OPEN_MILLIS, easing = LinearEasing))
        } else {
            if (progress.value == 1f) isClosingCurve = true
            progress.animateTo(0f, tween(CLOSE_MILLIS, easing = LinearEasing))
            isShown = false
        }
    }
    val ease = { easing: Easing, fraction: Float -> if (isClosingCurve) 1f - easing.transform(1f - fraction) else easing.transform(fraction) }
    if (!isShown) return
    // Where the card went: which corner it blooms from. Written while placing the popup, read only while drawing.
    val placement = remember { mutableStateOf(MenuPlacement()) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        with(density) { MenuPositionProvider(MenuGap.roundToPx(), MenuShadowRoom.roundToPx(), MenuScreenMargin.roundToPx(), placement) }
    }
    val tint = MaterialTheme.extendedColors.glass.copy(alpha = MENU_TINT_ALPHA)
    val edge = MaterialTheme.extendedColors.menuEdge
    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismissRequest, properties = MenuPopupProperties) {
        Box(Modifier.padding(MenuShadowRoom)) {
            Column(
                modifier = modifier
                    .graphicsLayer {
                        val card = ease(CardEasing, phase(progress.value, 0f, CARD_END))
                        shape = bloomShape(card, placement.value)
                        clip = true
                        // A quick fade at the very start, so the small corner doesn't pop in.
                        alpha = (card * 4f).coerceAtMost(1f)
                        shadowElevation = MenuElevation.toPx()
                        ambientShadowColor = GlassDefaults.Shadow
                        spotShadowColor = GlassDefaults.Shadow
                    }
                    .drawBehind { drawRect(tint) }
                    .drawWithContent {
                        drawContent()
                        val card = ease(CardEasing, phase(progress.value, 0f, CARD_END))
                        // Twice the edge: the clip keeps its inner half.
                        drawOutline(bloomShape(card, placement.value).createOutline(size, layoutDirection, this), edge, style = Stroke(GlassDefaults.Edge.toPx() * 2))
                    }
                    .width(IntrinsicSize.Max)
                    .widthIn(min = MenuMinWidth)
                    // A height limit of its own: on iOS a popup is now and then measured with no height limit, and a
                    // scrolling column then throws ("measured with an infinity maximum height").
                    .heightIn(max = MenuMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                actions.forEachIndexed { index, action ->
                    MenuRow(
                        action = action,
                        // The first and last rows reach the card's edge, so their ripple fills its corners; inside, they
                        // keep their icon as far from that edge as from the side.
                        edgeInset = PaddingValues(top = if (index == 0) RowEdgeInset else 0.dp, bottom = if (index == actions.lastIndex) RowEdgeInset else 0.dp),
                        // Not disabled while it closes: that would cut the ripple of the tap that closed it.
                        onClick = {
                            if (isOpen) {
                                action.onClick()
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            val place = placement.value
                            // Nearest the button first: the top row below it, the bottom row above it.
                            val order = if (place.isAbove) actions.lastIndex - index else index
                            val stagger = if (actions.size > 1) min(MAX_ROW_STAGGER, ROWS_SPREAD / actions.lastIndex) else 0f
                            val start = ROWS_START + order * stagger
                            val row = ease(RowEasing, phase(progress.value, start, start + ROW_LENGTH))
                            alpha = row
                            translationX = (1f - row) * RowSlide.toPx() * if (place.isRight) 1f else -1f
                        },
                    )
                }
            }
        }
    }
}

/** One entry: edge to edge, so its ripple fills the card's width and, first or last, its rounded corners. */
@Composable
private fun MenuRow(action: DropdownAction, edgeInset: PaddingValues, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val accent = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(edgeInset)
            .heightIn(min = MenuRowHeight)
            .padding(horizontal = RowSidePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MenuRowGap),
    ) {
        if (action.icon != null) {
            Box(
                modifier = Modifier.size(IconTileSize).clip(IconTileShape).background(accent.copy(alpha = ICON_TILE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = accent, modifier = Modifier.size(MenuIconSize))
            }
        }
        Text(text = action.label, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        when {
            action.badge != null -> MenuBadge(action.badge)
            action.selected -> Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Which corner the card grew from: the button's side (right or left) and whether it opened upwards. */
@Immutable
private data class MenuPlacement(val isRight: Boolean = true, val isAbove: Boolean = false)

/**
 * Below the anchor, or above it when it doesn't fit below; its right edge on the anchor's right on the screen's right
 * half, else its left edge on the anchor's left; kept [margin] inside the window. [room] is the popup's padding around
 * the card for its shadow, so the card itself is what's lined up.
 */
private class MenuPositionProvider(
    private val gap: Int,
    private val room: Int,
    private val margin: Int,
    private val placement: MutableState<MenuPlacement>,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val width = popupContentSize.width - 2 * room
        val height = popupContentSize.height - 2 * room
        val isRight = anchorBounds.center.x > windowSize.width / 2
        val left = if (isRight) anchorBounds.right - width else anchorBounds.left
        val below = anchorBounds.bottom + gap
        val roomBelow = windowSize.height - margin - anchorBounds.bottom
        val roomAbove = anchorBounds.top - margin
        val isAbove = below + height > windowSize.height - margin && roomAbove > roomBelow
        val top = if (isAbove) anchorBounds.top - gap - height else below
        placement.value = MenuPlacement(isRight, isAbove)
        return IntOffset(
            x = left.coerceIn(margin, max(margin, windowSize.width - margin - width)) - room,
            y = top.coerceIn(margin, max(margin, windowSize.height - margin - height)) - room,
        )
    }
}

/** The card at [progress] of its bloom: a rounded rectangle growing out of the corner [placement] names. */
private fun Density.bloomShape(progress: Float, placement: MenuPlacement): Shape =
    BloomShape(progress, placement, MenuBloomStart.toPx(), MenuCorner.toPx())

private data class BloomShape(val progress: Float, val placement: MenuPlacement, val startPx: Float, val radiusPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = lerp(min(startPx, size.width), size.width, progress)
        val height = lerp(min(startPx, size.height), size.height, progress)
        val left = if (placement.isRight) size.width - width else 0f
        val top = if (placement.isAbove) size.height - height else 0f
        val radius = min(radiusPx, min(width, height) / 2f)
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(radius)))
    }
}

/** How far [progress] is through the part of the motion from [start] to [end], 0 before it and 1 after. */
private fun phase(progress: Float, start: Float, end: Float) = ((progress - start) / (end - start)).coerceIn(0f, 1f)

@Composable
private fun MenuBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall / 2),
    )
}

private val MenuMinWidth = 200.dp
private val MenuMaxHeight = 560.dp
private val MenuRowHeight = 52.dp
private val MenuRowGap = 12.dp
private val MenuIconSize = 20.dp
private val IconTileSize = 34.dp
private val IconTileShape = RoundedCornerShape(12.dp)
private const val ICON_TILE_ALPHA = 0.12f
private val MenuCorner = 20.dp
/** The card's size as it starts to bloom out of its corner: about the button's. */
private val MenuBloomStart = 40.dp
private val MenuElevation = 6.dp
/** Room around the card inside the popup, so its shadow is never cut by the popup's window. */
private val MenuShadowRoom = 16.dp
private val MenuGap = 10.dp
private val MenuScreenMargin = 16.dp
private val RowSlide = 14.dp
private val RowSidePadding = 16.dp
/** Extra room above the first row and below the last, so their icon tile sits as far from the card's edge as from its side. */
private val RowEdgeInset = RowSidePadding - (MenuRowHeight - IconTileSize) / 2
/** Nearly opaque, a little more than the map's glass (Shahin, 2026-11-22). */
private const val MENU_TINT_ALPHA = 0.98f

// The position provider keeps the card inside the window; the shadow's room may reach past its edge, so the window
// isn't pushed back in (that would move the card off its anchor).
private val MenuPopupProperties = PopupProperties(focusable = true, clippingEnabled = false)

// The motion, as parts of one progress from 0 (closed) to 1 (open), run over OPEN_MILLIS or, back, CLOSE_MILLIS:
// the card blooms in the first CARD_END; each row takes ROW_LENGTH, from ROWS_START, the next one a stagger later.
private const val OPEN_MILLIS = 400
private const val CLOSE_MILLIS = 300
private const val CARD_END = 0.7f
private const val ROWS_START = 0.3f
private const val ROW_LENGTH = 0.5f
private const val ROWS_SPREAD = 0.2f
private const val MAX_ROW_STAGGER = 0.075f
private val CardEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val RowEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val previewActions = listOf(
    DropdownAction("Trip members · 4", icon = Icons.Outlined.Group, badge = "2", onClick = {}),
    DropdownAction("Rename trip", icon = Icons.Outlined.Edit, onClick = {}),
    DropdownAction("Change dates", icon = Icons.Outlined.DateRange, selected = true, onClick = {}),
    DropdownAction("Delete trip", isDestructive = true, icon = Icons.Outlined.Delete, onClick = {}),
)

@Preview
@Composable
private fun AppDropdownMenuPreview() {
    TripinglyTheme { AppDropdownMenu(expanded = true, onDismissRequest = {}, actions = previewActions) }
}

@Preview
@Composable
private fun AppDropdownMenuDarkPreview() {
    TripinglyTheme(darkTheme = true) { AppDropdownMenu(expanded = true, onDismissRequest = {}, actions = previewActions) }
}
