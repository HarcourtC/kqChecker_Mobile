package org.xjtuai.kqchecker.debug

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.xjtuai.kqchecker.util.CalendarHelper
import java.text.SimpleDateFormat

/**
 * Test worker for debugging calendar write functionality
 * Loads data from assets/weekly.json and writes to calendar
 */
class TestWriteCalendar(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "TestWriteCalendar"
    }
    private val mockRepo = MockRepository(appContext)

    override suspend fun doWork(): Result {
        return try {
            val weekly = mockRepo.loadWeeklyFromAssets()
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val calId = CalendarHelper.getDefaultCalendarId(applicationContext)
            if (calId == null) {
                return Result.failure()
            }

            weekly.forEach { (datetime, items) ->
                try {
                    val date = fmt.parse(datetime)
                    if (date != null) {
                        val startMillis = date.time
                        val endMillis = startMillis + 90 * 60 * 1000 // 90 minutes duration
                        items.forEach { item ->
                            val title = item.course ?: "课程"
                            val existing = CalendarHelper.findExistingEvent(applicationContext, title, startMillis)
                            if (existing == null) {
                                CalendarHelper.insertEvent(applicationContext, calId, title, startMillis, endMillis, item.room ?: "")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing calendar event", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork", e)
            Result.retry()
        }
    }
}

/**
 * Legacy sync worker for testing
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
