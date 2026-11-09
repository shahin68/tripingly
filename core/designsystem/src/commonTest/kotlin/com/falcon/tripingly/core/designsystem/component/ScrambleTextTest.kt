package com.falcon.tripingly.core.designsystem.component

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ScrambleTextTest {

    private class Run {
        val frames = mutableListOf<String>()
        var ticks = 0
    }

    private suspend fun run(from: String, to: String, seed: Int = 7) = Run().also { r ->
        scramble(from, to, Random(seed), onFrame = { r.frames += it }, onLetterLocked = { r.ticks++ })
    }

    @Test
    fun theSameText_showsNothing() = runTest {
        val r = run("6 stops", "6 stops")
        assertTrue(r.frames.isEmpty())
        assertEquals(0, r.ticks)
        assertEquals(0, currentTime)
    }

    @Test
    fun itAlwaysEndsOnTheNewText() = runTest {
        assertEquals("Ordering…", run("6 stops", "Ordering…").frames.last())
        assertEquals("6 stops", run("Ordering…", "6 stops").frames.last())
        assertEquals("", run("6 stops", "").frames.last())
    }

    @Test
    fun everyFrameHasTheNewLength_andKeepsWhatIsNotALetter() = runTest {
        val to = "12 Stopps · 3…"
        run("x", to).frames.forEach { frame ->
            assertEquals(to.length, frame.length)
            to.forEachIndexed { i, c -> if (!c.isLetter()) assertEquals(c, frame[i]) }
        }
    }

    @Test
    fun shuffledLettersComeFromTheNewText() = runTest {
        val to = "Ordering…"
        val letters = to.filter(Char::isLetter).toSet()
        run("6 stops", to).frames.forEach { frame -> frame.filter(Char::isLetter).forEach { assertTrue(it in letters, "$it in $frame") } }
    }

    @Test
    fun lockedLettersNeverChangeAgain_andLockFromStartToEnd() = runTest {
        val to = "Wird sortiert…"
        val letters = to.indices.filter { to[it].isLetter() }
        var lockedBefore = 0
        run("6 Stopps", to, seed = 3).frames.forEachIndexed { n, frame ->
            // Frame n shows at least the first n letters as they end up.
            val minLocked = minOf(n, letters.size)
            for (i in 0 until minLocked) assertEquals(to[letters[i]], frame[letters[i]], "frame $n: $frame")
            assertTrue(minLocked >= lockedBefore)
            lockedBefore = minLocked
        }
    }

    @Test
    fun aTickForEveryFrameThatLocksALetter() = runTest {
        val r = run("6 stops", "Ordering…")
        assertEquals("Ordering".length, r.ticks)
        assertEquals(r.ticks + 1, r.frames.size)
    }

    @Test
    fun itFinishesWithinItsTime_howeverLongTheText() = runTest {
        run("a", "Ordering…")
        assertTrue(currentTime <= SCRAMBLE_MAX_TIME.inWholeMilliseconds, "$currentTime")
        val start = currentTime
        val r = run("a", "x".repeat(500))
        assertTrue(currentTime - start <= SCRAMBLE_MAX_TIME.inWholeMilliseconds, "${currentTime - start}")
        assertTrue(r.ticks <= SCRAMBLE_MAX_FRAMES)
    }

    @Test
    fun aTextWithoutLetters_justShows() = runTest {
        val r = run("6 stops", "12 · 3")
        assertEquals(listOf("12 · 3"), r.frames)
        assertEquals(0, r.ticks)
    }

    @Test
    fun aChangeUndoneWithinTheDebounce_neverShows() = runTest {
        val r = Run()
        val job = launch { scramble("6 stops", "Ordering…", Random(1), { r.frames += it }, { r.ticks++ }) }
        advanceTimeBy(SCRAMBLE_DEBOUNCE.inWholeMilliseconds - 1)
        job.cancel()
        runCurrent()
        assertTrue(r.frames.isEmpty())
        assertEquals(0, r.ticks)
    }

    @Test
    fun cancelledHalfway_itStopsAtOnce() = runTest {
        val r = Run()
        val job = launch { scramble("6 stops", "Ordering…", Random(1), { r.frames += it }, { r.ticks++ }) }
        advanceTimeBy(SCRAMBLE_DEBOUNCE.inWholeMilliseconds + SCRAMBLE_FRAME.inWholeMilliseconds * 2 + 1)
        job.cancel()
        val framesAtCancel = r.frames.size
        val ticksAtCancel = r.ticks
        advanceTimeBy(SCRAMBLE_MAX_TIME.inWholeMilliseconds * 2)
        assertEquals(framesAtCancel, r.frames.size)
        assertEquals(ticksAtCancel, r.ticks)
        assertTrue(r.frames.none { it == "Ordering…" })
    }

    @Test
    fun emojiAndOtherScriptsStayWhole() = runTest {
        val to = "Úti 🚲 terv"
        val r = run("x", to)
        r.frames.forEach { assertTrue(it.contains("🚲"), it) }
        assertEquals(to, r.frames.last())
    }
}
