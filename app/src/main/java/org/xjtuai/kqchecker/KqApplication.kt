package org.xjtuai.kqchecker

import android.app.Application
import androidx.work.Configuration
import android.util.Log
import java.io.InputStreamReader
import org.json.JSONObject
import org.xjtuai.kqchecker.network.NetworkModule


class KqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable WebView remote debugging for development (inspect via chrome://inspect)
        try {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
            Log.i("KqApplication", "WebView remote debugging enabled")
        } catch (t: Throwable) {
            Log.w("KqApplication", "Failed to enable WebView debugging", t)
        }
        // Initialize NetworkModule with configurable base URL.
        val defaultUrl = BuildConfig.BASE_URL
        var baseUrl = defaultUrl
        try {
            assets.open("config.json").use { stream ->
                val text = InputStreamReader(stream, Charsets.UTF_8).readText()
                val obj = JSONObject(text)
                if (obj.has("base_url")) {
                    baseUrl = obj.getString("base_url")
                }
            }
        } catch (e: Exception) {
            Log.i("KqApplication", "No assets/config.json found or failed to read, using BuildConfig.BASE_URL: $defaultUrl")
        }

        NetworkModule.init(this, baseUrl)
    }
}
