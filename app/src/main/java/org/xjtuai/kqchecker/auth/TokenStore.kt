package org.xjtuai.kqchecker.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple token store placeholder. Replace with EncryptedSharedPreferences in production.
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun clear() { prefs.edit().clear().apply() }
}
