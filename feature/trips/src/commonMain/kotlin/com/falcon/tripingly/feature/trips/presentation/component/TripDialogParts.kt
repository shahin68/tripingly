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
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment

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

@Preview
@Composable
private fun DialogPartsPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            DialogError("Couldn’t rename the trip. No internet connection.")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SavingLabel(isSaving = false, label = "Save")
                SavingLabel(isSaving = true, label = "Save")
            }
        }
    }
}
