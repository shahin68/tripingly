package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.location.model.Coordinates
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What a held pin draws over the map, and what it does while held: a dotted line of small circles from the pin to a
 * handle (an arrow in a circle) that pops out a little above the finger, follows it and points
 * along the line, a ring that bounces out on the pin the handle would be let go on with its name in a tag above the handle, a tick when that pin changes and a long-press haptic when the hold
 * starts. While the finger pulls toward the map's edges the camera moves ([onPan]: px per second, and the frame's
 * seconds; zero once it stops), faster nearer the edges;
 * never in the middle of the map, near the held pin or before the finger has moved. [project] places a point on the
 * map view in px and must read the camera's state, so the line follows the camera; [pins] are the pins on screen now.
 */
@Composable
internal fun PinHoldLayer(
    holder: PinHolder,
    /** The part of the map nothing covers, px in the map view: pulling toward its edges moves the camera. */
    freeArea: Rect,
    project: (Coordinates) -> Offset?,
    pins: () -> List<ShownPin>,
    onPan: (velocity: Offset, seconds: Float) -> Unit,
) {
    val hold = holder.hold ?: return
    val density = LocalDensity.current
    val color = MaterialTheme.extendedColors.stop
    val haptics = rememberHaptics()
    val currentProject by rememberUpdatedState(project)
    val currentPins by rememberUpdatedState(pins)
    val currentOnPan by rememberUpdatedState(onPan)
    val currentFree by rememberUpdatedState(freeArea)

    // The line ends at the handle, which follows the finger; a pin the handle is on only gets a ring.
    fun lineEnd(): Offset = handleOf(hold.finger, density)

    LaunchedEffect(hold) { haptics.longPress() }
    LaunchedEffect(hold) {
        // At most one tick per TICK_GAP_MILLIS, so a quick pass over a crowd doesn't buzz.
        var lastTick: TimeSource.Monotonic.ValueTimeMark? = null
        snapshotFlow { hold.target?.end?.key }.collect {
            if (it == null || lastTick?.let { mark -> mark.elapsedNow() < TICK_GAP_MILLIS.milliseconds } == true) return@collect
            lastTick = TimeSource.Monotonic.markNow()
            haptics.tick()
        }
    }

    // The camera follows a pull toward the edges, every frame while the finger is there; it stops when the hold ends.
    LaunchedEffect(hold) {
        var last = withFrameNanos { it }
        var lastVelocity = Offset.Zero
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val pin = currentProject(hold.from.position)
            val isStill = !hold.hasMoved || pin != null && (hold.finger - pin).getDistance() < with(density) { PAN_STILL_AROUND_PIN.toPx() }
            val velocity = if (isStill) Offset.Zero else edgePanVelocity(hold.finger, currentFree, density)
            if (velocity == Offset.Zero && lastVelocity == Offset.Zero) continue
            lastVelocity = velocity
            currentOnPan(velocity, seconds)
            holder.retarget(currentPins(), density)
        }
    }
    DisposableEffect(hold) { onDispose { currentOnPan(Offset.Zero, 0f) } }

    // The pin the handle is on gets a ring that bounces out; the ring of the pin it leaves shrinks and fades meanwhile.
    val ring = remember(hold) { Animatable(0f) }
    var ringPin by remember(hold) { mutableStateOf<ShownPin?>(null) }
    val leave = remember(hold) { Animatable(0f) }
    var leavingPin by remember(hold) { mutableStateOf<ShownPin?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(hold) {
        snapshotFlow { hold.target?.end?.key }.collectLatest {
            ringPin?.let { last ->
                leavingPin = last
                val from = ring.value.coerceIn(0f, 1f)
                scope.launch {
                    leave.snapTo(from)
                    leave.animateTo(0f, tween(RING_OUT_MILLIS))
                }
            }
            ringPin = hold.target
            ring.snapTo(0f)
            if (ringPin != null) ring.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Pops out of the finger.
    val pop = remember(hold) { Animatable(0f) }
    LaunchedEffect(hold) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }

    Canvas(Modifier.fillMaxSize()) {
        val pin = currentProject(hold.from.position) ?: return@Canvas
        drawDots(from = pin, to = lineEnd(), startGap = PIN_SIZE.toPx() / 2, endGap = HANDLE_SIZE.toPx() / 2, color = color)
        leavingPin?.let { drawRing(it, leave.value, currentProject, color) }
        ringPin?.let { drawRing(it, ring.value, currentProject, color) }
    }
    ringPin?.let { NameTag(it.end, handle = { handleOf(hold.finger, density) }, alpha = { ring.value.coerceIn(0f, 1f) }) }
    Box(
        modifier = Modifier
            .size(HANDLE_SIZE)
            .graphicsLayer {
                val handle = handleOf(hold.finger, density)
                translationX = handle.x - HANDLE_SIZE.toPx() / 2
                translationY = handle.y - HANDLE_SIZE.toPx() / 2
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
                shadowElevation = 6.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Along the line, from the pin out through the handle.
                val pin = currentProject(hold.from.position)
                val handle = handleOf(hold.finger, density)
                if (pin != null) rotationZ = atan2(handle.y - pin.y, handle.x - pin.x) * 180f / PI.toFloat() + 90f
                scaleX = 0.6f
                scaleY = 0.6f
            },
        )
    }
}

/** The ring around [shown], [grow] from 0 (gone) to 1 (out, a little past it while bouncing); sized from the pin, so a dot gets a small ring. */
private fun DrawScope.drawRing(shown: ShownPin, grow: Float, project: (Coordinates) -> Offset?, color: Color) {
    val fade = grow.coerceIn(0f, 1f)
    if (fade <= 0f) return
    val at = project(shown.end.position) ?: return
    val pinRadius = shown.radius - PIN_TOUCH_SLOP.toPx()
    val radius = pinRadius + RING_GAP.toPx() + pinRadius * RING_GROW_SHARE * grow
    val stroke = (RING_STROKE + (RING_STROKE_HELD - RING_STROKE) * fade).toPx()
    drawCircle(color.copy(alpha = 0.18f * fade), radius = radius, center = at)
    drawCircle(Color.White.copy(alpha = fade), radius = radius, center = at, style = Stroke(stroke + 1.5.dp.toPx()))
    drawCircle(color.copy(alpha = fade), radius = radius, center = at, style = Stroke(stroke))
}

/**
 * The name of the pin the handle is on, in a glass pill above the handle, so it reads even where pins crowd: a small
 * mark at its start (the pin's image, else the stop's number or the place's category), as far in from the top, bottom and start, then the name.
 */
@Composable
private fun NameTag(end: PinEnd, handle: () -> Offset, alpha: () -> Float) {
    val name = when (end) {
        is PinEnd.Stop -> end.stop.title.asString()
        is PinEnd.Place -> end.place.name
    }
    GlassSurface(
        shape = CircleShape,
        shadowElevation = 0.dp,
        blur = false,
        modifier = Modifier
            .layout { measurable, constraints ->
                val tag = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Placed in the placement step, so following the finger only moves it.
                    val at = handle()
                    val bottom = at.y - (HANDLE_SIZE / 2 + NAME_TAG_GAP).toPx()
                    tag.place((at.x - tag.width / 2).toInt(), (bottom - tag.height).toInt())
                }
            }
            .graphicsLayer { this.alpha = alpha() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NAME_TAG_INSET),
            // The mark is NAME_TAG_INSET from the top, bottom and start; the name ends a pill's radius from the end.
            modifier = Modifier.padding(start = NAME_TAG_INSET, top = NAME_TAG_INSET, bottom = NAME_TAG_INSET, end = NAME_TAG_MARK / 2 + NAME_TAG_INSET),
        ) {
            NameTagMark(end)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = NAME_TAG_MAX_WIDTH),
            )
        }
    }
}

/**
 * The pin's image when it has one (a stop's cover, a place's photo; the thumbnail its pin already loaded, from the
 * cache), else a stop's number on stop green or a place's category icon on the place colour, like their pins.
 */
@Composable
private fun NameTagMark(end: PinEnd) {
    val (imageId, imageUrl) = when (end) {
        is PinEnd.Stop -> end.stop.coverPhotoId to end.stop.coverThumbUrl
        is PinEnd.Place -> end.place.coverImageId to end.place.coverThumbUrl
    }
    if (imageId != null && imageUrl != null) {
        PhotoImage(photoId = imageId, url = imageUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.size(NAME_TAG_MARK).clip(CircleShape))
        return
    }
    val fill = when (end) {
        is PinEnd.Stop -> MaterialTheme.extendedColors.stop
        is PinEnd.Place -> MaterialTheme.extendedColors.place
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(NAME_TAG_MARK).background(fill, CircleShape)) {
        when (end) {
            is PinEnd.Stop -> Text(
                text = end.stop.orderNumber.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            is PinEnd.Place -> Icon(
                imageVector = end.place.category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(NAME_TAG_MARK_ICON),
            )
        }
    }
}

/** Small circles from [from] to [to], leaving [startGap] and [endGap] clear at the ends. */
private fun DrawScope.drawDots(from: Offset, to: Offset, startGap: Float, endGap: Float, color: Color) {
    val length = (to - from).getDistance()
    if (length <= startGap + endGap) return
    val direction = (to - from) / length
    val step = DOT_SPACING.toPx()
    var at = startGap + step / 2
    while (at < length - endGap) {
        val center = from + direction * at
        drawCircle(color, radius = DOT_RADIUS.toPx(), center = center)
        at += step
    }
}

private val HANDLE_SIZE = 40.dp
private val DOT_RADIUS = 3.dp
private val DOT_SPACING = 11.dp

/** Around the held pin the camera stays put, wherever on the map it is. */
private val PAN_STILL_AROUND_PIN = 64.dp

/** The ring of the pin the handle is on: its gap to the pin, how much it grows (a share of the pin's radius), how thick it gets, how fast it fades. */
private val RING_GAP = 2.dp
private const val RING_GROW_SHARE = 0.2f
private val RING_STROKE = 1.5.dp
private val RING_STROKE_HELD = 3.dp
private const val RING_OUT_MILLIS = 160

/** At most one tick this often while the target changes. */
private const val TICK_GAP_MILLIS = 80

/** The name tag: how far above the handle it floats, the mark at its start and its inset, and how wide the name gets before it's cut. */
private val NAME_TAG_GAP = 8.dp
private val NAME_TAG_MARK = 24.dp
private val NAME_TAG_MARK_ICON = 14.dp
private val NAME_TAG_INSET = 6.dp
private val NAME_TAG_MAX_WIDTH = 200.dp
