package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.LocalScreenOnTop
import com.falcon.tripingly.core.designsystem.component.SearchKeyboard
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_route_change_from
import com.falcon.tripingly.feature.map.generated.resources.map_route_change_to
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_route_from
import com.falcon.tripingly.feature.map.generated.resources.map_route_swap
import com.falcon.tripingly.feature.map.generated.resources.map_route_to
import com.falcon.tripingly.feature.map.generated.resources.map_route_to_placeholder
import com.falcon.tripingly.feature.map.presentation.component.lineColor
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.RouteBarState
import com.falcon.tripingly.feature.map.presentation.map.SearchState
import org.jetbrains.compose.resources.stringResource

/**
 * The header while routing between two points (design: round 3's routing header on the pin card canvas, Shahin,
 * 2026-11-13): grown out of the title pill like the trip card ([PillGrowthPanel]), pushing the header's buttons out.
 * Two pills: where from, by its start mark, with ✕ beside it (✕ and back close the route and the start); where to, by a
 * flag, with ⇅ beside it once both ends are set, which switches them (the names glide past each other). The marks take
 * the way of travelling's colour, like the route. A tap on a set end's pill picks it again (Shahin, 2026-11-13): its
 * pill becomes the search field ([search] open, the keyboard up, outlined while it's typed in), or "Search or tap a
 * pin", which opens it. Every pill and button is the same small spacing apart and in from the bar's edges.
 */
@Composable
internal fun RouteBar(
    bar: RouteBarState,
    search: SearchState?,
    transition: Transition<Boolean>,
    progress: () -> Float,
    pill: Rect,
    pillContent: @Composable () -> Unit,
    onAction: (MapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    @OptIn(ExperimentalComposeUiApi::class)
    BackHandler(enabled = transition.targetState && LocalScreenOnTop.current) { onAction(MapAction.PinRoute.Close) }
    val color = bar.mode.lineColor
    // The names glide to each other's pill when the ends switch; the gap they cross is a pill and the space between.
    val swapDistance = with(LocalDensity.current) { (RouteHeight + MaterialTheme.spacing.small).roundToPx() }
    PillGrowthPanel(transition, progress, pill, pillContent, modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            RouteRow(transition.rowMotion(0), RouteIcon(Icons.Default.Close, stringResource(Res.string.map_route_close)) { onAction(MapAction.PinRoute.Close) }) {
                RouteEnd(
                    icon = Icons.Outlined.TripOrigin,
                    tint = color,
                    label = stringResource(Res.string.map_route_from),
                    bar = bar,
                    name = { it.from },
                    swapDistance = swapDistance,
                    search = search,
                    editLabel = stringResource(Res.string.map_route_change_from),
                    onEdit = { onAction(MapAction.PinRoute.EditEnd(isFrom = true)) },
                    onAction = onAction,
                )
            }
            val swap = RouteIcon(Icons.Default.SwapVert, stringResource(Res.string.map_route_swap)) { onAction(MapAction.PinRoute.Swap) }
            RouteRow(transition.rowMotion(1), swap.takeIf { bar.from != null && bar.to != null }) {
                RouteEnd(
                    icon = Icons.Outlined.Flag,
                    tint = color,
                    label = stringResource(Res.string.map_route_to),
                    bar = bar,
                    name = { it.to },
                    swapDistance = -swapDistance,
                    search = search,
                    editLabel = stringResource(Res.string.map_route_change_to),
                    onEdit = { onAction(MapAction.PinRoute.EditEnd(isFrom = false)) },
                    onAction = onAction,
                )
            }
        }
    }
}

/** A round button beside an end. */
private data class RouteIcon(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** An end's pill and its button; without one ([button] null) its place stays empty, so both pills are as wide. */
@Composable
private fun RouteRow(modifier: Modifier, button: RouteIcon?, end: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { end() }
        if (button == null) {
            Spacer(Modifier.size(RouteHeight))
        } else {
            FilledTonalIconButton(
                onClick = button.onClick,
                modifier = Modifier.size(RouteHeight),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(button.icon, contentDescription = button.label)
            }
        }
    }
}

/**
 * One end's pill: its [icon] (read out as [label]) as far in as a round button's, then the end's [name] in [bar] (a tap
 * picks it again: [onEdit]), or while it's being picked the search field or "Search or tap a pin"; outlined in [tint]
 * while its field is typed in. When the ends switch, the names glide [swapDistance] px (down, or up when negative):
 * the name leaving goes to the other pill and the one coming arrives from it, so the two names cross on the way.
 */
@Composable
private fun RouteEnd(
    icon: ImageVector,
    tint: Color,
    label: String,
    bar: RouteBarState,
    name: (RouteBarState) -> UiText?,
    swapDistance: Int,
    search: SearchState?,
    editLabel: String,
    onEdit: () -> Unit,
    onAction: (MapAction) -> Unit,
) {
    val isPicked = name(bar) == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RouteHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .then(if (isPicked && search != null) Modifier.border(FocusWidth, tint, CircleShape) else Modifier)
            .padding(start = (RouteHeight - JoinedButtonDefaults.IconSize) / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
        AnimatedContent(
            targetState = bar,
            contentKey = { name(it) },
            transitionSpec = {
                val isSwap = initialState.from != null && initialState.from == targetState.to && initialState.to == targetState.from
                if (isSwap) {
                    // No clipping: the name leaving slides into the other pill as the other's name slides into this one.
                    slideInVertically(tween(SWAP_MILLIS)) { swapDistance } togetherWith slideOutVertically(tween(SWAP_MILLIS)) { swapDistance } using null
                } else {
                    fadeIn(tween(SWAP_MILLIS)) togetherWith fadeOut(tween(SWAP_MILLIS)) using null
                }
            },
            modifier = Modifier.weight(1f),
            label = "route end",
        ) { shown ->
            val text = name(shown)
            when {
                text != null -> EndName(text.asString(), Modifier.clickable(onClickLabel = editLabel, onClick = onEdit))
                search != null -> EndField(search.query, search.isSearching, onAction)
                else -> Text(
                    stringResource(Res.string.map_route_to_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { onAction(MapAction.Search.Open) }.padding(horizontal = MaterialTheme.spacing.medium),
                )
            }
        }
    }
}

/** An end's name, where the field's text would start, the whole pill's width (its tap target). */
@Composable
private fun EndName(name: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(RouteHeight), contentAlignment = Alignment.CenterStart) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        )
    }
}

/** The end being picked: searches like the header's search, with the keyboard up as it opens. */
@Composable
private fun EndField(query: TextFieldState, isSearching: Boolean, onAction: (MapAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AppTextField(
        state = query,
        placeholder = stringResource(Res.string.map_route_to_placeholder),
        onTextChange = { onAction(MapAction.Search.QueryChanged(it)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        style = AppTextFieldStyle.Inline,
        trailing = if (isSearching) {
            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else {
            null
        },
        keyboardOptions = SearchKeyboard,
    )
}

/** Every pill and button's height: a touch target, as tall as the map's glass buttons. */
private val RouteHeight = GlassButtonDefaults.Size
private val FocusWidth = 1.dp

/** How long the names take to switch pills. */
private const val SWAP_MILLIS = 220

@Composable
private fun RouteBarPreviewContent() {
    val open = rememberTransition(remember { MutableTransitionState(true) })
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        RouteBar(RouteBarState(UiText.Server("Café Central"), to = null), search = null, open, { 1f }, Rect.Zero, {}, onAction = {})
        RouteBar(RouteBarState(UiText.Server("Café Central"), UiText.Server("Musée national des Arts asiatiques – Guimet"), TravelMode.Cycling), search = null, open, { 1f }, Rect.Zero, {}, onAction = {})
        RouteBar(RouteBarState(from = null, UiText.Server("Prater"), TravelMode.Driving), search = null, open, { 1f }, Rect.Zero, {}, onAction = {})
    }
}

@Preview(widthDp = 392)
@Composable
private fun RouteBarPreview() {
    TripinglyTheme { RouteBarPreviewContent() }
}

@Preview(widthDp = 392)
@Composable
private fun RouteBarDarkPreview() {
    TripinglyTheme(darkTheme = true) { RouteBarPreviewContent() }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun RouteBarLandscapePreview() {
    TripinglyTheme { Box(Modifier.padding(start = 400.dp)) { RouteBarPreviewContent() } }
}
