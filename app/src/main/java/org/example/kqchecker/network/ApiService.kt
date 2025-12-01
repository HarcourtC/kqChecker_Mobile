package org.example.kqchecker.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.nio.charset.StandardCharsets

/**
 * API服务接口，定义所有API端点
 */
interface ApiService {
    /**
     * 获取周课表数据（API1）
     */
    @POST("attendance-student/rankClass/getWeekSchedule2")
    suspend fun getWeeklyData(@Body requestBody: RequestBody): JSONObject?
    
    /**
     * 获取水课表数据（API2）
     */
    @POST("attendance-student/rankClass/getWaterList")
    suspend fun getWaterListData(@Body requestBody: RequestBody): JSONObject?
    
    companion object {
        /**
         * 创建ApiService实例
         */
        fun create(client: OkHttpClient, baseUrl: String): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .build()
            return retrofit.create(ApiService::class.java)
        }
        
        /**
         * 将JSONObject转换为RequestBody
         */
        fun jsonToRequestBody(jsonObject: JSONObject): RequestBody {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull() ?: error("Invalid media type")
            return jsonObject.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(mediaType)
        }
    }
}
