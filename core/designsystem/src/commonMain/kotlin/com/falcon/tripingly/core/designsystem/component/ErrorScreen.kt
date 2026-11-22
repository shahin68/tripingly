package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.error_message
import com.falcon.tripingly.core.designsystem.generated.resources.error_retry
import com.falcon.tripingly.core.designsystem.generated.resources.error_title
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A whole screen (or sheet) that couldn't load: an [illustration], a [title], a [message] and a retry button.
 * Every part has a default, so `ErrorScreen(onRetry = viewModel::retry)` is enough; screens pass their own
 * words or picture where they say more. [contentPadding] keeps it clear of floating bars.
 */
@Composable
fun ErrorScreen(
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.error_title),
    message: String = stringResource(Res.string.error_message),
    retryLabel: String = stringResource(Res.string.error_retry),
    contentPadding: PaddingValues = PaddingValues(),
    illustration: @Composable () -> Unit = { ErrorIllustration() },
) {
    // Edge to edge (Shahin, 2026-11-22): the scroll fills the screen under the bars, [contentPadding] is inside it, and the
    // content is at least as big as the screen both ways, so it's centred in the room between the bars. In landscape the
    // picture stands beside the words, so it fits between the bars. In a sheet with no height limit it just wraps its content.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val isBounded = constraints.hasBoundedHeight
        val isLandscape = isBounded && maxWidth > maxHeight
        Box(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .sizeIn(minWidth = maxWidth, minHeight = if (isBounded) maxHeight else 0.dp)
                .padding(contentPadding)
                .padding(horizontal = MaterialTheme.spacing.extraLarge, vertical = MaterialTheme.spacing.large),
            contentAlignment = Alignment.Center,
        ) {
            if (isLandscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                ) {
                    illustration()
                    ErrorWords(title, message, retryLabel, onRetry, Modifier.weight(1f, fill = false))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    illustration()
                    Spacer(Modifier.height(MaterialTheme.spacing.medium))
                    ErrorWords(title, message, retryLabel, onRetry)
                }
            }
        }
    }
}

/** The title, the message and the retry button of [ErrorScreen], centred. */
@Composable
private fun ErrorWords(title: String, message: String, retryLabel: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        // Changes only with an answer (a retry leaves the screen as it is), and then animates (Shahin, 2026-11-22).
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform() },
            label = "error message",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            PrimaryButton(text = retryLabel, onClick = onRetry, size = ButtonSize.Medium)
        }
    }
}

/**
 * The default picture of [ErrorScreen]: a folded map with a broken dotted route and a small cross, drawn in the
 * theme's colors so it works in light and dark. A placeholder that can be swapped for a designed one later.
 */
@Composable
fun ErrorIllustration(modifier: Modifier = Modifier) {
    val wash = MaterialTheme.colorScheme.primaryContainer
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(IllustrationSize)) {
        val u = size.minDimension / 168f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val line = Stroke(width = 2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(wash, radius = 80f * u, center = center)
        val map = Path().apply {
            moveTo(40f * u, 58f * u); lineTo(68f * u, 48f * u); lineTo(100f * u, 58f * u); lineTo(128f * u, 48f * u)
            lineTo(128f * u, 110f * u); lineTo(100f * u, 120f * u); lineTo(68f * u, 110f * u); lineTo(40f * u, 120f * u); close()
        }
        drawPath(map, paper, style = Fill)
        drawPath(map, ink, style = line)
        drawLine(ink.copy(alpha = 0.5f), p(68f, 48f), p(68f, 110f), strokeWidth = line.width, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.5f), p(100f, 58f), p(100f, 120f), strokeWidth = line.width, cap = StrokeCap.Round)
        val route = Path().apply {
            moveTo(52f * u, 92f * u)
            cubicTo(62f * u, 88f * u, 68f * u, 98f * u, 78f * u, 94f * u)
            cubicTo(88f * u, 90f * u, 92f * u, 84f * u, 102f * u, 88f * u)
            cubicTo(110f * u, 91f * u, 114f * u, 96f * u, 120f * u, 94f * u)
        }
        drawPath(
            route,
            accent,
            style = Stroke(width = 3f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * u, 7f * u))),
        )
        drawCircle(accent, radius = 13f * u, center = p(118f, 62f))
        drawLine(onAccent, p(113f, 57f), p(123f, 67f), strokeWidth = line.width, cap = StrokeCap.Round)
        drawLine(onAccent, p(123f, 57f), p(113f, 67f), strokeWidth = line.width, cap = StrokeCap.Round)
    }
}

private val IllustrationSize = 168.dp

@Composable
private fun ErrorScreenPreviewContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ErrorScreen(onRetry = {})
    }
}

@Preview
@Composable
private fun ErrorScreenPreview() {
    TripinglyTheme { ErrorScreenPreviewContent() }
}

@Preview
@Composable
private fun ErrorScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { ErrorScreenPreviewContent() }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ErrorScreenLandscapePreview() {
    TripinglyTheme { ErrorScreenPreviewContent() }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ErrorScreenLandscapeDarkPreview() {
    TripinglyTheme(darkTheme = true) { ErrorScreenPreviewContent() }
}

@Preview
@Composable
private fun ErrorScreenCustomPreview() {
    TripinglyTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ErrorScreen(onRetry = null, title = "This trip is private", message = "Only its members can see it.")
        }
    }
}
