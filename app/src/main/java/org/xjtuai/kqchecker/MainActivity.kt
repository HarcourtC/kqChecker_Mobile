package org.xjtuai.kqchecker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.auth.TokenManager
import org.xjtuai.kqchecker.auth.WebLoginActivity
import org.xjtuai.kqchecker.repository.CacheManager
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.repository.WeeklyCleaner
import org.xjtuai.kqchecker.repository.WeeklyRepository
import org.xjtuai.kqchecker.sync.Api2AttendanceQueryWorker
import org.xjtuai.kqchecker.debug.Api2QueryTestWorker
import org.xjtuai.kqchecker.debug.TestWriteCalendar
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.util.ConfigHelper
import org.xjtuai.kqchecker.util.LoginHelper
import org.xjtuai.kqchecker.util.NotificationHelper
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    RepositoryProvider.initialize(this)
    setContent {
      AppContent()
    }
  }
}

@Composable
fun AppContent() {
  val scope = rememberCoroutineScope()
  val events = remember { mutableStateListOf<String>() }
  val context = LocalContext.current
  val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

  fun postEvent(msg: String) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
      events.add(msg)
    } else {
      mainHandler.post { events.add(msg) }
    }
  }

  val weeklyRepository = RepositoryProvider.getWeeklyRepository()
  val waterListRepository = RepositoryProvider.getWaterListRepository()
  val weeklyCleaner = RepositoryProvider.getWeeklyCleaner()

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
          val expires = JSONObject(f.readText()).optString("expires", "Unknown")
          postEvent("Weekly cache is up-to-date, expires on: $expires")
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

  // Page state: 0 = Home, 1 = Tools, 2 = Integration
  var currentPage by remember { mutableStateOf(0) }

  // Auto-query toggle (persisted in SharedPreferences)
  val prefs = context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE)
  var api2AutoEnabled by remember { mutableStateOf(prefs.getBoolean("api2_auto_enabled", false)) }
  var api2ForegroundEnabled by remember { mutableStateOf(prefs.getBoolean("api2_foreground_enabled", false)) }

  fun startForegroundPolling(intervalMin: Int = 5) {
    try {
      prefs.edit()
        .putBoolean("api2_foreground_enabled", true)
        .putInt("api2_foreground_interval_min", intervalMin)
        .apply()
      val svc = Intent(context, org.xjtuai.kqchecker.sync.Api2PollingService::class.java)
      svc.putExtra(org.xjtuai.kqchecker.sync.Api2PollingService.EXTRA_INTERVAL_MIN, intervalMin)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(svc)
      } else {
        context.startService(svc)
      }
      api2ForegroundEnabled = true
      postEvent("Foreground polling started (interval ${intervalMin}m)")
    } catch (e: Exception) {
      Log.e("Api2Polling", "Failed to start foreground polling", e)
      postEvent("Failed to start foreground polling: ${e.message}")
    }
  }

  fun stopForegroundPolling() {
    try {
      prefs.edit().putBoolean("api2_foreground_enabled", false).apply()
      context.stopService(Intent(context, org.xjtuai.kqchecker.sync.Api2PollingService::class.java))
      api2ForegroundEnabled = false
      postEvent("Foreground polling stopped")
    } catch (e: Exception) {
      Log.e("Api2Polling", "Failed to stop foreground polling", e)
      postEvent("Failed to stop foreground polling: ${e.message}")
    }
  }

  fun enableApi2Periodic() {
    try {
      val workManager = WorkManager.getInstance(context)
      val periodic = PeriodicWorkRequestBuilder<Api2AttendanceQueryWorker>(15, TimeUnit.MINUTES).build()
      workManager.enqueueUniquePeriodicWork("api2_att_query_periodic", ExistingPeriodicWorkPolicy.REPLACE, periodic)
      prefs.edit().putBoolean("api2_auto_enabled", true).apply()
      postEvent("Automatic api2 queries enabled")
    } catch (e: Exception) {
      Log.e("Api2Auto", "Failed to enable periodic work", e)
      postEvent("Failed to enable automatic api2 queries: ${e.message}")
    }
  }

  fun disableApi2Periodic() {
    try {
      WorkManager.getInstance(context).cancelUniqueWork("api2_att_query_periodic")
      prefs.edit().putBoolean("api2_auto_enabled", false).apply()
      postEvent("Automatic api2 queries disabled")
    } catch (e: Exception) {
      Log.e("Api2Auto", "Failed to disable periodic work", e)
      postEvent("Failed to disable automatic api2 queries: ${e.message}")
    }
  }

  // Restore periodic work if previously enabled
  LaunchedEffect(Unit) {
    if (api2AutoEnabled) enableApi2Periodic()
    if (prefs.getBoolean("api2_foreground_enabled", false)) api2ForegroundEnabled = true
  }

    

  var integrationPending by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions.entries.all { it.value }
    if (granted) {
      if (integrationPending) {
        integrationPending = false
        scope.launch { startIntegrationFlow(context, weeklyRepository, weeklyCleaner, events, scope) }
      } else {
        events.add("Starting calendar write from backend...")
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
                    if (!events.contains(statusMessage)) events.add(statusMessage)
                  }
                }
            }
          } catch (e: Exception) {
            Log.e("WriteCalendarButton", "Calendar write error", e)
            withContext(Dispatchers.Main) {
              events.add("Calendar write error: ${e.message ?: e.toString()}")
            }
          }
        }
      }
    } else {
      events.add("Calendar permission denied. Cannot sync to calendar.")
    }
  }

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
      Log.d("MainActivity", "Login success, source=$tokenSource")
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

  Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Column(modifier = Modifier.weight(1f)) {
        if (currentPage == 0) {
          // Home page
          Text(text = "kqChecker", style = MaterialTheme.typography.h5)
          Spacer(modifier = Modifier.padding(6.dp))
          Text(text = "Welcome to kqChecker. Please login and go to the Integration page to use.")
          Button(onClick = { currentPage = 1 }, modifier = Modifier.padding(top = 12.dp)) {
            Text(text = "Tools")
          }
          Button(onClick = { currentPage = 2 }, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = "Integration")
          }
          Button(onClick = {
            LoginHelper.launchLogin(context, loginLauncher)
          }, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = "Login")
          }
          Button(onClick = {
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
          }, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = "Check Cache Status")
          }
        } else if (currentPage == 1) {
          // Tools page
          Row {
            Button(onClick = { currentPage = 0 }) { Text(text = "Home") }
            Spacer(modifier = Modifier.padding(8.dp))
            Button(onClick = { currentPage = 2 }) { Text(text = "Integration") }
          }
          val buttonsScroll = rememberScrollState()
          Column(modifier = Modifier
            .fillMaxHeight(0.5f)
            .verticalScroll(buttonsScroll)) {
            Text(text = "kqChecker - Android Skeleton")
            Button(onClick = {
              events.add("Triggering manual sync...")
              scope.launch(Dispatchers.IO) {
                try {
                  val result = weeklyRepository.refreshWeeklyData()
                  withContext(Dispatchers.Main) {
                    if (result != null) {
                      events.add("Sync completed successfully")
                      val cacheStatus = weeklyRepository.getCacheStatus()
                      val statusText = when {
                        !cacheStatus.exists -> "No Cache"
                        cacheStatus.isExpired -> "Cache Expired"
                        else -> "Cache Valid"
                      }
                      events.add("Cache status: $statusText")
                      events.add("Cache expires on: ${cacheStatus.expiresDate ?: "unknown"}")
                    } else {
                      events.add("Sync failed - null result")
                    }
                  }
                } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                  Log.w("SyncButton", "Auth required", e)
                  withContext(Dispatchers.Main) {
                    events.add("Authentication required: opening login...")
                    LoginHelper.launchLogin(context, loginLauncher)
                  }
                } catch (e: Exception) {
                  withContext(Dispatchers.Main) {
                    events.add("Sync exception: ${e.message}")
                  }
                }
              }
              val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
              val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
              if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                startSync(context)
              } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Test Write Calendar")
            }

            // Write Calendar button
            Button(onClick = {
              Log.d("WriteCalendarButton", "Write Calendar clicked")
              val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
              val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
              if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                events.add("Writing calendar from backend...")
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
                            if (!events.contains(statusMessage)) events.add(statusMessage)
                          }
                        }
                    }
                  } catch (e: Exception) {
                    Log.e("WriteCalendarButton", "Calendar write error", e)
                    withContext(Dispatchers.Main) {
                      events.add("Calendar write error: ${e.message ?: e.toString()}")
                    }
                  }
                }
              } else {
                events.add("Requesting calendar permissions...")
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
              }
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Write Calendar")
            }

            Button(onClick = {
              events.add("Running experimental sync (API2)...")
              scope.launch(Dispatchers.IO) {
                try {
                  val result = waterListRepository.refreshWaterListData()
                  withContext(Dispatchers.Main) {
                    if (result != null) {
                      events.add("Experimental sync completed successfully")
                      events.add("API2 data fetched and saved")
                    } else {
                      events.add("Experimental sync failed - null result")
                    }
                  }
                } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                  Log.w("ExperimentalSync", "Auth required", e)
                  withContext(Dispatchers.Main) {
                    events.add("Authentication required: opening login...")
                    LoginHelper.launchLogin(context, loginLauncher)
                  }
                } catch (e: Exception) {
                  withContext(Dispatchers.Main) {
                    events.add("Experimental sync exception: ${e.message ?: e.toString()}")
                  }
                }
              }
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Run Experimental Sync")
            }

            // Manual api2 query
            Button(onClick = {
              events.add("Manual api2 query triggered")
              scope.launch(Dispatchers.IO) {
                try {
                  val request = OneTimeWorkRequestBuilder<Api2AttendanceQueryWorker>().build()
                  WorkManager.getInstance(context).enqueue(request)
                  withContext(Dispatchers.Main) { postEvent("Manual api2 query enqueued") }
                } catch (e: Exception) {
                  Log.e("ManualApi2", "Failed to enqueue manual api2 query", e)
                  withContext(Dispatchers.Main) { postEvent("Failed to enqueue: ${e.message}") }
                }
              }
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Manual Api2 Query")
            }

            // Api2 test button (no force)
            Button(onClick = {
              events.add("Manual api2 TEST triggered")
              scope.launch(Dispatchers.IO) {
                try {
                  val req = OneTimeWorkRequestBuilder<Api2QueryTestWorker>().build()
                  WorkManager.getInstance(context).enqueue(req)
                  withContext(Dispatchers.Main) { postEvent("Manual api2 TEST enqueued") }
                } catch (e: Exception) {
                  Log.e("ManualApi2Test", "Failed to enqueue api2 test", e)
                  withContext(Dispatchers.Main) { postEvent("Failed to enqueue: ${e.message}") }
                }
              }
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Run Api2 Test")
            }

            // Force api2 test button
            Button(onClick = {
              events.add("Manual api2 FORCE TEST triggered")
              scope.launch(Dispatchers.IO) {
                try {
                  val data = androidx.work.Data.Builder().putBoolean("force", true).build()
                  val req = OneTimeWorkRequestBuilder<Api2QueryTestWorker>().setInputData(data).build()
                  WorkManager.getInstance(context).enqueue(req)
                  withContext(Dispatchers.Main) { postEvent("Manual api2 FORCE TEST enqueued") }
                } catch (e: Exception) {
                  Log.e("ManualApi2Test", "Failed to enqueue force test", e)
                  withContext(Dispatchers.Main) { postEvent("Failed to enqueue: ${e.message}") }
                }
              }
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Force Api2 Test")
            }

            // Auto api2 query toggle
            Row(modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Enable automatic Api2 queries:")
              Spacer(modifier = Modifier.padding(8.dp))
              Switch(checked = api2AutoEnabled, onCheckedChange = { checked ->
                api2AutoEnabled = checked
                if (checked) enableApi2Periodic() else disableApi2Periodic()
              })
            }

            Button(onClick = {
              scope.launch {
                events.add("Testing authenticated request (via DebugRepository)...")
                try {
                  val debugRepo = RepositoryProvider.getDebugRepository()
                  val result = debugRepo.performDebugRequest()
                  if (result.code >= 0) {
                    events.add("HTTP ${result.code} - headers logged (see Logcat)")
                    events.add(result.sentHeaders.take(300))
                    val preview = result.bodyPreview ?: ""
                    if (preview.isNotBlank()) events.add(preview.take(200))
                  } else {
                    events.add("Debug request failed: ${result.bodyPreview}")
                  }
                } catch (e: Exception) {
                  Log.e("DebugRequest", "Exception during debug request", e)
                  events.add("Debug request failed: ${e.message ?: e.toString()}")
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Debug Request")
            }

            Button(onClick = {
              scope.launch {
                events.add("Fetching weekly from API (via WeeklyRepository)...")
                try {
                  val config = ConfigHelper.getConfig(context)
                  val baseUrl = config.baseUrl
                  val path = "attendance-student/rankClass/getWeekSchedule2"
                  val fullUrl = try {
                    val baseUri = java.net.URI(baseUrl)
                    val scheme = baseUri.scheme ?: "http"
                    val host = baseUri.host ?: baseUrl.replace(Regex("https?://"), "").split(":")[0]
                    val port = if (baseUri.port != -1) ":${baseUri.port}" else ""
                    "$scheme://$host$port/$path"
                  } catch (e: Exception) {
                    baseUrl.trimEnd('/') + "/" + path
                  }

                  // DNS resolution info
                  try {
                    val uri = java.net.URI(fullUrl)
                    val host = uri.host ?: fullUrl.replace(Regex("https?://"), "").split(":")[0]
                    val addrs = java.net.InetAddress.getAllByName(host)
                    events.add("DNS: $host -> ${addrs.joinToString(",") { it.hostAddress }}")
                  } catch (e: Exception) {
                    events.add("Host resolve failed: ${e.message}")
                  }

                  val weeklyRepo = WeeklyRepository(context)
                  try {
                    val rawResp = withContext(Dispatchers.IO) { weeklyRepo.fetchWeeklyRawFromApi() }
                    if (!rawResp.isNullOrBlank()) {
                      events.add("Weekly API raw response fetched (${rawResp.length} bytes)")
                      withContext(Dispatchers.IO) {
                        rawResp.chunked(4000).forEachIndexed { i, chunk ->
                          Log.d("FetchWeeklyRaw", "Chunk ${i + 1}: $chunk")
                        }
                      }
                      events.add(rawResp.take(800))
                    } else {
                      val result = withContext(Dispatchers.IO) { weeklyRepo.refreshWeeklyData() }
                      if (result != null) {
                        events.add("Weekly fetch: success (via refresh)")
                      } else {
                        events.add("Weekly fetch: failed")
                      }
                      events.add("Saved weekly.json: ${File(context.filesDir, CacheManager.WEEKLY_CACHE_FILE).absolutePath}")
                      val cm = CacheManager(context)
                      val raw = withContext(Dispatchers.IO) { cm.readFromCache(CacheManager.WEEKLY_RAW_CACHE_FILE) }
                      if (!raw.isNullOrBlank()) events.add(raw.take(800))
                    }
                  } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                    Log.w("FetchWeekly", "Auth required", e)
                    withContext(Dispatchers.Main) {
                      events.add("Authentication required: opening login...")
                      LoginHelper.launchLogin(context, loginLauncher)
                    }
                  }
                } catch (e: Exception) {
                  Log.e("FetchWeekly", "fetch failed", e)
                  events.add("Fetch failed: ${e.message ?: e.toString()}")
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Fetch Weekly (API)")
            }

            Button(onClick = {
              scope.launch {
                events.add("Fetching api2 (water list) via WaterListRepository...")
                try {
                  val result = withContext(Dispatchers.IO) { waterListRepository.refreshWaterListData() }
                  withContext(Dispatchers.Main) {
                    if (result != null) {
                      events.add("api2 fetch: success")
                    } else {
                      events.add("api2 fetch: failed or returned null")
                    }
                    val cm = CacheManager(context)
                    events.add("Saved: ${File(context.filesDir, CacheManager.WATER_LIST_CACHE_FILE).absolutePath}")
                    val raw = withContext(Dispatchers.IO) { cm.readFromCache(CacheManager.WATER_LIST_CACHE_FILE) }
                    if (!raw.isNullOrBlank()) events.add(raw.take(800))
                  }
                } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
                  Log.w("FetchApi2", "Auth required", e)
                  withContext(Dispatchers.Main) {
                    events.add("Authentication required: opening login...")
                    LoginHelper.launchLogin(context, loginLauncher)
                  }
                } catch (e: Exception) {
                  Log.e("FetchApi2", "error", e)
                  withContext(Dispatchers.Main) {
                    events.add("api2 fetch failed: ${e.message ?: e.toString()}")
                  }
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Fetch api2 (Water List)")
            }

            // Print api2 raw log button
            Button(onClick = {
              scope.launch {
                events.add("Printing api2 raw query log...")
                try {
                  val cm = CacheManager(context)
                  val content = withContext(Dispatchers.IO) { cm.readFromCache("api2_query_log.json") }
                  if (content.isNullOrBlank()) {
                    events.add("No api2 query log found")
                  } else {
                    withContext(Dispatchers.IO) {
                      content.chunked(4000).forEachIndexed { i, chunk ->
                        Log.d("Api2RawLog", "Chunk ${i + 1}: $chunk")
                      }
                    }
                    events.add("Printed api2_query_log.json to logcat (${content.length} bytes)")
                    events.add("Preview: ${content.take(800)}")
                  }
                } catch (e: Exception) {
                  Log.e("PrintApi2Raw", "Failed to print api2 raw log", e)
                  events.add("Print api2 raw failed: ${e.message ?: e.toString()}")
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Print API2 Raw")
            }

            // Test cache status button
            Button(onClick = {
              events.add("Testing cache status...")
              scope.launch(Dispatchers.IO) {
                val cacheStatus = weeklyRepository.getCacheStatus()
                withContext(Dispatchers.Main) {
                  events.add("Cache exists: ${cacheStatus.exists}")
                  events.add("Cache expired: ${cacheStatus.isExpired}")
                  events.add("Expires date: ${cacheStatus.expiresDate ?: "N/A"}")
                  if (cacheStatus.fileInfo != null) {
                    events.add("Cache file: ${cacheStatus.fileInfo.path}")
                    events.add("File size: ${cacheStatus.fileInfo.size / 1024} KB")
                    events.add("Last modified: ${cacheStatus.fileInfo.getFormattedLastModified()}")
                  } else {
                    events.add("No file information available")
                  }
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Test Cache Status")
            }

            Button(onClick = {
              scope.launch {
                events.add("Printing weekly files...")
                try {
                  val previews = withContext(Dispatchers.IO) { weeklyRepository.getWeeklyFilePreviews() }
                  if (previews.isEmpty()) {
                    events.add("No weekly files found to print")
                    return@launch
                  }
                  withContext(Dispatchers.IO) {
                    for (p in previews) {
                      Log.d("PrintWeekly", "=== ${p.name} ===")
                      p.preview.chunked(4000).forEachIndexed { i, chunk ->
                        Log.d("PrintWeekly", "Chunk ${i + 1}: $chunk")
                      }
                    }
                  }
                  withContext(Dispatchers.Main) {
                    for (p in previews) {
                      events.add("Printed ${p.name} (${p.size} bytes)")
                      events.add("Preview: ${p.preview.take(200)}...")
                    }
                    events.add("All files printed to logs")
                  }
                } catch (e: Exception) {
                  Log.e("PrintWeekly", "Print failed", e)
                  events.add("Print failed: ${e.message ?: e.toString()}")
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Print weekly.json")
            }

            Button(onClick = {
              scope.launch {
                events.add("Printing cleaned weekly to logs...")
                try {
                  val cm = CacheManager(context)
                  val content = withContext(Dispatchers.IO) {
                    cm.readFromCache(WeeklyCleaner.CLEANED_WEEKLY_FILE)
                  }
                  if (content.isNullOrBlank()) {
                    events.add("No cleaned weekly found in cache")
                  } else {
                    withContext(Dispatchers.IO) {
                      content.chunked(4000).forEachIndexed { i, chunk ->
                        Log.d("WeeklyCleaned", "Chunk ${i + 1}: $chunk")
                      }
                    }
                    events.add("Printed cleaned weekly to logs (${content.length} bytes)")
                    events.add("Preview: ${content.take(800)}")
                  }
                } catch (e: Exception) {
                  Log.e("PrintWeeklyCleaned", "Print cleaned weekly failed", e)
                  events.add("Print cleaned weekly failed: ${e.message ?: e.toString()}")
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Print cleaned weekly")
            }

            Button(onClick = {
              scope.launch {
                events.add("Generating cleaned weekly...")
                try {
                  val ok = withContext(Dispatchers.IO) { weeklyCleaner.generateCleanedWeekly() }
                  withContext(Dispatchers.Main) {
                    if (ok) {
                      events.add("Weekly cleaned generation succeeded")
                      val cleanedPath = weeklyCleaner.getCleanedFilePath()
                      if (cleanedPath != null) {
                        events.add("Saved cleaned weekly: $cleanedPath")
                        val cm = CacheManager(context)
                        val content = withContext(Dispatchers.IO) { cm.readFromCache(WeeklyCleaner.CLEANED_WEEKLY_FILE) }
                        if (!content.isNullOrBlank()) {
                          withContext(Dispatchers.IO) {
                            content.chunked(4000).forEachIndexed { i, chunk ->
                              Log.d("WeeklyCleaned", "Chunk ${i + 1}: $chunk")
                            }
                          }
                          events.add("Preview: ${content.take(800)}")
                        } else {
                          events.add("Cleaned file is empty or unreadable")
                        }
                      } else {
                        events.add("Could not determine cleaned file path")
                      }
                    } else {
                      events.add("Weekly cleaned generation failed")
                    }
                  }
                } catch (e: IllegalStateException) {
                  Log.w("WeeklyCleanerBtn", "Raw weekly data missing", e)
                  withContext(Dispatchers.Main) {
                    events.add("Raw weekly data missing: ${e.message ?: "missing weekly_raw.json"}")
                  }
                } catch (e: Exception) {
                  Log.e("WeeklyCleanerBtn", "Error generating cleaned weekly", e)
                  withContext(Dispatchers.Main) {
                    events.add("Exception during cleaning: ${e.message ?: e.toString()}")
                  }
                }
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Generate Cleaned Weekly")
            }
          }
        } else {
          // Integration page
          Row {
            Button(onClick = { currentPage = 0 }) { Text(text = "Home") }
            Spacer(modifier = Modifier.padding(8.dp))
            Button(onClick = { currentPage = 1 }) { Text(text = "Tools") }
          }

          Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(text = "Integration", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.padding(6.dp))
            Text(text = "This page contains integration with external systems/services.")

            Button(onClick = {
              val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
              val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
              if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                startIntegrationFlow(context, weeklyRepository, weeklyCleaner, events, scope)
              } else {
                integrationPending = true
                events.add("[Integration] Missing calendar permission, requesting...")
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
              }
            }, modifier = Modifier.padding(top = 12.dp)) {
              Text(text = "Write to Calendar")
            }

            Button(onClick = {
              events.add("[Integration] Status: OK (mock)")
            }, modifier = Modifier.padding(top = 8.dp)) {
              Text(text = "Show Integration Status")
            }

            // Auto api2 query toggle
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
              Text(text = "Enable automatic API2 queries (every 15 min)", modifier = Modifier.weight(1f))
              Switch(checked = api2AutoEnabled, onCheckedChange = { checked ->
                if (checked) enableApi2Periodic() else disableApi2Periodic()
              })
            }

            // Foreground polling toggle
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
              Text(text = "Enable foreground polling (realtime)", modifier = Modifier.weight(1f))
              Switch(checked = api2ForegroundEnabled, onCheckedChange = { checked ->
                if (checked) {
                  Toast.makeText(context, "Foreground polling increases battery usage.", Toast.LENGTH_LONG).show()
                  startForegroundPolling(5)
                } else {
                  stopForegroundPolling()
                }
              })
            }

            // Description of the differences
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
              Text(text = "Difference:", style = MaterialTheme.typography.subtitle2)
              Text(text = "1) Foreground polling: High real-time, persistent notification, higher battery usage.")
              Text(text = "2) Periodic (WorkManager): Battery-friendly, system Doze constraints (min ~15 min).")
            }
          }
        }
      }

      // Global log area
      LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp)) {
        items(events) { e ->
          Card(modifier = Modifier.padding(4.dp)) {
            Text(text = e, modifier = Modifier.padding(8.dp))
          }
        }
      }
    }
  }
}

// Integration flow to ensure cleaned weekly exists and then write to calendar
fun startIntegrationFlow(
  ctx: Context,
  weeklyRepo: WeeklyRepository,
  cleaner: WeeklyCleaner,
  eventsList: MutableList<String>,
  coroutineScope: CoroutineScope
) {
  coroutineScope.launch(Dispatchers.IO) {
    fun post(msg: String) {
      android.os.Handler(android.os.Looper.getMainLooper()).post { eventsList.add(msg) }
    }

    post("[Integration] Checking cleaned weekly and calendar write flow...")

    val cm = CacheManager(ctx)
    try {
      val cleaned = cm.readFromCache(WeeklyCleaner.CLEANED_WEEKLY_FILE)
      if (!cleaned.isNullOrBlank()) {
        post("[Integration] Found cleaned weekly, writing to calendar")
      } else {
        post("[Integration] No cleaned weekly found, checking weekly.json cache...")
        val raw = cm.readFromCache(CacheManager.WEEKLY_CACHE_FILE)
        if (raw.isNullOrBlank()) {
          post("[Integration] No weekly.json found, fetching from backend...")
          try {
            val res = weeklyRepo.refreshWeeklyData()
            if (res == null) {
              post("[Integration] Backend returned null")
              return@launch
            }
            post("[Integration] Successfully fetched and cached weekly data")
          } catch (e: org.xjtuai.kqchecker.auth.AuthRequiredException) {
            post("[Integration] Login required, please login first")
            return@launch
          } catch (e: Exception) {
            post("[Integration] Failed to fetch weekly: ${e.message}")
            return@launch
          }
        } else {
          post("[Integration] Found weekly.json cache, generating cleaned weekly")
        }

        try {
          val ok = cleaner.generateCleanedWeekly()
          if (ok) {
            post("[Integration] Cleaned weekly generated successfully")
          } else {
            post("[Integration] Cleaned weekly generation returned false")
          }
        } catch (e: IllegalStateException) {
          post("[Integration] Generation failed: raw data missing - ${e.message}")
          return@launch
        } catch (e: Exception) {
          post("[Integration] Generation error: ${e.message}")
          return@launch
        }
      }

      // Write to calendar
      post("[Integration] Submitting calendar write task...")
      try {
        val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
        WorkManager.getInstance(ctx).enqueue(request)
        val workId = request.id

        withContext(Dispatchers.Main) {
          WorkManager.getInstance(ctx).getWorkInfoByIdLiveData(workId).observeForever { info ->
            if (info != null) {
              val statusMessage = when (info.state) {
                androidx.work.WorkInfo.State.ENQUEUED -> "[Integration] Calendar write enqueued"
                androidx.work.WorkInfo.State.RUNNING -> "[Integration] Calendar write running"
                androidx.work.WorkInfo.State.SUCCEEDED -> "[Integration] Calendar write succeeded"
                androidx.work.WorkInfo.State.FAILED -> "[Integration] Calendar write failed"
                else -> "[Integration] Calendar write: ${info.state}"
              }
              eventsList.add(statusMessage)
            }
          }
        }
      } catch (e: Exception) {
        post("[Integration] Failed to submit calendar task: ${e.message}")
      }
    } catch (e: Exception) {
      post("[Integration] Unknown error: ${e.message}")
    }
  }
}

fun startSync(context: Context) {
  val request = OneTimeWorkRequestBuilder<TestWriteCalendar>().build()
  WorkManager.getInstance(context).enqueue(request)
}

fun writeCalendar(context: Context): androidx.work.WorkInfo.State {
  Log.d("WriteCalendar", "Creating and executing WriteCalendar work request")
  val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
  WorkManager.getInstance(context).enqueue(request)
  return WorkManager.getInstance(context).getWorkInfoById(request.id).get().state
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  AppContent()
}
