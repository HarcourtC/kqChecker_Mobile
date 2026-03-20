package org.xjtuai.kqchecker.model

data class ScheduleItem(
    val dayOfWeek: Int, // 1=Mon .. 7=Sun
    val startPeriod: Int,
    val endPeriod: Int,
    val courseName: String,
    val location: String,
    val teacher: String,
    val colorIndex: Int = 0
)
