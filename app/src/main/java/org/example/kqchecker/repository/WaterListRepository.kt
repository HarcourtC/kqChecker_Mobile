package org.example.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.kqchecker.network.ApiClient
import org.example.kqchecker.network.ApiService
import org.example.kqchecker.network.WaterListResponse
import org.json.JSONObject
import java.net.SocketTimeoutException

/**
 * 水课表仓库类，负责处理API2（水课表）数据的获取、缓存和业务逻辑
 */
class WaterListRepository(private val context: Context) {
    companion object {
        private const val TAG = "WaterListRepository"
    }
    
    private val apiClient = ApiClient(context)
    private val apiService = apiClient.createService(getBaseUrl())
    
    private fun getBaseUrl(): String {
        // 默认基础URL
        var baseUrl = "https://api.example.com/"
        try {
            context.assets.open("config.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val json = org.json.JSONObject(text)
                if (json.has("base_url")) {
                    baseUrl = json.getString("base_url")
                    // 确保URL以/结尾
                    if (!baseUrl.endsWith("/")) {
                        baseUrl += '/'
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No valid config.json, using default base URL")
        }
        return baseUrl
    }
    private val cacheManager = CacheManager(context)
    
    /**
     * 获取水课表数据（API2）
     * @param termno 学期号，可选，如果为空则从配置中读取
     */
    suspend fun getWaterListData(termno: String? = null, forceRefresh: Boolean = false): WaterListResponse? {
        return withContext(Dispatchers.IO) {
            try {
                // 从API获取数据
                Log.d(TAG, "Fetching water list data from API (API2)")
                return@withContext getWaterListDataFromApi(termno)

            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Network timeout when fetching water list data", e)
                throw e
            } catch (e: Exception) {
                // 如果是认证失败异常，向上抛出以便 UI/调用方做登录处理
                if (e is org.example.kqchecker.auth.AuthRequiredException) {
                    Log.w(TAG, "Authentication required while fetching water list: rethrowing AuthRequiredException")
                    throw e
                }

                Log.e(TAG, "Error fetching water list data from API", e)
                // 如果有缓存，尝试从缓存读取
                if (!forceRefresh) {
                    val cachedData = getWaterListDataFromCache()
                    if (cachedData != null) {
                        Log.d(TAG, "Using cached water list data due to API error")
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
            // 准备请求参数
            val requestData = JSONObject()
            requestData.put("action", "getWaterList")
            
            // 设置日期（当前日期）
            requestData.put("date", cacheManager.getCurrentDate())
            
            // 设置学期号
            val effectiveTermno = termno ?: getDefaultTermno()
            if (effectiveTermno.isNotEmpty()) {
                requestData.put("termno", effectiveTermno)
            }
            
            // 转换为RequestBody
            val requestBody = ApiService.jsonToRequestBody(requestData)
            
            // 调用API，使用 Retrofit Response 来能够检查 HTTP 状态码和错误体
            val resp = apiService.getWaterListData(requestBody)

            if (!resp.isSuccessful) {
                val httpCode = resp.code()
                Log.e(TAG, "api2 http error code: $httpCode")
                // 尝试读取错误体中的信息
                val errBodyStr = try { resp.errorBody()?.string() ?: "" } catch (e: Exception) { "" }
                Log.d(TAG, "api2 error body: ${errBodyStr.length}")

                // 依据HTTP状态判断是否为认证相关错误；对认证错误立即清理本地 Token 并通知
                val tm = org.example.kqchecker.auth.TokenManager(context)
                if (httpCode == 400 || httpCode == 401 || httpCode == 403) {
                    try {
                        tm.clear()
                    } catch (_: Throwable) { }
                    try {
                        tm.notifyTokenInvalid()
                    } catch (_: Throwable) { }
                    throw org.example.kqchecker.auth.AuthRequiredException("HTTP $httpCode: authentication required")
                }

                // 有时后端会在 errorBody 中返回 JSON 包含 code/msg，尝试解析并处理
                if (errBodyStr.isNotEmpty()) {
                    try {
                        val maybe = WaterListResponse.fromJson(errBodyStr)
                        if (!maybe.success) {
                            if (maybe.code == 400 || maybe.code == 401 || maybe.code == 403 || maybe.msg.contains("请登录") || maybe.msg.contains("未登录")) {
                                try { tm.clear() } catch (_: Throwable) {}
                                try { tm.notifyTokenInvalid() } catch (_: Throwable) {}
                                throw org.example.kqchecker.auth.AuthRequiredException(maybe.msg)
                            }
                        }
                    } catch (_: Exception) {
                        // ignore parse errors
                    }
                }

                // 非认证类的HTTP错误或未能处理，回退尝试使用缓存
                return null
            }

            val respBody = resp.body()
            if (respBody == null) {
                Log.e(TAG, "api2 returned empty body despite successful HTTP response")
                return null
            }

            val respStr = try { respBody.string() } catch (e: Exception) {
                Log.e(TAG, "Failed to read api2 response body", e)
                return null
            }

            Log.d(TAG, "api2 resp length: ${respStr.length}")
            val wr = try { WaterListResponse.fromJson(respStr) } catch (e: Exception) {
                Log.e(TAG, "Failed to parse api2 json response", e)
                null
            }

            if (wr == null || !wr.success) {
                Log.e(TAG, "Invalid API2 response: code=${wr?.code}, success=${wr?.success}, data=${wr?.data}")
                // 认证类错误：立即清理本地 token，通知用户并抛出异常交由上层处理
                if (wr != null) {
                    if (wr.code == 400 || wr.code == 401 || wr.code == 403 || wr.msg.contains("请登录") || wr.msg.contains("未登录")) {
                        val tm = org.example.kqchecker.auth.TokenManager(context)
                        try { tm.clear() } catch (_: Throwable) {}
                        try { tm.notifyTokenInvalid() } catch (_: Throwable) {}
                        throw org.example.kqchecker.auth.AuthRequiredException(wr.msg)
                    }
                }
                return null
            }

            // 保存到缓存
            cacheManager.saveToCache(CacheManager.WATER_LIST_CACHE_FILE, respStr)

            Log.d(TAG, "Successfully fetched and cached water list data")
            return wr
        } catch (e: Exception) {
            Log.e(TAG, "Error in getWaterListDataFromApi", e)
            throw e
        }
    }
    
    /**
     * 获取默认学期号（可以从配置文件或其他来源读取）
     */
    private fun getDefaultTermno(): String {
        // 这里可以实现从配置文件或其他来源读取学期号的逻辑
        // 暂时返回空字符串，让API服务处理默认值
        return ""
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
}