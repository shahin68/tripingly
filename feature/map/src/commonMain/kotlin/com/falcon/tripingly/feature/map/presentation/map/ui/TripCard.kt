package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_brand
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_dates
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_days
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_holder
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_issued
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_likes
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_members
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_name
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_no_dates
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_private
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_public
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_saves
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_stops
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_title
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_visibility
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreviewStates
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The trip card over the map, grown out of the title pill at [from] (its bounds on the screen) as a shared element,
 * and shrunk back into it when closed: a tap outside it, back, or the owner's photo (which then opens their
 * profile, [onOwnerClick]). [onDismiss] once it is back on the pill. While it shows, the header hides its pill.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TripCardDialog(header: HeaderState, from: Rect, onOwnerClick: () -> Unit, onDismiss: () -> Unit) {
    // Where the dialog starts on the screen, so the pill's copy lands on the pill; nothing shows until it's known.
    var origin by remember { mutableStateOf<Offset?>(null) }
    var isOpen by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    val transition = updateTransition(isOpen, label = "trip card")
    LaunchedEffect(origin) { if (origin != null) isOpen = true }
    // Back on the pill after being open: the dialog goes and the real pill shows again.
    LaunchedEffect(transition.currentState) { if (transition.currentState) hasOpened = true else if (hasOpened) onDismiss() }

    Dialog(onDismissRequest = { isOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SharedTransitionLayout(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
                .graphicsLayer { alpha = if (origin == null) 0f else 1f },
        ) {
            transition.AnimatedContent { open ->
                val shared = Modifier.sharedBounds(rememberSharedContentState(TRIP_CARD_KEY), this@AnimatedContent)
                if (open) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(interactionSource = null, indication = null) { isOpen = false }
                            .safeDrawingPadding()
                            .padding(MaterialTheme.spacing.large),
                        contentAlignment = Alignment.Center,
                    ) {
                        TripCard(
                            header = header,
                            onOwnerClick = {
                                isOpen = false
                                onOwnerClick()
                            },
                            // A tap on the card itself keeps it open; a long name scrolls in landscape.
                            modifier = shared
                                .widthIn(max = TripCardMaxWidth)
                                .clickable(interactionSource = null, indication = null) {}
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    val start = origin ?: Offset.Zero
                    val size = with(LocalDensity.current) { from.width.toDp() to from.height.toDp() }
                    Box(Modifier.fillMaxSize()) {
                        TripTitle(
                            header = header,
                            isInteractive = false,
                            modifier = Modifier
                                .offset { IntOffset((from.left - start.x).roundToInt(), (from.top - start.y).roundToInt()) }
                                .size(size.first, size.second)
                                .then(shared),
                        )
                    }
                }
            }
        }
    }
}

private const val TRIP_CARD_KEY = "trip-card"

private val TripCardMaxWidth = 420.dp

/**
 * The trip as a residence permit: the issuing band, the owner's photo (opening their profile) beside the whole
 * name and the holder, the dates and counts in labelled fields, and a machine-readable zone at the bottom.
 */
@Composable
internal fun TripCard(header: HeaderState, onOwnerClick: () -> Unit, modifier: Modifier = Modifier) {
    val card = header.card
    val owner = header.owner
    val pattern = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.drawBehind {
                // The fine rings printed on permits, behind everything.
                val center = Offset(size.width * 0.85f, size.height * 0.45f)
                repeat(12) { ring -> drawCircle(pattern, radius = 24.dp.toPx() + ring * 14.dp.toPx(), center = center, style = Stroke(1.dp.toPx())) }
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.map_trip_card_brand).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(Res.string.map_trip_card_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), letterSpacing = 1.sp)
            }
            Column(modifier = Modifier.padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    if (owner != null) {
                        val profile = stringResource(Res.string.map_owner_profile, owner.username)
                        AvatarCircle(
                            user = owner,
                            size = OwnerPhotoSize,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onOwnerClick).semantics { contentDescription = profile },
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                        Field(stringResource(Res.string.map_trip_card_name), header.tripName, isMain = true)
                        owner?.let { Field(stringResource(Res.string.map_trip_card_holder), "${it.displayName}\n@${it.username}") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    Field(stringResource(Res.string.map_trip_card_dates), card.dates ?: stringResource(Res.string.map_trip_card_no_dates), Modifier.weight(2f))
                    Field(stringResource(Res.string.map_trip_card_issued), card.created, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    Field(stringResource(Res.string.map_trip_card_days), card.dayCount.toString(), Modifier.weight(1f))
                    Field(stringResource(Res.string.map_trip_card_stops), card.stopCount.toString(), Modifier.weight(1f))
                    Field(stringResource(Res.string.map_trip_card_members), card.memberCount.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                    Field(stringResource(Res.string.map_trip_card_likes), compactCount(card.likeCount), Modifier.weight(1f))
                    Field(stringResource(Res.string.map_trip_card_saves), compactCount(card.saveCount), Modifier.weight(1f))
                    Field(
                        stringResource(Res.string.map_trip_card_visibility),
                        stringResource(if (card.isPublic) Res.string.map_trip_card_public else Res.string.map_trip_card_private),
                        Modifier.weight(1f),
                    )
                }
            }
            // Decoration only, like the code lines at the bottom of a permit: screen readers skip it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    .clearAndSetSemantics {},
            ) {
                machineZone(header).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** A label in small capitals over its value; the trip's name ([isMain]) larger, never cut. */
@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, isMain: Boolean = false) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
        Text(value, style = if (isMain) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Two permit-style code lines from the trip: its name, then the owner and the counts, filled with `<`. */
private fun machineZone(header: HeaderState): List<String> {
    fun code(text: String) = text.uppercase().map { if (it.isLetterOrDigit()) it else '<' }.joinToString("")
    val card = header.card
    return listOf(
        "TRP<" + code(header.tripName),
        code(header.owner?.username.orEmpty()) + "<<" + "${card.dayCount}D<${card.stopCount}S<${card.memberCount}M",
    ).map { it.padEnd(MACHINE_LINE_LENGTH, '<') }
}

/** Longer than any card is wide: the lines run to the edge and are cut there. */
private const val MACHINE_LINE_LENGTH = 60

private val OwnerPhotoSize = 88.dp

@Composable
private fun TripCardPreviewContent() {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
        TripCard(header = MapHeaderPreviewStates().values.elementAt(1).header, onOwnerClick = {})
    }
}

@Preview
@Composable
private fun TripCardPreview() {
    TripinglyTheme { TripCardPreviewContent() }
}

@Preview
@Composable
private fun TripCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { TripCardPreviewContent() }
}
