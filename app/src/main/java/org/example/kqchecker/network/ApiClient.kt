package org.example.kqchecker.network

import android.content.Context
import okhttp3.OkHttpClient
import org.example.kqchecker.auth.TokenManager
import java.util.concurrent.TimeUnit

/**
 * API客户端工厂，负责创建和配置OkHttpClient
 */
class ApiClient(context: Context) {
    private val tokenManager = TokenManager(context)
    private val tokenInterceptor = TokenInterceptor(tokenManager)
    
    // 创建OkHttpClient实例
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(tokenInterceptor)
            .build()
    }
    
    /**
     * 创建带基础URL的API服务
     */
    fun createService(baseUrl: String): ApiService {
        return ApiService.create(client, baseUrl)
    }
}