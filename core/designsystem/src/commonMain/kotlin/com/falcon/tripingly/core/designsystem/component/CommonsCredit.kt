package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.falcon.tripingly.core.model.place.ImageCredit
import org.jetbrains.compose.resources.stringResource

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
    val commons = stringResource(Res.string.image_credit_commons)
    val publicDomain = stringResource(Res.string.image_credit_public_domain)
    val croppedLabel = stringResource(Res.string.image_credit_cropped)
    val links = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        fun link(label: String, url: String?) {
            if (url == null) append(label) else withLink(LinkAnnotation.Url(url, links)) { append(label) }
        }
        val parts = buildList<() -> Unit> {
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
        }
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
