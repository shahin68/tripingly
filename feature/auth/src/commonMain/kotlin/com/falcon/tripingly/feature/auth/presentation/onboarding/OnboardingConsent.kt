package com.falcon.tripingly.feature.auth.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SkeletonLine
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.LegalDocumentType
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_accept
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_marketing
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_terms
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_terms_and_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_marketing
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_privacy
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_document_terms
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Action
import org.jetbrains.compose.resources.stringResource

/* The consent step: the documents to read, and the ticks and button anchored under them. */

/** The documents as rows on a card, each opening in the browser; lines while they load. */
@Composable
internal fun ConsentStep(state: OnboardingViewModel.State) {
    val uriHandler = LocalUriHandler.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = MaterialTheme.spacing.extraSmall)) {
            if (state.isLoadingDocuments) {
                repeat(SKELETON_ROWS) {
                    Box(Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = MaterialTheme.spacing.medium), contentAlignment = Alignment.CenterStart) {
                        SkeletonLine(Modifier.fillMaxWidth(0.5f))
                    }
                }
            }
            state.documents.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowHeight)
                        .clickable { runCatching { uriHandler.openUri(item.document.url) } }
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(item.document.type.title()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun ConsentFooter(state: OnboardingViewModel.State, onAction: (Action) -> Unit) {
    val (required, optional) = state.documents.partition { it.document.required }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (required.isNotEmpty()) {
            // One tick accepts every required document (Terms and Privacy); each is still recorded on its own.
            ConsentRow(
                label = stringResource(requiredLabel(required.map { it.document.type })),
                checked = required.all { it.accepted },
                onCheckedChange = { onAction(Action.OnRequiredDocumentsAcceptedChange(it)) },
            )
        }
        optional.forEach { item ->
            ConsentRow(
                label = stringResource(item.document.type.acceptLabel()),
                checked = item.accepted,
                onCheckedChange = { onAction(Action.OnDocumentAcceptedChange(item.document, it)) },
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.onboarding_consent_accept),
            onClick = { onAction(Action.OnAcceptClick) },
            enabled = state.canAccept,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConsentRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private fun requiredLabel(types: List<LegalDocumentType>) = when {
    LegalDocumentType.TERMS in types && LegalDocumentType.PRIVACY in types -> Res.string.onboarding_consent_terms_and_privacy
    LegalDocumentType.PRIVACY in types -> Res.string.onboarding_consent_privacy
    else -> Res.string.onboarding_consent_terms
}

private fun LegalDocumentType.title() = when (this) {
    LegalDocumentType.TERMS -> Res.string.onboarding_document_terms
    LegalDocumentType.PRIVACY -> Res.string.onboarding_document_privacy
    LegalDocumentType.MARKETING -> Res.string.onboarding_document_marketing
}

private fun LegalDocumentType.acceptLabel() = when (this) {
    LegalDocumentType.TERMS -> Res.string.onboarding_consent_terms
    LegalDocumentType.PRIVACY -> Res.string.onboarding_consent_privacy
    LegalDocumentType.MARKETING -> Res.string.onboarding_consent_marketing
}

private val RowHeight = 56.dp
private const val SKELETON_ROWS = 2

@Preview
@Composable
private fun ConsentRowPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            ConsentRow(label = "I accept the Terms of Service and the Privacy Policy", checked = true, onCheckedChange = {})
            ConsentRow(label = "Send me news and travel tips by email", checked = false, onCheckedChange = {})
        }
    }
}

@Preview
@Composable
private fun ConsentRowDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        ConsentRow(label = "I accept the Terms of Service", checked = true, onCheckedChange = {})
    }
}
