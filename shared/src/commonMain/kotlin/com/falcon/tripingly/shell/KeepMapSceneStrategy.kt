package com.falcon.tripingly.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

/**
 * Keeps the trip map composed under the screens opened over it (a profile, Settings, the paywall, the notification
 * center), so going back shows it at once instead of building the native map, its pins and its camera again.
 *
 * The map and what covers it are one scene to the NavDisplay (its key is the map's), so a screen opening over the map
 * never moves or rebuilds it: the covering screens come and go inside the scene, with the platform's own transitions,
 * back and predictive back included. Only the top-most map is kept; a map further down the back stack is built again
 * when it comes back, as before. A covered map stops once the screen over it has settled (its lifecycle drops to
 * CREATED: no rendering, location or collecting), and its sheets hide as on any screen that isn't on top.
 */
class KeepMapSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val mapIndex = entries.indexOfLast { it.metadata[KEEP_UNDER] == true }
        if (mapIndex < 0) return null
        val covers = entries.drop(mapIndex + 1)
        return MapScene(
            map = entries[mapIndex],
            cover = Cover(covers.lastOrNull(), depth = covers.size),
            below = Cover(covers.getOrNull(covers.size - 2), depth = covers.size - 1),
            previousEntries = entries.dropLast(1),
            onBack = onBack,
        )
    }

    companion object {
        private const val KEEP_UNDER = "keepUnderCovers"

        /** The metadata of the map entry that stays composed under the screens opened over it. */
        val keepUnder: Map<String, Any> = mapOf(KEEP_UNDER to true)
    }
}

/** The map and the screen over it, if any; [below] is what back shows. */
private class MapScene<T : Any>(
    private val map: NavEntry<T>,
    private val cover: Cover<T>,
    private val below: Cover<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : Scene<T> {
    override val key: Any = map.contentKey
    override val entries: List<NavEntry<T>> = listOfNotNull(map, cover.entry)
    override val content: @Composable () -> Unit = { MapWithCovers(map, cover, below, onBack) }

    override fun equals(other: Any?): Boolean =
        other is MapScene<*> && map == other.map && cover == other.cover && below == other.below && previousEntries == other.previousEntries

    override fun hashCode(): Int = ((map.hashCode() * 31 + cover.hashCode()) * 31 + below.hashCode()) * 31 + previousEntries.hashCode()
}

/**
 * The screen [depth] screens over the map, or none ([entry] null, depth 0). A scene of its own only so the
 * NavDisplay's default transitions animate it.
 */
private class Cover<T : Any>(val entry: NavEntry<T>?, val depth: Int) : Scene<T> {
    override val key: Any = entry?.contentKey ?: Unit
    override val entries: List<NavEntry<T>> = listOfNotNull(entry)
    override val previousEntries: List<NavEntry<T>> = emptyList()
    override val content: @Composable () -> Unit = { entry?.Content() }

    override fun equals(other: Any?): Boolean = other is Cover<*> && entry == other.entry && depth == other.depth

    override fun hashCode(): Int = (entry?.hashCode() ?: 0) * 31 + depth
}

@OptIn(ExperimentalTransitionApi::class)
@Composable
private fun <T : Any> MapWithCovers(map: NavEntry<T>, cover: Cover<T>, below: Cover<T>, onBack: () -> Unit) {
    val coverState = remember { SeekableTransitionState<Scene<T>>(cover) }
    val transition = rememberTransition(coverState, label = "covers")
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    val gesture = (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.takeIf { cover.entry != null }

    // As the NavDisplay does: a back gesture seeks towards the screen below, letting go finishes or undoes it.
    if (gesture != null) {
        LaunchedEffect(below, gesture.progress) { coverState.seekTo(gesture.progress, below) }
    } else {
        LaunchedEffect(cover) {
            if (coverState.currentState != cover) {
                coverState.animateTo(cover)
            } else {
                val totalMillis = transition.totalDurationNanos / 1_000_000
                val completed = transition.targetState == cover
                val (end, remaining) =
                    if (completed) 1f to ((1f - coverState.fraction) * totalMillis).toInt() else 0f to (coverState.fraction * totalMillis).toInt()
                animate(coverState.fraction, end, animationSpec = tween(remaining)) { value, _ ->
                    launch { if (value != end) coverState.seekTo(value) else coverState.snapTo(cover) }
                }
            }
        }
    }

    val isSettled = transition.currentState == transition.targetState
    val mapLifecycle = when {
        cover.entry == null && isSettled -> Lifecycle.State.RESUMED
        cover.entry != null && isSettled -> Lifecycle.State.CREATED
        else -> Lifecycle.State.STARTED
    }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLifecycleOwner provides rememberLifecycleOwner(mapLifecycle)) { map.Content() }
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val target = targetState as Cover<T>
                val spec = when {
                    gesture != null -> defaultPredictivePopTransitionSpec<T>().invoke(this, gesture.swipeEdge)
                    target.depth < (initialState as Cover<T>).depth -> defaultPopTransitionSpec<T>().invoke(this)
                    else -> defaultTransitionSpec<T>().invoke(this)
                }
                // The deeper screen is in front, whichever way it goes.
                ContentTransform(spec.targetContentEnter, spec.initialContentExit, targetContentZIndex = target.depth.toFloat(), sizeTransform = null)
            },
            contentKey = { it.key },
        ) { it.content() }
    }
    // After the map's own back handlers, so this one goes first while a screen covers the map.
    NavigationBackHandler(state = backState, isBackEnabled = cover.entry != null, onBackCompleted = onBack)
}
