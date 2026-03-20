package org.xjtuai.kqchecker.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * UI hint for rate-limit events.
 * Uses a short cooldown to avoid toast spam.
 */
object RateLimitNotifier {
    private const val COOLDOWN_MILLIS = 2500L
    private const val DEFAULT_MESSAGE = "请求过于频繁，请稍后再试"

    @Volatile
    private var lastShownAt: Long = 0L

    fun show(context: Context, message: String = DEFAULT_MESSAGE) {
        val now = System.currentTimeMillis()
        if (now - lastShownAt < COOLDOWN_MILLIS) return
        lastShownAt = now

        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
