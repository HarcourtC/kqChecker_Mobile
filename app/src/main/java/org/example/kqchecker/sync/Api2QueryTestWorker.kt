package org.example.kqchecker.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.kqchecker.network.ApiClient
import org.example.kqchecker.network.ApiService
import org.example.kqchecker.repository.CacheManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "Api2QueryTest"

/**
 * Test worker: 读取 assets/example_weekly_cleaned.json （或 files 下同名文件）并对每个 key 发起 api2 查询，
 * 将每次查询结果追加到 files/api2_query_test_result.json
 */
class Api2QueryTestWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val context = appContext
    private val cacheManager = CacheManager(context)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Test worker started")

                // 首先尝试从 files 目录读取示例文件（方便在运行时替换），否则从 assets 读取
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

                        // 如果非强制模式且课程不在当日，则跳过匹配
                        if (!force) {
                            val todayStr = dateOnlySdf.format(Date())
                            if (datePart != todayStr) continue
                        }
                        var startTimeStr: String? = null
                        if (timePart.contains(":")) startTimeStr = timePart
                        if (startTimeStr == null) continue
                        if (startTimeStr.split(":").size == 2) startTimeStr += ":00"

                        // 与 Api2AttendanceQueryWorker 一致：按事件时间决定是否执行查询（除非 force=true）
                        // ensure seconds part
                        if (startTimeStr.split(":").size == 2) startTimeStr += ":00"
                        val dtStr = "$datePart $startTimeStr"
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val eventDate = try { sdf.parse(dtStr) } catch (e: Exception) { null }
                        if (eventDate == null) continue
                        val now = Date()
                        val diffMs = eventDate.time - now.time
                        val diffMinutes = diffMs / 60000

                        // Only proceed if forced or within [-5, +10] minutes
                        if (!force && (diffMinutes < -5 || diffMinutes > 10)) {
                            // skip this key in non-forced mode
                            continue
                        }

                        // 构建请求（按 worker 的做法仅以日期为范围）
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
                                        // 检查 data.list 是否为空，以便触发无考勤提醒
                                        var noAttendance = false
                                        try {
                                            val parsed = JSONObject(respStr)
                                            val data = if (parsed.has("data")) parsed.opt("data") else null
                                            var matched = false

                                            // 从 cleanedObj 中提取课程信息（若存在）以便匹配
                                            var subj: String? = null
                                            var td: String? = null
                                            var loc: String? = null
                                            try {
                                                if (cleanedObj.has(key)) {
                                                    val arr = cleanedObj.optJSONArray(key)
                                                    if (arr != null && arr.length() > 0) {
                                                        val o = arr.optJSONObject(0)
                                                        subj = if (o.has("subjectSName")) o.optString("subjectSName") else null
                                                        td = if (o.has("time_display")) o.optString("time_display") else null
                                                        loc = if (o.has("location")) o.optString("location") else null
                                                    }
                                                }
                                            } catch (_: Exception) { }

                                            // 解析事件时间用于匹配
                                            val eventDate = try { sdf.parse("$datePart ${startTimeStr}") } catch (_: Exception) { null }

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
                                                    // 事件时间无法解析，回退为非空判断
                                                    matched = list.length() > 0
                                                }
                                            } else if (data is org.json.JSONArray) {
                                                matched = data.length() > 0
                                            }

                                            if (!matched) noAttendance = true
                                        } catch (_: Exception) { }

                                        if (noAttendance) {
                                            entry.put("success", false)
                                            entry.put("error", "no_attendance")
                                            entry.put("response_preview", if (respStr.length > 4000) respStr.substring(0,4000) + "..." else respStr)

                                            // 从 cleanedObj 中提取课程信息（若存在）
                                            var subj: String? = null
                                            var td: String? = null
                                            var loc: String? = null
                                            try {
                                                if (cleanedObj.has(key)) {
                                                    val arr = cleanedObj.optJSONArray(key)
                                                    if (arr != null && arr.length() > 0) {
                                                        val o = arr.optJSONObject(0)
                                                        subj = if (o.has("subjectSName")) o.optString("subjectSName") else null
                                                        td = if (o.has("time_display")) o.optString("time_display") else null
                                                        loc = if (o.has("location")) o.optString("location") else null
                                                    }
                                                }
                                            } catch (_: Exception) { }

                                            // 发送广播，MainActivity 的动态接收器会接收（如果 app 在前台）
                                            try {
                                                val b = Intent("org.example.kqchecker.ACTION_NO_ATTENDANCE")
                                                b.putExtra("key", key)
                                                b.putExtra("date", datePart)
                                                subj?.let { b.putExtra("subject", it) }
                                                td?.let { b.putExtra("time_display", it) }
                                                loc?.let { b.putExtra("location", it) }
                                                context.sendBroadcast(b)
                                            } catch (_: Exception) { }

                                            // 也发系统通知以保证用户能看到（即使 UI 未注册动态接收器）
                                            try {
                                                val channelId = "api2_no_attendance_channel"
                                                val channelName = "API2 无考勤提醒"
                                                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
                                                    nm.createNotificationChannel(ch)
                                                }
                                                val launchIntent = Intent(context, org.example.kqchecker.MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                }
                                                val pending = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                                                val title = subj ?: "没有匹配到考勤记录"
                                                val content = listOfNotNull(td, loc).joinToString(" @ ")

                                                val notification = NotificationCompat.Builder(context, channelId)
                                                    .setSmallIcon(android.R.drawable.stat_notify_more)
                                                    .setContentTitle(title)
                                                    .setContentText(content)
                                                    .setContentIntent(pending)
                                                    .setAutoCancel(true)
                                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                    .build()
                                                val nid = (key + datePart).hashCode()
                                                nm.notify(nid, notification)
                                            } catch (_: Exception) { }
                                        } else {
                                            entry.put("success", true)
                                            // 保留响应的前 4000 字
                                            entry.put("response_preview", if (respStr.length > 4000) respStr.substring(0,4000) + "..." else respStr)
                                        }
                                    }
                        }

                        results.put(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying for key $key", e)
                    }
                }

                // 写入结果文件（覆盖写入测试结果）
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
        } catch (_: Exception) {}
        return baseUrl
    }

    private fun isAttendanceMatch(item: JSONObject, expectedLoc: String?, eventDate: Date?, sdf: SimpleDateFormat): Boolean {
        try {
            if (eventDate == null) return false

            // location match: compare normalized contains (ignore spaces/case)
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
                    if (a.contains(b) || b.contains(a)) return true
                    return false
                }
            }

            // 未提供期望地点时，按时间匹配即可
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
