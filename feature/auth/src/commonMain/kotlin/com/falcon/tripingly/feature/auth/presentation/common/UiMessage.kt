package com.falcon.tripingly.feature.auth.presentation.common

import androidx.compose.runtime.Composable
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.error_no_internet
import com.falcon.tripingly.feature.auth.generated.resources.error_server
import com.falcon.tripingly.feature.auth.generated.resources.error_timeout
import com.falcon.tripingly.feature.auth.generated.resources.error_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A message for the user: our own string, or the server's already localized text. */
internal sealed interface UiMessage {
    data class Resource(val resource: StringResource) : UiMessage
    data class Server(val text: String) : UiMessage

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(resource)
        is Server -> text
    }
}

/** Errors without specific handling: the server's message when it sent one. */
internal fun DataError.Network.toUiMessage(): UiMessage = when (this) {
    is DataError.Network.Api -> UiMessage.Server(message)
    DataError.Network.NoInternet -> UiMessage.Resource(Res.string.error_no_internet)
    DataError.Network.RequestTimeout -> UiMessage.Resource(Res.string.error_timeout)
    DataError.Network.ServerError -> UiMessage.Resource(Res.string.error_server)
    DataError.Network.Unauthorized,
    DataError.Network.Serialization,
    is DataError.Network.Unknown,
    -> UiMessage.Resource(Res.string.error_unknown)
}
