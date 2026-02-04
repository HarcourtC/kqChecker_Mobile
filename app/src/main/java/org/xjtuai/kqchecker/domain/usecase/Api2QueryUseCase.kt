package org.xjtuai.kqchecker.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.sync.Api2AttendanceQueryWorker
import org.xjtuai.kqchecker.sync.Api2PollingService
import java.util.concurrent.TimeUnit

/**
 * UseCase for managing API2 query operations
 * Encapsulates business logic for both periodic and foreground polling modes
 */
class Api2QueryUseCase(private val context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE)

  /**
   * Enable periodic background API2 queries (every 15 minutes)
   * Uses WorkManager for battery-friendly scheduling
   */
  suspend fun enablePeriodicQueries(): QueryResult {
    return try {
      val workManager = WorkManager.getInstance(context)
      val periodic = PeriodicWorkRequestBuilder<Api2AttendanceQueryWorker>(
        15,
        TimeUnit.MINUTES
      ).build()

      withContext(Dispatchers.IO) {
        workManager.enqueueUniquePeriodicWork(
          "api2_att_query_periodic",
          ExistingPeriodicWorkPolicy.REPLACE,
          periodic
        )
      }

      prefs.edit().putBoolean("api2_auto_enabled", true).apply()
      Log.d("Api2QueryUseCase", "Periodic API2 queries enabled")

      QueryResult.Success(message = "Automatic api2 queries enabled")
    } catch (e: Exception) {
      Log.e("Api2QueryUseCase", "Failed to enable periodic work", e)
      QueryResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Disable periodic background API2 queries
   */
  suspend fun disablePeriodicQueries(): QueryResult {
    return try {
      withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork("api2_att_query_periodic")
      }

      prefs.edit().putBoolean("api2_auto_enabled", false).apply()
      Log.d("Api2QueryUseCase", "Periodic API2 queries disabled")

      QueryResult.Success(message = "Automatic api2 queries disabled")
    } catch (e: Exception) {
      Log.e("Api2QueryUseCase", "Failed to disable periodic work", e)
      QueryResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Start foreground polling service for real-time API2 queries
   * Higher battery usage but better real-time responsiveness
   */
  suspend fun startForegroundPolling(intervalMinutes: Int = 5): QueryResult {
    return try {
      withContext(Dispatchers.IO) {
        prefs.edit()
          .putBoolean("api2_foreground_enabled", true)
          .putInt("api2_foreground_interval_min", intervalMinutes)
          .apply()

        val svc = android.content.Intent(context, Api2PollingService::class.java)
        svc.putExtra(Api2PollingService.EXTRA_INTERVAL_MIN, intervalMinutes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(svc)
        } else {
          context.startService(svc)
        }
      }

      Log.d("Api2QueryUseCase", "Foreground polling started with interval ${intervalMinutes}m")

      QueryResult.Success(message = "Foreground polling started (interval ${intervalMinutes}m)")
    } catch (e: Exception) {
      Log.e("Api2QueryUseCase", "Failed to start foreground polling", e)
      QueryResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Stop foreground polling service
   */
  suspend fun stopForegroundPolling(): QueryResult {
    return try {
      withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("api2_foreground_enabled", false).apply()
        context.stopService(
          android.content.Intent(context, Api2PollingService::class.java)
        )
      }

      Log.d("Api2QueryUseCase", "Foreground polling stopped")

      QueryResult.Success(message = "Foreground polling stopped")
    } catch (e: Exception) {
      Log.e("Api2QueryUseCase", "Failed to stop foreground polling", e)
      QueryResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Enqueue a manual one-time API2 query
   */
  suspend fun enqueueManualQuery(): QueryResult {
    return try {
      val request = OneTimeWorkRequestBuilder<Api2AttendanceQueryWorker>().build()

      withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).enqueue(request)
      }

      Log.d("Api2QueryUseCase", "Manual api2 query enqueued")

      QueryResult.Success(message = "Manual api2 query enqueued")
    } catch (e: Exception) {
      Log.e("Api2QueryUseCase", "Failed to enqueue manual query", e)
      QueryResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Check if periodic queries are currently enabled
   */
  suspend fun isPeriodicEnabled(): Boolean {
    return withContext(Dispatchers.IO) {
      prefs.getBoolean("api2_auto_enabled", false)
    }
  }

  /**
   * Check if foreground polling is currently enabled
   */
  suspend fun isForegroundEnabled(): Boolean {
    return withContext(Dispatchers.IO) {
      prefs.getBoolean("api2_foreground_enabled", false)
    }
  }

  /**
   * Get the current foreground polling interval in minutes
   */
  suspend fun getForegroundInterval(): Int {
    return withContext(Dispatchers.IO) {
      prefs.getInt("api2_foreground_interval_min", 5)
    }
  }

  sealed class QueryResult {
    data class Success(val message: String) : QueryResult()
    data class Error(val message: String) : QueryResult()
  }
}
