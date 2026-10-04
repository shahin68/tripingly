package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import kotlinx.datetime.LocalDate

/** The server's answer when it refused a dialog's change. */
@Composable
internal fun DialogError(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(12.dp))
    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/** A confirm button's label, or a spinner while the change is being saved. */
@Composable
internal fun SavingLabel(isSaving: Boolean, label: String) {
    if (isSaving) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Text(label)
    }
}

/** "Jun 1, 2025 - Jun 15, 2025", or null for a trip without dates. */
internal fun formatDateRange(start: LocalDate?, end: LocalDate?): String? = when {
    start == null -> null
    end == null || end == start -> DateUtils.formatFormal(start)
    else -> "${DateUtils.formatFormal(start)} - ${DateUtils.formatFormal(end)}"
}
