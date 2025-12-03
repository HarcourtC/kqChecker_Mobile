package org.example.kqchecker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.example.kqchecker.repo.MockRepository
import org.example.kqchecker.auth.AuthRequiredException
import org.example.kqchecker.auth.TokenManager
import org.example.kqchecker.util.CalendarHelper
import java.text.SimpleDateFormat

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val repo = MockRepository(appContext)

    override suspend fun doWork(): Result {
        return try {
            val weekly = repo.loadWeeklyFromAssets()
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val calId = CalendarHelper.getDefaultCalendarId(applicationContext)
            if (calId == null) {
                // no calendar found or permission missing
                return Result.failure()
            }

            weekly.forEach { (datetime, items) ->
                val date = fmt.parse(datetime)
                val startMillis = date.time
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
        } catch (e: AuthRequiredException) {
            try {
                val tm = TokenManager(applicationContext)
                tm.clear()
                tm.notifyTokenInvalid()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
