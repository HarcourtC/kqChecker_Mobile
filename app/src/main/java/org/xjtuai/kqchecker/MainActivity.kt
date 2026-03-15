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
import org.xjtuai.kqchecker.model.ScheduleItem
import org.xjtuai.kqchecker.auth.TokenManager
import org.xjtuai.kqchecker.auth.WebLoginActivity
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.ui.MainScreen
import org.xjtuai.kqchecker.ui.components.UpdateDialog
import org.xjtuai.kqchecker.ui.theme.KqCheckerTheme
import org.xjtuai.kqchecker.util.LoginHelper
import org.xjtuai.kqchecker.util.NotificationHelper
import org.xjtuai.kqchecker.util.ScheduleParser
import org.xjtuai.kqchecker.util.VersionChecker
import org.xjtuai.kqchecker.util.VersionInfo
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
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
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var showEventLog by remember { mutableStateOf(prefs.getBoolean("event_log_enabled", true)) }
    var startupSyncEnabled by remember { mutableStateOf(prefs.getBoolean("startup_sync_enabled", true)) }

    // 版本更新状态
    var versionInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var versionCheckDone by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "event_log_enabled") {
                showEventLog = sharedPreferences.getBoolean("event_log_enabled", true)
            } else if (key == "startup_sync_enabled") {
                startupSyncEnabled = sharedPreferences.getBoolean("startup_sync_enabled", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun postEvent(msg: String) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            events.add(msg)
        } else {
            mainHandler.post { events.add(msg) }
        }
    }

    val weeklyRepository = remember { RepositoryProvider.getWeeklyRepository() }
    var scheduleItems by remember { mutableStateOf<List<ScheduleItem>>(emptyList()) }

    fun loadHomeSchedule(forceRefresh: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val response = weeklyRepository.getWeeklyData(forceRefresh)
                if (response != null && response.success) {
                    val parsed = ScheduleParser.parse(response.data)
                    withContext(Dispatchers.Main) {
                        scheduleItems = parsed
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to load home schedule", e)
                withContext(Dispatchers.Main) {
                    postEvent("Failed to load home schedule: ${e.message ?: e.toString()}")
                }
            }
        }
    }

    // 检查版本更新
    LaunchedEffect(Unit) {
        if (!versionCheckDone) {
            versionCheckDone = true
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName ?: "1.0"

                val info = VersionChecker.checkForUpdate(currentVersion)
                if (info != null && info.isUpdateAvailable) {
                    versionInfo = info
                    showUpdateDialog = true
                }
            } catch (e: Exception) {
                Log.d("VersionCheck", "Failed to check version", e)
            }
        }
    }

    // 检查缓存过期
    LaunchedEffect(Unit) {
        postEvent(
            if (startupSyncEnabled) {
                "Startup sync enabled, refreshing weekly data once..."
            } else {
                "Checking weekly.json cache expiration..."
            }
        )
        try {
            val cacheStatus = weeklyRepository.getCacheStatus()
            if (startupSyncEnabled || !cacheStatus.exists || cacheStatus.isExpired) {
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
                                loadHomeSchedule(false)
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
                loadHomeSchedule(false)
            }
        } catch (e: Exception) {
            Log.e("AutoRefreshWeekly", "Error checking cache status", e)
            postEvent("Auto-refresh check failed: ${e.message ?: e.toString()}")
            loadHomeSchedule(false)
        }
    }

    // 注册广播接收器
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

    // 登录启动器
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

    // 主屏幕
    MainScreen(
        events = events,
        scheduleItems = scheduleItems,
        showEventLog = showEventLog,
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

    // 显示版本更新对话框
    if (showUpdateDialog && versionInfo != null) {
        UpdateDialog(
            versionInfo = requireNotNull(versionInfo),
            onDismiss = { showUpdateDialog = false },
            onUpdate = { showUpdateDialog = false }
        )
    }
}
