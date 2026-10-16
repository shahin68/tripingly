package com.falcon.tripingly.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.ui.generated.resources.Res
import com.falcon.tripingly.core.ui.generated.resources.error_forbidden
import com.falcon.tripingly.core.ui.generated.resources.error_no_internet
import com.falcon.tripingly.core.ui.generated.resources.error_not_available
import com.falcon.tripingly.core.ui.generated.resources.error_reason_forbidden
import com.falcon.tripingly.core.ui.generated.resources.error_reason_not_available
import com.falcon.tripingly.core.ui.generated.resources.error_server
import com.falcon.tripingly.core.ui.generated.resources.error_timeout
import com.falcon.tripingly.core.ui.generated.resources.error_try_again
import com.falcon.tripingly.core.ui.generated.resources.error_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Text for the user: one of our strings, or text the server already localized. */
@Immutable
sealed interface UiText {
    class Resource(val resource: StringResource, vararg val args: Any) : UiText {
        override fun equals(other: Any?): Boolean =
            other is Resource && other.resource == resource && other.args.contentEquals(args)

        override fun hashCode(): Int = 31 * resource.hashCode() + args.contentHashCode()
    }

    data class Server(val text: String) : UiText

    /** Two sentences in a row, e.g. what failed, then why. */
    data class Joined(val first: UiText, val second: UiText) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(resource, *args)
        is Server -> text
        is Joined -> "${first.asString()} ${second.asString()}"
    }
}

/**
 * Errors without specific handling: the server's message when it sent one.
 * `NOT_FOUND` reads "isn't available" (private resources look the same as missing ones).
 */
fun DataError.Network.toUiText(): UiText = when (this) {
    is DataError.Network.Api -> when (code) {
        "NOT_FOUND" -> UiText.Resource(Res.string.error_not_available)
        "FORBIDDEN" -> UiText.Resource(Res.string.error_forbidden)
        else -> UiText.Server(message)
    }
    DataError.Network.NoInternet -> UiText.Resource(Res.string.error_no_internet)
    DataError.Network.RequestTimeout -> UiText.Resource(Res.string.error_timeout)
    DataError.Network.ServerError -> UiText.Resource(Res.string.error_server)
    DataError.Network.Unauthorized,
    DataError.Network.Serialization,
    is DataError.Network.Unknown,
    -> UiText.Resource(Res.string.error_unknown)
}

/**
 * What failed, then why: [failed] is our sentence ("Couldn't load your trips."), followed by the server's
 * message or the connection problem. Without a known reason it only adds "Try again."
 */
fun DataError.Network.toUiText(failed: StringResource): UiText = UiText.Joined(UiText.Resource(failed), reason())

private fun DataError.Network.reason(): UiText = when (this) {
    is DataError.Network.Api -> when (code) {
        "NOT_FOUND" -> UiText.Resource(Res.string.error_reason_not_available)
        "FORBIDDEN" -> UiText.Resource(Res.string.error_reason_forbidden)
        else -> UiText.Server(message)
    }
    DataError.Network.NoInternet -> UiText.Resource(Res.string.error_no_internet)
    DataError.Network.RequestTimeout -> UiText.Resource(Res.string.error_timeout)
    DataError.Network.ServerError -> UiText.Resource(Res.string.error_server)
    DataError.Network.Unauthorized,
    DataError.Network.Serialization,
    is DataError.Network.Unknown,
    -> UiText.Resource(Res.string.error_try_again)
}
