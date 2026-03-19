package org.xjtuai.kqchecker.repository

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.xjtuai.kqchecker.network.ApiClient
import org.xjtuai.kqchecker.network.ApiService
import org.xjtuai.kqchecker.network.CurrentTermResponse
import org.xjtuai.kqchecker.util.ApiRateLimiter
import org.xjtuai.kqchecker.util.ConfigHelper
import org.xjtuai.kqchecker.util.RateLimitNotifier

/**
 * 学期信息仓库，负责获取当前学期、周次等全局配置信息
 */
class TermRepository(private val context: Context) {
    companion object {
        private const val TAG = "TermRepository"
    }

    private val apiClient = ApiClient(context)
    private val baseUrl: String = ConfigHelper.getBaseUrl(context)
    private val apiService = apiClient.createService(baseUrl)
    private val config = ConfigHelper.getConfig(context)

    /**
     * 获取当前学期信息
     * @return CurrentTermResponse? 包含学期号(bh)和当前周(currentWeek)
     */
    suspend fun fetchCurrentTerm(): CurrentTermResponse? {
        Log.d(TAG, "Fetching current term info...")
        return try {
            if (!ApiRateLimiter.tryAcquire("current_term")) {
                Log.w(TAG, "Rate limited: current-term API exceeded 5 requests / 10 minutes")
                RateLimitNotifier.show(context)
                return null
            }
            // Empty JSON body for the request
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = "{}".toRequestBody(mediaType)
            
            val responseBody = apiService.getCurrentTerm(config.currentTermEndpoint, requestBody) ?: return null
            val json = responseBody.string()
            Log.d(TAG, "Current term response: $json")
            CurrentTermResponse.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch current term", e)
            null
        }
    }
}
