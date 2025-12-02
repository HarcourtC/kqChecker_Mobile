package org.example.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import org.example.kqchecker.network.ApiClient
import org.example.kqchecker.network.ApiService
import org.example.kqchecker.network.WeeklyResponse
import org.json.JSONObject
import java.net.SocketTimeoutException

/**
 * 周课表仓库类，负责处理周课表数据的获取、缓存和业务逻辑
 */
class WeeklyRepository(private val context: Context) {
    companion object {
        private const val TAG = "WeeklyRepository"
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
     * 获取周课表数据
     * 1. 首先检查缓存是否有效
     * 2. 如果缓存有效，从缓存中读取
     * 3. 如果缓存无效或不存在，从API获取新数据
     */
    suspend fun getWeeklyData(forceRefresh: Boolean = false): WeeklyResponse? {
        Log.d(TAG, "开始获取周课表数据，强制刷新: $forceRefresh")
        return withContext(Dispatchers.IO) {
            try {
                // 如果不是强制刷新且缓存有效，从缓存读取
                if (!forceRefresh) {
                    Log.d(TAG, "非强制刷新模式，先尝试从缓存获取")
                    if (!cacheManager.isWeeklyCacheExpired()) {
                        Log.d(TAG, "✅ 缓存数据有效，返回缓存数据")
                        return@withContext getWeeklyDataFromCache()
                    } else {
                        Log.d(TAG, "⚠️ 缓存数据无效或已过期，需要从API获取")
                    }
                } else {
                    Log.d(TAG, "强制刷新模式，跳过缓存检查，直接从API获取")
                }
                
                // 从API获取新数据
                Log.d(TAG, "开始从API获取数据...")
                val startTime = System.currentTimeMillis()
                val apiData = getWeeklyDataFromApi()
                val endTime = System.currentTimeMillis()
                
                Log.d(TAG, "API请求耗时: ${endTime - startTime}ms")
                
                if (apiData != null) {
                    Log.d(TAG, "✅ API数据获取成功")
                } else {
                    Log.e(TAG, "❌ API数据获取失败")
                }
                return@withContext apiData
            } catch (e: Exception) {
                Log.e(TAG, "❌ 获取周课表数据时发生异常", e)
                null
            }
        }
    }
    
    /**
     * 从缓存中获取周课表数据
     */
    private fun getWeeklyDataFromCache(): WeeklyResponse? {
        Log.d(TAG, "检查缓存数据...")
        try {
            val jsonContent = cacheManager.readFromCache(CacheManager.WEEKLY_CACHE_FILE)
            Log.d(TAG, "缓存数据存在: ${jsonContent != null}")
            
                if (jsonContent != null) {
                Log.d(TAG, "缓存数据长度: ${jsonContent.length} 字符")
                val response = WeeklyResponse.fromJson(jsonContent)
                Log.d(TAG, "缓存数据解析结果 - success: ${response.success}, dataCount: ${response.data.length()}")
                return response
            }
        } catch (e: Exception) {
            Log.e(TAG, "从缓存获取周课表数据时发生异常", e)
        }
        return null
    }
    
    /**
     * 从API获取周课表数据
     */
    private suspend fun getWeeklyDataFromApi(): WeeklyResponse? {
        Log.d(TAG, "开始API请求...")
        return try {
            // 创建请求体
            Log.d(TAG, "准备请求参数...")
            val requestBody = createWeeklyRequest()
            
            Log.d(TAG, "发送API请求到: ${getBaseUrl()}attendance-student/rankClass/getWeekSchedule2")
            val respBody = apiService.getWeeklyData(requestBody)

            // 验证响应
            if (respBody == null) {
                Log.e(TAG, "❌ API returned null response")
                return null
            }

            val responseString = try { respBody.string() } catch (e: Exception) {
                Log.e(TAG, "Failed to read response body", e)
                return null
            }

            Log.d(TAG, "API响应内容长度: ${responseString.length} 字符")
            Log.d(TAG, "API响应内容预览: ${responseString.take(100)}...")

            // 转换为WeeklyResponse
            Log.d(TAG, "解析API响应数据...")
            val response = WeeklyResponse.fromJson(responseString)
            
            if (!response.success || response.data.length() == 0) {
                Log.e(TAG, "❌ Invalid API response: success=${response.success}, dataCount=${response.data.length()}")
                // 若后端提示未登录或返回认证类错误（code 400/401/403 或 msg 包含登录提示），通知用户重新登录
                try {
                    val tm = org.example.kqchecker.auth.TokenManager(context)
                    if (response.code == 400 || response.code == 401 || response.code == 403 || response.msg.contains("请登录") || response.msg.contains("未登录")) {
                        tm.notifyTokenInvalid()
                        // 抛出认证异常，交由 UI 层处理（例如弹出登录）
                        throw org.example.kqchecker.auth.AuthRequiredException(response.msg)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to notify token invalid", t)
                }
                return null
            }
            
            Log.d(TAG, "✅ API数据解析成功")
            
            // 添加缓存过期信息
            val weekendDate = cacheManager.getCurrentWeekendDate()
            Log.d(TAG, "设置缓存过期时间: $weekendDate")
            
            val cacheJson = response.toJson().toString()
            val jsonWithExpires = cacheJson.replace(
                "{", 
                "{\"expires\":\"$weekendDate\"," 
            )
            
            // 保存到缓存（处理后的响应，包含 expires 字段）
            Log.d(TAG, "开始缓存数据...")
            cacheManager.saveToCache(CacheManager.WEEKLY_CACHE_FILE, jsonWithExpires)

            // 保存原始响应（在原始响应中注入 expires 字段，存为 weekly_raw.json）
            val rawWithExpires = if (responseString.trimStart().startsWith("{")) {
                // 将 expires 注入到原始 JSON 的开头位置
                responseString.replaceFirst("{", "{\"expires\":\"$weekendDate\",")
            } else {
                // 回退：如果不是 JSON 对象，仍保存原始内容并在 meta 中记录过期
                responseString
            }
            cacheManager.saveToCache(CacheManager.WEEKLY_RAW_CACHE_FILE, rawWithExpires)

            // 保存元数据
            val metaData = "{\"last_fetched\":\"${cacheManager.getCurrentDate()}\",\"expires\":\"$weekendDate\"}"
            cacheManager.saveToCache(CacheManager.WEEKLY_RAW_META_FILE, metaData)
            
            Log.d(TAG, "✅ 成功获取并缓存周课表数据")
            return response
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "❌ 网络请求超时", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 从API获取周课表数据时发生异常", e)
            null
        }
    }
    
    /**
     * 创建周课表请求体
     */
    private fun createWeeklyRequest(): RequestBody {
        val payloadObj = JSONObject()
        
        // 尝试从配置文件读取termNo和week
        try {
            context.assets.open("config.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val config = JSONObject(text)
                if (config.has("termNo")) {
                    payloadObj.put("termNo", config.getInt("termNo"))
                }
                if (config.has("week")) {
                    payloadObj.put("week", config.getInt("week"))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No termNo/week in config.json, using empty request body")
        }
        
        return ApiService.jsonToRequestBody(payloadObj)
    }
    
    /**
     * 强制刷新周课表数据
     */
    suspend fun refreshWeeklyData(): WeeklyResponse? {
        Log.d(TAG, "========== refreshWeeklyData() 调用开始 ==========")
        val result = getWeeklyData(forceRefresh = true)
        Log.d(TAG, "========== refreshWeeklyData() 调用结束，结果: ${if (result != null) "成功" else "失败"} ==========")
        return result
    }
    
    /**
     * 检查缓存状态
     */
    fun getCacheStatus(): CacheStatus {
        val cacheExists = cacheManager.cacheFileExists(CacheManager.WEEKLY_CACHE_FILE)
        val isExpired = if (cacheExists) cacheManager.isWeeklyCacheExpired() else true
        val expiresDate = cacheManager.getWeeklyCacheExpiresDate()
        val cacheInfo = cacheManager.getCacheFileInfo(CacheManager.WEEKLY_CACHE_FILE)
        
        return CacheStatus(
            exists = cacheExists,
            isExpired = isExpired,
            expiresDate = expiresDate,
            fileInfo = cacheInfo
        )
    }
    
    /**
     * 获取缓存文件路径（用于导出）
     */
    fun getWeeklyCacheFilePath(): String {
        return CacheManager.WEEKLY_CACHE_FILE
    }

    /**
     * 获取 weekly 相关缓存文件的预览（用于 UI 打印）
     */
    suspend fun getWeeklyFilePreviews(): List<FilePreview> {
        return withContext(Dispatchers.IO) {
            val files = listOf(
                CacheManager.WEEKLY_CACHE_FILE,
                CacheManager.WEEKLY_RAW_CACHE_FILE,
                CacheManager.WEEKLY_RAW_META_FILE
            )

            val result = mutableListOf<FilePreview>()
            for (fname in files) {
                try {
                    val info = cacheManager.getCacheFileInfo(fname)
                    if (info == null) continue
                    val content = cacheManager.readFromCache(fname) ?: ""
                    val preview = if (content.length > 4000) content.substring(0, 4000) + "... (truncated)" else content
                    result.add(FilePreview(name = fname, path = info.path, size = info.size, lastModified = info.lastModified, preview = preview))
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing preview for $fname", e)
                }
            }
            result
        }
    }
}

/**
 * 缓存状态数据类
 */
data class CacheStatus(
    val exists: Boolean,
    val isExpired: Boolean,
    val expiresDate: String?,
    val fileInfo: CacheFileInfo?
)

/**
 * 单个缓存文件的预览信息
 */
data class FilePreview(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val preview: String
)
