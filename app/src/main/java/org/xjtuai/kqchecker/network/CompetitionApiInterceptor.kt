package org.xjtuai.kqchecker.network

import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log

/**
 * 竞赛API拦截器：添加 X-API-KEY 请求头
 */
class CompetitionApiInterceptor(private val apiKey: String) : Interceptor {
    companion object {
        private const val TAG = "CompetitionApiInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        if (apiKey.isNotBlank()) {
            requestBuilder.header("X-API-KEY", apiKey)
            Log.d(TAG, "Adding X-API-KEY header to request")
        } else {
            Log.w(TAG, "Competition API key is empty; sending request without X-API-KEY")
        }

        return chain.proceed(requestBuilder.build())
    }
}
