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
