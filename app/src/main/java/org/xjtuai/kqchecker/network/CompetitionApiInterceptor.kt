package org.xjtuai.kqchecker.network

import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log

/**
 * 竞赛API拦截器：添加 X-API-KEY 请求头
 */
class CompetitionApiInterceptor : Interceptor {
    companion object {
        private const val TAG = "CompetitionApiInterceptor"
        private const val API_KEY = "HarcoSecret2026XJTUDeanApi"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("X-API-KEY", API_KEY)
            .build()

        Log.d(TAG, "Adding X-API-KEY header to request")
        return chain.proceed(requestBuilder)
    }
}
