package org.xjtuai.kqchecker.util

import android.content.Context
import java.util.Calendar
import java.util.Locale
import org.json.JSONObject

object ScheduleTimeHelper {
    private const val MINUTES_PER_HOUR = 60
    private const val DEFAULT_CLASS_DURATION_MINUTES = 50
    private const val TIME_PART_COUNT = 2
    private const val TIME_DISPLAY_PATTERN = "%02d:%02d"
    private const val ZERO_SECOND = 0
    private const val ZERO_MILLISECOND = 0

    data class PeriodSlot(
        val period: Int,
        val startMinutes: Int,
        val endMinutes: Int,
        val startText: String,
        val endText: String
    )

    private val fallbackSlots = mapOf(
        1 to PeriodSlot(
            1,
            8 * MINUTES_PER_HOUR,
            8 * MINUTES_PER_HOUR + DEFAULT_CLASS_DURATION_MINUTES,
            "08:00",
            "08:50"
        ),
        2 to PeriodSlot(
            2,
            9 * MINUTES_PER_HOUR,
            9 * MINUTES_PER_HOUR + DEFAULT_CLASS_DURATION_MINUTES,
            "09:00",
            "09:50"
        ),
        3 to PeriodSlot(3, 10 * MINUTES_PER_HOUR + 10, 11 * MINUTES_PER_HOUR, "10:10", "11:00"),
        4 to PeriodSlot(4, 11 * MINUTES_PER_HOUR + 10, 12 * MINUTES_PER_HOUR, "11:10", "12:00"),
        5 to PeriodSlot(
            5,
            14 * MINUTES_PER_HOUR,
            14 * MINUTES_PER_HOUR + DEFAULT_CLASS_DURATION_MINUTES,
            "14:00",
            "14:50"
        ),
        6 to PeriodSlot(
            6,
            15 * MINUTES_PER_HOUR,
            15 * MINUTES_PER_HOUR + DEFAULT_CLASS_DURATION_MINUTES,
            "15:00",
            "15:50"
        ),
        7 to PeriodSlot(7, 16 * MINUTES_PER_HOUR + 10, 17 * MINUTES_PER_HOUR, "16:10", "17:00"),
        8 to PeriodSlot(8, 17 * MINUTES_PER_HOUR + 10, 18 * MINUTES_PER_HOUR, "17:10", "18:00"),
        9 to PeriodSlot(9, 19 * MINUTES_PER_HOUR + 10, 20 * MINUTES_PER_HOUR, "19:10", "20:00"),
        10 to PeriodSlot(10, 20 * MINUTES_PER_HOUR + 10, 21 * MINUTES_PER_HOUR, "20:10", "21:00"),
        11 to PeriodSlot(11, 21 * MINUTES_PER_HOUR + 10, 22 * MINUTES_PER_HOUR, "21:10", "22:00")
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
            val slot = parsePeriodSlot(data.optJSONObject(i))
            if (slot != null && !result.containsKey(slot.period)) {
                result[slot.period] = slot
            }
        }
        return result
    }

    private fun parsePeriodSlot(item: JSONObject?): PeriodSlot? {
        item ?: return null
        val period = item.optString("jc", "").trim().toIntOrNull() ?: return null
        val startMin = parseTimeToMinutes(item.optString("starttime", "").trim()) ?: return null
        val parsedEnd = parseTimeToMinutes(item.optString("endtime", "").trim())
        val endMin = if (parsedEnd != null && parsedEnd > startMin) {
            parsedEnd
        } else {
            startMin + DEFAULT_CLASS_DURATION_MINUTES
        }
        return PeriodSlot(
            period = period,
            startMinutes = startMin,
            endMinutes = endMin,
            startText = toDisplayTime(startMin),
            endText = toDisplayTime(endMin)
        )
    }

    private fun parseTimeToMinutes(raw: String): Int? {
        if (raw.isBlank()) return null
        val parts = raw.split(":")
        if (parts.size < TIME_PART_COUNT) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * MINUTES_PER_HOUR + minute
    }

    private fun toDisplayTime(minutes: Int): String {
        val h = minutes / MINUTES_PER_HOUR
        val m = minutes % MINUTES_PER_HOUR
        return String.format(Locale.getDefault(), TIME_DISPLAY_PATTERN, h, m)
    }

    private fun buildRange(
        dueDateMillis: Long,
        startSlot: PeriodSlot,
        endSlot: PeriodSlot
    ): Pair<Long, Long> {
        val safeEndMinutes = if (endSlot.endMinutes > startSlot.startMinutes) {
            endSlot.endMinutes
        } else {
            startSlot.startMinutes + DEFAULT_CLASS_DURATION_MINUTES
        }

        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            applyMinutes(this, startSlot.startMinutes)
        }
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            applyMinutes(this, safeEndMinutes)
        }
        return startCalendar.timeInMillis to endCalendar.timeInMillis
    }

    private fun applyMinutes(calendar: Calendar, totalMinutes: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, totalMinutes / MINUTES_PER_HOUR)
        calendar.set(Calendar.MINUTE, totalMinutes % MINUTES_PER_HOUR)
        calendar.set(Calendar.SECOND, ZERO_SECOND)
        calendar.set(Calendar.MILLISECOND, ZERO_MILLISECOND)
    }
}
