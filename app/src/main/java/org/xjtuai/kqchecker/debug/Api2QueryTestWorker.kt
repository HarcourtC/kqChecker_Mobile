package org.xjtuai.kqchecker.debug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xjtuai.kqchecker.MainActivity
import org.xjtuai.kqchecker.network.ApiClient
import org.xjtuai.kqchecker.network.ApiService
import org.xjtuai.kqchecker.repository.CacheManager
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "Api2QueryTest"

/**
 * Debug worker for testing API2 queries
 * Reads example cleaned weekly data and performs API2 queries for testing
 */
class Api2QueryTestWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val context = appContext
    private val cacheManager = CacheManager(context)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Test worker started")

                val fname = "example_weekly_cleaned.json"
                var cleanedText: String? = null
                try {
                    val f = java.io.File(context.filesDir, fname)
                    if (f.exists()) cleanedText = f.readText()
                } catch (_: Exception) {}

                if (cleanedText.isNullOrBlank()) {
                    try {
                        context.assets.open(fname).use { stream ->
                            cleanedText = stream.bufferedReader().readText()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to read $fname from files or assets", e)
                    }
                }

                if (cleanedText.isNullOrBlank()) {
                    Log.e(TAG, "No example cleaned file found: $fname")
                    return@withContext Result.failure()
                }

                val cleanedObj = try { JSONObject(cleanedText) } catch (e: Exception) {
                    Log.e(TAG, "Invalid cleaned JSON", e)
                    return@withContext Result.failure()
                }

                val apiClient = ApiClient(context)
                val apiService = apiClient.createService(getBaseUrl())

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateOnlySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val keys = cleanedObj.keys()
                val force = try { inputData.getBoolean("force", false) } catch (_: Exception) { false }
                val results = JSONArray()

                while (keys.hasNext()) {
                    val key = keys.next()
                    try {
                        val parts = key.split(" ", limit = 2)
                        if (parts.size < 2) continue
                        val datePart = parts[0]
                        val timePart = parts[1]

                        if (!force) {
                            val todayStr = dateOnlySdf.format(Date())
                            if (datePart != todayStr) continue
                        }
                        var startTimeStr: String? = null
                        if (timePart.contains(":")) startTimeStr = timePart
                        if (startTimeStr == null) continue
                        if (startTimeStr.split(":").size == 2) startTimeStr += ":00"

                        val dtStr = "$datePart $startTimeStr"
                        val eventDate = try { sdf.parse(dtStr) } catch (e: Exception) { null }
                        if (eventDate == null) continue
                        val now = Date()
                        val diffMs = eventDate.time - now.time
                        val diffMinutes = diffMs / 60000

                        if (!force && (diffMinutes < -5 || diffMinutes > 10)) {
                            continue
                        }

                        val payload = JSONObject()
                        payload.put("startdate", datePart)
                        payload.put("enddate", datePart)
                        payload.put("pageSize", 10)
                        payload.put("current", 1)

                        val requestBody = ApiService.jsonToRequestBody(payload)
                        val respBody = apiService.getWaterListData(requestBody)

                        val entry = JSONObject()
                        entry.put("key", key)
                        entry.put("queried_at", sdf.format(Date()))

                        if (respBody == null) {
                            entry.put("success", false)
                            entry.put("detail", "null response")
                        } else {
                            val respStr = try { respBody.string() } catch (e: Exception) { null }
                            if (respStr == null) {
                                entry.put("success", false)
                                entry.put("detail", "read body failed")
                            } else {
                                var noAttendance = false
                                var loc: String? = null
                                try {
                                    if (cleanedObj.has(key)) {
                                        val arr = cleanedObj.optJSONArray(key)
                                        if (arr != null && arr.length() > 0) {
                                            val o = arr.optJSONObject(0)
                                            loc = if (o.has("location")) o.optString("location") else null
                                        }
                                    }
                                } catch (_: Exception) { }

                                try {
                                    val parsed = JSONObject(respStr)
                                    val data = if (parsed.has("data")) parsed.opt("data") else null
                                    var matched = false

                                    if (data is JSONObject) {
                                        val list = data.optJSONArray("list")
                                        if (list == null || list.length() == 0) {
                                            matched = false
                                        } else if (eventDate != null) {
                                            for (i in 0 until list.length()) {
                                                try {
                                                    val item = list.optJSONObject(i) ?: continue
                                                    if (isAttendanceMatch(item, loc, eventDate, sdf)) {
                                                        matched = true
                                                        break
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        } else {
                                            matched = list.length() > 0
                                        }
                                    } else if (data is JSONArray) {
                                        matched = data.length() > 0
                                    }

                                    if (!matched) noAttendance = true
                                } catch (_: Exception) { }

                                if (noAttendance) {
                                    entry.put("success", false)
                                    entry.put("error", "no_attendance")
                                    entry.put("response_preview", if (respStr.length > 4000) respStr.substring(0, 4000) + "..." else respStr)
                                } else {
                                    entry.put("success", true)
                                    entry.put("response_preview", if (respStr.length > 4000) respStr.substring(0, 4000) + "..." else respStr)
                                }
                            }
                        }

                        results.put(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying for key $key", e)
                    }
                }

                try {
                    val outName = "api2_query_test_result.json"
                    cacheManager.saveToCache(outName, results.toString(2))
                    Log.d(TAG, "Saved test results to $outName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save test results", e)
                }

                Log.d(TAG, "Test worker finished, queries executed: ${results.length()}")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Test worker failed", e)
                Result.failure()
            }
        }
    }

    private fun getBaseUrl(): String {
        var baseUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc"
        try {
            context.assets.open("config.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val json = JSONObject(text)
                if (json.has("base_url")) {
                    baseUrl = json.getString("base_url")
                    if (!baseUrl.endsWith("/")) baseUrl += "/"
                }
            }
        } catch (_: Exception) {}
        return baseUrl
    }

    private fun isAttendanceMatch(item: JSONObject, expectedLoc: String?, eventDate: Date?, sdf: SimpleDateFormat): Boolean {
        try {
            if (eventDate == null) return false

            val eqno = item.optString("eqno", "").trim()
            val intimeStr = item.optString("intime", item.optString("watertime", "")).trim()

            if (intimeStr.isBlank()) return false
            val intime = try { sdf.parse(intimeStr) } catch (_: Exception) { null }
            if (intime == null) return false

            val diffMin = Math.abs((eventDate.time - intime.time) / 60000)
            if (diffMin > 15) return false

            if (!expectedLoc.isNullOrBlank()) {
                val a = expectedLoc.replace("\u00A0", " ").replace(" ", "").lowercase(Locale.getDefault())
                val b = eqno.replace("\u00A0", " ").replace(" ", "").lowercase(Locale.getDefault())
                if (a.isNotBlank() && b.isNotBlank()) {
                    return a.contains(b) || b.contains(a)
                }
            }

            return true
        } catch (_: Exception) {
            return false
        }
    }
}
