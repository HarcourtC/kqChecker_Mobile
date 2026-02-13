package org.xjtuai.kqchecker.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.xjtuai.kqchecker.sync.Api2PollingService
import org.xjtuai.kqchecker.sync.CompetitionDeadlineWorker
import org.xjtuai.kqchecker.ui.components.InfoCard

@Composable
fun IntegrationScreen(
    onPostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var api2ForegroundEnabled by remember { mutableStateOf(prefs.getBoolean("api2_foreground_enabled", false)) }
    var deadlineCheckEnabled by remember { mutableStateOf(prefs.getBoolean("deadline_check_enabled", false)) }

    fun startForegroundPolling(intervalMin: Int = 5) {
        try {
            prefs.edit()
                .putBoolean("api2_foreground_enabled", true)
                .putInt("api2_foreground_interval_min", intervalMin)
                .apply()
            val svc = Intent(context, Api2PollingService::class.java)
            svc.putExtra(Api2PollingService.EXTRA_INTERVAL_MIN, intervalMin)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
            api2ForegroundEnabled = true
            onPostEvent("Foreground polling started (interval ${intervalMin}m)")
        } catch (e: Exception) {
            Log.e("Api2Polling", "Failed to start foreground polling", e)
            onPostEvent("Failed to start foreground polling: ${e.message}")
        }
    }

    fun stopForegroundPolling() {
        try {
            prefs.edit().putBoolean("api2_foreground_enabled", false).apply()
            context.stopService(Intent(context, Api2PollingService::class.java))
            api2ForegroundEnabled = false
            onPostEvent("Foreground polling stopped")
        } catch (e: Exception) {
            Log.e("Api2Polling", "Failed to stop foreground polling", e)
            onPostEvent("Failed to stop foreground polling: ${e.message}")
        }
    }

    fun enableDeadlineCheck() {
        try {
            val workManager = WorkManager.getInstance(context)
            // Check once a day (24 hours)
            val periodic = PeriodicWorkRequestBuilder<CompetitionDeadlineWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(10, TimeUnit.SECONDS) // Initial delay to not block startup
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "competition_deadline_check", 
                ExistingPeriodicWorkPolicy.UPDATE, 
                periodic
            )
            prefs.edit().putBoolean("deadline_check_enabled", true).apply()
            deadlineCheckEnabled = true
            onPostEvent("Deadline check enabled (daily)")
        } catch (e: Exception) {
            Log.e("DeadlineCheck", "Failed to enable deadline check", e)
            onPostEvent("Failed to enable deadline check: ${e.message}")
        }
    }

    fun disableDeadlineCheck() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("competition_deadline_check")
            prefs.edit().putBoolean("deadline_check_enabled", false).apply()
            deadlineCheckEnabled = false
            onPostEvent("Deadline check disabled")
        } catch (e: Exception) {
            Log.e("DeadlineCheck", "Failed to disable deadline check", e)
            onPostEvent("Failed to disable deadline check: ${e.message}")
        }
    }
    
    // Restore state on launch
    LaunchedEffect(Unit) {
        if (deadlineCheckEnabled) {
             // Just to be sure it's enqueued if prefs say so
             // enableDeadlineCheck() // Optional: re-enqueue to ensure it's there
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        InfoCard(title = "Background Services") {
             Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enable foreground polling (Realtime)",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = api2ForegroundEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            Toast.makeText(context, "Foreground polling increases battery usage.", Toast.LENGTH_LONG).show()
                            startForegroundPolling(5)
                        } else {
                            stopForegroundPolling()
                        }
                    }
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "Enable deadline daily reminder",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = deadlineCheckEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            enableDeadlineCheck()
                        } else {
                            disableDeadlineCheck()
                        }
                    }
                )
            }
        }
    }
}
