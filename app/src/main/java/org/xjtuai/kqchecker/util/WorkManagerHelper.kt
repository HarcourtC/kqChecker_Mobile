package org.xjtuai.kqchecker.util

import android.content.Context
import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Unified WorkManager observation patterns
 */
object WorkManagerHelper {
  private const val TAG = "WorkManagerHelper"

  fun getStatusMessage(state: WorkInfo.State, taskName: String = "Task"): String = when (state) {
    WorkInfo.State.ENQUEUED -> "Work enqueued, waiting..."
    WorkInfo.State.RUNNING -> "Work running..."
    WorkInfo.State.SUCCEEDED -> "$taskName completed successfully!"
    WorkInfo.State.FAILED -> "$taskName failed, check logs for details"
    WorkInfo.State.CANCELLED -> "$taskName cancelled"
    else -> "Work state: $state"
  }

  fun createWorkObserver(
    onStatusChange: (WorkInfo.State, String) -> Unit,
    taskName: String = "Task"
  ): Observer<WorkInfo> = Observer { workInfo ->
    if (workInfo == null) return@Observer

    val statusMessage = getStatusMessage(workInfo.state, taskName)
    onStatusChange(workInfo.state, statusMessage)

    if (workInfo.state.isFinished) {
      when (workInfo.state) {
        WorkInfo.State.SUCCEEDED -> {
          onStatusChange(workInfo.state, "Calendar data updated successfully")
          onStatusChange(workInfo.state, "Tip: Use 'Print weekly.json' button to view raw data")
        }
        WorkInfo.State.FAILED -> {
          onStatusChange(workInfo.state, "Suggestion: Check logs for detailed error info")
          onStatusChange(workInfo.state, "Tip: Ensure valid weekly data cache exists")
        }
        else -> {}
      }
    }
  }

  suspend fun enqueueWorkRequest(context: Context, workRequest: OneTimeWorkRequest): UUID {
    return withContext(Dispatchers.IO) {
      WorkManager.getInstance(context).enqueue(workRequest)
      workRequest.id
    }
  }

  suspend fun observeWorkStatus(
    context: Context,
    workId: UUID,
    onStatusChange: (WorkInfo.State, String) -> Unit,
    taskName: String = "Task"
  ) {
    withContext(Dispatchers.Main) {
      WorkManager.getInstance(context)
        .getWorkInfoByIdLiveData(workId)
        .observeForever(createWorkObserver(onStatusChange, taskName))
    }
  }

  suspend fun enqueueAndObserve(
    context: Context,
    workRequest: OneTimeWorkRequest,
    onStatusChange: (WorkInfo.State, String) -> Unit,
    taskName: String = "Task"
  ) {
    val workId = enqueueWorkRequest(context, workRequest)
    Log.d(TAG, "Work request enqueued with ID: $workId")
    observeWorkStatus(context, workId, onStatusChange, taskName)
  }
}
