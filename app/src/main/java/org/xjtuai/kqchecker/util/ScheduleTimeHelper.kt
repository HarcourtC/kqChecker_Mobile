package org.xjtuai.kqchecker.util

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

object ScheduleTimeHelper {
    data class PeriodSlot(
        val period: Int,
        val startMinutes: Int,
        val endMinutes: Int,
        val startText: String,
        val endText: String
    )

    private val fallbackSlots = mapOf(
        1 to PeriodSlot(1, 8 * 60, 8 * 60 + 50, "08:00", "08:50"),
        2 to PeriodSlot(2, 9 * 60, 9 * 60 + 50, "09:00", "09:50"),
        3 to PeriodSlot(3, 10 * 60 + 10, 11 * 60, "10:10", "11:00"),
        4 to PeriodSlot(4, 11 * 60 + 10, 12 * 60, "11:10", "12:00"),
        5 to PeriodSlot(5, 14 * 60, 14 * 60 + 50, "14:00", "14:50"),
        6 to PeriodSlot(6, 15 * 60, 15 * 60 + 50, "15:00", "15:50"),
        7 to PeriodSlot(7, 16 * 60 + 10, 17 * 60, "16:10", "17:00"),
        8 to PeriodSlot(8, 17 * 60 + 10, 18 * 60, "17:10", "18:00"),
        9 to PeriodSlot(9, 19 * 60 + 10, 20 * 60, "19:10", "20:00"),
        10 to PeriodSlot(10, 20 * 60 + 10, 21 * 60, "20:10", "21:00"),
        11 to PeriodSlot(11, 21 * 60 + 10, 22 * 60, "21:10", "22:00")
    )

    @Volatile
    private var cachedPeriodSlots: Map<Int, PeriodSlot>? = null

    fun getCurrentPeriodSlots(context: Context): Map<Int, PeriodSlot> {
        val cached = cachedPeriodSlots
        if (cached != null) return cached

        val fromAssets = loadSlotsFromAssets(context)
        val merged = fallbackSlots.toMutableMap().apply { putAll(fromAssets) }
        cachedPeriodSlots = merged
        return merged
    }

    fun buildClassTimeRange(
        dueDateMillis: Long,
        startPeriod: Int,
        endPeriod: Int
    ): Pair<Long, Long>? {
        val startSlot = fallbackSlots[startPeriod] ?: return null
        val endSlot = fallbackSlots[endPeriod] ?: return null
        return buildRange(dueDateMillis, startSlot, endSlot)
    }

    fun buildClassTimeRange(
        context: Context,
        dueDateMillis: Long,
        startPeriod: Int,
        endPeriod: Int
    ): Pair<Long, Long>? {
        val slots = getCurrentPeriodSlots(context)
        val startSlot = slots[startPeriod] ?: fallbackSlots[startPeriod] ?: return null
        val endSlot = slots[endPeriod] ?: fallbackSlots[endPeriod] ?: startSlot
        return buildRange(dueDateMillis, startSlot, endSlot)
    }

    private fun loadSlotsFromAssets(context: Context): Map<Int, PeriodSlot> {
        val text = try {
            context.assets.open("periods.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyMap()
        }
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return emptyMap()
        }
        val data = root.optJSONArray("data") ?: return emptyMap()
        val result = linkedMapOf<Int, PeriodSlot>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val period = item.optString("jc", "").trim().toIntOrNull() ?: continue
            if (result.containsKey(period)) continue
            val startMin = parseTimeToMinutes(item.optString("starttime", "").trim()) ?: continue
            val parsedEnd = parseTimeToMinutes(item.optString("endtime", "").trim())
            val endMin = if (parsedEnd != null && parsedEnd > startMin) parsedEnd else startMin + 50
            result[period] = PeriodSlot(
                period = period,
                startMinutes = startMin,
                endMinutes = endMin,
                startText = toDisplayTime(startMin),
                endText = toDisplayTime(endMin)
            )
        }
        return result
    }

    private fun parseTimeToMinutes(raw: String): Int? {
        if (raw.isBlank()) return null
        val parts = raw.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun toDisplayTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }

    private fun buildRange(
        dueDateMillis: Long,
        startSlot: PeriodSlot,
        endSlot: PeriodSlot
    ): Pair<Long, Long> {
        val safeEndMinutes = if (endSlot.endMinutes > startSlot.startMinutes) {
            endSlot.endMinutes
        } else {
            startSlot.startMinutes + 50
        }

        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, startSlot.startMinutes / 60)
            set(Calendar.MINUTE, startSlot.startMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, safeEndMinutes / 60)
            set(Calendar.MINUTE, safeEndMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return startCalendar.timeInMillis to endCalendar.timeInMillis
    }
}
