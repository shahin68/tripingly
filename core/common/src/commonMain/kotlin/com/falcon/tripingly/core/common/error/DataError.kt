package com.falcon.tripingly.core.common.error

sealed interface DataError : RootError {
    sealed interface Network : DataError {
        data object RequestTimeout : Network
        data object Unauthorized : Network
        data object ServerError : Network
        data object NoInternet : Network
        data class Unknown(val message: String? = null) : Network
    }

    sealed interface Local : DataError {
        data object DiskFull : Local
        data object NotFound : Local
        data class Unknown(val message: String? = null) : Local
    }

    sealed interface Location : DataError {
        data object PermissionDenied : Location
        data object ServiceDisabled : Location
        data object Unavailable : Location
        data class Unknown(val message: String? = null) : Location
    }
}
