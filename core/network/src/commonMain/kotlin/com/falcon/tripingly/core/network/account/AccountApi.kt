package com.falcon.tripingly.core.network.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.model.ConsentsDto
import com.falcon.tripingly.core.network.model.LegalDocumentsDto
import com.falcon.tripingly.core.network.model.MeDto
import com.falcon.tripingly.core.network.model.RecordConsentDto
import com.falcon.tripingly.core.network.model.UpdateMeDto
import com.falcon.tripingly.core.network.model.UsernameAvailabilityDto
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query

/** The signed-in user's profile, onboarding state and consents, plus the legal documents. */
interface AccountApi {
    @GET("me")
    suspend fun me(): AppResult<MeDto, DataError.Network>

    @PATCH("me")
    @Headers("Content-Type: application/json")
    suspend fun updateMe(@Body body: UpdateMeDto): AppResult<MeDto, DataError.Network>

    @GET("users/check-username")
    suspend fun checkUsername(@Query("username") username: String): AppResult<UsernameAvailabilityDto, DataError.Network>

    @GET("legal/documents")
    suspend fun legalDocuments(@Query("locale") locale: String): AppResult<LegalDocumentsDto, DataError.Network>

    @GET("me/consents")
    suspend fun consents(): AppResult<ConsentsDto, DataError.Network>

    @POST("me/consents")
    @Headers("Content-Type: application/json")
    suspend fun recordConsent(@Body body: RecordConsentDto): AppResult<ConsentsDto, DataError.Network>
}
