package org.xjtuai.kqchecker.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CompetitionDeadlineWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CompetitionDeadlineWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting deadline check...")
        return try {
            // Ensure repository is initialized (safe to call multiple times)
            RepositoryProvider.initialize(applicationContext)
            val repo = RepositoryProvider.getCompetitionRepository()

            // Fetch data (prefer cache, but can also trigger network if needed)
            // Here we use forceRefresh = false to respect cache, assuming cache is updated by other means
            // or the user opening the app. If we want background updates, we might want forceRefresh=true
            // or rely on a separate sync worker. Let's start with false to be conservative.
            val response = repo.getCompetitionData(forceRefresh = false)

            if (response != null && response.status == "success") {
                val items = response.data
                val tomorrowDate = getTomorrowDateString()
                Log.d(TAG, "Checking for deadlines on: $tomorrowDate")

                var notificationCount = 0
                items.forEach { item ->
                    if (!item.deadline.isNullOrBlank() && item.deadline == tomorrowDate) {
                        Log.d(TAG, "Found deadline match: ${item.title}")
                        NotificationHelper.sendDeadlineNotification(
                            applicationContext,
                            item.title,
                            item.deadline,
                            item.url
                        )
                        notificationCount++
                    }
                }
                Log.d(TAG, "Sent $notificationCount deadline notifications")
            } else {
                Log.w(TAG, "Failed to get competition data or invalid status")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking deadlines", e)
            Result.failure()
        }
    }

    private fun getTomorrowDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
