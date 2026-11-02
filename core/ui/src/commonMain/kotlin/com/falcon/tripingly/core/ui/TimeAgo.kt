package com.falcon.tripingly.core.ui

import androidx.compose.runtime.Composable
import com.falcon.tripingly.core.ui.generated.resources.Res
import com.falcon.tripingly.core.ui.generated.resources.time_ago_days
import com.falcon.tripingly.core.ui.generated.resources.time_ago_hours
import com.falcon.tripingly.core.ui.generated.resources.time_ago_just_now
import com.falcon.tripingly.core.ui.generated.resources.time_ago_minutes
import com.falcon.tripingly.core.ui.generated.resources.time_ago_months
import com.falcon.tripingly.core.ui.generated.resources.time_ago_weeks
import com.falcon.tripingly.core.ui.generated.resources.time_ago_years
import com.falcon.tripingly.core.ui.generated.resources.time_short_days
import com.falcon.tripingly.core.ui.generated.resources.time_short_hours
import com.falcon.tripingly.core.ui.generated.resources.time_short_minutes
import com.falcon.tripingly.core.ui.generated.resources.time_short_now
import com.falcon.tripingly.core.ui.generated.resources.time_short_weeks
import com.falcon.tripingly.core.ui.generated.resources.time_short_years
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long ago [instant] was, the way social apps say it: "just now", "5 minutes ago", "a week ago", "2 years ago". */
@Composable
fun timeAgo(instant: Instant): String {
    val elapsed = (Clock.System.now() - instant).coerceAtLeast(0.minutes)
    return when {
        elapsed < 1.minutes -> stringResource(Res.string.time_ago_just_now)
        elapsed < 1.hours -> elapsed.inWholeMinutes.toInt().let { pluralStringResource(Res.plurals.time_ago_minutes, it, it) }
        elapsed < 1.days -> elapsed.inWholeHours.toInt().let { pluralStringResource(Res.plurals.time_ago_hours, it, it) }
        elapsed < 7.days -> elapsed.inWholeDays.toInt().let { pluralStringResource(Res.plurals.time_ago_days, it, it) }
        elapsed < 30.days -> (elapsed.inWholeDays / 7).toInt().let { pluralStringResource(Res.plurals.time_ago_weeks, it, it) }
        elapsed < 365.days -> (elapsed.inWholeDays / 30).toInt().let { pluralStringResource(Res.plurals.time_ago_months, it, it) }
        else -> (elapsed.inWholeDays / 365).toInt().let { pluralStringResource(Res.plurals.time_ago_years, it, it) }
    }
}

/**
 * How long ago [instant] was in Instagram's comment style, one short unit: "now", "5m", "2h", "3d", "4w" (weeks up to
 * a year, no months), "1y". For comments; trips and other places use [timeAgo].
 */
@Composable
fun shortTimeAgo(instant: Instant): String {
    val elapsed = (Clock.System.now() - instant).coerceAtLeast(0.minutes)
    return when {
        elapsed < 1.minutes -> stringResource(Res.string.time_short_now)
        elapsed < 1.hours -> stringResource(Res.string.time_short_minutes, elapsed.inWholeMinutes)
        elapsed < 1.days -> stringResource(Res.string.time_short_hours, elapsed.inWholeHours)
        elapsed < 7.days -> stringResource(Res.string.time_short_days, elapsed.inWholeDays)
        elapsed < 365.days -> stringResource(Res.string.time_short_weeks, elapsed.inWholeDays / 7)
        else -> stringResource(Res.string.time_short_years, elapsed.inWholeDays / 365)
    }
}
