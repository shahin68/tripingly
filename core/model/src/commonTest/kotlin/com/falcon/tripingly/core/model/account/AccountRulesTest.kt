package com.falcon.tripingly.core.model.account

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRulesTest {

    @Test
    fun age_countsWholeYearsUpToTheBirthday() {
        val birth = LocalDate(2010, 6, 15)
        assertFalse(birth.isAtLeast(16, LocalDate(2026, 6, 14)))
        assertTrue(birth.isAtLeast(16, LocalDate(2026, 6, 15)))
    }

    @Test
    fun age_leapDayBirthdayTurnsOverOnMarchFirst() {
        val birth = LocalDate(2008, 2, 29)
        assertFalse(birth.isAtLeast(18, LocalDate(2026, 2, 28)))
        assertTrue(birth.isAtLeast(18, LocalDate(2026, 3, 1)))
    }

    @Test
    fun usernameFormat_followsTheServerRules() {
        listOf("abc", "jonas.k", "_ab", "ab_", "a1.b2_c3", "a".repeat(30)).forEach {
            assertTrue(it.isUsernameFormatValid(), it)
        }
        listOf("ab", "a".repeat(31), ".ab", "ab.", "a..b", "Abc", "ab-c", "äbc").forEach {
            assertFalse(it.isUsernameFormatValid(), it)
        }
    }
}
