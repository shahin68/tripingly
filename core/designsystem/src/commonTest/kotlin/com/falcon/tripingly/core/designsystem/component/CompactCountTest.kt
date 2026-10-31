package com.falcon.tripingly.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactCountTest {

    @Test
    fun smallCounts_stayExact() {
        assertEquals("0", compactCount(0))
        assertEquals("999", compactCount(999))
    }

    @Test
    fun thousands_showOneDecimalBelowTen() {
        assertEquals("1k", compactCount(1_000))
        assertEquals("1.2k", compactCount(1_204))
        assertEquals("9.9k", compactCount(9_999))
        assertEquals("12k", compactCount(12_480))
        assertEquals("999k", compactCount(999_999))
    }

    @Test
    fun millions_useM() {
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("1.2M", compactCount(1_250_000))
        assertEquals("12M", compactCount(12_500_000))
    }
}
