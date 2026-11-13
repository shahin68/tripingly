package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.AppTextFieldStyle
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.JoinedButtonDefaults
import com.falcon.tripingly.core.designsystem.component.LocalScreenOnTop
import com.falcon.tripingly.core.designsystem.component.SearchKeyboard
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_route_close
import com.falcon.tripingly.feature.map.generated.resources.map_route_from
import com.falcon.tripingly.feature.map.generated.resources.map_route_to
import com.falcon.tripingly.feature.map.generated.resources.map_route_to_placeholder
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.RouteBarState
import com.falcon.tripingly.feature.map.presentation.map.SearchState
import org.jetbrains.compose.resources.stringResource

/**
 * The header while routing between two points (design: the routing scenarios of the pin card canvas, Shahin,
 * 2026-11-12): where from, by the start's blue ring, and where to, by a flag: the route's other end, the search field
 * ([search] open, the keyboard up right after Route from), or "Search or tap a pin", which opens it. ✕ and back close
 * the route and the start. Over the native map, so no blur.
 */
@Composable
internal fun RouteBar(bar: RouteBarState, search: SearchState?, onAction: (MapAction) -> Unit, modifier: Modifier = Modifier) {
    @OptIn(ExperimentalComposeUiApi::class)
    BackHandler(enabled = LocalScreenOnTop.current) { onAction(MapAction.PinRoute.Close) }
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        shape = MaterialTheme.shapes.extraLarge,
        blur = false,
    ) {
        // The ring and ✕'s icon sit the same distance in from the bar's two sides.
        Row(Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RouteEnd(Icons.Outlined.TripOrigin, MapColors.Car, stringResource(Res.string.map_route_from)) { EndName(bar.from.asString()) }
                HorizontalDivider(Modifier.padding(start = JoinedButtonDefaults.IconSize + MaterialTheme.spacing.medium), color = MaterialTheme.colorScheme.outlineVariant)
                RouteEnd(Icons.Outlined.Flag, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(Res.string.map_route_to)) {
                    when {
                        bar.to != null -> EndName(bar.to.asString())
                        search != null -> ToField(search.query, search.isSearching, onAction)
                        else -> Text(
                            stringResource(Res.string.map_route_to_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { onAction(MapAction.Search.Open) },
                        )
                    }
                }
            }
            IconButton(onClick = { onAction(MapAction.PinRoute.Close) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_route_close))
            }
        }
    }
}

/** One end's row: its [icon] (read out as [label]) and [content], as high as a touch target. */
@Composable
private fun RouteEnd(icon: ImageVector, tint: Color, label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(JoinedButtonDefaults.Height),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(JoinedButtonDefaults.IconSize))
        Box(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically, content = content) }
    }
}

@Composable
private fun EndName(name: String) {
    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Where to: searches like the header's search, with the keyboard up as it opens. */
@Composable
private fun ToField(query: TextFieldState, isSearching: Boolean, onAction: (MapAction) -> Unit) {
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

@Composable
private fun RouteBarPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        RouteBar(RouteBarState(UiText.Server("Café Central"), to = null), search = null, onAction = {})
        RouteBar(RouteBarState(UiText.Server("Café Central"), UiText.Server("Musée national des Arts asiatiques – Guimet")), search = null, onAction = {})
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
