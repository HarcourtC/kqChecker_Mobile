package org.example.kqchecker

import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.widget.Toast
import org.example.kqchecker.auth.WebLoginActivity
import org.example.kqchecker.auth.TokenManager
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import org.example.kqchecker.repo.MockRepository
import androidx.compose.material.Card
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.File

import kotlinx.coroutines.withContext
import org.example.kqchecker.network.NetworkModule
import org.example.kqchecker.network.WeeklyResponse
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import org.example.kqchecker.sync.TestWriteCalendar
import org.example.kqchecker.sync.WriteCalendar
import androidx.work.WorkManager
import org.example.kqchecker.sync.SyncWorker
import org.example.kqchecker.sync.Api2AttendanceQueryWorker
import org.example.kqchecker.repository.RepositoryProvider
import org.example.kqchecker.repository.WeeklyRepository
import org.example.kqchecker.repository.WaterListRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化Repository提供者，为整个应用提供Repository实例
        RepositoryProvider.initialize(this)
        
        setContent {
            AppContent()
        }
    }
    
    // 缓存检查方法已移至Repository模块，保留注释说明
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
    
    // 获取Repository实例
    val weeklyRepository = RepositoryProvider.getWeeklyRepository()
    val waterListRepository = RepositoryProvider.getWaterListRepository()
    val weeklyCleaner = RepositoryProvider.getWeeklyCleaner()
    
    // 组件启动时自动检查缓存是否过期并在必要时触发自动刷新
    LaunchedEffect(key1 = Unit) {
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
                                try {
                                    val updatedCacheStatus = weeklyRepository.getCacheStatus()
                                    if (updatedCacheStatus.exists && updatedCacheStatus.expiresDate != null) {
                                        postEvent("Cache will expire on: ${updatedCacheStatus.expiresDate}")
                                    } else {
                                        postEvent("Cache expiration date unknown")
                                    }
                                } catch (e: Exception) {
                                    Log.e("AutoRefreshWeekly", "Error getting cache status", e)
                                }
                            } else {
                                postEvent("Auto-refresh failed: Repository returned null")
                            }
                        }
                    } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                        withContext(Dispatchers.Main) {
                            postEvent("Authentication required: please login")
                            // launch WebLoginActivity for re-login
                            var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                            var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                            try {
                                context.assets.open("config.json").use { stream ->
                                    val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                    val obj = JSONObject(text)
                                    if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                                    if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                                }
                            } catch (_: Exception) { }
                            val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                                putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                                putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                            }
                            // loginLauncher is declared later in this composable; start activity directly here
                            context.startActivity(loginIntent)
                        }
                    } catch (e: Exception) {
                        Log.e("AutoRefreshWeekly", "auto-refresh failed", e)
                        withContext(Dispatchers.Main) {
                            postEvent("Auto-refresh failed: ${e.message ?: e.toString()}")
                        }
                    }
                }
            } else {
                // 缓存有效时获取过期时间并显示
                try {
                    val f = File(context.filesDir, "weekly.json")
                    if (f.exists()) {
                        val jsonStr = f.readText()
                        val jsonObj = JSONObject(jsonStr)
                        val expires = jsonObj.optString("expires", "Unknown")
                        postEvent("Weekly cache is up-to-date, expires on: $expires")
                        Log.d("AutoRefreshWeekly", "Cache is valid, expires on: $expires")
                    } else {
                        postEvent("Weekly cache is up-to-date")
                    }
                } catch (e: Exception) {
                    postEvent("Weekly cache is up-to-date")
                    Log.e("AutoRefreshWeekly", "Error reading cache expiration time", e)
                }
            }
        } catch (e: Exception) {
                Log.e("AutoRefreshWeekly", "Error checking cache status", e)
                postEvent("Auto-refresh check failed: ${e.message ?: e.toString()}")
            }
    }

    // 页面切换: 0 = 首页, 1 = 工具页(原有按钮+日志)
    var currentPage by remember { mutableStateOf(0) }

    // --- 自动查询开关相关 (SharedPreferences 保存) ---
    val prefs = context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE)
    var api2AutoEnabled by remember { mutableStateOf(prefs.getBoolean("api2_auto_enabled", false)) }

    fun enableApi2Periodic() {
        try {
            val workManager = WorkManager.getInstance(context)
            val periodic = PeriodicWorkRequestBuilder<Api2AttendanceQueryWorker>(15, TimeUnit.MINUTES)
                .build()
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
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("api2_att_query_periodic")
            prefs.edit().putBoolean("api2_auto_enabled", false).apply()
            postEvent("Automatic api2 queries disabled")
        } catch (e: Exception) {
            Log.e("Api2Auto", "Failed to disable periodic work", e)
            postEvent("Failed to disable automatic api2 queries: ${e.message}")
        }
    }

    // 如果偏好里已开启，则确保周期任务在运行
    LaunchedEffect(key1 = Unit) {
        if (api2AutoEnabled) {
            enableApi2Periodic()
        }
    }

    

    var integrationPending by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            // If this permission grant was requested for an integration flow, continue it
            if (integrationPending) {
                integrationPending = false
                // launch the integration flow after permission granted
                scope.launch { startIntegrationFlow(context, weeklyRepository, weeklyCleaner, events, scope) }
            } else {
                events.add("开始从后端获取数据并写入日历...")
                scope.launch {
                    try {
                        val request = androidx.work.OneTimeWorkRequestBuilder<org.example.kqchecker.sync.WriteCalendar>().build()

                        // 在IO线程中执行工作请求的提交
                        val workId = withContext(Dispatchers.IO) {
                            androidx.work.WorkManager.getInstance(context).enqueue(request)
                            request.id
                        }

                        // 在主线程上监听工作状态变化
                        withContext(Dispatchers.Main) {
                            androidx.work.WorkManager.getInstance(context)
                                .getWorkInfoByIdLiveData(workId)
                                .observeForever { workInfo ->
                                    if (workInfo != null) {
                                        val statusMessage = when (workInfo.state) {
                                            androidx.work.WorkInfo.State.ENQUEUED -> "工作已入队，等待执行..."
                                            androidx.work.WorkInfo.State.RUNNING -> "工作正在执行中..."
                                            androidx.work.WorkInfo.State.SUCCEEDED -> "✅ 日历写入成功完成！"
                                            androidx.work.WorkInfo.State.FAILED -> "❌ 日历写入失败，请查看日志获取详细信息"
                                            androidx.work.WorkInfo.State.CANCELLED -> "日历写入已取消"
                                            else -> "工作状态: ${workInfo.state}"
                                        }

                                        Log.d("WriteCalendarObserver", statusMessage)

                                        if (!events.contains(statusMessage)) events.add(statusMessage)

                                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                            events.add("📅 日历数据已成功更新，请在系统日历中查看结果")
                                            events.add("📱 提示：可以通过'Print weekly.json'按钮查看原始数据")
                                        } else if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                                            events.add("🔍 建议：检查日志获取详细错误信息")
                                            events.add("💡 提示：确保有有效的weekly数据缓存")
                                        }
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        Log.e("WriteCalendarButton", "执行writeCalendar时发生异常", e)
                        withContext(Dispatchers.Main) {
                            events.add("❌ 执行日历写入时出错: ${e.message ?: e.toString()}")
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
        if (token != null) {
            Toast.makeText(context, "Login success", Toast.LENGTH_SHORT).show()
            events.add("Token: ${token.take(40)}...")
        } else {
            // fallback: read from TokenManager
            val tm = TokenManager(context)
            val saved = tm.getAccessToken()
            if (saved != null) {
                Toast.makeText(context, "Login success (saved)", Toast.LENGTH_SHORT).show()
                events.add("Token: ${saved.take(40)}...")
            } else {
                Toast.makeText(context, "Login canceled or failed", Toast.LENGTH_SHORT).show()
                events.add("Login canceled or failed")
            }
        }
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 页面内容区域：使用 weight(1f) 以保证下方的全局日志区始终可见
            Column(modifier = Modifier.weight(1f)) {
                if (currentPage == 0) {
                // 简洁首页
                Text(text = "kqChecker", style = MaterialTheme.typography.h5)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))
                Text(text = "欢迎使用 kqChecker。此页为简洁首页。点击下面进入工具页或集成页。")
                Button(onClick = { currentPage = 1 }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "打开工具页")
                }
                Button(onClick = { currentPage = 2 }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "打开集成页")
                }
                Button(onClick = {
                    // 快速触发一次缓存检查作为示例操作
                    scope.launch(Dispatchers.IO) {
                        try {
                            val cacheStatus = weeklyRepository.getCacheStatus()
                            withContext(Dispatchers.Main) {
                                events.add("[首页] Cache exists: ${cacheStatus.exists}, expired: ${cacheStatus.isExpired}")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { events.add("[首页] 无法读取缓存状态: ${e.message ?: e.toString()}") }
                        }
                    }
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "检查缓存状态")
                }
            } else if (currentPage == 1) {
                // 工具页：在顶部添加返回按钮，然后保留原有按钮区和日志区
                androidx.compose.foundation.layout.Row {
                    Button(onClick = { currentPage = 0 }) { Text(text = "返回首页") }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = { currentPage = 2 }) { Text(text = "打开集成页") }
                }
                // 按钮区域：可滚动，防止按钮溢出屏幕
                val buttonsScroll = rememberScrollState()
                Column(modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .verticalScroll(buttonsScroll)) {
                Text(text = "kqChecker - Android Skeleton")

                Button(onClick = {
                    var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                    var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                    try {
                        context.assets.open("config.json").use { stream ->
                            val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                            val obj = JSONObject(text)
                            if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                            if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                        }
                    } catch (e: Exception) {
                            Log.i("MainActivity", "No config.json or parse error, using defaults: ${e.message ?: e.toString()}")
                    }

                    val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                        putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                        putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                    }
                    loginLauncher.launch(loginIntent)
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "登录")
                }

                Button(onClick = {
                    events.add("Triggering manual sync...")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = weeklyRepository.refreshWeeklyData()
                            withContext(Dispatchers.Main) {
                                if (result != null) {
                                    events.add("Sync completed successfully")
                                    // 更新缓存状态显示
                                    val cacheStatus = weeklyRepository.getCacheStatus()
                                    events.add("Cache status: " + when {
                                        !cacheStatus.exists -> "No Cache"
                                        cacheStatus.isExpired -> "Cache Expired"
                                        else -> "Cache Valid"
                                    })
                                    val expiresMsg = cacheStatus.expiresDate ?: "unknown"
                                    events.add("Cache expires on: $expiresMsg")
                                } else {
                                    events.add("Sync failed - null result")
                                }
                            }
                        } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                            withContext(Dispatchers.Main) {
                                events.add("Authentication required: opening login...")
                                // launch login
                                var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                                var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                                try {
                                    context.assets.open("config.json").use { stream ->
                                        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                        val obj = JSONObject(text)
                                        if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                                        if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                                    }
                                } catch (_: Exception) { }
                                val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                                    putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                                    putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                                }
                                loginLauncher.launch(loginIntent)
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
                    Text(text = "Test Write Calendar") // 修改按钮文本
                }

                // 添加新的按钮，用于从后端获取数据并写入日历
                Button(onClick = {
                    Log.d("WriteCalendarButton", "Write Calendar按钮被点击")
                    val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                    val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                    if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                        Log.d("WriteCalendarButton", "已有日历权限，开始执行writeCalendar")
                        events.add("正在从后端获取数据并写入日历...")
                        scope.launch {
                            try {
                                val request = androidx.work.OneTimeWorkRequestBuilder<org.example.kqchecker.sync.WriteCalendar>().build()

                                // 在IO线程中执行工作请求的提交
                                val workId = withContext(Dispatchers.IO) {
                                    androidx.work.WorkManager.getInstance(context).enqueue(request)
                                    request.id
                                }

                                // 在主线程上监听工作状态变化
                                withContext(Dispatchers.Main) {
                                    androidx.work.WorkManager.getInstance(context)
                                        .getWorkInfoByIdLiveData(workId)
                                        .observeForever { workInfo ->
                                            if (workInfo != null) {
                                                val statusMessage = when (workInfo.state) {
                                                    androidx.work.WorkInfo.State.ENQUEUED -> "工作已入队，等待执行..."
                                                    androidx.work.WorkInfo.State.RUNNING -> "工作正在执行中..."
                                                    androidx.work.WorkInfo.State.SUCCEEDED -> "✅ 日历写入成功完成！"
                                                    androidx.work.WorkInfo.State.FAILED -> "❌ 日历写入失败，请查看日志获取详细信息"
                                                    androidx.work.WorkInfo.State.CANCELLED -> "日历写入已取消"
                                                    else -> "工作状态: ${workInfo.state}"
                                                }

                                                Log.d("WriteCalendarObserver", statusMessage)

                                                // 避免重复添加相同的状态信息
                                                if (!events.contains(statusMessage)) {
                                                    events.add(statusMessage)
                                                }

                                                // 如果工作已完成，添加更详细的信息
                                                if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                                    events.add("📅 日历数据已成功更新，请在系统日历中查看结果")
                                                    events.add("📱 提示：可以通过'Print weekly.json'按钮查看原始数据")
                                                } else if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                                                    events.add("🔍 建议：检查日志获取详细错误信息")
                                                    events.add("💡 提示：确保有有效的weekly数据缓存")
                                                }
                                            }
                                        }
                                }
                            } catch (e: Exception) {
                                Log.e("WriteCalendarButton", "执行writeCalendar时发生异常", e)
                                withContext(Dispatchers.Main) {
                                    events.add("❌ 执行日历写入时出错: ${e.message ?: e.toString()}")
                                }
                            }
                        }
                    } else {
                        Log.d("WriteCalendarButton", "缺少日历权限，请求权限")
                        events.add("请求日历权限...")
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
                                    // 处理API2返回的数据
                                    events.add("API2 data fetched and saved")
                                } else {
                                    events.add("Experimental sync failed - null result")
                                }
                            }
                        } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                            withContext(Dispatchers.Main) {
                                events.add("Authentication required: opening login...")
                                var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                                var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                                try {
                                    context.assets.open("config.json").use { stream ->
                                        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                        val obj = JSONObject(text)
                                        if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                                        if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                                    }
                                } catch (_: Exception) { }
                                val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                                    putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                                    putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                                }
                                loginLauncher.launch(loginIntent)
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

                // 手动触发 Api2AttendanceQueryWorker（一次性）
                Button(onClick = {
                    events.add("Manual api2 query triggered")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val request = OneTimeWorkRequestBuilder<Api2AttendanceQueryWorker>().build()
                            WorkManager.getInstance(context).enqueue(request)
                            withContext(Dispatchers.Main) { postEvent("Manual api2 query enqueued") }
                        } catch (e: Exception) {
                            Log.e("ManualApi2", "Failed to enqueue manual api2 query", e)
                            withContext(Dispatchers.Main) { postEvent("Failed to enqueue manual api2 query: ${e.message}") }
                        }
                    }
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Manual Api2 Query")
                }

                // 自动查询开关
                androidx.compose.foundation.layout.Row(modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Enable automatic Api2 queries:")
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Switch(checked = api2AutoEnabled, onCheckedChange = { checked ->
                        api2AutoEnabled = checked
                        if (checked) enableApi2Periodic() else disableApi2Periodic()
                    })
                }

                Button(onClick = {
                    // Debug: delegate authenticated GET to DebugRepository
                    scope.launch {
                        events.add("Testing authenticated request (via DebugRepository)...")
                            try {
                                val debugRepo = RepositoryProvider.getDebugRepository()
                                val result = debugRepo.performDebugRequest()

                                if (result.code >= 0) {
                                    events.add("HTTP ${result.code} — headers logged (see Logcat)")
                                    events.add(result.sentHeaders.take(300))
                                    val preview = result.bodyPreview ?: ""
                                    if (preview.isNotBlank()) {
                                        events.add(preview.take(200))
                                    }
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
                    // Fetch weekly from API via WeeklyRepository (migrated)
                    scope.launch {
                        events.add("Fetching weekly from API (via WeeklyRepository)...")
                        try {
                            // optional: compute and log DNS for debug like previous implementation
                            var baseUrl = "https://api.example.com/"
                            try {
                                context.assets.open("config.json").use { stream ->
                                    val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                    val obj = JSONObject(text)
                                    if (obj.has("base_url")) baseUrl = obj.getString("base_url")
                                }
                            } catch (_: Exception) {
                                // ignore and use default
                            }

                            val path = "attendance-student/rankClass/getWeekSchedule2"
                            val fullUrl = try {
                                val baseUri = java.net.URI(baseUrl)
                                val schemeStr = baseUri.scheme ?: "http"
                                val hostFromBase = baseUri.host ?: baseUrl.replace(Regex("https?://"), "").split(":")[0]
                                val portPart = if (baseUri.port != -1) ":${baseUri.port}" else ""
                                "$schemeStr://$hostFromBase$portPart/$path"
                            } catch (e: Exception) {
                                baseUrl.trimEnd('/') + "/" + path
                            }
                            try {
                                val uri = java.net.URI(fullUrl)
                                val hostStr: String = uri.host ?: fullUrl.replace(Regex("https?://"), "").split(":")[0]
                                val addrs = java.net.InetAddress.getAllByName(hostStr)
                                val ipsStr: String = addrs.joinToString(",") { it.hostAddress }
                                val dnsMsg = "DNS: " + hostStr.toString() + " -> " + ipsStr.toString()
                                events.add(dnsMsg)
                            } catch (e: Exception) {
                                events.add("Host resolve failed: ${e.message ?: e.toString()}")
                            }

                            // Use WeeklyRepository to fetch raw API response for debug, falling back to refresh if raw unavailable
                            val weeklyRepo = WeeklyRepository(context)
                            try {
                                val rawResp = withContext(Dispatchers.IO) { weeklyRepo.fetchWeeklyRawFromApi() }
                                if (!rawResp.isNullOrBlank()) {
                                    events.add("Weekly API raw response fetched (${rawResp.length} bytes)")
                                    // Log full raw response in IO to avoid truncation
                                    withContext(Dispatchers.IO) {
                                        val tag = "FetchWeeklyRaw"
                                        val chunks = rawResp.chunked(4000)
                                        for ((index, chunk) in chunks.withIndex()) {
                                            Log.d(tag, "Chunk ${index + 1}/${chunks.size}: $chunk")
                                        }
                                    }
                                    events.add(rawResp.take(800))
                                } else {
                                    // fallback: perform existing refresh and report cached files
                                    val result = withContext(Dispatchers.IO) { weeklyRepo.refreshWeeklyData() }
                                    if (result != null) {
                                        events.add("Weekly fetch: success (via refresh)")
                                    } else {
                                        events.add("Weekly fetch: failed or returned invalid data (via refresh)")
                                    }

                                    val cm = org.example.kqchecker.repository.CacheManager(context)
                                    val weeklyPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_CACHE_FILE).absolutePath
                                    val rawPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_CACHE_FILE).absolutePath
                                    val metaPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_META_FILE).absolutePath
                                    events.add("Saved weekly.json: $weeklyPath")
                                    events.add("Saved weekly_raw.json: $rawPath")
                                    events.add("Saved weekly_raw_meta.json: $metaPath")

                                    val raw = withContext(Dispatchers.IO) { cm.readFromCache(org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_CACHE_FILE) }
                                    if (!raw.isNullOrBlank()) events.add(raw.take(800))
                                }
                            } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                                withContext(Dispatchers.Main) {
                                    events.add("Authentication required during raw fetch: ${e.message}")
                                    var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                                    var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                                    try {
                                        context.assets.open("config.json").use { stream ->
                                            val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                            val obj = JSONObject(text)
                                            if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                                            if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                                        }
                                    } catch (_: Exception) { }
                                    val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                                        putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                                        putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                                    }
                                    loginLauncher.launch(loginIntent)
                                }
                            }

                            } catch (e: Exception) {
                                Log.e("FetchWeekly", "migrated fetch failed", e)
                                events.add("Fetch failed: ${e.message ?: e.toString()}")
                            }
                    }
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Fetch Weekly (API)")
                }

                Button(onClick = {
                        // Migrate: use WaterListRepository to fetch and cache water list (API2)
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

                                    // Report saved cache file path and a preview of the cached response
                                    val cm = org.example.kqchecker.repository.CacheManager(context)
                                    val cachePath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WATER_LIST_CACHE_FILE).absolutePath
                                    events.add("Saved water list cache: $cachePath")

                                    // Read preview from cache on IO dispatcher
                                    val raw = withContext(Dispatchers.IO) { cm.readFromCache(org.example.kqchecker.repository.CacheManager.WATER_LIST_CACHE_FILE) }
                                    if (!raw.isNullOrBlank()) events.add(raw.take(800))
                                }
                            } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                                withContext(Dispatchers.Main) {
                                    events.add("Authentication required: opening login...")
                                    var loginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
                                    var redirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"
                                    try {
                                        context.assets.open("config.json").use { stream ->
                                            val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                            val obj = JSONObject(text)
                                            if (obj.has("auth_login_url")) loginUrl = obj.getString("auth_login_url")
                                            if (obj.has("auth_redirect_prefix")) redirectPrefix = obj.getString("auth_redirect_prefix")
                                        }
                                    } catch (_: Exception) { }
                                    val loginIntent = Intent(context, WebLoginActivity::class.java).apply {
                                        putExtra(WebLoginActivity.EXTRA_LOGIN_URL, loginUrl)
                                        putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, redirectPrefix)
                                    }
                                    loginLauncher.launch(loginIntent)
                                }
                            } catch (e: Exception) {
                                Log.e("FetchApi2", "error", e)
                                withContext(Dispatchers.Main) {
                                    events.add("api2 water list request failed: ${e.message ?: e.toString()}")
                                }
                            }

                        }
                    }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(text = "Fetch api2 (Water List)")
                    }

                        // 新增按钮：打印 api2 原始响应（读取 api2_query_log.json 并打印）
                        Button(onClick = {
                            scope.launch {
                                events.add("Printing api2 raw query log...")
                                try {
                                    val cm = org.example.kqchecker.repository.CacheManager(context)
                                    val fname = "api2_query_log.json"
                                    val content = withContext(Dispatchers.IO) { cm.readFromCache(fname) }

                                    if (content.isNullOrBlank()) {
                                        withContext(Dispatchers.Main) { events.add("No api2 query log found: $fname") }
                                    } else {
                                        // Log full content in chunks to avoid truncation
                                        withContext(Dispatchers.IO) {
                                            val tag = "Api2RawLog"
                                            val chunks = content.chunked(4000)
                                            for ((index, chunk) in chunks.withIndex()) {
                                                Log.d(tag, "Chunk ${index + 1}/${chunks.size}: $chunk")
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            events.add("✅ Printed $fname to logcat (${content.length} bytes)")
                                            val preview = if (content.length > 800) content.substring(0, 800) + "... (truncated)" else content
                                            events.add("Preview: $preview")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("PrintApi2Raw", "Failed to print api2 raw log", e)
                                    withContext(Dispatchers.Main) { events.add("Print api2 raw failed: ${e.message ?: e.toString()}") }
                                }
                            }
                        }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(text = "打印 API2 原始响应")
                        }
                    
                    // 测试缓存状态按钮
                    Button(onClick = { 
                        events.add("Testing cache status...")
                        scope.launch(Dispatchers.IO) {
                            val cacheStatus = weeklyRepository.getCacheStatus()
                            withContext(Dispatchers.Main) {
                                events.add("Cache exists: ${cacheStatus.exists}")
                                events.add("Cache expired: ${cacheStatus.isExpired}")
                                val expiresStr = cacheStatus.expiresDate ?: "N/A"
                                events.add("Expires date: $expiresStr")
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
                        Text(text = "测试缓存状态")
                    }

                Button(onClick = {
                    // Delegate printing of weekly cache files to WeeklyRepository
                    scope.launch {
                        events.add("Printing weekly files...")
                        try {
                            val previews = withContext(Dispatchers.IO) { weeklyRepository.getWeeklyFilePreviews() }

                            if (previews.isEmpty()) {
                                withContext(Dispatchers.Main) { events.add("No weekly files found to print") }
                                return@launch
                            }

                            // Log full content in IO to avoid blocking UI
                            withContext(Dispatchers.IO) {
                                for (p in previews) {
                                    Log.d("PrintWeekly", "📄 === Content of ${p.name} ===")
                                    if (p.preview.length > 4000) {
                                        val chunks = p.preview.chunked(4000)
                                        for ((index, chunk) in chunks.withIndex()) {
                                            Log.d("PrintWeekly", "📄 块 ${index + 1}/${chunks.size}: $chunk")
                                        }
                                    } else {
                                        Log.d("PrintWeekly", p.preview)
                                    }
                                    Log.d("PrintWeekly", "📄 === End of ${p.name} ===")
                                }
                            }

                            // Update UI with summary and previews
                            withContext(Dispatchers.Main) {
                                for (p in previews) {
                                    events.add("✅ Printed ${p.name} (${p.size} bytes) to logs")
                                    events.add("Path: ${p.path}")
                                    val display = if (p.preview.length > 200) p.preview.substring(0, 200) + "... (truncated, full content in logs)" else p.preview
                                    events.add("Preview: $display")
                                }
                                events.add("All files printed to logs")
                            }

                        } catch (e: Exception) {
                            Log.e("PrintWeekly", "Print failed: ${e.message ?: e.toString()}", e)
                            withContext(Dispatchers.Main) { events.add("Print failed: ${e.message ?: e.toString()}") }
                        }
                    }
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Print weekly.json")
                }

                Button(onClick = {
                    // Print cleaned weekly (weekly_cleaned.json) to Logcat and UI
                    scope.launch {
                        events.add("Printing cleaned weekly (weekly_cleaned.json) to logs...")
                        try {
                            val cm = org.example.kqchecker.repository.CacheManager(context)
                            val content = withContext(Dispatchers.IO) {
                                cm.readFromCache(org.example.kqchecker.repository.WeeklyCleaner.CLEANED_WEEKLY_FILE)
                            }

                            if (content.isNullOrBlank()) {
                                withContext(Dispatchers.Main) { events.add("No cleaned weekly found in cache") }
                            } else {
                                // Log full content in IO to avoid blocking UI and to prevent truncation in Logcat
                                withContext(Dispatchers.IO) {
                                    val tag = "WeeklyCleaned"
                                    val chunks = content.chunked(4000)
                                    for ((index, chunk) in chunks.withIndex()) {
                                        Log.d(tag, "Chunk ${index + 1}/${chunks.size}: $chunk")
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    events.add("✅ Printed cleaned weekly to logs (${content.length} bytes)")
                                    val preview = if (content.length > 800) content.substring(0, 800) + "... (truncated)" else content
                                    events.add("Preview: $preview")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PrintWeeklyCleaned", "Print cleaned weekly failed", e)
                            withContext(Dispatchers.Main) { events.add("Print cleaned weekly failed: ${e.message ?: e.toString()}") }
                        }
                    }
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Print cleaned weekly")
                }

                Button(onClick = {
                    // Trigger cleaning and show weekly_cleaned.json path/preview
                    scope.launch {
                        events.add("Generating cleaned weekly (weekly_cleaned.json)...")
                        try {
                            val ok = withContext(Dispatchers.IO) { weeklyCleaner.generateCleanedWeekly() }
                            withContext(Dispatchers.Main) {
                                if (ok) {
                                    events.add("Weekly cleaned generation succeeded")
                                    val cleanedPath = weeklyCleaner.getCleanedFilePath()
                                    if (cleanedPath != null) {
                                        events.add("Saved cleaned weekly: $cleanedPath")
                                        // read preview
                                        try {
                                            val cm = org.example.kqchecker.repository.CacheManager(context)
                                            val content = withContext(Dispatchers.IO) { cm.readFromCache(org.example.kqchecker.repository.WeeklyCleaner.CLEANED_WEEKLY_FILE) }
                                            if (!content.isNullOrBlank()) {
                                                // Log full content to Logcat in chunks to avoid truncation
                                                withContext(Dispatchers.IO) {
                                                    val tag = "WeeklyCleaned"
                                                    val chunks = content.chunked(4000)
                                                    for ((index, chunk) in chunks.withIndex()) {
                                                        Log.d(tag, "Chunk ${index + 1}/${chunks.size}: $chunk")
                                                    }
                                                }
                                                val preview = if (content.length > 800) content.substring(0, 800) + "... (truncated)" else content
                                                events.add("Preview: $preview")
                                            } else {
                                                events.add("Cleaned file is empty or unreadable")
                                            }
                                        } catch (e: Exception) {
                                            events.add("Failed to read cleaned file: ${e.message ?: e.toString()}")
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
                                events.add("⚠ 原始周课表数据缺失：请尝试刷新数据或检查网络/登录状态。详细信息: ${e.message ?: "缺少weekly_raw.json"}")
                            }
                        } catch (e: Exception) {
                            Log.e("WeeklyCleanerBtn", "Error generating cleaned weekly", e)
                            withContext(Dispatchers.Main) { events.add("Exception during cleaning: ${e.message ?: e.toString()}") }
                        }
                    }
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Generate Cleaned Weekly")
                }

                }

                // 日志区域已移至页面底部的全局显示区，供所有页面复用
                // (避免在工具页内重复渲染)
            } else {
                // 集成页
                androidx.compose.foundation.layout.Row {
                    Button(onClick = { currentPage = 0 }) { Text(text = "返回首页") }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = { currentPage = 1 }) { Text(text = "返回工具页") }
                }

                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "集成", style = MaterialTheme.typography.h6)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))
                    Text(text = "此页面用于放置与其他系统/服务集成的入口与状态。下面为占位操作：")

                    Button(onClick = {
                        // 实现：集成指令一 — 确保有 cleaned weekly 并写入日历
                        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                        if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                            startIntegrationFlow(context, weeklyRepository, weeklyCleaner, events, scope)
                        } else {
                            // 请求权限后继续集成流程
                            integrationPending = true
                            events.add("[集成] 缺少日历权限，正在请求权限...")
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }
                    }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(text = "运行集成指令一-写入日历")
                    }

                    Button(onClick = {
                        // 占位：显示集成状态
                        events.add("[集成] 当前集成状态：正常（模拟）")
                    }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = "显示集成状态")
                    }
                }

                // 全局日志区域已移动到文件底部统一渲染位置
            }
        }
        
        // 全局日志区域：在页面内容下方统一显示，适用于首页/工具页/集成页
        // 使用固定高度确保不会被上方内容推离屏幕
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

// Top-level helper: integration flow to ensure cleaned weekly exists and then write calendar
fun startIntegrationFlow(
    ctx: Context,
    weeklyRepo: WeeklyRepository,
    cleaner: org.example.kqchecker.repository.WeeklyCleaner,
    eventsList: MutableList<String>,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch(Dispatchers.IO) {
        fun post(msg: String) {
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            h.post { eventsList.add(msg) }
        }

        post("[集成] 开始检查 cleaned weekly 与写日历流程...")

        val cm = org.example.kqchecker.repository.CacheManager(ctx)
        try {
            val cleaned = cm.readFromCache(org.example.kqchecker.repository.WeeklyCleaner.CLEANED_WEEKLY_FILE)
            if (!cleaned.isNullOrBlank()) {
                post("[集成] 已找到 cleaned weekly，直接写入日历")
            } else {
                post("[集成] 未找到 cleaned weekly，检查 weekly.json 缓存...")
                val raw = cm.readFromCache(org.example.kqchecker.repository.CacheManager.WEEKLY_CACHE_FILE)
                if (raw.isNullOrBlank()) {
                    post("[集成] 未找到 weekly.json，尝试从后端请求...")
                    try {
                        val res = weeklyRepo.refreshWeeklyData()
                        if (res == null) {
                            post("[集成] 后端返回空，无法获取 weekly 数据")
                            return@launch
                        } else {
                            post("[集成] 已成功从后端获取 weekly 数据并缓存")
                        }
                    } catch (e: org.example.kqchecker.auth.AuthRequiredException) {
                        post("[集成] 需要登录：请先登录后重试")
                        return@launch
                    } catch (e: Exception) {
                        post("[集成] 请求 weekly 数据失败: ${e.message ?: e.toString()}")
                        return@launch
                    }
                } else {
                    post("[集成] 找到 weekly.json 缓存，准备生成 cleaned weekly")
                }

                // 尝试生成 cleaned weekly
                try {
                    val ok = cleaner.generateCleanedWeekly()
                    if (ok) post("[集成] cleaned weekly 生成成功") else post("[集成] cleaned weekly 生成返回 false")
                } catch (e: IllegalStateException) {
                    post("[集成] cleaned 生成失败：原始数据缺失 - ${e.message}")
                    return@launch
                } catch (e: Exception) {
                    post("[集成] cleaned 生成异常: ${e.message ?: e.toString()}")
                    return@launch
                }
            }

            // 到这里 ensured cleaned exists; 开始写日历
            post("[集成] 准备写日历（提交 WorkManager 任务）")
            try {
                val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
                val workId = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(ctx).enqueue(request)
                    request.id
                }

                withContext(Dispatchers.Main) {
                    WorkManager.getInstance(ctx).getWorkInfoByIdLiveData(workId).observeForever { info ->
                        if (info != null) {
                            val statusMessage = when (info.state) {
                                androidx.work.WorkInfo.State.ENQUEUED -> "[集成] 写日历已入队"
                                androidx.work.WorkInfo.State.RUNNING -> "[集成] 写日历正在执行"
                                androidx.work.WorkInfo.State.SUCCEEDED -> "[集成] ✅ 写日历成功"
                                androidx.work.WorkInfo.State.FAILED -> "[集成] ❌ 写日历失败"
                                else -> "[集成] 写日历状态: ${info.state}"
                            }
                            eventsList.add(statusMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                post("[集成] 提交写日历任务失败: ${e.message ?: e.toString()}")
            }

        } catch (e: Exception) {
            post("[集成] 未知错误: ${e.message ?: e.toString()}")
        }
    }
}

fun startSync(context: Context) {
    // 修改为使用TestWriteCalendar（从assets读取数据）
    val request = OneTimeWorkRequestBuilder<TestWriteCalendar>().build()
    WorkManager.getInstance(context).enqueue(request)
}

/**
 * 从后端获取weekly数据并写入日历
 * @return WorkInfo的Flow，用于监听工作状态
 */
fun writeCalendar(context: Context): androidx.work.WorkInfo.State {
    Log.d("WriteCalendar", "开始创建并执行WriteCalendar工作请求")
    val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
    WorkManager.getInstance(context).enqueue(request)
    Log.d("WriteCalendar", "WriteCalendar工作请求已提交到WorkManager")
    // 返回请求的ID，用于后续监听
    return WorkManager.getInstance(context).getWorkInfoById(request.id).get().state
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppContent()
}
