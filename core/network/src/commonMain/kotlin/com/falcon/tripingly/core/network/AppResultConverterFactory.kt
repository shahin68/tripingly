package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.common.result.AppResult
import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import de.jensklingenberg.ktorfit.converter.TypeData
import io.ktor.client.statement.HttpResponse

/**
 * Lets Ktorfit functions return `AppResult<T, DataError.Network>`, mapped exactly
 * like [apiCall]: nothing throws across the call except cancellation.
 */
internal class AppResultConverterFactory : Converter.Factory {
    override fun suspendResponseConverter(
        typeData: TypeData,
        ktorfit: Ktorfit,
    ): Converter.SuspendResponseConverter<HttpResponse, *>? {
        if (typeData.typeInfo.type != AppResult::class) return null
        val bodyType = typeData.typeArgs.first().typeInfo
        return object : Converter.SuspendResponseConverter<HttpResponse, AppResult<Any?, *>> {
            override suspend fun convert(result: KtorfitResult): AppResult<Any?, *> = when (result) {
                is KtorfitResult.Success -> executeApiCall<Any?>(bodyType) { result.response }
                is KtorfitResult.Failure -> executeApiCall<Any?>(bodyType) { throw result.throwable }
            }
        }
    }
}
