package com.falcon.tripingly.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MessageBannerTest {

    @Test
    fun show_hidesTheMessageAfterAWhile() = runTest {
        val banner = MessageBanner(backgroundScope)
        banner.show("Couldn't load your trips.")
        runCurrent()
        assertEquals("Couldn't load your trips.", banner.current?.text)

        advanceTimeBy(MessageBanner.VISIBLE - MessageBanner.MIN_VISIBLE / 2)
        assertEquals("Couldn't load your trips.", banner.current?.text)
        advanceTimeBy(MessageBanner.MIN_VISIBLE)
        assertNull(banner.current)
    }

    @Test
    fun show_backToBack_newerReplacesTheFirstAfterTheMinimumTime() = runTest {
        val banner = MessageBanner(backgroundScope)
        banner.show("First")
        runCurrent()
        banner.show("Second")
        runCurrent()
        assertEquals("First", banner.current?.text)

        advanceTimeBy(MessageBanner.MIN_VISIBLE)
        runCurrent()
        assertEquals("Second", banner.current?.text)
        advanceTimeBy(MessageBanner.VISIBLE)
        runCurrent()
        assertNull(banner.current)
    }

    @Test
    fun show_sameTextTwice_showsItOnce() = runTest {
        val banner = MessageBanner(backgroundScope)
        banner.show("Offline")
        banner.show("Offline")
        runCurrent()
        advanceTimeBy(MessageBanner.VISIBLE)
        runCurrent()
        assertNull(banner.current)
    }

    @Test
    fun dismiss_showsTheNextOneAtOnce() = runTest {
        val banner = MessageBanner(backgroundScope)
        banner.show("First")
        banner.show("Second")
        runCurrent()
        banner.dismiss(banner.current!!)
        runCurrent()
        assertEquals("Second", banner.current?.text)
        banner.dismiss(banner.current!!)
        runCurrent()
        assertNull(banner.current)
    }

    @Test
    fun show_withAction_staysLonger() = runTest {
        val banner = MessageBanner(backgroundScope)
        banner.show("Change not saved", actionLabel = "Retry", onAction = {})
        runCurrent()
        advanceTimeBy(MessageBanner.VISIBLE + MessageBanner.MIN_VISIBLE)
        assertEquals("Retry", banner.current?.actionLabel)
        advanceTimeBy(MessageBanner.VISIBLE_WITH_ACTION)
        runCurrent()
        assertNull(banner.current)
    }
}
