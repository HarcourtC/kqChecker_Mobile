package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.model.ScheduleItem
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard
import java.util.Calendar

/**
 * 首页屏幕
 * 展示欢迎信息和基本操作入口
 */
@Composable
fun HomeScreen(
    onLoginClick: () -> Unit,
    onCheckCacheStatus: () -> Unit,
    scheduleItems: List<ScheduleItem>,
    modifier: Modifier = Modifier
) {
    val nextClass = remember(scheduleItems) { findNextClass(scheduleItems) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "kqChecker",
            style = MaterialTheme.typography.h3,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        InfoCard(title = "下一节课") {
            if (nextClass == null) {
                Text(text = "当前没有可用课表数据。请先登录并同步课表。")
            } else {
                Text(text = "课程：${nextClass.item.courseName}")
                Text(text = "地点：${nextClass.item.location.ifBlank { "未提供" }}")
                Text(text = "教师：${nextClass.item.teacher.ifBlank { "未提供" }}")
                Text(text = "时间：${nextClass.dayText} ${nextClass.timeText}")
                Text(text = "节次：第${nextClass.item.startPeriod}-${nextClass.item.endPeriod}节")
                Text(text = "状态：${nextClass.statusText}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AppButton(
            text = "Login",
            onClick = onLoginClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        AppButton(
            text = "Check Cache Status",
            onClick = onCheckCacheStatus,
            backgroundColor = MaterialTheme.colors.secondary
        )
    }
}

private data class NextClassResult(
    val item: ScheduleItem,
    val dayText: String,
    val timeText: String,
    val statusText: String
)

private val periodStartTimes = mapOf(
    1 to "08:00",
    2 to "09:00",
    3 to "10:10",
    4 to "11:10",
    5 to "14:00",
    6 to "15:00",
    7 to "16:10",
    8 to "17:10",
    9 to "19:10",
    10 to "20:10",
    11 to "21:10"
)

private val periodEndTimes = mapOf(
    1 to "08:50",
    2 to "09:50",
    3 to "11:00",
    4 to "12:00",
    5 to "14:50",
    6 to "15:50",
    7 to "17:00",
    8 to "18:00",
    9 to "20:00",
    10 to "21:00",
    11 to "22:00"
)

private fun findNextClass(items: List<ScheduleItem>): NextClassResult? {
    if (items.isEmpty()) return null

    val now = Calendar.getInstance()
    val nowDay = calendarToMonDay(now.get(Calendar.DAY_OF_WEEK))
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    val candidates = items.mapNotNull { item ->
        val startMinutes = toMinutes(periodStartTimes[item.startPeriod]) ?: return@mapNotNull null
        val endMinutes = toMinutes(periodEndTimes[item.endPeriod]) ?: (startMinutes + 50)
        val dayOffset = (item.dayOfWeek - nowDay + 7) % 7

        if (dayOffset == 0 && endMinutes <= nowMinutes) return@mapNotNull null

        val status = if (dayOffset == 0 && startMinutes <= nowMinutes && nowMinutes < endMinutes) {
            "进行中"
        } else {
            "即将开始"
        }

        Candidate(item, dayOffset, startMinutes, status)
    }

    val next = candidates.minWithOrNull(compareBy<Candidate> { it.dayOffset }.thenBy { it.startMinutes }) ?: return null
    val dayText = dayOffsetText(next.dayOffset, next.item.dayOfWeek)
    val startText = periodStartTimes[next.item.startPeriod] ?: "--:--"
    val endText = periodEndTimes[next.item.endPeriod] ?: "--:--"
    return NextClassResult(
        item = next.item,
        dayText = dayText,
        timeText = "$startText-$endText",
        statusText = next.status
    )
}

private data class Candidate(
    val item: ScheduleItem,
    val dayOffset: Int,
    val startMinutes: Int,
    val status: String
)

private fun toMinutes(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun calendarToMonDay(calendarDay: Int): Int {
    return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
}

private fun dayOffsetText(dayOffset: Int, dayOfWeek: Int): String {
    return when (dayOffset) {
        0 -> "今天"
        1 -> "明天"
        else -> weekDayName(dayOfWeek)
    }
}

private fun weekDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "未知"
    }
}
