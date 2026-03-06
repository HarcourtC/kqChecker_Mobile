package org.xjtuai.kqchecker.util

import org.json.JSONArray
import org.xjtuai.kqchecker.model.ScheduleItem
import kotlin.math.abs

object ScheduleParser {
    fun parse(data: JSONArray): List<ScheduleItem> {
        val list = mutableListOf<ScheduleItem>()
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val rawDay = obj.optString("accountWeeknum", "0")
            // Handle day conversion safely
            val day = try {
                 rawDay.trim().toInt()
            } catch (e: Exception) { 0 }
            
            if (day !in 1..7) continue // Skip invalid days (or implement special logic for 0 if needed)

            val periods = obj.optString("accountJtNo", "")
            val (start, end) = parsePeriods(periods)
            if (start == -1) continue // Invalid period format

            val name = obj.optString("subjectSName", "Unknown")
            // Use trim() to clean up potentials spaces, consistent with WeeklyCleaner
            val room = obj.optString("roomRoomnum", "").trim()
            val build = obj.optString("buildName", "").trim()
            val location = when {
                build.isNotEmpty() && room.isNotEmpty() -> "$build-$room"
                build.isNotEmpty() -> build
                room.isNotEmpty() -> room
                else -> ""
            }
            val teacher = obj.optString("teachNameList", "")
            
            // Simple hash for color consistency
            val colorIdx = abs(name.hashCode()) % 8 

            list.add(ScheduleItem(day, start, end, name, location, teacher, colorIdx))
        }
        return list
    }

    private fun parsePeriods(periodStr: String): Pair<Int, Int> {
        if (periodStr.isBlank()) return -1 to -1
        return try {
            val parts = periodStr.split("-")
            if (parts.size == 2) {
                parts[0].trim().toInt() to parts[1].trim().toInt()
            } else if (parts.size == 1) {
                val p = parts[0].trim().toInt()
                p to p
            } else {
                -1 to -1
            }
        } catch (e: Exception) {
            -1 to -1
        }
    }
}
