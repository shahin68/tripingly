package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.material3.Text
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.image_credit_commons
import com.falcon.tripingly.core.designsystem.generated.resources.image_credit_cropped
import com.falcon.tripingly.core.designsystem.generated.resources.image_credit_public_domain
import com.falcon.tripingly.core.designsystem.generated.resources.osm_attribution
import com.falcon.tripingly.core.designsystem.generated.resources.photo_viewer_uploaded_by
import com.falcon.tripingly.core.model.place.ImageCredit
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.falcon.tripingly.core.designsystem.theme.spacing
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/**
 * The credit a Wikimedia Commons image needs wherever it shows: "© author · licence · Wikimedia Commons",
 * with the title first when the licence asks for it, "author · Public domain" for public-domain images, and
 * "· cropped" when the image is shown [cropped]. The author and "Wikimedia Commons" open the file's page,
 * the licence its text.
 */
@Composable
fun CommonsCredit(
    credit: ImageCredit,
    cropped: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    CreditLine(osm = false, credit = credit, cropped = cropped, modifier = modifier, color = color, maxLines = maxLines)
}

/**
 * One line of credits under a place's image, dot separated: "© OpenStreetMap contributors" for a place from
 * OpenStreetMap ([osm], linked to its copyright page), then the image's: a Commons image's [CommonsCredit], or
 * "Photo by @name" for a person's photo ([photoBy]). Nothing when there's nothing to credit.
 */
@Composable
fun CreditLine(
    osm: Boolean,
    credit: ImageCredit?,
    cropped: Boolean,
    modifier: Modifier = Modifier,
    photoBy: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) {
    val osmLabel = stringResource(Res.string.osm_attribution)
    val commons = stringResource(Res.string.image_credit_commons)
    val publicDomain = stringResource(Res.string.image_credit_public_domain)
    val croppedLabel = stringResource(Res.string.image_credit_cropped)
    val uploader = photoBy?.let { stringResource(Res.string.photo_viewer_uploaded_by, it) }
    val links = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val parts = buildList<AnnotatedString.Builder.() -> Unit> {
        fun AnnotatedString.Builder.link(label: String, url: String?) {
            if (url == null) append(label) else withLink(LinkAnnotation.Url(url, links)) { append(label) }
        }
        if (osm) add { link(osmLabel, OSM_COPYRIGHT_URL) }
        if (credit != null) {
            credit.title?.let { title -> add { append(title) } }
            if (credit.isPublicDomain) {
                credit.author?.let { author -> add { link(author, credit.sourceUrl) } }
                add { link(publicDomain, credit.licenseUrl ?: credit.sourceUrl) }
            } else {
                credit.author?.let { author -> add { append("© "); link(author, credit.sourceUrl) } }
                add { link(credit.license, credit.licenseUrl) }
                add { link(commons, credit.sourceUrl) }
            }
            if (cropped) add { append(croppedLabel) }
        } else if (uploader != null) {
            add { append(uploader) }
        }
    }
    if (parts.isEmpty()) return
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(" · ")
            part()
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun CommonsCreditPreviewContent() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        CommonsCredit(credit = previewCredit, cropped = false)
        CommonsCredit(credit = previewCredit.copy(title = "Tour Eiffel at dusk"), cropped = true)
        CommonsCredit(credit = previewCredit.copy(license = ImageCredit.PUBLIC_DOMAIN, licenseUrl = null), cropped = false)
        CommonsCredit(credit = previewCredit.copy(author = "Jean-Baptiste Alexandre Durand-Montgolfier"), cropped = true, maxLines = 1)
        CreditLine(osm = true, credit = previewCredit, cropped = true)
        CreditLine(osm = true, credit = null, cropped = false)
        CreditLine(osm = false, credit = null, cropped = false, photoBy = "shahin")
    }
}

private val previewCredit = ImageCredit(
    author = "Benh Lieu Song",
    title = null,
    license = "CC BY-SA 3.0",
    licenseUrl = "https://creativecommons.org/licenses/by-sa/3.0",
    sourceUrl = "https://commons.wikimedia.org/wiki/File:Tour_Eiffel.jpg",
)

@Preview
@Composable
private fun CommonsCreditPreview() {
    TripinglyTheme { CommonsCreditPreviewContent() }
}

@Preview
@Composable
private fun CommonsCreditDarkPreview() {
    TripinglyTheme(darkTheme = true) { CommonsCreditPreviewContent() }
}
