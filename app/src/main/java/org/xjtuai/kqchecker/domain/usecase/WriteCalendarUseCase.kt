package org.xjtuai.kqchecker.domain.usecase

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.util.WorkManagerHelper
import java.util.UUID

/**
 * UseCase for writing attendance data to calendar
 * Encapsulates business logic for enqueuing and monitoring calendar write operations
 */
class WriteCalendarUseCase(private val context: Context) {

  suspend fun writeCalendarFromBackend(): WriteResult {
    return try {
      val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
      val workId = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).enqueue(request)
        request.id
      }

      Log.d("WriteCalendarUseCase", "Calendar write work request enqueued with ID: $workId")
      WriteResult.Enqueued(
        workId = workId,
        message = "Writing calendar from backend..."
      )
    } catch (e: Exception) {
      Log.e("WriteCalendarUseCase", "Failed to enqueue calendar write", e)
      WriteResult.Error(e.message ?: e.toString())
    }
  }

  suspend fun observeCalendarWriteStatus(
    workId: UUID,
    onStatusChange: (String) -> Unit
  ) {
    try {
      WorkManagerHelper.observeWorkStatus(
        context,
        workId,
        onStatusChange = { state, message ->
          onStatusChange(message)
        },
        taskName = "Calendar write"
      )
    } catch (e: Exception) {
      Log.e("WriteCalendarUseCase", "Failed to observe calendar write status", e)
      onStatusChange("Failed to monitor calendar write: ${e.message}")
    }
  }

  sealed class WriteResult {
    data class Enqueued(
      val workId: UUID,
      val message: String
    ) : WriteResult()

    data class Error(val message: String) : WriteResult()
  }
}
