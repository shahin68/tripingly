package com.falcon.tripingly.core.common.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateIntervalFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

internal actual fun formatLocalized(date: LocalDate, skeleton: String): String {
    val nsDate = date.toNSDate() ?: return date.toString()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(skeleton)
    }
    return formatter.stringFromDate(nsDate)
}

internal actual fun formatLocalizedRange(start: LocalDate, end: LocalDate, skeleton: String): String {
    val from = start.toNSDate() ?: return start.toString()
    val to = end.toNSDate() ?: return end.toString()
    val formatter = NSDateIntervalFormatter().apply {
        locale = NSLocale.currentLocale
        dateTemplate = skeleton
    }
    return formatter.stringFromDate(from, toDate = to) ?: "$start – $end"
}

private fun LocalDate.toNSDate(): NSDate? = NSCalendar.currentCalendar.dateFromComponents(
    NSDateComponents().apply {
        year = this@toNSDate.year.toLong()
        month = this@toNSDate.month.number.toLong()
        day = this@toNSDate.day.toLong()
    },
)
