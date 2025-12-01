package org.example.kqchecker.auth

import okhttp3.Interceptor
import okhttp3.Response

class TokenInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken()
        val requestBuilder = original.newBuilder()
        if (!token.isNullOrEmpty()) {
            // Ensure value uses 'bearer ' prefix as expected by api2 examples
            val normalized = if (token.startsWith("bearer ", true) || token.startsWith("Bearer ", true)) token else "bearer $token"
            // Set synjones-auth for the backend; also set Authorization for compatibility
            requestBuilder.header("synjones-auth", normalized)
            requestBuilder.header("Authorization", normalized)
        }
        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
