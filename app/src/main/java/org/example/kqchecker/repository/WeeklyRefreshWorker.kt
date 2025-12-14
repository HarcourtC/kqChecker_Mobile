package org.example.kqchecker.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "WeeklyRefreshWorker"
private const val CHANNEL_ID = "weekly_retry_channel"

class WeeklyRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val attempt = inputData.getInt("attempt", 1)
            Log.d(TAG, "Worker started, attempt=$attempt")

            val repo = WeeklyRepository(applicationContext)
            val res = try { repo.refreshWeeklyData() } catch (t: Throwable) { null }

            if (res != null) {
                Log.d(TAG, "Weekly refresh succeeded on attempt=$attempt")
                return@withContext Result.success()
            }

            if (attempt < 3) {
                Log.w(TAG, "Weekly refresh failed on attempt=$attempt, scheduling next attempt")
                scheduleNextAttempt(attempt + 1)
                return@withContext Result.success()
            } else {
                Log.e(TAG, "Weekly refresh failed after $attempt attempts, notifying user")
                notifyBackendFailure()
                return@withContext Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker error", e)
            return@withContext Result.retry()
        }
    }

    private fun scheduleNextAttempt(nextAttempt: Int) {
        try {
            val data: Data = workDataOf("attempt" to nextAttempt)
            val req = OneTimeWorkRequestBuilder<WeeklyRefreshWorker>()
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(req)
            Log.d(TAG, "Scheduled next weekly refresh attempt=$nextAttempt in 20 minutes")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to schedule next attempt", t)
        }
    }

    private fun notifyBackendFailure() {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, "周课表刷新失败", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("后端考勤系统异常")
                .setContentText("无法获取周课表（多次重试失败），请稍后检查或联系运维。")
                .setAutoCancel(true)
                .build()
            nm.notify(3001, notif)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to send backend failure notification", t)
        }
    }
}
