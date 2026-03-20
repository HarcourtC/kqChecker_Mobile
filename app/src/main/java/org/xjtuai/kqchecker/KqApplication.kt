package org.xjtuai.kqchecker

import android.app.Application
import org.xjtuai.kqchecker.repository.RepositoryProvider

class KqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable WebView remote debugging for development only.
        if (BuildConfig.DEBUG) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        RepositoryProvider.initialize(this)
    }
}
