package org.xjtuai.kqchecker.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.nio.charset.StandardCharsets

/**
 * API服务接口，定义所有API端点
 */
interface ApiService {
    /**
     * 获取周课表数据（API1）
     */
    @POST
    suspend fun getWeeklyData(@Url url: String, @Body requestBody: RequestBody): ResponseBody?
    
    /**
     * 获取水课表数据（API2）
     */
    @POST
    suspend fun getWaterListData(@Url url: String, @Body requestBody: RequestBody): ResponseBody?

    /**
     * 获取竞赛数据
     */
    @GET
    suspend fun getCompetitionData(@Url url: String): ResponseBody?

    /**
     * 获取当前学期信息
     */
    @POST
    suspend fun getCurrentTerm(@Url url: String, @Body requestBody: RequestBody): ResponseBody?
    
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
