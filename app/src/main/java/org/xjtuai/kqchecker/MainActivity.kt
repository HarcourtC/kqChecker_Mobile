package org.xjtuai.kqchecker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.auth.TokenManager
import org.xjtuai.kqchecker.auth.WebLoginActivity
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.ui.MainScreen
import org.xjtuai.kqchecker.ui.theme.KqCheckerTheme
import org.xjtuai.kqchecker.util.LoginHelper
import org.xjtuai.kqchecker.util.NotificationHelper
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RepositoryProvider.initialize(this)
        setContent {
            KqCheckerTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val scope = rememberCoroutineScope()
    val events = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun postEvent(msg: String) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            events.add(msg)
        } else {
            mainHandler.post { events.add(msg) }
        }
    }

    val weeklyRepository = remember { RepositoryProvider.getWeeklyRepository() }

    // Check cache expiration on startup
    LaunchedEffect(Unit) {
        postEvent("Checking weekly.json cache expiration...")
        try {
            val cacheStatus = weeklyRepository.getCacheStatus()
            if (!cacheStatus.exists || cacheStatus.isExpired) {
                postEvent("Weekly cache is expired or not found, triggering automatic refresh...")
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = weeklyRepository.refreshWeeklyData()
                        withContext(Dispatchers.Main) {
                            if (result != null) {
                                postEvent("Auto-refreshed and saved weekly.json")
                                val updatedStatus = weeklyRepository.getCacheStatus()
                                val expiresMsg = updatedStatus.expiresDate ?: "unknown"
                                postEvent("Cache will expire on: $expiresMsg")
                            } else {
                                postEvent("Auto-refresh failed: Repository returned null")
                            }
                        }
                    } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                        Log.w("AutoRefreshWeekly", "Auth required", e)
                        withContext(Dispatchers.Main) {
                            postEvent("Authentication required: please login")
                            context.startActivity(LoginHelper.createLoginIntent(context))
                        }
                    } catch (e: Exception) {
                        Log.e("AutoRefreshWeekly", "auto-refresh failed", e)
                        withContext(Dispatchers.Main) {
                            postEvent("Auto-refresh failed: ${e.message ?: e.toString()}")
                        }
                    }
                }
            } else {
                val f = File(context.filesDir, "weekly.json")
                if (f.exists()) {
                    try {
                        val expires = JSONObject(f.readText()).optString("expires", "Unknown")
                        postEvent("Weekly cache is up-to-date, expires on: $expires")
                    } catch (e: Exception) {
                         postEvent("Weekly cache check error: ${e.message}")
                    }
                } else {
                    postEvent("Weekly cache is up-to-date")
                }
            }
        } catch (e: Exception) {
            Log.e("AutoRefreshWeekly", "Error checking cache status", e)
            postEvent("Auto-refresh check failed: ${e.message ?: e.toString()}")
        }
    }

    // Register receiver for no-attendance broadcast
    val noAttendanceReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    val k = intent?.getStringExtra("key") ?: "?"
                    val date = intent?.getStringExtra("date") ?: "?"
                    val subj = intent?.getStringExtra("subject")
                    val td = intent?.getStringExtra("time_display")
                    val loc = intent?.getStringExtra("location")

                    val display = if (!subj.isNullOrBlank()) {
                        val parts = listOfNotNull(subj, td, loc).joinToString(" - ")
                        "Warning: No attendance record: $parts"
                    } else {
                        "Warning: No attendance record: $k; please check manually."
                    }
                    postEvent(display)

                    if (ctx != null) {
                        NotificationHelper.sendNoAttendanceNotification(ctx, k, date, subj, td, loc)
                    }
                } catch (t: Throwable) {
                    Log.w("MainActivity", "noAttendanceReceiver failed", t)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter("org.xjtuai.kqchecker.ACTION_NO_ATTENDANCE")
        try {
            context.registerReceiver(noAttendanceReceiver, filter)
        } catch (t: Throwable) {
            Log.w("MainActivity", "registerReceiver failed", t)
        }
        onDispose {
            try {
                context.unregisterReceiver(noAttendanceReceiver)
            } catch (_: Throwable) {}
        }
    }

    // Login launcher
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val token = data?.getStringExtra(WebLoginActivity.RESULT_TOKEN)
        val tokenSource = data?.getStringExtra(WebLoginActivity.RESULT_TOKEN_SOURCE)
        if (token != null) {
            Toast.makeText(context, "Login success", Toast.LENGTH_SHORT).show()
            val srcLabel = LoginHelper.getTokenSourceLabel(tokenSource)
            events.add("Token: ${token.take(40)}... (source: $srcLabel)")
        } else {
            val saved = TokenManager(context).getAccessToken()
            if (saved != null) {
                Toast.makeText(context, "Login success (saved)", Toast.LENGTH_SHORT).show()
                events.add("Token: ${saved.take(40)}... (source: saved)")
            } else {
                Toast.makeText(context, "Login canceled or failed", Toast.LENGTH_SHORT).show()
                events.add("Login canceled or failed")
            }
        }
    }
    
    // MainScreen manages the tabs and content
    MainScreen(
        events = events,
        onPostEvent = { postEvent(it) },
        onLoginClick = { LoginHelper.launchLogin(context, loginLauncher) },
        onCheckCacheStatus = {
            scope.launch(Dispatchers.IO) {
                try {
                    val cacheStatus = weeklyRepository.getCacheStatus()
                    withContext(Dispatchers.Main) {
                        events.add("[Home] Cache exists: ${cacheStatus.exists}, expired: ${cacheStatus.isExpired}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        events.add("[Home] Cannot read cache status: ${e.message ?: e.toString()}")
                    }
                }
            }
        },
        onLoginRequired = { LoginHelper.launchLogin(context, loginLauncher) }
    )
}
