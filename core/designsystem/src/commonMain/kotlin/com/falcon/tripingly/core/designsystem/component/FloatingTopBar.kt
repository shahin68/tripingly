package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_back
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
 * below, the page's own [pageActions] cross-fade (the shared [actions] stay put), and the [search] row grows,
 * shrinks, or cross-fades to the new page's field.
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
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { TitleIn togetherWith TitleOut using SizeTransform(clip = false) },
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                ) { shown ->
                    Text(
                        text = title(shown),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(TopBarDefaults.Fade) togetherWith fadeOut(TopBarDefaults.Fade) using SizeTransform(clip = false) },
                    contentAlignment = Alignment.CenterEnd,
                ) { shown -> Row(verticalAlignment = Alignment.CenterVertically) { pageActions(shown) } }
                actions()
            }
            // A page without search shows no row at all; between two pages with search the fields cross-fade.
            AnimatedContent(
                targetState = page,
                contentKey = { if (search(it) == null) null else it },
                transitionSpec = { fadeIn(TopBarDefaults.Fade) togetherWith fadeOut(TopBarDefaults.Fade) using SizeTransform(clip = true) },
            ) { shown ->
                search(shown)?.let { field -> Box(Modifier.padding(top = MaterialTheme.spacing.small)) { field() } }
            }
        }
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

/** The new title comes up from below while the old one leaves upwards. */
private val TitleIn = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))
private val TitleOut = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200))

@Composable
private fun FloatingTopBarPreviewContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            FloatingTopBar(title = "My Trips", actions = { SettingsAction({}) }, search = {
                AppTextField(rememberTextFieldState(), "Search trips…", Modifier.fillMaxWidth(), style = AppTextFieldStyle.Search)
            })
            FloatingTopBar(title = "Nearby", actions = { SettingsAction({}) })
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
