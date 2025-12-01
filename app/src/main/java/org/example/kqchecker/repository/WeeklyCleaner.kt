package org.example.kqchecker.repository

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * WeeklyCleaner
 * 读取 assets/periods.json 与 cache weekly_raw.json，清洗并输出 weekly_cleaned.json
 * 输出每项包含字段：time, weekday, location, subjectSName
 */
class WeeklyCleaner(private val context: Context) {
    companion object {
        private const val TAG = "WeeklyCleaner"
        const val CLEANED_WEEKLY_FILE = "weekly_cleaned.json"
    }

    private val cacheManager = CacheManager(context)

    /**
     * 生成清洗后的周课表并保存到缓存。返回保存成功与否。
     */
    fun generateCleanedWeekly(): Boolean {
        try {
            // 1. 读取 periods.json (assets)
            val periodsJsonText = try {
                context.assets.open("periods.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "无法读取 assets/periods.json", e)
                null
            }

            val jcToStartFull = mutableMapOf<String, String>()
            val jcToDisplay = mutableMapOf<String, String>()
            if (!periodsJsonText.isNullOrBlank()) {
                try {
                    val pj = JSONObject(periodsJsonText)
                    val pdata = pj.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until pdata.length()) {
                        val item = pdata.optJSONObject(i) ?: continue
                        val jc = item.optString("jc", "").trim()
                        val start = item.optString("starttime", "").trim()
                        val end = item.optString("endtime", "").trim()
                        if (jc.isNotEmpty() && start.isNotEmpty()) {
                            // start may include seconds (08:00:00). Keep full for keys.
                            jcToStartFull[jc] = start
                            // display: drop seconds for readability: 08:00-08:50
                            val ds = start.split(":").take(2).joinToString(":")
                            val de = if (end.isNotEmpty()) end.split(":").take(2).joinToString(":") else ""
                            jcToDisplay[jc] = if (de.isNotEmpty()) "$ds-$de" else ds
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析 periods.json 失败", e)
                }
            }

            // 2. 读取 weekly_raw.json（优先缓存），若不存在则回退到 assets/example_weekly.json
            val raw = cacheManager.readFromCache(CacheManager.WEEKLY_RAW_CACHE_FILE)
                ?: try {
                    context.assets.open("example_weekly.json").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    Log.e(TAG, "无法读取 weekly 原始数据（缓存或 assets 均失败）", e)
                    null
                }

            if (raw.isNullOrBlank()) {
                Log.w(TAG, "没有可用的周课表原始数据，跳过清洗")
                return false
            }

            val rawObj = try {
                JSONObject(raw)
            } catch (e: Exception) {
                Log.e(TAG, "原始周课表不是有效的 JSON 对象", e)
                return false
            }

            val dataArray = rawObj.optJSONArray("data") ?: JSONArray()
            val cleanedObj = JSONObject()

            // compute week start (Monday)
            val cal = java.util.Calendar.getInstance()
            cal.firstDayOfWeek = java.util.Calendar.MONDAY
            // set to Monday of current week
            cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            val weekStartDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)

            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue

                // weekday number
                val wk = item.optString("accountWeeknum", "").trim()
                val wkInt = try {
                    wk.toInt()
                } catch (e: Exception) {
                    null
                }
                if (wkInt == null) continue
                // normalize 0 -> 7
                val wkNorm = if (wkInt == 0) 7 else wkInt
                if (wkNorm < 1 || wkNorm > 7) continue

                // calculate date: Monday + (wkNorm -1) days
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val weekCal = java.util.Calendar.getInstance()
                weekCal.firstDayOfWeek = java.util.Calendar.MONDAY
                weekCal.time = sdf.parse(weekStartDate) ?: java.util.Date()
                weekCal.add(java.util.Calendar.DAY_OF_YEAR, wkNorm - 1)
                val dateStr = sdf.format(weekCal.time)

                // jt -> jc start time
                val accountJtNo = item.optString("accountJtNo", "").trim()
                var startFull = jcToStartFull[accountJtNo] ?: ""
                var displayTime = jcToDisplay[accountJtNo] ?: accountJtNo
                if (startFull.isBlank() && accountJtNo.contains("-")) {
                    val left = accountJtNo.split("-")[0].trim()
                    if (jcToStartFull.containsKey(left)) {
                        startFull = jcToStartFull[left] ?: ""
                        displayTime = jcToDisplay[left] ?: displayTime
                    }
                }

                // If we still don't have a concrete start time, use accountJtNo as fallback
                val timeKeyPart = if (startFull.isNotBlank()) startFull else accountJtNo
                val key = "$dateStr $timeKeyPart"

                // location
                val buildName = item.optString("buildName", "").trim()
                val room = item.optString("roomRoomnum", "").trim()
                val location = when {
                    buildName.isNotEmpty() && room.isNotEmpty() -> "$buildName-$room"
                    buildName.isNotEmpty() -> buildName
                    room.isNotEmpty() -> room
                    else -> ""
                }

                // subject
                val subject = item.optString("subjectSName", "").trim()

                // weekday label
                val weekdayLabel = when (wkNorm) {
                    1 -> "Monday"
                    2 -> "Tuesday"
                    3 -> "Wednesday"
                    4 -> "Thursday"
                    5 -> "Friday"
                    6 -> "Saturday"
                    7 -> "Sunday"
                    else -> wk
                }

                val out = JSONObject()
                out.put("weekday", weekdayLabel)
                out.put("location", location)
                out.put("subjectSName", subject)
                out.put("time_display", displayTime)

                try {
                    if (cleanedObj.has(key)) {
                        val arr = cleanedObj.optJSONArray(key)
                        arr.put(out)
                        cleanedObj.put(key, arr)
                    } else {
                        val arr = JSONArray()
                        arr.put(out)
                        cleanedObj.put(key, arr)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding cleaned entry for key $key", e)
                }
            }

            // 保存到缓存
            val saved = cacheManager.saveToCache(CLEANED_WEEKLY_FILE, cleanedObj.toString(2))
            if (saved) {
                Log.d(TAG, "已生成并保存清洗后的周课表: ${CLEANED_WEEKLY_FILE}")
            } else {
                Log.e(TAG, "保存清洗后的周课表失败")
            }
            return saved

        } catch (e: Exception) {
            Log.e(TAG, "生成清洗周课表时发生异常", e)
            return false
        }
    }

    /**
     * 返回清洗后的文件路径（如果存在），否则 null
     */
    fun getCleanedFilePath(): String? {
        val info = cacheManager.getCacheFileInfo(CLEANED_WEEKLY_FILE) ?: return null
        return info.path
    }
}
