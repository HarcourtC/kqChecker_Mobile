package org.example.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.example.kqchecker.auth.TokenManager
import org.example.kqchecker.network.TokenInterceptor

data class DebugResult(
    val code: Int,
    val sentHeaders: String,
    val bodyPreview: String?
)

/**
 * 用于调试的轻量级仓库，将原先散落在 UI 的调试网络逻辑集中到这里
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
