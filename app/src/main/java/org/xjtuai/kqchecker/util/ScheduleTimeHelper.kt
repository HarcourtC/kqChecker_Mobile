package org.xjtuai.kqchecker.util

import java.util.Calendar

object ScheduleTimeHelper {
    private val periodStartTimes = mapOf(
        1 to (8 to 0),
        2 to (9 to 0),
        3 to (10 to 10),
        4 to (11 to 10),
        5 to (14 to 0),
        6 to (15 to 0),
        7 to (16 to 10),
        8 to (17 to 10),
        9 to (19 to 10),
        10 to (20 to 10),
        11 to (21 to 10)
    )

    private val periodEndTimes = mapOf(
        1 to (8 to 50),
        2 to (9 to 50),
        3 to (11 to 0),
        4 to (12 to 0),
        5 to (14 to 50),
        6 to (15 to 50),
        7 to (17 to 0),
        8 to (18 to 0),
        9 to (20 to 0),
        10 to (21 to 0),
        11 to (22 to 0)
    )

    fun buildClassTimeRange(
        dueDateMillis: Long,
        startPeriod: Int,
        endPeriod: Int
    ): Pair<Long, Long>? {
        val start = periodStartTimes[startPeriod] ?: return null
        val end = periodEndTimes[endPeriod] ?: return null

        val startCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, start.first)
            set(Calendar.MINUTE, start.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, end.first)
            set(Calendar.MINUTE, end.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return startCalendar.timeInMillis to endCalendar.timeInMillis
    }
}
