package org.example.kqchecker.network

import okhttp3.Interceptor
import okhttp3.Response
import org.example.kqchecker.auth.TokenManager
import android.util.Log

/**
 * 统一的 Token 拦截器：
 * - 规范 bearer 前缀
 * - 添加 `Authorization` 和 `synjones-auth` 头（保持与旧实现兼容）
 */
class TokenInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken()
        val requestBuilder = original.newBuilder()

        if (!token.isNullOrEmpty()) {
            try {
                val normalized = if (token.startsWith("bearer ", true) || token.startsWith("Bearer ", true)) token else "bearer $token"
                requestBuilder.header("synjones-auth", normalized)
                requestBuilder.header("Authorization", normalized)
            } catch (t: Throwable) {
                Log.w("TokenInterceptor", "Failed to normalize token", t)
            }
        }

        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}