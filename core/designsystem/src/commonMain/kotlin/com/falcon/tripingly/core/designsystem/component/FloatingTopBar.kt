package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_back
import com.falcon.tripingly.core.designsystem.generated.resources.common_search
import com.falcon.tripingly.core.designsystem.generated.resources.common_settings
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The floating glass header of a screen: a large [title] with [actions] on the right (usually [SettingsAction]),
 * an optional [navigationIcon] (usually [BackAction]) and an optional [search] field below. Use it as a
 * `Scaffold`'s `topBar` and draw the content behind it with the scaffold's padding as content padding. A new
 * title animates in like the tabs' shared bar ([AnimatedTopBar]).
 */
@Composable
fun FloatingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    search: (@Composable () -> Unit)? = null,
) {
    AnimatedTopBar(page = title, title = { it }, modifier = modifier, navigationIcon = navigationIcon, actions = actions, search = { search })
}

/**
 * The floating glass header shared by several pages (the tabs), showing what [page] asks for. When the page
 * changes it animates as on the design canvas: the old [title] slides up and fades while the new one comes in from
 * below, the page's own [pageActions] cross-fade (the shared [actions] stay put), and the [search] row (shown while
 * [isSearchOpen] says so, so a page can open and close it) grows, shrinks, or cross-fades to the new page's field. Like the trip card's content, a field coming in slides down a
 * little while it fades in, and one going away only fades, while the row grows or shrinks with one easing.
 */
@Composable
fun <T> AnimatedTopBar(
    page: T,
    title: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    pageActions: @Composable RowScope.(T) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    search: (T) -> (@Composable () -> Unit)? = { null },
    isSearchOpen: (T) -> Boolean = { true },
) {
    GlassSurface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeTopAndSides)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(TopBarDefaults.InnerPadding)) {
            Row(
                modifier = Modifier.heightIn(min = TopBarDefaults.RowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(MaterialTheme.spacing.small))
                // Always the full width the row leaves it, so its size never animates: an animated width larger than
                // that space got centred and pushed the title's first letter out of the bar. The titles roll inside
                // this box, so the old and the new one never show on top of each other.
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { TitleIn togetherWith TitleOut using null },
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f).clipToBounds(),
                ) { shown ->
                    Text(
                        text = title(shown),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(TopBarDefaults.Fade) togetherWith fadeOut(TopBarDefaults.Fade) using SizeTransform(clip = false) },
                    contentAlignment = Alignment.CenterEnd,
                ) { shown -> Row(verticalAlignment = Alignment.CenterVertically) { pageActions(shown) } }
                actions()
            }
            // The page whose field shows, or null for no row at all; a field going away keeps drawing while it fades.
            AnimatedContent(
                targetState = page.takeIf { search(it) != null && isSearchOpen(it) },
                transitionSpec = { SearchIn togetherWith SearchOut using SizeTransform(clip = true) { _, _ -> SearchResize } },
            ) { shown ->
                shown?.let(search)?.let { field -> Box(Modifier.padding(top = MaterialTheme.spacing.small)) { field() } }
            }
        }
    }
}

/**
 * The tabs' filled pill at the right of the top bar: a search button, a thin line and Settings. The search half shows
 * while [onSearch] is set (a tab with search, closed) and folds away to the right when it isn't, leaving Settings
 * round in the pill (Shahin's E1, 2026-11-08).
 */
@Composable
fun SearchSettingsPill(onSearch: (() -> Unit)?, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = onSearch != null, enter = SearchButtonIn, exit = SearchButtonOut) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSearch?.invoke() }) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.common_search))
                }
                GlassButtonDivider()
            }
        }
        SettingsAction(onSettings)
    }
}

/** The top bar's Settings button: a gear that opens Settings straight away. */
@Composable
fun SettingsAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(Res.string.common_settings))
    }
}

/** The top bar's back button. */
@Composable
fun BackAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
    }
}

private object TopBarDefaults {
    val InnerPadding = 8.dp
    val RowHeight = 48.dp
    val Fade = tween<Float>(durationMillis = 220)
}

/** The new title rolls up from below while the old one rolls out upwards, together, as one strip. */
private val TitleRoll = tween<IntOffset>(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
private val TitleIn = slideInVertically(TitleRoll) { it } + fadeIn(tween(300))
private val TitleOut = slideOutVertically(TitleRoll) { -it } + fadeOut(tween(200))

/** The search row, as the trip card's content: in, it slides down a quarter of its height and fades; out, it only fades. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val SearchResize = tween<IntSize>(durationMillis = 300, easing = Emphasized)
private val SearchIn = slideInVertically(tween(300, easing = Emphasized)) { -it / 4 } + fadeIn(tween(220))
private val SearchOut = fadeOut(tween(90))

/** The pill's search half folds into its right edge (towards Settings) and unfolds from it. */
private val SearchButtonIn = expandHorizontally(tween(300, easing = Emphasized), expandFrom = Alignment.End) +
    scaleIn(tween(300, easing = Emphasized)) + fadeIn(tween(220))
private val SearchButtonOut = shrinkHorizontally(tween(300, easing = Emphasized), shrinkTowards = Alignment.End) +
    scaleOut(tween(300, easing = Emphasized)) + fadeOut(tween(150))

@Composable
private fun FloatingTopBarPreviewContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            FloatingTopBar(title = "My Trips", actions = { SearchSettingsPill(onSearch = null, onSettings = {}) }, search = {
                AppTextField(rememberTextFieldState(), "Search trips…", Modifier.fillMaxWidth(), style = AppTextFieldStyle.Search)
            })
            FloatingTopBar(title = "Profile", actions = { SearchSettingsPill(onSearch = {}, onSettings = {}) })
            FloatingTopBar(title = "Nearby", actions = { SearchSettingsPill(onSearch = null, onSettings = {}) })
            FloatingTopBar(title = "@anna", navigationIcon = { BackAction({}) })
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun FloatingTopBarPreview() {
    TripinglyTheme { FloatingTopBarPreviewContent() }
}

@Preview
@Composable
private fun FloatingTopBarDarkPreview() {
    TripinglyTheme(darkTheme = true) { FloatingTopBarPreviewContent() }
}
