package com.falcon.tripingly.core.common.util

import java.text.NumberFormat

internal actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(value)
