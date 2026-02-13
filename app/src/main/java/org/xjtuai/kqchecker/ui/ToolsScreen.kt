package org.xjtuai.kqchecker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
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
import org.xjtuai.kqchecker.debug.Api2QueryTestWorker
import org.xjtuai.kqchecker.repository.CacheManager
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.repository.WeeklyCleaner
import org.xjtuai.kqchecker.repository.WeeklyRepository
import org.xjtuai.kqchecker.repository.WaterListRepository
import org.xjtuai.kqchecker.sync.Api2AttendanceQueryWorker
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.util.ConfigHelper
import org.xjtuai.kqchecker.util.LoginHelper
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard
import java.io.File
import java.util.concurrent.TimeUnit

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
    
    fun startSync() {
        // Logic for WriteCalendar extracted from MainActivity
        onPostEvent("Writing calendar from backend...")
        scope.launch {
            try {
                val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
                val workId = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).enqueue(request)
                    request.id
                }
                withContext(Dispatchers.Main) {
                    WorkManager.getInstance(context)
                        .getWorkInfoByIdLiveData(workId)
                        .observeForever { workInfo ->
                            if (workInfo != null) {
                                val statusMessage = when (workInfo.state) {
                                    androidx.work.WorkInfo.State.ENQUEUED -> "Work enqueued..."
                                    androidx.work.WorkInfo.State.RUNNING -> "Work running..."
                                    androidx.work.WorkInfo.State.SUCCEEDED -> "Calendar write completed!"
                                    androidx.work.WorkInfo.State.FAILED -> "Calendar write failed"
                                    androidx.work.WorkInfo.State.CANCELLED -> "Calendar write cancelled"
                                    else -> "Work state: ${workInfo.state}"
                                }
                                Log.d("WriteCalendarObserver", statusMessage)
                                // Note: Avoiding duplicate events might be needed but simple post is fine
                                onPostEvent(statusMessage)
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("WriteCalendarButton", "Calendar write error", e)
                withContext(Dispatchers.Main) {
                    onPostEvent("Calendar write error: ${e.message ?: e.toString()}")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        InfoCard(title = "Synchronization") {
            AppButton(text = "Trigger Sync (Week)", onClick = {
                onPostEvent("Triggering manual sync...")
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = weeklyRepository.refreshWeeklyData()
                        withContext(Dispatchers.Main) {
                            if (result != null) {
                                onPostEvent("Sync completed successfully")
                            } else {
                                onPostEvent("Sync failed - null result")
                            }
                        }
                    } catch (e: AuthRequiredException) {
                        Log.w("SyncButton", "Auth required", e)
                        withContext(Dispatchers.Main) {
                            onPostEvent("Authentication required.")
                            onLoginRequired()
                        }
                    } catch (e: Exception) {
                         withContext(Dispatchers.Main) {
                            onPostEvent("Sync exception: ${e.message}")
                         }
                    }
                }
            })
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AppButton(text = "Trigger Sync (WaterList)", onClick = {
                 onPostEvent("Running experimental sync (API2)...")
                  scope.launch(Dispatchers.IO) {
                    try {
                      val result = waterListRepository.refreshWaterListData()
                      withContext(Dispatchers.Main) {
                        if (result != null) {
                          onPostEvent("API2 data fetched and saved")
                        } else {
                          onPostEvent("Experimental sync failed - null result")
                        }
                      }
                    } catch (e: AuthRequiredException) {
                      Log.w("ExperimentalSync", "Auth required", e)
                      withContext(Dispatchers.Main) {
                        onPostEvent("Authentication required.")
                        onLoginRequired()
                      }
                    } catch (e: Exception) {
                      withContext(Dispatchers.Main) {
                        onPostEvent("Experimental sync exception: ${e.message ?: e.toString()}")
                      }
                    }
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
