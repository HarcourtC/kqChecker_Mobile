package org.xjtuai.kqchecker.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val access_token: String?,
    val refresh_token: String?
)
