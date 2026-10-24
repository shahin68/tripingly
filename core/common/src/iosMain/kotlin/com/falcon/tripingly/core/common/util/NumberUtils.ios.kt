package com.falcon.tripingly.core.common.util

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale

internal actual fun formatDecimal(value: Double, fractionDigits: Int): String {
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
    }
    return formatter.stringFromNumber(NSNumber(value)) ?: value.toString()
}
