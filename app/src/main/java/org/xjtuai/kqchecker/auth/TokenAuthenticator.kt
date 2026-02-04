package org.xjtuai.kqchecker.auth

import android.content.Context
import com.squareup.moshi.Moshi
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.xjtuai.kqchecker.network.TokenResponse

class TokenAuthenticator(private val context: Context, private val baseUrl: String) : Authenticator {
    private val tokenManager by lazy { TokenManager(context) }

    override fun authenticate(route: Route?, response: Response): Request? {
        synchronized(this) {
            // If there's no refresh token, cannot refresh
            val refresh = tokenManager.getRefreshToken() ?: return null

            // If another thread already refreshed, retry with latest token
            val current = tokenManager.getAccessToken()
            val requestAuth = response.request.header("Authorization")
            if (!current.isNullOrEmpty() && requestAuth != null && requestAuth != current) {
                return response.request.newBuilder().header("Authorization", current).build()
            }

            try {
                val client = OkHttpClient.Builder().build()
                val json = "{\"refresh_token\":\"$refresh\"}"
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/auth/refresh")
                    .post(body)
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    tokenManager.clear()
                    return null
                }
                val respBody = resp.body?.string() ?: return null
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(TokenResponse::class.java)
                val tokenResp = adapter.fromJson(respBody) ?: return null
                val access = tokenResp.access_token
                val newRefresh = tokenResp.refresh_token
                if (access == null) return null
                val bearer = if (access.startsWith("bearer ", true)) access else "bearer $access"
                tokenManager.saveAccessToken(bearer)
                if (newRefresh != null) tokenManager.saveRefreshToken(newRefresh)

                return response.request.newBuilder().header("Authorization", tokenManager.getAccessToken()!!).build()
            } catch (t: Throwable) {
                tokenManager.clear()
                return null
            }
        }
    }
}
