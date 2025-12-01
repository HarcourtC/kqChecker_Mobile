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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.os.Build
import java.io.OutputStream
import kotlinx.coroutines.withContext
import org.example.kqchecker.network.NetworkModule
import org.example.kqchecker.network.WeeklyResponse
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.example.kqchecker.sync.SyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val repo = MockRepository(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            startSync(context)
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
                val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                if (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED) {
                    startSync(context)
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(text = "Trigger Sync")
            }

            Button(onClick = {
                scope.launch {
                    events.clear()
                    val weekly = repo.loadWeeklyFromAssets()
                    val periods = repo.loadPeriodsFromAssets()
                    events.add("Loaded ${weekly.size} datetimes, ${periods.size} periods")
                    weekly.forEach { (dt, items) ->
                        items.forEach { item ->
                            events.add("$dt | ${item.course} @ ${item.room}")
                        }
                    }
                }
            }, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Run Experimental Sync")
            }

            Button(onClick = {
                // Debug: issue an authenticated GET and log the outgoing request headers
                scope.launch {
                    events.add("Testing authenticated request...")
                    try {
                        val tm = TokenManager(context)
                        val client = OkHttpClient.Builder()
                            .addInterceptor(org.example.kqchecker.auth.TokenInterceptor(tm))
                            .build()
                        val req = Request.Builder()
                            .url("https://httpbin.org/anything")
                            .get()
                            .build()
                        val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                        val sentHeaders = resp.request.headers.toString()
                        val code = resp.code
                        val body = resp.body?.string()
                        Log.d("DebugRequest", "Response code=$code")
                        Log.d("DebugRequest", "Sent request headers:\n$sentHeaders")
                        events.add("HTTP $code — headers logged (see Logcat)")
                        events.add(sentHeaders.take(300))
                        if (body != null) events.add(body.take(200))
                    } catch (e: Exception) {
                        Log.e("DebugRequest", "Exception during debug request", e)
                        events.add("Debug request failed: ${e.message}")
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(text = "Debug Request")
            }

            Button(onClick = {
                // Fetch weekly from API using Retrofit/NetworkModule
                scope.launch {
                    events.add("Fetching weekly from API...")
                    try {
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

                        // ensure trailing slash
                        if (!baseUrl.endsWith('/')) baseUrl += '/'

                        // Construct the correct full API URL for weekly schedule
                        // Use only the scheme+host(+port) from baseUrl to avoid duplicating path segments
                        val path = "attendance-student/rankClass/getWeekSchedule2"
                        val fullUrl = try {
                            val uri = java.net.URI(baseUrl)
                            val scheme = uri.scheme ?: "http"
                            val host = uri.host ?: baseUrl.replace(Regex("https?://"), "").split(":")[0]
                            val portPart = if (uri.port != -1) ":${uri.port}" else ""
                            "$scheme://$host$portPart/$path"
                        } catch (e: Exception) {
                            // fallback: naive join
                            if (baseUrl.endsWith('/')) baseUrl + path else baseUrl + "/" + path
                        }
                        Log.d("FetchWeekly", "baseUrl=$baseUrl fullUrl=$fullUrl")

                        // Resolve host and log IPs for debugging
                        try {
                            val uri = java.net.URI(fullUrl)
                            val host = uri.host ?: fullUrl.replace(Regex("https?://"), "").split(":")[0]
                            val addrs = java.net.InetAddress.getAllByName(host)
                            val ips = addrs.joinToString(",") { it.hostAddress }
                            Log.d("FetchWeekly", "resolved host=$host ips=$ips")
                            events.add("DNS: $host -> $ips")
                        } catch (e: Exception) {
                            Log.d("FetchWeekly", "host resolve failed: ${e.message}")
                        }

                        // Perform direct OkHttp GET to the fullUrl using TokenInterceptor to ensure correct headers
                        try {
                            val tm = TokenManager(context)
                            val client = OkHttpClient.Builder()
                                .addInterceptor(org.example.kqchecker.auth.TokenInterceptor(tm))
                                .build()
                            // Check config for api1_params; if present, send POST with JSON body, else GET
                            var req: Request
                            try {
                                var bodyJson: String? = null
                                try {
                                    context.assets.open("config.json").use { stream ->
                                        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                                        val obj = JSONObject(text)
                                        if (obj.has("api1_params")) {
                                            bodyJson = obj.getJSONObject("api1_params").toString()
                                        }
                                    }
                                } catch (_: Exception) {
                                }

                                // Copy to an immutable local variable for safe smart-cast
                                val payload = bodyJson
                                if (payload != null) {
                                    val mediaType = "application/json; charset=utf-8".toMediaType()
                                    val body = payload.toRequestBody(mediaType)
                                    req = Request.Builder().url(fullUrl).post(body).build()
                                    // Log payload snippet
                                    events.add("Payload: ${payload.take(200)}")
                                    Log.d("FetchWeekly", "Payload: ${payload}")
                                } else {
                                    req = Request.Builder().url(fullUrl).get().build()
                                }

                                // Log request headers before executing so we can inspect what's sent
                                try {
                                    val reqHeaders = req.headers.toString()
                                    Log.d("FetchWeekly", "Sending request ${req.method} ${req.url}\n$reqHeaders")
                                    events.add("Req: ${req.method} ${req.url}")
                                    events.add(reqHeaders.take(800))
                                } catch (e: Exception) {
                                    Log.d("FetchWeekly", "failed to stringify request headers: ${e.message}")
                                }
                            } catch (e: Exception) {
                                Log.d("FetchWeekly", "failed to build request: ${e.message}")
                                throw e
                            }
                            // execute request and attempt to read body; if empty, retry a couple times
                            var resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                            var code = resp.code
                            var contentLength: Long = -1
                            try {
                                contentLength = resp.body?.contentLength() ?: -1
                            } catch (_: Exception) {}
                            var bodyText: String? = null
                            try {
                                bodyText = resp.body?.string()
                            } catch (e: Exception) {
                                Log.d("FetchWeekly", "read body failed: ${e.message}")
                                bodyText = null
                            }

                            // If body is empty (null or blank) but server returned 200, retry a couple times
                            if ((bodyText == null || bodyText.isBlank()) && code == 200) {
                                Log.d("FetchWeekly", "Empty body received; attempting retries")
                                events.add("Empty body; retrying...")
                                for (i in 1..2) {
                                    try {
                                        // dispose previous response and issue new call
                                        resp.close()
                                    } catch (_: Exception) {}
                                    kotlinx.coroutines.delay(500L * i)
                                    val r2 = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                                    code = r2.code
                                    try { contentLength = r2.body?.contentLength() ?: contentLength } catch (_: Exception) {}
                                    try {
                                        val bt = r2.body?.string()
                                        if (!bt.isNullOrBlank()) {
                                            bodyText = bt
                                            resp = r2
                                            break
                                        }
                                    } catch (e: Exception) {
                                        Log.d("FetchWeekly", "retry read failed: ${e.message}")
                                    }
                                    try { r2.close() } catch (_: Exception) {}
                                }
                            }

                            // Log the final request headers (including those added by interceptors)
                            try {
                                val sent = resp.request.headers.toString()
                                Log.d("FetchWeekly", "Sent request headers:\n$sent")
                                events.add("Sent headers:")
                                events.add(sent.take(800))
                            } catch (e: Exception) {
                                Log.d("FetchWeekly", "failed to read sent request headers: ${e.message}")
                            }

                            Log.d("FetchWeekly", "code=$code contentLength=$contentLength headers=${resp.headers}\nbody=${bodyText?.take(1000)}")
                            events.add("Weekly fetch HTTP $code (len=$contentLength)")

                            // Format and save weekly.json for frontend use
                            try {
                                val dataArray = if (!bodyText.isNullOrBlank()) {
                                    try {
                                        val jo = JSONObject(bodyText)
                                        jo.optJSONArray("data") ?: org.json.JSONArray()
                                    } catch (e: Exception) {
                                        org.json.JSONArray()
                                    }
                                } else {
                                    org.json.JSONArray()
                                }

                                val out = JSONObject()
                                out.put("code", 200)
                                out.put("success", true)
                                out.put("data", dataArray)
                                out.put("msg", "操作成功")

                                // date: today; expires: this week's Sunday
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val now = Calendar.getInstance()
                                val todayStr = sdf.format(now.time)
                                val cal = Calendar.getInstance()
                                // set to sunday of current week
                                cal.firstDayOfWeek = Calendar.MONDAY
                                // move to end of week (Sunday)
                                cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                                val sundayStr = sdf.format(cal.time)
                                out.put("date", todayStr)
                                out.put("expires", sundayStr)

                                // write to internal storage (pretty printed) and also save raw response
                                try {
                                    val f = File(context.filesDir, "weekly.json")
                                    f.writeText(out.toString(2))
                                    Log.d("FetchWeekly", "saved weekly.json to ${f.absolutePath}")
                                    events.add("Saved weekly.json: ${f.absolutePath}")
                                } catch (e: Exception) {
                                    Log.e("FetchWeekly", "failed to write weekly.json", e)
                                    events.add("Failed to save weekly.json: ${e.message}")
                                }
                                try {
                                    // Always write raw file (may be empty string)
                                    val raw = File(context.filesDir, "weekly_raw.json")
                                    raw.writeText(bodyText ?: "")
                                    Log.d("FetchWeekly", "saved weekly_raw.json to ${raw.absolutePath} (len=${(bodyText?:"").length})")
                                    events.add("Saved weekly_raw.json: ${raw.absolutePath}")

                                    // write metadata for diagnostics
                                    val meta = JSONObject()
                                    meta.put("http_code", code)
                                    meta.put("content_length", contentLength)
                                    meta.put("headers", resp.headers.toString())
                                    meta.put("fetched_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(now.time))
                                    val metaFile = File(context.filesDir, "weekly_raw_meta.json")
                                    metaFile.writeText(meta.toString(2))
                                    Log.d("FetchWeekly", "saved weekly_raw_meta.json to ${metaFile.absolutePath}")
                                } catch (e: Exception) {
                                    Log.e("FetchWeekly", "failed to write weekly_raw.json/meta", e)
                                }
                            } catch (e: Exception) {
                                Log.e("FetchWeekly", "format/save failed", e)
                                events.add("Format/save failed: ${e.message}")
                            }

                            if (!bodyText.isNullOrBlank()) events.add(bodyText.take(800))
                        } catch (e: Exception) {
                            Log.e("FetchWeekly", "call failed", e)
                            events.add("Fetch failed: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("FetchWeekly", "unexpected", e)
                        events.add("Unexpected error: ${e.message}")
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(text = "Fetch Weekly (API)")
            }

            Button(onClick = {
                // Export weekly.json from internal storage to Downloads
                scope.launch(Dispatchers.IO) {
                    try {
                        val filesToExport = listOf("weekly.json", "weekly_raw.json", "weekly_raw_meta.json")
                        var exportedAny = false
                        for (filename in filesToExport) {
                            val src = File(context.filesDir, filename)
                            if (!src.exists()) {
                                Log.d("FetchWeekly", "internal file not found: $filename")
                                continue
                            }
                            val bytes = src.readBytes()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val resolver = context.contentResolver
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                if (uri == null) {
                                    events.add("Failed to create Downloads file via MediaStore for $filename")
                                    continue
                                }
                                resolver.openOutputStream(uri).use { os ->
                                    if (os == null) throw java.io.IOException("Unable to open output stream")
                                    os.write(bytes)
                                    os.flush()
                                }
                                events.add("Exported $filename to Downloads/$filename")
                            } else {
                                // fallback for older devices
                                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                if (!downloads.exists()) downloads.mkdirs()
                                val outFile = File(downloads, filename)
                                outFile.writeBytes(bytes)
                                events.add("Exported $filename to ${outFile.absolutePath}")
                            }
                            exportedAny = true
                        }
                        if (!exportedAny) {
                            events.add("No weekly files found to export")
                        }
                    } catch (e: Exception) {
                        Log.e("FetchWeekly", "export failed", e)
                        events.add("Export failed: ${e.message}")
                    }
                }
            }, modifier = Modifier.padding(top = 12.dp)) {
                Text(text = "Export weekly.json")
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

fun startSync() {
    // Trigger a background sync; placeholder for WorkManager request
    println("Sync triggered")
}

fun startSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
    WorkManager.getInstance(context).enqueue(request)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppContent()
}
