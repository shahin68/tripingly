package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The pictures of the full-screen states, one SVG per theme in `composeResources/files/illustrations/`
 * (`<file>_light.svg`, `<file>_dark.svg`), drawn in Shahin's line style A (2026-11-23). The files keep to paths,
 * circles, rects, strokes and dashes (no text, filters, masks, gradients or CSS), so Coil's SVG decoder draws them the
 * same on Android (AndroidSVG) and iOS (Skia). New pictures follow the same rules and the same 200 × 160 viewBox.
 */
enum class Illustration(internal val file: String) {
    ExploreEmpty("explore_empty"),
    TripsEmpty("trips_empty"),
    SearchEmpty("search_empty"),
    NearbyEmpty("nearby_empty"),
    LocationAsk("location_ask"),
    Error("error"),
    NotificationsEmpty("notifications_empty"),
    ProfileMissing("profile_missing"),
}

/** [illustration] in the app's theme (not only the phone's), at its own size. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun Illustration(illustration: Illustration, modifier: Modifier = Modifier) {
    val theme = if (MaterialTheme.extendedColors.isDark) "dark" else "light"
    AsyncImage(
        model = Res.getUri("files/illustrations/${illustration.file}_$theme.svg"),
        contentDescription = null,
        modifier = modifier.size(IllustrationWidth, IllustrationHeight),
    )
}

/**
 * Nothing to show: an [illustration] over a [title] and an optional [message], centred in the room it's given (a list
 * places it with `fillParentMaxSize()`, so pull-to-refresh still works). With a bounded room wider than tall
 * (landscape) the picture stands beside the words, as in [ErrorScreen], and takes the whole height: landscape has
 * little of it, and a margin above and below shrank the picture (Shahin, 2026-11-24).
 */
@Composable
fun EmptyState(illustration: Illustration, title: String, modifier: Modifier = Modifier, message: String? = null) {
    BoxWithConstraints(modifier.padding(horizontal = MaterialTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
        if (constraints.hasBoundedHeight && maxWidth > maxHeight) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)) {
                Illustration(illustration)
                EmptyWords(title, message, Modifier.weight(1f, fill = false))
            }
        } else {
            Column(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Illustration(illustration)
                EmptyWords(title, message)
            }
        }
    }
}

@Composable
private fun EmptyWords(title: String, message: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val IllustrationWidth = 200.dp
private val IllustrationHeight = 160.dp

@Preview
@Composable
private fun EmptyStatePreview() {
    TripinglyTheme { EmptyState(Illustration.ExploreEmpty, "No trips here yet", message = "Try another period, or come back a little later.") }
}

@Preview
@Composable
private fun EmptyStateDarkPreview() {
    TripinglyTheme(darkTheme = true) { EmptyState(Illustration.ExploreEmpty, "No trips here yet", message = "Try another period, or come back a little later.") }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun EmptyStateLandscapePreview() {
    TripinglyTheme { EmptyState(Illustration.TripsEmpty, "No trips yet", message = "Plan your first one with the + button.") }
}
