package org.example.kqchecker.network

import retrofit2.Response
import retrofit2.http.GET

data class WeeklyResponse(val data: Any?)

interface ApiService {
    @GET("/weekly")
    suspend fun getWeekly(): Response<WeeklyResponse>
}
