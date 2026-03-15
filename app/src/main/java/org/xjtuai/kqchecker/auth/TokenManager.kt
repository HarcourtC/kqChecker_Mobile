package org.xjtuai.kqchecker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.webkit.CookieManager
import android.os.Build
import androidx.core.app.NotificationCompat
import org.xjtuai.kqchecker.auth.WebLoginActivity
import android.webkit.WebStorage
import android.os.Handler
import android.os.Looper

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
        const val ACTION_TOKEN_CLEARED = "org.xjtuai.kqchecker.ACTION_TOKEN_CLEARED"
        const val ACTION_REQUEST_LOGIN = "org.xjtuai.kqchecker.ACTION_REQUEST_LOGIN"
    }

    private inline fun runBestEffort(actionName: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w("TokenManager", "$actionName failed", e)
        }
    }

    private fun clearCookiesBestEffort() {
        runBestEffort("clearCookies") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    private fun broadcastTokenClearedBestEffort() {
        runBestEffort("broadcastTokenCleared") {
            context.sendBroadcast(Intent(ACTION_TOKEN_CLEARED))
        }
    }

    private fun clearWebStorageBestEffort() {
        runBestEffort("clearWebStorage") {
            Handler(Looper.getMainLooper()).post {
                runBestEffort("deleteAllWebStorage") {
                    WebStorage.getInstance().deleteAllData()
                    Log.d("TokenManager", "WebStorage cleared successfully")
                }
            }
        }
    }

    private fun broadcastLoginRequestBestEffort() {
        runBestEffort("broadcastLoginRequest") {
            context.sendBroadcast(Intent(ACTION_REQUEST_LOGIN))
            Log.d("TokenManager", "notifyTokenInvalid: broadcasted ACTION_REQUEST_LOGIN")
        }
    }

    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString("access_token", accessToken).apply()
        prefs.edit()
            .putLong("token_saved_at", System.currentTimeMillis())
            .putLong("token_cleared_at", 0L)
            .apply()
        Log.d("TokenManager", "saveAccessToken: saved access token (length=${accessToken.length})")
    }

    fun saveRefreshToken(refreshToken: String?) {
        if (refreshToken != null) {
            prefs.edit().putString("refresh_token", refreshToken).apply()
            Log.d("TokenManager", "saveRefreshToken: saved refresh token (present=true)")
        } else {
            Log.d("TokenManager", "saveRefreshToken: refreshToken is null")
        }
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() {
        try {
            val now = System.currentTimeMillis()
            prefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .putLong("token_cleared_at", now)
                .apply()
            clearCookiesBestEffort()
            broadcastTokenClearedBestEffort()
            clearWebStorageBestEffort()
        } catch (e: Exception) {
            Log.e("TokenManager", "clear failed", e)
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
            // Intent to open WebLoginActivity so user can re-login
            val loginIntent = Intent(context, WebLoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getActivity(context, 2001, loginIntent, pendingFlags)

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Token 失效，请重新登录")
                .setContentText("检测到认证错误，点击此处打开登录页面以刷新 Token")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(2001, notif)
            broadcastLoginRequestBestEffort()
        } catch (e: Exception) {
            Log.e("TokenManager", "notifyTokenInvalid failed", e)
        }
    }

    fun getTokenSavedAt(): Long {
        return prefs.getLong("token_saved_at", 0L)
    }

    fun getTokenClearedAt(): Long {
        return prefs.getLong("token_cleared_at", 0L)
    }
}
