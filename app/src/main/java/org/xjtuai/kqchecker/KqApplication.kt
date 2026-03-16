package org.xjtuai.kqchecker

import android.app.Application
import androidx.work.Configuration
import android.util.Log
import org.xjtuai.kqchecker.network.NetworkModule
import org.xjtuai.kqchecker.util.ConfigHelper


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
        val baseUrl = try {
            ConfigHelper.getBaseUrl(this)
        } catch (e: Exception) {
            Log.i("KqApplication", "Failed to load config, using BuildConfig.BASE_URL: ${BuildConfig.BASE_URL}")
            BuildConfig.BASE_URL
        }

        NetworkModule.init(this, baseUrl)
    }
}
