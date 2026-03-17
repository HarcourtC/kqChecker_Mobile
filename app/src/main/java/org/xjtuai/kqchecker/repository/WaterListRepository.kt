package org.xjtuai.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import org.xjtuai.kqchecker.api2.Api2ResponseParser
import org.xjtuai.kqchecker.network.ApiClient
import org.xjtuai.kqchecker.network.ApiService
import org.xjtuai.kqchecker.network.WaterListResponse
import org.xjtuai.kqchecker.util.ConfigHelper
import org.json.JSONObject
import java.net.SocketTimeoutException

/**
 * 水课表仓库类，负责处理API2（水课表）数据的获取、缓存和业务逻辑
 */
class WaterListRepository(private val context: Context) {
    companion object {
        private const val TAG = "WaterListRepository"
        private const val MAX_LOG_PREVIEW = 500
        private const val MAX_PERSISTED_PREVIEW = 2000
    }
    
    private val apiClient = ApiClient(context)
    private val baseUrl: String = ConfigHelper.getBaseUrl(context)
    private val apiService = apiClient.createService(baseUrl)
    private val cacheManager = CacheManager(context)
    private val termRepository = TermRepository(context)
    
    /**
     * 获取水课表数据（API2）
     * @param termno 学期号，可选，如果为空则从配置中读取
     */
    suspend fun getWaterListData(termno: String? = null, forceRefresh: Boolean = false): WaterListResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "API2 request start: forceRefresh=$forceRefresh, explicitTerm=${termno ?: "<auto>"}")
                return@withContext getWaterListDataFromApi(termno)
                
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Network timeout when fetching water list data", e)
                appendApi2DebugLog(
                    stage = "api_call",
                    success = false,
                    detail = JSONObject().apply {
                        put("error", "timeout")
                        put("message", e.message ?: "timeout")
                    }
                )
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching water list data from API", e)
                // 如果有缓存，尝试从缓存读取
                if (!forceRefresh) {
                    val cachedData = getWaterListDataFromCache()
                    if (cachedData != null) {
                        Log.w(TAG, "Using cached water list data due to API error")
                        return@withContext cachedData
                    }
                }
                null
            }
        }
    }
    
    /**
     * 从缓存中获取水课表数据
     */
    private fun getWaterListDataFromCache(): WaterListResponse? {
        try {
            val jsonContent = cacheManager.readFromCache(CacheManager.WATER_LIST_CACHE_FILE)
            if (jsonContent != null) {
                logApi2ResponseSummary(source = "cache", responseText = jsonContent)
                appendApi2DebugLog(
                    stage = "cache_read",
                    success = true,
                    detail = buildApi2SummaryJson("cache", jsonContent)
                )
                return WaterListResponse.fromJson(jsonContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading water list data from cache", e)
        }
        return null
    }
    
    /**
     * 从API获取水课表数据
     * @param termno 学期号，可选
     */
    private suspend fun getWaterListDataFromApi(termno: String? = null): WaterListResponse? {
        try {
            val requestPayload = buildWaterListRequestPayload(
                date = cacheManager.getCurrentDate(),
                termno = termno
            )
            logApi2RequestPayload(requestPayload, "repository")
            appendApi2DebugLog(
                stage = "request_payload",
                success = true,
                detail = JSONObject(requestPayload.toString())
            )
            val requestBody = ApiService.jsonToRequestBody(requestPayload)
            
            // 调用API
            val respBody = apiService.getWaterListData(requestBody)

            if (respBody == null) {
                Log.e(TAG, "api2 returned null")
                return null
            }

            val respStr = try { respBody.string() } catch (e: Exception) {
                Log.e(TAG, "Failed to read api2 response body", e)
                return null
            }

            Log.d(TAG, "api2 resp length: ${respStr.length}")
            logApi2ResponseSummary(source = "network", responseText = respStr)
            val wr = WaterListResponse.fromJson(respStr)

            if (!wr.success) {
                Log.e(TAG, "Invalid API2 response: code=${wr.code}, success=${wr.success}, data=${wr.data}")
                appendApi2DebugLog(
                    stage = "response_invalid",
                    success = false,
                    detail = buildApi2SummaryJson("network", respStr)
                )
                // 若后端认为未登录或返回认证失败，通知用户
                try {
                    val tm = org.xjtuai.kqchecker.auth.TokenManager(context)
                    if (wr.code == 400 || wr.code == 401 || wr.code == 403 || wr.msg.contains("请登录") || wr.msg.contains("未登录")) {
                            // 先清除过期 token，再通知用户
                            tm.clear()
                            tm.notifyTokenInvalid()
                            // 抛出认证异常，交由 UI 层处理（例如弹出登录）
                    throw org.xjtuai.kqchecker.auth.AuthRequiredException(wr.msg)
                        }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to notify token invalid", t)
                }
                return null
            }

            // 保存到缓存
            cacheManager.saveToCache(CacheManager.WATER_LIST_CACHE_FILE, respStr)
            appendApi2DebugLog(
                stage = "response_ok",
                success = true,
                detail = buildApi2SummaryJson("network", respStr)
            )

            Log.d(TAG, "Successfully fetched and cached water list data")
            return wr
        } catch (e: Exception) {
            Log.e(TAG, "Error in getWaterListDataFromApi", e)
            throw e
        }
    }
    
    /**
     * 构造 API2 请求体，统一学期参数解析逻辑。
     */
    suspend fun buildWaterListRequestBody(date: String, termno: String? = null): RequestBody {
        return ApiService.jsonToRequestBody(buildWaterListRequestPayload(date, termno))
    }

    suspend fun buildWaterListRequestPayload(date: String, termno: String? = null): JSONObject {
        val requestData = JSONObject()
        requestData.put("action", "getWaterList")
        requestData.put("date", date)
        requestData.put("startdate", date)
        requestData.put("enddate", date)
        requestData.put("pageSize", 10)
        requestData.put("current", 1)

        val resolvedBh = resolveApi2TermBh(termno)
        if (!resolvedBh.isNullOrBlank()) {
            requestData.put("termno", resolvedBh)
            requestData.put("calendarBh", resolvedBh)
        }

        Log.i(
            TAG,
            "API2 payload prepared: date=$date, termno=${requestData.optString("termno", "")}, calendarBh=${requestData.optString("calendarBh", "")}"
        )

        return requestData
    }

    private suspend fun resolveApi2TermBh(explicitTermno: String? = null): String? {
        val provided = explicitTermno?.trim()
        if (!provided.isNullOrEmpty()) {
            Log.i(TAG, "Using explicit API2 term value: $provided")
            return provided
        }

        try {
            val termInfo = termRepository.fetchCurrentTerm()
            if (termInfo != null && termInfo.success && termInfo.bh > 0) {
                val resolved = termInfo.bh.toString()
                Log.i(TAG, "Resolved API2 term from current term API: bh=$resolved, currentWeek=${termInfo.currentWeek}")
                return resolved
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve API2 term from current term API", e)
        }

        val configTerm = ConfigHelper.getConfig(context).termNo?.toString()
        if (!configTerm.isNullOrBlank()) {
            Log.i(TAG, "Resolved API2 term from config termNo: $configTerm")
            return configTerm
        }

        Log.w(TAG, "API2 term resolution failed: no explicit term, no current term bh, no config termNo")
        return null
    }
    
    /**
     * 强制刷新水课表数据
     */
    suspend fun refreshWaterListData(termno: String? = null): WaterListResponse? {
        return getWaterListData(termno, forceRefresh = true)
    }
    
    /**
     * 检查水课表缓存状态
     */
    fun getWaterListCacheStatus(): CacheStatus {
        val cacheExists = cacheManager.cacheFileExists(CacheManager.WATER_LIST_CACHE_FILE)
        val cacheInfo = cacheManager.getCacheFileInfo(CacheManager.WATER_LIST_CACHE_FILE)
        
        // 水课表缓存没有明确的过期时间，这里简单返回是否存在
        return CacheStatus(
            exists = cacheExists,
            isExpired = !cacheExists, // 如果不存在则认为已过期
            expiresDate = null, // 没有明确的过期日期
            fileInfo = cacheInfo
        )
    }
    
    /**
     * 获取水课表缓存文件路径（用于导出）
     */
    fun getWaterListCacheFilePath(): String {
        return CacheManager.WATER_LIST_CACHE_FILE
    }

    suspend fun getApi2FilePreviews(): List<FilePreview> {
        return withContext(Dispatchers.IO) {
            val files = listOf(
                CacheManager.WATER_LIST_CACHE_FILE,
                CacheManager.API2_QUERY_LOG_FILE
            )

            val result = mutableListOf<FilePreview>()
            for (fname in files) {
                try {
                    val info = cacheManager.getCacheFileInfo(fname) ?: continue
                    val content = cacheManager.readFromCache(fname) ?: ""
                    val preview = if (content.length > 4000) {
                        content.substring(0, 4000) + "... (truncated)"
                    } else {
                        content
                    }
                    result.add(
                        FilePreview(
                            name = fname,
                            path = info.path,
                            size = info.size,
                            lastModified = info.lastModified,
                            preview = preview
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing API2 preview for $fname", e)
                }
            }
            result
        }
    }

    private fun logApi2RequestPayload(payload: JSONObject, source: String) {
        Log.i(
            TAG,
            "API2 request[$source]: date=${payload.optString("date")}, start=${payload.optString("startdate")}, end=${payload.optString("enddate")}, termno=${payload.optString("termno")}, calendarBh=${payload.optString("calendarBh")}, pageSize=${payload.optInt("pageSize")}, current=${payload.optInt("current")}"
        )
    }

    private fun logApi2ResponseSummary(source: String, responseText: String) {
        val summary = buildApi2SummaryJson(source, responseText)
        Log.i(
            TAG,
            "API2 response[$source]: code=${summary.optInt("code", -1)}, success=${summary.optBoolean("success")}, items=${summary.optInt("itemCount", -1)}, total=${summary.optInt("totalCount", -1)}, msg=${summary.optString("msg")}"
        )
        Log.d(TAG, "API2 response[$source] preview=${summary.optString("preview")}")
    }

    private fun buildApi2SummaryJson(source: String, responseText: String): JSONObject {
        val summary = JSONObject().apply {
            put("source", source)
            put("preview", responseText.take(MAX_LOG_PREVIEW))
        }

        return try {
            val root = JSONObject(responseText)
            val dataObject = root.optJSONObject("data")
            val recordsCount = dataObject?.optJSONArray("records")?.length() ?: -1
            val listCount = dataObject?.optJSONArray("list")?.length() ?: -1
            val itemCount = maxOf(recordsCount, listCount)
            val totalCount = when {
                dataObject == null -> -1
                dataObject.has("total") -> dataObject.optInt("total", -1)
                dataObject.has("totalCount") -> dataObject.optInt("totalCount", -1)
                else -> -1
            }
            val firstCalendarBh = dataObject
                ?.optJSONArray("records")
                ?.optJSONObject(0)
                ?.optString("calendarBh")
                ?.takeIf { it.isNotBlank() }
                ?: dataObject
                    ?.optJSONArray("list")
                    ?.optJSONObject(0)
                    ?.optString("calendarBh")
                    ?.takeIf { it.isNotBlank() }

            summary.put("code", root.optInt("code", -1))
            summary.put("success", root.optBoolean("success", false))
            summary.put("msg", root.optString("msg", ""))
            summary.put("itemCount", itemCount)
            summary.put("totalCount", totalCount)
            summary.put("firstCalendarBh", firstCalendarBh ?: "")
            summary
        } catch (e: Exception) {
            summary.put("parseError", e.message ?: "parse failed")
            summary
        }
    }

    private fun appendApi2DebugLog(stage: String, success: Boolean, detail: JSONObject) {
        try {
            val existing = cacheManager.readFromCache(CacheManager.API2_QUERY_LOG_FILE)
            val arr = if (!existing.isNullOrBlank()) {
                runCatching { org.json.JSONArray(existing) }.getOrElse { org.json.JSONArray() }
            } else {
                org.json.JSONArray()
            }

            val record = JSONObject().apply {
                put("stage", stage)
                put("success", success)
                put("timestamp", cacheManager.getCurrentDate())
                put("detail", JSONObject(detail.toString()).apply {
                    if (has("preview")) {
                        put("preview", optString("preview").take(MAX_PERSISTED_PREVIEW))
                    }
                })
            }
            arr.put(record)

            // Keep the log bounded for easier on-device inspection.
            val bounded = org.json.JSONArray()
            val start = maxOf(0, arr.length() - 20)
            for (i in start until arr.length()) {
                bounded.put(arr.get(i))
            }

            cacheManager.saveToCache(CacheManager.API2_QUERY_LOG_FILE, bounded.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append API2 debug log", e)
        }
    }

    fun appendApi2QueryEvent(
        source: String,
        stage: String,
        success: Boolean,
        key: String? = null,
        date: String? = null,
        requestPayload: JSONObject? = null,
        responseJson: JSONObject? = null,
        message: String? = null
    ) {
        val detail = JSONObject().apply {
            put("source", source)
            key?.let { put("key", it) }
            date?.let { put("date", it) }
            requestPayload?.let { put("request", JSONObject(it.toString())) }

            if (responseJson != null) {
                put("code", responseJson.optInt("code", -1))
                put("msg", responseJson.optString("msg", ""))
                put("responsePreview", responseJson.toString().take(MAX_LOG_PREVIEW))
                val candidates = Api2ResponseParser.extractCandidates(responseJson)
                put("itemCount", candidates?.length() ?: -1)
            }

            if (!message.isNullOrBlank()) {
                put("message", message.take(MAX_PERSISTED_PREVIEW))
            }
        }
        appendApi2DebugLog(stage = stage, success = success, detail = detail)
    }
}
