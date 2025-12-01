package org.example.kqchecker.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.kqchecker.network.ApiClient
import org.example.kqchecker.network.ApiService
import org.example.kqchecker.repository.CacheManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "Api2AttendanceQuery"
private const val NOTIF_CHANNEL_ID = "api2_att_query_channel"
private const val WEEKLY_CLEANED_FILE = "weekly_cleaned.json"

/**
 * Worker: 定期扫描 weekly_cleaned.json，在课程开始前5分钟到开始后10分钟窗口内
 * 对每个事件向 api2 发送查询；如果返回错误码（例如 400/401/403 或 code != 0），则发送通知提示用户重新登录，
 * 否则把查询记录追加到 `api2_query_log.json`。
 */
class Api2AttendanceQueryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val context = appContext
    private val cacheManager = CacheManager(context)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Worker started: scanning cleaned weekly events")

                val cleanedText = cacheManager.readFromCache(WEEKLY_CLEANED_FILE)
                if (cleanedText.isNullOrBlank()) {
                    Log.d(TAG, "No cleaned weekly file found, nothing to query")
                    return@withContext Result.success()
                }

                val cleanedObj = try { JSONObject(cleanedText) } catch (e: Exception) {
                    Log.e(TAG, "Invalid cleaned JSON", e)
                    return@withContext Result.failure()
                }

                // weekly_cleaned keys are expected to include explicit start time (HH:MM[:SS])

                val now = Date()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                // prepare ApiService
                val apiClient = ApiClient(context)
                val apiService = apiClient.createService(getBaseUrl())

                val keys = cleanedObj.keys()
                val queryResults = mutableListOf<JSONObject>()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val parts = key.split(" ", limit = 2)
                    if (parts.size < 2) continue
                    val datePart = parts[0]
                    val timePart = parts[1]

                    // normalize start time (must be explicit HH:MM or HH:MM:SS)
                    var startTimeStr: String? = null
                    if (timePart.contains(":")) {
                        startTimeStr = timePart
                    }

                    if (startTimeStr.isNullOrBlank()) {
                        Log.d(TAG, "Skipping key $key because no start time available")
                        continue
                    }

                    // ensure seconds part
                    if (startTimeStr.split(":").size == 2) startTimeStr += ":00"
                    val dtStr = "$datePart $startTimeStr"
                    val eventDate = try { sdf.parse(dtStr) } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse event datetime $dtStr", e)
                        null
                    }
                    if (eventDate == null) continue

                    val diffMs = eventDate.time - now.time
                    val diffMinutes = diffMs / 60000

                    // if now in [-5, +10] minutes window
                    if (diffMinutes >= -5 && diffMinutes <= 10) {
                        Log.d(TAG, "Event $key is within query window (diffMinutes=$diffMinutes), sending api2 query")

                        val success = try {
                            val payload = JSONObject()
                            payload.put("startdate", datePart)
                            payload.put("enddate", datePart)
                            payload.put("pageSize", 10)
                            payload.put("current", 1)
                            // optional: include other fields if available

                            val requestBody = ApiService.jsonToRequestBody(payload)
                            val respBody = apiService.getWaterListData(requestBody)

                            if (respBody == null) {
                                Log.e(TAG, "api2 returned null")
                                recordQuery(key, datePart, false, "null response")
                                false
                            } else {
                                val respStr = try { respBody.string() } catch (e: Exception) {
                                    Log.e(TAG, "Failed to read api2 response body", e)
                                    recordQuery(key, datePart, false, "read body failed: ${e.message}")
                                    null
                                }

                                if (respStr == null) {
                                    false
                                } else {
                                    val respJson = try { JSONObject(respStr) } catch (e: Exception) {
                                        Log.e(TAG, "api2 returned invalid JSON", e)
                                        recordQuery(key, datePart, false, "invalid json: ${e.message}")
                                        null
                                    }

                                    if (respJson == null) {
                                        false
                                    } else {
                                        val code = respJson.optInt("code", -1)
                                        if (code == 0) {
                                            recordQuery(key, datePart, true, respJson.toString())
                                            true
                                        } else {
                                            if (code == 400 || code == 401 || code == 403) {
                                                notifyTokenInvalid()
                                            }
                                            recordQuery(key, datePart, false, respJson.toString())
                                            false
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "api2 query exception for $key", e)
                            recordQuery(key, datePart, false, e.message ?: "exception")
                            false
                        }

                        queryResults.add(JSONObject().apply {
                            put("key", key)
                            put("queried_at", sdf.format(Date()))
                            put("result", success)
                        })
                    }
                }

                Log.d(TAG, "Worker finished, queries executed: ${queryResults.size}")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Worker failed", e)
                Result.retry()
            }
        }
    }

    private fun getBaseUrl(): String {
        var baseUrl = "https://api.example.com/"
        try {
            context.assets.open("config.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val json = JSONObject(text)
                if (json.has("base_url")) {
                    baseUrl = json.getString("base_url")
                    if (!baseUrl.endsWith("/")) baseUrl += "/"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No config.json, using default base URL")
        }
        return baseUrl
    }

    private fun notifyTokenInvalid() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(NOTIF_CHANNEL_ID, "API2 Alerts", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("token失效，请重新登录！")
                .setContentText("检测到与 api2 的认证错误，请打开应用重新登录以刷新 Token")
                .setAutoCancel(true)
                .build()
            nm.notify(1001, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send token invalid notification", e)
        }
    }

    private fun recordQuery(key: String, date: String, success: Boolean, detail: String) {
        try {
            val fname = "api2_query_log.json"
            val existing = cacheManager.readFromCache(fname)
            val arr = if (!existing.isNullOrBlank()) {
                try { JSONArray(existing) } catch (_: Exception) { JSONArray() }
            } else JSONArray()

            val rec = JSONObject()
            rec.put("key", key)
            rec.put("date", date)
            rec.put("queried_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            rec.put("success", success)
            rec.put("detail", if (detail.length > 2000) detail.substring(0,2000) + "..." else detail)

            arr.put(rec)
            cacheManager.saveToCache(fname, arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record query", e)
        }
    }

    // periods mapping removed: cleaned weekly keys must contain explicit time
}
