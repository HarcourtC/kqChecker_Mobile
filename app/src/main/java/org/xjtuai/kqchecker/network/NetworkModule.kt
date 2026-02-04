package org.xjtuai.kqchecker.network

import android.content.Context
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import org.xjtuai.kqchecker.auth.TokenAuthenticator
import org.xjtuai.kqchecker.network.TokenInterceptor
import org.xjtuai.kqchecker.auth.TokenManager
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val moshi = Moshi.Builder().build()

    // default base url (replace with your API endpoint when initializing)
    private const val DEFAULT_BASE_URL = "http://bkkq.xjtu.edu.cn/attendance-student-pc"

    @Volatile
    private var retrofit: Retrofit? = null

    fun init(context: Context, baseUrl: String = DEFAULT_BASE_URL) {
        if (retrofit != null) return

        // Ensure baseUrl ends with a '/' because Retrofit requires it
        val normalizedBaseUrl = if (baseUrl.endsWith('/')) baseUrl else baseUrl + '/'

        val tokenManager = TokenManager(context)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TokenInterceptor(tokenManager))
            .authenticator(TokenAuthenticator(context, normalizedBaseUrl))
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    fun apiService(context: Context, baseUrl: String = DEFAULT_BASE_URL): ApiService {
        init(context, baseUrl)
        return retrofit!!.create(ApiService::class.java)
    }
}
