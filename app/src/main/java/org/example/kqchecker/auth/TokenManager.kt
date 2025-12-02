package org.example.kqchecker.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
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

    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString("access_token", accessToken).apply()
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
        prefs.edit().clear().apply()
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
}
