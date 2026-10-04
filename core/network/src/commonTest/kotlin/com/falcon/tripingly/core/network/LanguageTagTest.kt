package com.falcon.tripingly.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTagTest {

    @Test
    fun dropsExtensionsAndPrivateUse() {
        assertEquals("en-US", "en-US-u-mu-celsius".baseLanguageTag())
        assertEquals("de-AT", "de-AT-u-fw-mon-x-test".baseLanguageTag())
    }

    @Test
    fun keepsLanguageScriptAndRegion() {
        assertEquals("hu", "hu".baseLanguageTag())
        assertEquals("zh-Hant-TW", "zh-Hant-TW".baseLanguageTag())
        assertEquals("en-GB", "en_GB".baseLanguageTag())
    }

    @Test
    fun fallsBackToEnglish() {
        assertEquals("en", "".baseLanguageTag())
    }
}
