package org.xjtuai.kqchecker.domain.usecase

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.auth.AuthRequiredException
import org.xjtuai.kqchecker.repository.CacheManager
import org.xjtuai.kqchecker.repository.WeeklyCleaner
import org.xjtuai.kqchecker.repository.WeeklyRepository
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.util.WorkManagerHelper
import java.util.UUID

/**
 * UseCase for orchestrating end-to-end integration workflow
 * Ensures cleaned weekly exists and coordinates calendar write operations
 */
class IntegrationFlowUseCase(
  private val context: Context,
  private val weeklyRepository: WeeklyRepository,
  private val weeklyCleaner: WeeklyCleaner
) {

  /**
   * Execute the complete integration flow:
   * 1. Check if cleaned weekly exists
   * 2. If not, check and fetch weekly.json if needed
   * 3. Generate cleaned weekly
   * 4. Enqueue calendar write task
   */
  suspend fun executeIntegrationFlow(): IntegrationResult {
    return try {
      val cm = CacheManager(context)

      // Step 1: Check if cleaned weekly exists
      Log.d("IntegrationFlowUseCase", "Checking cleaned weekly...")
      val cleaned = withContext(Dispatchers.IO) {
        cm.readFromCache(WeeklyCleaner.CLEANED_WEEKLY_FILE)
      }

      if (!cleaned.isNullOrBlank()) {
        Log.d("IntegrationFlowUseCase", "Found cleaned weekly, proceeding to calendar write")
        return IntegrationResult.CleanedWeeklyExists(
          message = "Found cleaned weekly, writing to calendar"
        )
      }

      Log.d("IntegrationFlowUseCase", "Cleaned weekly not found, checking weekly.json...")

      // Step 2: Check if weekly.json exists
      val raw = withContext(Dispatchers.IO) {
        cm.readFromCache(CacheManager.WEEKLY_CACHE_FILE)
      }

      if (raw.isNullOrBlank()) {
        Log.d("IntegrationFlowUseCase", "weekly.json not found, fetching from backend...")

        // Step 2b: Fetch weekly data from backend
        try {
          val result = withContext(Dispatchers.IO) {
            weeklyRepository.refreshWeeklyData()
          }

          if (result == null) {
            Log.e("IntegrationFlowUseCase", "Backend returned null")
            IntegrationResult.Error("Backend returned null")
          } else {
            Log.d("IntegrationFlowUseCase", "Successfully fetched and cached weekly data")
            IntegrationResult.DataFetched(
              message = "Successfully fetched and cached weekly data"
            )
          }
        } catch (e: AuthRequiredException) {
          Log.w("IntegrationFlowUseCase", "Auth required during integration", e)
          IntegrationResult.AuthRequired(e.message ?: "Authentication required")
        } catch (e: Exception) {
          Log.e("IntegrationFlowUseCase", "Failed to fetch weekly", e)
          IntegrationResult.Error(e.message ?: e.toString())
        }
      } else {
        Log.d("IntegrationFlowUseCase", "Found weekly.json cache, ready to generate cleaned")
        IntegrationResult.DataExists(message = "Found weekly.json cache")
      }
    } catch (e: Exception) {
      Log.e("IntegrationFlowUseCase", "Integration flow error", e)
      IntegrationResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Generate cleaned weekly data from raw weekly.json
   */
  suspend fun generateCleanedWeekly(): GenerateCleanedResult {
    return try {
      Log.d("IntegrationFlowUseCase", "Generating cleaned weekly...")
      val ok = withContext(Dispatchers.IO) {
        weeklyCleaner.generateCleanedWeekly()
      }

      if (ok) {
        Log.d("IntegrationFlowUseCase", "Cleaned weekly generated successfully")
        GenerateCleanedResult.Success(message = "Cleaned weekly generated successfully")
      } else {
        Log.w("IntegrationFlowUseCase", "Cleaned weekly generation returned false")
        GenerateCleanedResult.Error("Cleaned weekly generation returned false")
      }
    } catch (e: IllegalStateException) {
      Log.w("IntegrationFlowUseCase", "Raw data missing during generation", e)
      GenerateCleanedResult.Error(e.message ?: "Raw data missing")
    } catch (e: Exception) {
      Log.e("IntegrationFlowUseCase", "Error generating cleaned weekly", e)
      GenerateCleanedResult.Error(e.message ?: e.toString())
    }
  }

  /**
   * Enqueue calendar write task and monitor its progress
   */
  suspend fun submitAndMonitorCalendarWrite(
    onStatusChange: (String) -> Unit
  ): CalendarWriteResult {
    return try {
      Log.d("IntegrationFlowUseCase", "Submitting calendar write task...")

      val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
      val workId = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).enqueue(request)
        request.id
      }

      Log.d("IntegrationFlowUseCase", "Calendar write work enqueued with ID: $workId")

      // Start monitoring the work
      WorkManagerHelper.observeWorkStatus(
        context,
        workId,
        onStatusChange = { state, message ->
          onStatusChange(message)
        },
        taskName = "Calendar write"
      )

      CalendarWriteResult.Submitted(
        workId = workId,
        message = "Calendar write submitted"
      )
    } catch (e: Exception) {
      Log.e("IntegrationFlowUseCase", "Failed to submit calendar write", e)
      CalendarWriteResult.Error(e.message ?: e.toString())
    }
  }

  sealed class IntegrationResult {
    data class CleanedWeeklyExists(val message: String) : IntegrationResult()
    data class DataExists(val message: String) : IntegrationResult()
    data class DataFetched(val message: String) : IntegrationResult()
    data class AuthRequired(val message: String) : IntegrationResult()
    data class Error(val message: String) : IntegrationResult()
  }

  sealed class GenerateCleanedResult {
    data class Success(val message: String) : GenerateCleanedResult()
    data class Error(val message: String) : GenerateCleanedResult()
  }

  sealed class CalendarWriteResult {
    data class Submitted(val workId: UUID, val message: String) : CalendarWriteResult()
    data class Error(val message: String) : CalendarWriteResult()
  }
}
