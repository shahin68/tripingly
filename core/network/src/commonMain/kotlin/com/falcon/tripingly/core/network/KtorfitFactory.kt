package com.falcon.tripingly.core.network

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient

/**
 * Ktorfit over the app's [HttpClient]. API interfaces declare their functions as
 * `suspend fun …(): AppResult<T, DataError.Network>`:
 *
 * ```
 * interface TripsApi {
 *     @GET("trips/{id}")
 *     suspend fun trip(@Path("id") id: String): AppResult<TripResponse, DataError.Network>
 * }
 * val tripsApi = ktorfit.createTripsApi()
 * ```
 */
fun createKtorfit(client: HttpClient, config: ApiConfig): Ktorfit = Ktorfit.Builder()
    .baseUrl("${config.baseUrl}/")
    .httpClient(client)
    .converterFactories(AppResultConverterFactory())
    .build()
