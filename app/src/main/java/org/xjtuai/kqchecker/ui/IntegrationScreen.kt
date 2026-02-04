package org.xjtuai.kqchecker.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.sync.Api2PollingService
import org.xjtuai.kqchecker.ui.components.InfoCard

@Composable
fun IntegrationScreen(
    onPostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var api2ForegroundEnabled by remember { mutableStateOf(prefs.getBoolean("api2_foreground_enabled", false)) }

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
        }
    }
}
