package com.falcon.tripingly.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkTest {

    @Test
    fun parsesTheServersLinks() {
        assertEquals(DeepLink.Trip("t1", "n1"), DeepLink.parse("tripinly://trips/t1", "n1"))
        assertEquals(DeepLink.Marker("m1"), DeepLink.parse("tripinly://markers/m1/"))
        assertEquals(DeepLink.Home("n2"), DeepLink.parse("tripinly://notifications", "n2"))
    }

    @Test
    fun unknownLinks_openTheApp() {
        assertEquals(DeepLink.Home(), DeepLink.parse("https://example.com/trips/t1"))
        assertEquals(DeepLink.Home(), DeepLink.parse("tripinly://trips/"))
        assertEquals(DeepLink.Home(), DeepLink.parse("tripinly://trips/t1/extra"))
        assertEquals(DeepLink.Home(), DeepLink.parse("tripinly://places/p1"))
    }

    @Test
    fun aLinkIsTakenOnce() {
        val links = DeepLinks()
        links.open(DeepLink.Trip("t1"))
        assertEquals(DeepLink.Trip("t1"), links.take())
        assertNull(links.take())
    }
}
