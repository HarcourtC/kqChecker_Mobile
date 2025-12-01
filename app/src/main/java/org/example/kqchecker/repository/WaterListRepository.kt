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
            
            // 调用API
            val jsonResponse = apiService.getWaterListData(requestBody)
            
            // 验证响应
            if (jsonResponse == null) {
                Log.e(TAG, "API2 returned null response")
                return null
            }
            
            // 转换为WaterListResponse
            val response = WaterListResponse.fromJson(jsonResponse.toString())
            
            if (response == null || response.code != 0 || response.data == null) {
                Log.e(TAG, "Invalid API2 response: code=${response?.code}, data=${response?.data}")
                return null
            }
            
            // 保存到缓存
            cacheManager.saveToCache(CacheManager.WATER_LIST_CACHE_FILE, jsonResponse.toString())
            
            Log.d(TAG, "Successfully fetched and cached water list data")
            return response
            
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