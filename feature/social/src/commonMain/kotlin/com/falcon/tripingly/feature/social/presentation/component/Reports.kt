package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_report
import com.falcon.tripingly.feature.social.generated.resources.social_reported

/** The report dialog, open for a trip, a person or an image ([target] with [id]). */
@Immutable
data class ReportState(val target: ReportTarget, val id: String, val isSending: Boolean = false)

/** Sends [report]; either way the outcome is shown as a message. True once the server took it. */
internal suspend fun SocialRepository.sendReport(
    report: ReportState,
    reason: ReportReason,
    details: String,
    showMessage: (UiText) -> Unit,
): Boolean {
    val result = report(report.target, report.id, reason, details)
    showMessage(
        when (result) {
            is AppResult.Success -> UiText.Note(UiText.Resource(Res.string.social_reported))
            is AppResult.Error -> result.error.toUiText(Res.string.social_error_report)
        },
    )
    return result is AppResult.Success
}
