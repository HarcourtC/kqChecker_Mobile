package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.xjtuai.kqchecker.model.ScheduleItem
import org.xjtuai.kqchecker.network.WaterRecord
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard
import org.xjtuai.kqchecker.util.ScheduleTimeHelper
import java.util.Calendar

/**
 * 首页屏幕
 * 展示欢迎信息和基本操作入口
 */
@Composable
fun HomeScreen(
    onLoginClick: () -> Unit,
    onManualSync: () -> Unit,
    isLoggedIn: Boolean,
    scheduleItems: List<ScheduleItem>,
    latestAttendance: WaterRecord?,
    latestAttendanceHint: String?,
    refreshToken: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val periodSlots = remember(context) {
        ScheduleTimeHelper.getCurrentPeriodSlots(context)
    }
    val nextClass = remember(scheduleItems, periodSlots, refreshToken) {
        findNextClass(scheduleItems, periodSlots)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "考勤助手",
            style = MaterialTheme.typography.h3,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        InfoCard(title = "下一节课") {
            if (nextClass == null) {
                InfoRow(icon = Icons.Default.Info, text = "当前没有可用课表数据。请先登录并同步课表。")
            } else {
                InfoRow(icon = Icons.Default.List, text = nextClass.item.courseName)
                InfoRow(icon = Icons.Default.LocationOn, text = nextClass.item.location.ifBlank { "未提供" })
                InfoRow(icon = Icons.Default.Person, text = nextClass.item.teacher.ifBlank { "未提供" })
                InfoRow(icon = Icons.Default.DateRange, text = "${nextClass.dayText} ${nextClass.timeText} (第${nextClass.item.startPeriod}-${nextClass.item.endPeriod}节)")
                
                val statusColor = when (nextClass.statusText) {
                    "进行中" -> MaterialTheme.colors.secondary
                    else -> MaterialTheme.colors.primary
                }
                InfoRow(icon = Icons.Default.PlayArrow, text = nextClass.statusText, textColor = statusColor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        InfoCard(title = "上次有效考勤记录") {
            if (!latestAttendanceHint.isNullOrBlank()) {
                InfoRow(icon = Icons.Default.Info, text = latestAttendanceHint)
            } else if (latestAttendance == null) {
                InfoRow(icon = Icons.Default.Info, text = "暂无考勤数据或未登录。")
            } else {
                InfoRow(icon = Icons.Default.LocationOn, text = latestAttendance.eqno.ifBlank { "未提供" })
                InfoRow(icon = Icons.Default.DateRange, text = latestAttendance.watertime)
                InfoRow(icon = Icons.Default.Info, text = latestAttendance.fromTypeText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isLoggedIn) {
            AppButton(
                text = "登录", 
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        AppButton(
            text = "手动同步",
            onClick = {
                onManualSync()
            },
            backgroundColor = MaterialTheme.colors.secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class NextClassResult(
    val item: ScheduleItem,
    val dayText: String,
    val timeText: String,
    val statusText: String
)

private fun findNextClass(
    items: List<ScheduleItem>,
    periodSlots: Map<Int, ScheduleTimeHelper.PeriodSlot>
): NextClassResult? {
    if (items.isEmpty()) return null

    val now = Calendar.getInstance()
    val nowDay = calendarToMonDay(now.get(Calendar.DAY_OF_WEEK))
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    val candidates = items.mapNotNull { item ->
        val startSlot = periodSlots[item.startPeriod] ?: return@mapNotNull null
        val endSlot = periodSlots[item.endPeriod] ?: startSlot
        val startMinutes = startSlot.startMinutes
        val endMinutes = if (endSlot.endMinutes > startMinutes) endSlot.endMinutes else (startMinutes + 50)
        var dayOffset = (item.dayOfWeek - nowDay + 7) % 7

        if (dayOffset == 0 && endMinutes <= nowMinutes) {
            dayOffset = 7
        }

        val status = if (dayOffset == 0 && startMinutes <= nowMinutes && nowMinutes < endMinutes) {
            "进行中"
        } else {
            "即将开始"
        }

        Candidate(item, dayOffset, startMinutes, status)
    }

    val next = candidates.minWithOrNull(compareBy<Candidate> { it.dayOffset }.thenBy { it.startMinutes }) ?: return null
    val dayText = dayOffsetText(next.dayOffset, next.item.dayOfWeek)
    val startText = periodSlots[next.item.startPeriod]?.startText ?: "--:--"
    val endText = periodSlots[next.item.endPeriod]?.endText ?: "--:--"
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

private fun calendarToMonDay(calendarDay: Int): Int {
    return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
}

private fun dayOffsetText(dayOffset: Int, dayOfWeek: Int): String {
    return when {
        dayOffset == 0 -> "今天"
        dayOffset == 1 -> "明天"
        dayOffset >= 7 -> "下" + weekDayName(dayOfWeek)
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

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    textColor: Color = MaterialTheme.colors.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colors.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.body1,
            color = textColor
        )
    }
}
