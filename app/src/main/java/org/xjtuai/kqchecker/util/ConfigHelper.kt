package org.xjtuai.kqchecker.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Unified configuration reader with caching for assets/config.json
 */
object ConfigHelper {
  private const val TAG = "ConfigHelper"
  private const val CONFIG_FILE = "config.json"

  private var cachedConfig: Config? = null

  fun getConfig(context: Context): Config {
    return cachedConfig ?: readConfig(context).also { cachedConfig = it }
  }

  fun clearCache() {
    cachedConfig = null
  }

  private fun readConfig(context: Context): Config {
    val defaults = Config(
      baseUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc",
      authLoginUrl = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login",
      authRedirectPrefix = "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home",
      termNo = null,
      week = null
    )

    return try {
      context.assets.open(CONFIG_FILE).use { stream ->
        val json = JSONObject(InputStreamReader(stream, Charsets.UTF_8).readText())
        Log.d(TAG, "Loaded config from assets/$CONFIG_FILE")
        Config(
          baseUrl = json.optString("base_url", defaults.baseUrl),
          authLoginUrl = json.optString("auth_login_url", defaults.authLoginUrl),
          authRedirectPrefix = json.optString("auth_redirect_prefix", defaults.authRedirectPrefix),
          termNo = if (json.has("termNo")) json.getInt("termNo") else null,
          week = if (json.has("week")) json.getInt("week") else null
        )
      }
    } catch (e: Exception) {
      Log.d(TAG, "Using defaults ($CONFIG_FILE not found or parse error: ${e.message})")
      defaults
    }
  }

  data class Config(
    val baseUrl: String,
    val authLoginUrl: String,
    val authRedirectPrefix: String,
    val termNo: Int?,
    val week: Int?
  )
}
