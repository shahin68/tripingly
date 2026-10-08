package com.falcon.tripingly.core.common.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

internal actual fun formatLocalized(date: LocalDate, skeleton: String): String {
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.number.toLong()
        day = date.day.toLong()
    }
    val nsDate = NSCalendar.currentCalendar.dateFromComponents(components) ?: return date.toString()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(skeleton)
    }
    return formatter.stringFromDate(nsDate)
}
