package org.example.kqchecker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.example.kqchecker.repo.MockRepository
import org.example.kqchecker.util.CalendarHelper
import java.text.SimpleDateFormat

/**
 * 测试用日历写入功能，从assets中的weekly.json读取数据并写入日历
 */
class TestWriteCalendar(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val repo = MockRepository(appContext)

    override suspend fun doWork(): Result {
        return try {
            val weekly = repo.loadWeeklyFromAssets()
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val calId = CalendarHelper.getDefaultCalendarId(applicationContext)
            if (calId == null) {
                // 未找到日历或缺少权限
                return Result.failure()
            }

            weekly.forEach { (datetime, items) ->
                val date = fmt.parse(datetime)
                val startMillis = date.time
                // 假设90分钟的持续时间作为占位符
                val endMillis = startMillis + 90 * 60 * 1000
                items.forEach { item ->
                    val title = item.course ?: "课程"
                    // 通过精确的标题和开始时间去重
                    val existing = CalendarHelper.findExistingEvent(applicationContext, title, startMillis)
                    if (existing == null) {
                        CalendarHelper.insertEvent(applicationContext, calId, title, startMillis, endMillis, item.room ?: "")
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}