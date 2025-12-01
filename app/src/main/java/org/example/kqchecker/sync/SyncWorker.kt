package org.example.kqchecker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.example.kqchecker.repo.MockRepository
import org.example.kqchecker.util.CalendarHelper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val repo = MockRepository(appContext)

    override suspend fun doWork(): Result {
        return try {
            val weekly = repo.loadWeeklyFromAssets()
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val calId = CalendarHelper.getDefaultCalendarId(applicationContext)
            if (calId == null) {
                // no calendar found or permission missing
                return Result.failure()
            }

            weekly.forEach { (datetime, items) ->
                val ldt = LocalDateTime.parse(datetime, fmt)
                val startMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // assume 90 minutes duration as placeholder
                val endMillis = startMillis + 90 * 60 * 1000
                items.forEach { item ->
                    val title = item.course ?: "课程"
                    // dedupe by exact title + startMillis
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
