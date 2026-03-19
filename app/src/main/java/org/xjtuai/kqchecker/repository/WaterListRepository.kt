package org.xjtuai.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.network.ApiClient
import org.xjtuai.kqchecker.network.ApiService
import org.xjtuai.kqchecker.network.WaterListResponse
import org.xjtuai.kqchecker.util.ApiRateLimiter
import org.xjtuai.kqchecker.util.ConfigHelper
import org.xjtuai.kqchecker.util.RateLimitNotifier
import org.json.JSONObject
import java.net.SocketTimeoutException

/**
 * 考勤打卡记录仓库（API2）
 */
class WaterListRepository(private val context: Context) {
    companion object {
        private const val TAG = "WaterListRepository"
    }

    private val apiClient = ApiClient(context)
    private val baseUrl: String = ConfigHelper.getBaseUrl(context)
    private val apiService = apiClient.createService(baseUrl)
    private val cacheManager = CacheManager(context)
    private val termRepository = TermRepository(context)

    /**
     * 获取考勤打卡记录（API2）。始终从网络获取，不使用过期缓存。
     */
    suspend fun getWaterListData(termno: String? = null): WaterListResponse? {
        return withContext(Dispatchers.IO) {
            try {
                if (!ApiRateLimiter.tryAcquire("water_list")) {
                    Log.w(TAG, "Rate limited: water-list API exceeded 5 requests / 10 minutes")
                    RateLimitNotifier.show(context)
                    return@withContext null
                }
                val payload = buildRequestPayload(termno)
                Log.i(TAG, "API2 request: date=${payload.optString("date")}, calendarBh=${payload.optString("calendarBh")}")
                val requestBody = ApiService.jsonToRequestBody(payload)
                val respBody = apiService.getWaterListData(requestBody)

                if (respBody == null) {
                    Log.e(TAG, "API2 returned null body")
                    return@withContext null
                }

                val respStr = try { respBody.string() } catch (e: Exception) {
                    Log.e(TAG, "Failed to read API2 response body", e)
                    return@withContext null
                }

                Log.i(TAG, "API2 response[network] preview=${respStr.take(500)}")
                val wr = WaterListResponse.fromJson(respStr)

                if (!wr.success) {
                    Log.e(TAG, "API2 failed: code=${wr.code}, msg=${wr.msg}")
                    if (wr.code == 400 || wr.code == 401 || wr.code == 403 ||
                        wr.msg.contains("请登录") || wr.msg.contains("未登录")) {
                        val tm = org.xjtuai.kqchecker.auth.TokenManager(context)
                        tm.clear()
                        tm.notifyTokenInvalid()
                        throw org.xjtuai.kqchecker.auth.AuthRequiredException(wr.msg)
                    }
                    return@withContext null
                }

                cacheManager.saveToCache(CacheManager.WATER_LIST_CACHE_FILE, respStr)
                Log.i(TAG, "API2 success: ${wr.data.list.size} records, total=${wr.data.totalCount}")
                wr

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "API2 timeout", e)
                throw e
            } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "API2 error", e)
                null
            }
        }
    }

    suspend fun refreshWaterListData(termno: String? = null): WaterListResponse? {
        return getWaterListData(termno)
    }

    private suspend fun buildRequestPayload(termno: String? = null): JSONObject {
        val payload = JSONObject()
        payload.put("action", "getWaterList")
        val date = cacheManager.getCurrentDate()
        payload.put("date", date)
        payload.put("startdate", date)
        payload.put("enddate", date)
        payload.put("pageSize", 10)
        payload.put("current", 1)

        val resolvedBh = resolveTermBh(termno)
        if (!resolvedBh.isNullOrBlank()) {
            payload.put("termno", resolvedBh)
            payload.put("calendarBh", resolvedBh)
        }
        return payload
    }

    private suspend fun resolveTermBh(explicitTermno: String? = null): String? {
        val provided = explicitTermno?.trim()
        if (!provided.isNullOrEmpty()) return provided

        try {
            val termInfo = termRepository.fetchCurrentTerm()
            if (termInfo != null && termInfo.success && termInfo.bh > 0) {
                return termInfo.bh.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve term from current term API", e)
        }

        val configTerm = ConfigHelper.getConfig(context).termNo?.toString()
        if (!configTerm.isNullOrBlank()) return configTerm

        return null
    }

    fun getWaterListCacheStatus(): CacheStatus {
        val cacheExists = cacheManager.cacheFileExists(CacheManager.WATER_LIST_CACHE_FILE)
        val cacheInfo = cacheManager.getCacheFileInfo(CacheManager.WATER_LIST_CACHE_FILE)
        return CacheStatus(
            exists = cacheExists,
            isExpired = !cacheExists,
            expiresDate = null,
            fileInfo = cacheInfo
        )
    }

    fun getWaterListCacheFilePath(): String = CacheManager.WATER_LIST_CACHE_FILE

    suspend fun getApi2FilePreviews(): List<FilePreview> {
        return withContext(Dispatchers.IO) {
            val files = listOf(CacheManager.WATER_LIST_CACHE_FILE, CacheManager.API2_QUERY_LOG_FILE)
            val result = mutableListOf<FilePreview>()
            for (fname in files) {
                try {
                    val info = cacheManager.getCacheFileInfo(fname) ?: continue
                    val content = cacheManager.readFromCache(fname) ?: ""
                    val preview = if (content.length > 4000) content.substring(0, 4000) + "... (truncated)" else content
                    result.add(FilePreview(name = fname, path = info.path, size = info.size, lastModified = info.lastModified, preview = preview))
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing API2 preview for $fname", e)
                }
            }
            result
        }
    }
}
