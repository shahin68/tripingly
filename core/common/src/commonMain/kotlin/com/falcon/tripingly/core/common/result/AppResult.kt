package com.falcon.tripingly.core.common.result

import com.falcon.tripingly.core.common.error.RootError

/**
 * A typed functional result representing either a [Success] with value [D]
 * or an [Error] with root error [E].
 */
sealed interface AppResult<out D, out E : RootError> {
    data class Success<out D>(val data: D) : AppResult<D, Nothing>
    data class Error<out E : RootError>(val error: E) : AppResult<Nothing, E>
}

inline fun <T, E : RootError> AppResult<T, E>.onSuccess(action: (T) -> Unit): AppResult<T, E> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T, E : RootError> AppResult<T, E>.onError(action: (E) -> Unit): AppResult<T, E> {
    if (this is AppResult.Error) action(error)
    return this
}

inline fun <T, E : RootError, R> AppResult<T, E>.map(transform: (T) -> R): AppResult<R, E> {
    return when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> this
    }
}

fun <D> D.asSuccess(): AppResult.Success<D> = AppResult.Success(this)
fun <E : RootError> E.asError(): AppResult.Error<E> = AppResult.Error(this)
