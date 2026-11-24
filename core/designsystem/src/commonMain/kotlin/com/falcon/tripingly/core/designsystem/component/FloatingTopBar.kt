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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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
 * [isSearchOpen] says so, so a page can open and close it) blooms out of the search button at the top end and back
 * into it ([Bloom]), or cross-fades to the new page's field.
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
                // that space got centred and pushed the title's first letter out of the bar. No clip of its own: the
                // titles roll out to the pill's edge and the pill's shape cuts them (Shahin's B, 2026-11-23); on a bar
                // with search, the field below is drawn later, so a title coming up passes under it.
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { TitleIn togetherWith TitleOut using null },
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
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
            // The page whose field shows. The row blooms out of the search button and back into it (Shahin, 2026-11-24),
            // the bar's height gliding with it; while it shows, another page's field cross-fades in. The last field stays
            // while the row goes back into the button.
            val open = page.takeIf { search(it) != null && isSearchOpen(it) }
            var lastOpen by remember { mutableStateOf(open) }
            if (open != null) lastOpen = open
            Bloom(visible = open != null, cornerRadius = SearchRowRadius, padding = PaddingValues(top = MaterialTheme.spacing.small)) {
                AnimatedContent(
                    targetState = lastOpen,
                    transitionSpec = { fadeIn(TopBarDefaults.Fade) togetherWith fadeOut(TopBarDefaults.Fade) using null },
                ) { shown -> shown?.let(search)?.invoke() }
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

/**
 * The new title rolls up from below while the old one rolls out upwards, together, as one strip. They travel one and a
 * half title heights: from the middle of the row past the pill's edge (half the pill plus half the title), so the pill
 * cuts them, not a line inside it.
 */
private val TitleRoll = tween<IntOffset>(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
private val TitleIn = slideInVertically(TitleRoll) { it * 3 / 2 } + fadeIn(tween(300))
private val TitleOut = slideOutVertically(TitleRoll) { -it * 3 / 2 } + fadeOut(tween(200))

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** The search row's corners while it blooms: the field's own, a pill as high as the row. */
private val SearchRowRadius = 24.dp

/**
 * The pill's search half folds into its right edge (towards Settings) and unfolds from it. Tapped, the icon shrinks
 * down out of its bottom, into the search row opening below; when the search closes it comes back up out of the
 * bottom, as if from the search row (Shahin, 2026-11-24). Not clipped, so it can go below the pill.
 */
private val BottomMiddle = TransformOrigin(0.5f, 1f)
private val SearchButtonIn = expandHorizontally(tween(300, easing = Emphasized), expandFrom = Alignment.End, clip = false) +
    scaleIn(tween(300, easing = Emphasized), transformOrigin = BottomMiddle) +
    slideInVertically(tween(300, easing = Emphasized)) { it / 3 } + fadeIn(tween(220))
private val SearchButtonOut = shrinkHorizontally(tween(300, easing = Emphasized), shrinkTowards = Alignment.End, clip = false) +
    scaleOut(tween(300, easing = Emphasized), transformOrigin = BottomMiddle) +
    slideOutVertically(tween(300, easing = Emphasized)) { it / 3 } + fadeOut(tween(150))

@Composable
private fun FloatingTopBarPreviewContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            FloatingTopBar(title = "My Trips", actions = { SearchSettingsPill(onSearch = null, onSettings = {}) }, search = {
                AppTextField(rememberTextFieldState(), "Search trips…", Modifier.fillMaxWidth(), style = AppTextFieldStyle.Search)
            })
            FloatingTopBar(title = "Profile", actions = { SearchSettingsPill(onSearch = {}, onSettings = {}) })
            FloatingTopBar(title = "Search", actions = { SearchSettingsPill(onSearch = null, onSettings = {}) })
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
