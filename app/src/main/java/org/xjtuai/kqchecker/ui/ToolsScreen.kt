package org.xjtuai.kqchecker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.auth.AuthRequiredException
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.sync.Api2AttendanceQueryWorker
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard
import org.xjtuai.kqchecker.util.WorkManagerHelper
import java.util.concurrent.TimeUnit

/**
 * 工具屏幕
 * 提供调试、同步、日历写入等开发工具功能
 */
@Composable
fun ToolsScreen(
    onPostEvent: (String) -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val weeklyRepository = remember { RepositoryProvider.getWeeklyRepository() }
    val waterListRepository = remember { RepositoryProvider.getWaterListRepository() }
    val weeklyCleaner = remember { RepositoryProvider.getWeeklyCleaner() }
    val tokenManager = remember { org.xjtuai.kqchecker.auth.TokenManager(context) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            onPostEvent("Permissions granted. Please try the action again.")
        } else {
            onPostEvent("Permissions denied.")
        }
    }

    // Prefs for toggles
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var api2AutoEnabled by remember { mutableStateOf(prefs.getBoolean("api2_auto_enabled", false)) }

    suspend fun postEventOnMain(message: String) {
        withContext(Dispatchers.Main) {
            onPostEvent(message)
        }
    }

    fun handleAuthRequired(tag: String, message: String, error: AuthRequiredException) {
        Log.w(tag, message, error)
        scope.launch {
            postEventOnMain("$message.")
            onLoginRequired()
        }
    }

    fun launchRepositoryAction(
        startMessage: String,
        successMessage: String,
        emptyMessage: String,
        tag: String,
        action: suspend () -> Any?
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                postEventOnMain(startMessage)
                val result = action()
                postEventOnMain(if (result != null) successMessage else emptyMessage)
            } catch (e: AuthRequiredException) {
                handleAuthRequired(tag, "Authentication required", e)
            } catch (e: Exception) {
                postEventOnMain("$tag failed: ${e.message ?: e.toString()}")
            }
        }
    }

    /**
     * 启用 API2 自动轮询
     */
    fun enableApi2Periodic() {
        try {
            val workManager = WorkManager.getInstance(context)
            val periodic = PeriodicWorkRequestBuilder<Api2AttendanceQueryWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork("api2_att_query_periodic", ExistingPeriodicWorkPolicy.REPLACE, periodic)
            prefs.edit().putBoolean("api2_auto_enabled", true).apply()
            onPostEvent("Automatic api2 queries enabled")
        } catch (e: Exception) {
            Log.e("Api2Auto", "Failed to enable periodic work", e)
            onPostEvent("Failed to enable automatic api2 queries: ${e.message}")
        }
    }

    /**
     * 禁用 API2 自动轮询
     */
    fun disableApi2Periodic() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("api2_att_query_periodic")
            prefs.edit().putBoolean("api2_auto_enabled", false).apply()
            onPostEvent("Automatic api2 queries disabled")
        } catch (e: Exception) {
            Log.e("Api2Auto", "Failed to disable periodic work", e)
            onPostEvent("Failed to disable automatic api2 queries: ${e.message}")
        }
    }

    /**
     * 开始日历同步
     * 将课程数据写入系统日历
     */
    fun startSync() {
        onPostEvent("Starting forced calendar sync pipeline...")
        scope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    weeklyRepository.refreshWeeklyData()
                }
                if (refreshed == null) {
                    postEventOnMain("Step 1/3 failed: unable to refresh latest weekly data")
                    return@launch
                }
                onPostEvent("Step 1/3 done: latest weekly data refreshed")

                val cleanedOk = withContext(Dispatchers.IO) {
                    weeklyCleaner.generateCleanedWeekly()
                }
                if (!cleanedOk) {
                    postEventOnMain("Step 2/3 failed: cleaned weekly generation failed")
                    return@launch
                }
                onPostEvent("Step 2/3 done: cleaned weekly regenerated")

                val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
                val workId = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).enqueue(request)
                    request.id
                }
                onPostEvent("Step 3/3 started: writing to calendar...")
                WorkManagerHelper.observeWorkStatus(
                    context = context,
                    workId = workId,
                    onStatusChange = { _, statusMessage ->
                        Log.d("WriteCalendarObserver", statusMessage)
                        onPostEvent(statusMessage)
                    },
                    taskName = "Calendar write"
                )
            } catch (e: AuthRequiredException) {
                handleAuthRequired(
                    tag = "WriteCalendarButton",
                    message = "Authentication required before forced calendar write",
                    error = e
                )
            } catch (e: Exception) {
                Log.e("WriteCalendarButton", "Calendar write error", e)
                postEventOnMain("Calendar write error: ${e.message ?: e.toString()}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        InfoCard(title = "Authentication") {
            AppButton(text = "Clear Token (Logout)", onClick = {
                onPostEvent("Clearing token...")
                try {
                    tokenManager.clear()
                    onPostEvent("Token cleared successfully. Please restart the app if needed.")
                } catch (e: Exception) {
                    onPostEvent("Failed to clear token: ${e.message}")
                }
            })
        }

    InfoCard(title = "Synchronization") {
            AppButton(text = "Trigger Sync (Week)", onClick = {
                launchRepositoryAction(
                    startMessage = "Triggering manual sync...",
                    successMessage = "Sync completed successfully",
                    emptyMessage = "Sync failed - null result",
                    tag = "SyncButton"
                ) {
                    weeklyRepository.refreshWeeklyData()
                }
            })

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "Trigger Sync (WaterList)", onClick = {
                launchRepositoryAction(
                    startMessage = "Running experimental sync (API2)...",
                    successMessage = "API2 data fetched and saved",
                    emptyMessage = "Experimental sync failed - null result",
                    tag = "ExperimentalSync"
                ) {
                    waterListRepository.refreshWaterListData()
                }
            })
        }

        InfoCard(title = "Calendar") {
             AppButton(text = "Write to Calendar", onClick = {
                 val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                  val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                  if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                    startSync()
                  } else {
                    onPostEvent("Requesting calendar permissions...")
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                  }
             })
        }

        InfoCard(title = "Debug & Raw Data") {
            AppButton(text = "Fetch Weekly (API Raw)", onClick = {
                scope.launch {
                    onPostEvent("Fetching weekly from API (via WeeklyRepository)...")
                    try {
                         val rawResp = withContext(Dispatchers.IO) { weeklyRepository.fetchWeeklyRawFromApi() }
                        if (!rawResp.isNullOrBlank()) {
                            onPostEvent("Weekly API raw response fetched (${rawResp.length} bytes)")
                             onPostEvent(rawResp.take(500) + "...")
                        } else {
                            onPostEvent("Raw fetch returned empty.")
                        }
                    } catch (e: Exception) {
                        onPostEvent("Fetch failed: ${e.message}")
                    }
                }
            })
             Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "Print weekly.json", onClick = {
                scope.launch {
                    onPostEvent("Printing weekly files...")
                    try {
                        val previews = withContext(Dispatchers.IO) { weeklyRepository.getWeeklyFilePreviews() }
                        if (previews.isEmpty()) {
                            onPostEvent("No weekly files found to print")
                        }
                        for (p in previews) {
                            onPostEvent("File: ${p.name} (${p.size} bytes)")
                            onPostEvent(p.preview.take(200) + "...")
                        }
                    } catch (e: Exception) {
                         onPostEvent("Print failed: ${e.message}")
                    }
                }
            })

             Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "Generate Cleaned Weekly", onClick = {
                 scope.launch {
                    onPostEvent("Generating cleaned weekly...")
                    try {
                        val ok = withContext(Dispatchers.IO) { weeklyCleaner.generateCleanedWeekly() }
                        if (ok) {
                             onPostEvent("Weekly cleaned generation succeeded")
                        } else {
                             onPostEvent("Weekly cleaned generation failed")
                        }
                    } catch(e: Exception) {
                        onPostEvent("Error: ${e.message}")
                    }
                 }
            })
        }

        InfoCard(title = "Settings") {
             Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = "Enable automatic API2 queries:", modifier = Modifier.weight(1f))
                  Switch(checked = api2AutoEnabled, onCheckedChange = { checked ->
                    api2AutoEnabled = checked
                    if (checked) enableApi2Periodic() else disableApi2Periodic()
                  })
             }
        }
    }
}
