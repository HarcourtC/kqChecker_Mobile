package org.xjtuai.kqchecker.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xjtuai.kqchecker.auth.TokenManager
import org.xjtuai.kqchecker.network.TokenInterceptor

data class DebugResult(
    val code: Int,
    val sentHeaders: String,
    val bodyPreview: String?
)

/**
 * Debug repository for testing authenticated requests
 * Centralizes debugging network logic previously scattered in UI
 */
class DebugRepository(private val context: Context) {
    companion object {
        private const val TAG = "DebugRepository"
    }

    suspend fun performDebugRequest(): DebugResult {
        return withContext(Dispatchers.IO) {
            try {
                val tm = TokenManager(context)
                val client = OkHttpClient.Builder()
                    .addInterceptor(TokenInterceptor(tm))
                    .build()

                val req = Request.Builder()
                    .url("https://httpbin.org/anything")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val sentHeaders = resp.request.headers.toString()
                    val code = resp.code
                    val body = resp.body?.string()
                    val preview = body?.take(800)
                    return@withContext DebugResult(code = code, sentHeaders = sentHeaders, bodyPreview = preview)
                }
            } catch (e: Exception) {
                Log.e(TAG, "performDebugRequest error", e)
                return@withContext DebugResult(code = -1, sentHeaders = "", bodyPreview = e.message)
            }
        }
    }
}
