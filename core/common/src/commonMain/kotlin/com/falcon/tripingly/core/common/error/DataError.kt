package com.falcon.tripingly.core.common.error

sealed interface DataError : RootError {
    sealed interface Network : DataError {
        data object RequestTimeout : Network
        /** No usable session: signed out, or the refresh token was refused. */
        data object Unauthorized : Network
        /** A 5xx without the API's error envelope (proxy, gateway). */
        data object ServerError : Network
        data object NoInternet : Network
        /** The response didn't match the expected model. */
        data object Serialization : Network

        /**
         * The API answered with its error envelope. Handle it by [code]; show the
         * server's localized [message] when there is no specific handling.
         * [details] holds the envelope's details flattened to strings
         * (`retryAfterSeconds`, `resource`, `fields.title` …).
         */
        data class Api(
            val status: Int,
            val code: String,
            val message: String,
            val details: Map<String, String> = emptyMap(),
        ) : Network

        data class Unknown(val message: String? = null) : Network
    }

    sealed interface Local : DataError {
        data object DiskFull : Local
        data object NotFound : Local
        data class Unknown(val message: String? = null) : Local
    }

    /** Buying Tripinly Pro in the app store. */
    sealed interface Purchase : DataError {
        /** This build has no purchases (no RevenueCat key), or the store can't be used on this device. */
        data object Unavailable : Purchase
        data object NoInternet : Purchase
        /** The store waits for the payment (a slow method or a parent's approval); Pro comes once it's paid. */
        data object Pending : Purchase
        /** Purchases are turned off on this device or account. */
        data object NotAllowed : Purchase
        data class Store(val message: String? = null) : Purchase
    }

    sealed interface Location : DataError {
        data object PermissionDenied : Location
        data object ServiceDisabled : Location
        data object Unavailable : Location
        data class Unknown(val message: String? = null) : Location
    }
}
