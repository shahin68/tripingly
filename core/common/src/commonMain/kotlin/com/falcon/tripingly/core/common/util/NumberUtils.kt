package com.falcon.tripingly.core.common.util

import kotlin.math.roundToInt

/** Numbers in the user's locale, like [DateUtils] for dates. */
object NumberUtils {
    /** [meters] in kilometres with one decimal, as the locale writes it: "2.4" in English, "2,4" in German and Hungarian. */
    fun kilometers(meters: Int): String = formatDecimal((meters / 100.0).roundToInt() / 10.0, fractionDigits = 1)
}

internal expect fun formatDecimal(value: Double, fractionDigits: Int): String
