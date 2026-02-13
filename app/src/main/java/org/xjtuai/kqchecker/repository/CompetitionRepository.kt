package org.xjtuai.kqchecker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.xjtuai.kqchecker.network.ApiService
import org.xjtuai.kqchecker.network.CompetitionApiInterceptor
import org.xjtuai.kqchecker.network.CompetitionResponse
import java.util.concurrent.TimeUnit

/**
 * 竞赛数据仓库类，负责处理竞赛数据的获取、缓存和业务逻辑
 */
class CompetitionRepository(private val context: Context) {
    companion object {
        private const val TAG = "CompetitionRepository"
        private const val COMPETITION_BASE_URL = "https://api.harco.top/"
    }

    private val competitionApiService: ApiService
    private val cacheManager = CacheManager(context)

    init {
        val client = createCompetitionClient()
        competitionApiService = ApiService.create(client, COMPETITION_BASE_URL)
    }

    private fun createCompetitionClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(CompetitionApiInterceptor())
            .build()
    }

    /**
     * 获取竞赛数据
     * 1. 首先检查缓存是否存在
     * 2. 如果缓存存在且非强制刷新，从缓存中读取
     * 3. 如果缓存不存在或强制刷新，从API获取新数据
     *
     * @param forceRefresh 是否强制从API刷新
     * @return CompetitionResponse 竞赛数据响应，失败返回 null
     */
    suspend fun getCompetitionData(forceRefresh: Boolean = false): CompetitionResponse? {
        Log.d(TAG, "开始获取竞赛数据，强制刷新: $forceRefresh")
        return withContext(Dispatchers.IO) {
            try {
                // 如果不是强制刷新，尝试从缓存获取
                if (!forceRefresh) {
                    Log.d(TAG, "非强制刷新模式，先尝试从缓存获取")
                    val cachedData = getCompetitionDataFromCache()
                    if (cachedData != null) {
                        Log.d(TAG, "✅ 缓存数据有效，返回缓存数据，共 ${cachedData.data.size} 项")
                        return@withContext cachedData
                    } else {
                        Log.d(TAG, "⚠️ 缓存不存在，需要从API获取")
                    }
                } else {
                    Log.d(TAG, "强制刷新模式，跳过缓存检查，直接从API获取")
                }

                // 从API获取新数据
                Log.d(TAG, "开始从API获取竞赛数据...")
                val startTime = System.currentTimeMillis()
                val apiData = getCompetitionDataFromApi()
                val endTime = System.currentTimeMillis()

                Log.d(TAG, "API请求耗时: ${endTime - startTime}ms")

                if (apiData != null) {
                    Log.d(TAG, "✅ API数据获取成功，共 ${apiData.data.size} 项")
                } else {
                    Log.e(TAG, "❌ API数据获取失败")
                }
                return@withContext apiData
            } catch (e: Exception) {
                Log.e(TAG, "❌ 获取竞赛数据时发生异常", e)
                null
            }
        }
    }

    /**
     * 从缓存中获取竞赛数据
     */
    private fun getCompetitionDataFromCache(): CompetitionResponse? {
        Log.d(TAG, "检查缓存数据...")
        return try {
            val jsonContent = cacheManager.readFromCache(CacheManager.COMPETITION_CACHE_FILE)
            Log.d(TAG, "缓存数据存在: ${jsonContent != null}")

            if (jsonContent != null) {
                Log.d(TAG, "缓存数据长度: ${jsonContent.length} 字符")
                val response = CompetitionResponse.fromJson(jsonContent)
                Log.d(TAG, "缓存数据解析结果 - status: ${response.status}, itemCount: ${response.data.size}")
                response
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "从缓存获取竞赛数据时发生异常", e)
            null
        }
    }

    /**
     * 从API获取竞赛数据
     */
    private suspend fun getCompetitionDataFromApi(): CompetitionResponse? {
        Log.d(TAG, "开始API请求...")
        return try {
            Log.d(TAG, "发送API请求到: ${COMPETITION_BASE_URL}xjtudean")
            val respBody = competitionApiService.getCompetitionData()

            // 验证响应
            if (respBody == null) {
                Log.e(TAG, "❌ API returned null response")
                return null
            }

            val responseString = try {
                respBody.string()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read response body", e)
                return null
            }

            Log.d(TAG, "API响应内容长度: ${responseString.length} 字符")
            Log.d(TAG, "API响应内容预览: ${responseString.take(200)}...")

            // 转换为CompetitionResponse
            Log.d(TAG, "解析API响应数据...")
            val response = CompetitionResponse.fromJson(responseString)

            if (response.status != "success") {
                Log.e(TAG, "❌ Invalid API response: status=${response.status}")
                return null
            }

            Log.d(TAG, "✅ API数据解析成功")

            // 缓存响应数据
            try {
                Log.d(TAG, "开始缓存数据...")
                cacheManager.saveToCache(CacheManager.COMPETITION_CACHE_FILE, responseString)
                Log.d(TAG, "✅ 成功缓存竞赛数据")
            } catch (e: Exception) {
                Log.w(TAG, "缓存数据时发生异常", e)
            }

            return response
        } catch (e: Exception) {
            Log.e(TAG, "❌ 从API获取竞赛数据时发生异常", e)
            null
        }
    }
}
