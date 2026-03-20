package org.xjtuai.kqchecker.util

import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.xjtuai.kqchecker.model.ScheduleItem

object ScheduleParser {
    fun parse(data: JSONArray): List<ScheduleItem> {
        val list = mutableListOf<ScheduleItem>()
        for (i in 0 until data.length()) {
            parseScheduleItem(data.optJSONObject(i))?.let(list::add)
        }
        return list
    }

    private fun parseScheduleItem(obj: JSONObject?): ScheduleItem? {
        obj ?: return null
        val day = obj.optString("accountWeeknum", "0").trim().toIntOrNull() ?: return null
        if (day !in 1..7) return null

        val (start, end) = parsePeriods(obj.optString("accountJtNo", ""))
        if (start < 0) return null

        val name = obj.optString("subjectSName", "Unknown")
        val room = obj.optString("roomRoomnum", "").trim()
        val build = obj.optString("buildName", "").trim()
        val location = when {
            build.isNotEmpty() && room.isNotEmpty() -> "$build-$room"
            build.isNotEmpty() -> build
            room.isNotEmpty() -> room
            else -> ""
        }
        val teacher = obj.optString("teachNameList", "")
        val colorIdx = abs(name.hashCode()) % 8

        return ScheduleItem(day, start, end, name, location, teacher, colorIdx)
    }

    private fun parsePeriods(periodStr: String): Pair<Int, Int> {
        if (periodStr.isBlank()) return -1 to -1
        val parts = periodStr.split("-")
        if (parts.isEmpty() || parts.size > 2) {
            -1 to -1
        }
        val start = parts.firstOrNull()?.trim()?.toIntOrNull() ?: return -1 to -1
        if (parts.size == 1) return start to start
        val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return -1 to -1
        return start to end
    }
}
