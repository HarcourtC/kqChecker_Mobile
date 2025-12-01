package org.example.kqchecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
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
import org.example.kqchecker.sync.TestWriteCalendar
import org.example.kqchecker.sync.WriteCalendar
import androidx.work.WorkManager
import org.example.kqchecker.sync.SyncWorker
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
                    } catch (e: Exception) {
                        Log.e("AutoRefreshWeekly", "auto-refresh failed", e)
                        withContext(Dispatchers.Main) {
                            postEvent("Auto-refresh failed: ${e.message}")
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
            postEvent("Auto-refresh check failed: ${e.message}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
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
                        events.add("❌ 执行日历写入时出错: ${e.message}")
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
                    Log.i("MainActivity", "No config.json or parse error, using defaults: ${e.message}")
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
                                if (cacheStatus.expiresDate != null) {
                                    events.add("Cache expires on: ${cacheStatus.expiresDate}")
                                } else {
                                    events.add("Cache expiration date unknown")
                                }
                            } else {
                                events.add("Sync failed - null result")
                            }
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
                                events.add("❌ 执行日历写入时出错: ${e.message}")
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
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            events.add("Experimental sync exception: ${e.message}")
                        }
                    }
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Run Experimental Sync")
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
                        events.add("Debug request failed: ${e.message}")
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
                            val host = uri.host ?: fullUrl.replace(Regex("https?://"), "").split(":")[0]
                            val addrs = java.net.InetAddress.getAllByName(host)
                            val ips = addrs.joinToString(",") { it.hostAddress }
                            val hostStr = host ?: "(unknown)"
                            val ipsStr = ips ?: "(unknown)"
                            events.add("DNS: $hostStr -> $ipsStr")
                        } catch (e: Exception) {
                            events.add("Host resolve failed: ${e.message}")
                        }

                        // Use WeeklyRepository to fetch and cache the weekly data
                        val weeklyRepo = WeeklyRepository(context)
                        val result = withContext(Dispatchers.IO) { weeklyRepo.refreshWeeklyData() }

                        if (result != null) {
                            events.add("Weekly fetch: success")
                        } else {
                            events.add("Weekly fetch: failed or returned invalid data")
                        }

                        // Report saved cache file paths and a snippet of the raw response
                        val cm = org.example.kqchecker.repository.CacheManager(context)
                        val weeklyPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_CACHE_FILE).absolutePath
                        val rawPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_CACHE_FILE).absolutePath
                        val metaPath = File(context.filesDir, org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_META_FILE).absolutePath
                        events.add("Saved weekly.json: $weeklyPath")
                        events.add("Saved weekly_raw.json: $rawPath")
                        events.add("Saved weekly_raw_meta.json: $metaPath")

                        val raw = withContext(Dispatchers.IO) { cm.readFromCache(org.example.kqchecker.repository.CacheManager.WEEKLY_RAW_CACHE_FILE) }
                        if (!raw.isNullOrBlank()) events.add(raw.take(800))

                    } catch (e: Exception) {
                        Log.e("FetchWeekly", "migrated fetch failed", e)
                        events.add("Fetch failed: ${e.message}")
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
                        } catch (e: Exception) {
                            Log.e("FetchApi2", "error", e)
                            withContext(Dispatchers.Main) {
                                events.add("api2 water list request failed: ${e.message}")
                            }
                        }

                    }
                }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "Fetch api2 (Water List)")
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
                // Print weekly.json content to logs
                scope.launch(Dispatchers.IO) {
                    suspend fun postEvent(msg: String) {
                        withContext(Dispatchers.Main) { events.add(msg) }
                    }

                    try {
                        Log.d("PrintWeekly", "🔄 开始打印weekly文件内容")
                        
                        // 使用Repository获取weekly.json缓存状态和文件信息
                        Log.d("PrintWeekly", "1. 获取缓存状态...")
                        val cacheStatus = weeklyRepository.getCacheStatus()
                        Log.d("PrintWeekly", "   缓存状态: 存在=${cacheStatus.exists}, 过期=${cacheStatus.isExpired}")
                        
                        val weeklyJsonFile = if (cacheStatus.exists && cacheStatus.fileInfo != null) {
                            File(cacheStatus.fileInfo.path)
                        } else {
                            File(context.filesDir, "weekly.json") // 回退到直接路径
                        }
                        
                        // 创建要打印的文件映射
                        val filesToPrint = mutableMapOf<String, File>()
                        filesToPrint["weekly.json"] = weeklyJsonFile
                        filesToPrint["weekly_raw.json"] = File(context.filesDir, "weekly_raw.json")
                        filesToPrint["weekly_raw_meta.json"] = File(context.filesDir, "weekly_raw_meta.json")
                        
                        Log.d("PrintWeekly", "2. 准备处理 ${filesToPrint.size} 个文件")
                        var printedAny = false
                        
                        for ((filename, src) in filesToPrint) {
                            Log.d("PrintWeekly", "3. 处理文件: $filename")
                            if (!src.exists()) {
                                Log.d("PrintWeekly", "❌ 文件不存在: $filename")
                                postEvent("File not found: $filename")
                                continue
                            }
                            
                            try {
                                val fileSize = src.length()
                                Log.d("PrintWeekly", "   文件大小: ${fileSize} bytes")
                                
                                val content = src.readText()
                                Log.d("PrintWeekly", "   内容长度: ${content.length} 字符")
                                
                                // 打印文件内容到日志
                                Log.d("PrintWeekly", "📄 === Content of $filename ===")
                                // 对于大文件，分段打印以避免日志截断
                                if (content.length > 4000) {
                                    val chunks = content.chunked(4000)
                                    for ((index, chunk) in chunks.withIndex()) {
                                        Log.d("PrintWeekly", "📄 块 ${index + 1}/${chunks.size}: $chunk")
                                    }
                                } else {
                                    Log.d("PrintWeekly", "📄 $content")
                                }
                                Log.d("PrintWeekly", "📄 === End of $filename ===")
                                
                                // 为了避免日志过长，只显示前200个字符在UI上
                                val displayContent = if (content.length > 200) {
                                    content.substring(0, 200) + "... (truncated, full content in logs)"
                                } else {
                                    content
                                }
                                
                                postEvent("✅ Printed $filename ($fileSize bytes) to logs")
                                postEvent("Preview: $displayContent")
                                printedAny = true
                                Log.d("PrintWeekly", "✅ $filename 打印完成")
                            } catch (fileError: Exception) {
                                Log.e("PrintWeekly", "❌ 读取文件 $filename 失败: ${fileError.message}", fileError)
                                events.add("Error reading $filename: ${fileError.message}")
                            }
                        }
                        
                        if (!printedAny) {
                            Log.d("PrintWeekly", "❌ 没有找到可打印的weekly文件")
                            postEvent("No weekly files found to print")
                        } else {
                            Log.d("PrintWeekly", "✅ 所有文件打印操作完成")
                            postEvent("All files printed to logs")
                        }
                    } catch (e: Exception) {
                        Log.e("PrintWeekly", "❌ 打印操作失败: ${e.message}", e)
                        postEvent("Print failed: ${e.message}")
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(text = "Print weekly.json")
            }

            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(events) { e ->
                    Card(modifier = Modifier.padding(4.dp)) {
                        Text(text = e, modifier = Modifier.padding(8.dp))
                    }
                }
            }
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
