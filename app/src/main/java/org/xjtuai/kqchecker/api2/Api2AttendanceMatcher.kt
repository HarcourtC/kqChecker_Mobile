package org.xjtuai.kqchecker.api2

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Api2AttendanceMatcher {
    private const val MATCH_WINDOW_MINUTES = 15L

    fun extractExpectedLocation(cleanedObj: JSONObject, key: String): String? {
        return try {
            if (!cleanedObj.has(key)) return null
            val arr = cleanedObj.optJSONArray(key) ?: return null
            if (arr.length() <= 0) return null
            val first = arr.optJSONObject(0) ?: return null
            if (first.has("location")) first.optString("location") else null
        } catch (_: Exception) {
            null
        }
    }

    fun isAttendanceMatch(
        item: JSONObject,
        expectedLoc: String?,
        eventDate: Date?,
        dateTimeFormat: SimpleDateFormat
    ): Boolean {
        try {
            if (eventDate == null) return false

            val eqno = item.optString("eqno", "").trim()
            val intimeStr = item.optString("intime", item.optString("watertime", "")).trim()
            if (intimeStr.isBlank()) return false

            val intime = try {
                dateTimeFormat.parse(intimeStr)
            } catch (_: Exception) {
                null
            }
            if (intime == null) return false

            val diffMin = abs((eventDate.time - intime.time) / 60000)
            if (diffMin > MATCH_WINDOW_MINUTES) return false

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
