package org.example.kqchecker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.webkit.CookieManager
import android.os.Build
import androidx.core.app.NotificationCompat

class TokenManager(private val context: Context) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        const val ACTION_TOKEN_CLEARED = "org.example.kqchecker.ACTION_TOKEN_CLEARED"
    }

    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString("access_token", accessToken).apply()
        // record when token was saved and clear any cleared marker
        try {
            prefs.edit().putLong("token_saved_at", System.currentTimeMillis()).putLong("token_cleared_at", 0L).apply()
        } catch (_: Throwable) {}
        try {
            val len = accessToken.length
            Log.d("TokenManager", "saveAccessToken: saved access token (length=$len)")
        } catch (_: Throwable) {
        }
    }

    fun saveRefreshToken(refreshToken: String?) {
        if (refreshToken != null) {
            prefs.edit().putString("refresh_token", refreshToken).apply()
            try {
                Log.d("TokenManager", "saveRefreshToken: saved refresh token (present=true)")
            } catch (_: Throwable) {
            }
        } else {
            Log.d("TokenManager", "saveRefreshToken: refreshToken is null")
        }
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() {
        try {
            // remove tokens but keep a cleared timestamp to signal WebView and other components
            val now = System.currentTimeMillis()
            prefs.edit().remove("access_token").remove("refresh_token").putLong("token_cleared_at", now).apply()
            // also try to clear cookies (best-effort)
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (_: Throwable) {}
            // broadcast an intent so activities (WebLoginActivity) can clear WebView localStorage
            try {
                val intent = Intent(ACTION_TOKEN_CLEARED)
                context.sendBroadcast(intent)
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            try { Log.e("TokenManager", "clear failed", e) } catch (_: Throwable) {}
        }
    }

    /**
     * 在检测到 token 无效或认证失败时，发送一个通知提示用户重新登录。
     */
    fun notifyTokenInvalid() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "auth_invalid_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(channelId, "认证提醒", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Token 失效，请重新登录")
                .setContentText("检测到认证错误，请打开应用重新登录以刷新 Token")
                .setAutoCancel(true)
                .build()
            nm.notify(2001, notif)
        } catch (e: Throwable) {
            try { Log.e("TokenManager", "notifyTokenInvalid failed", e) } catch (_: Throwable) {}
        }
    }

    fun getTokenSavedAt(): Long {
        return try { prefs.getLong("token_saved_at", 0L) } catch (_: Throwable) { 0L }
    }

    fun getTokenClearedAt(): Long {
        return try { prefs.getLong("token_cleared_at", 0L) } catch (_: Throwable) { 0L }
    }
}
