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
  private const val PRIVATE_CONFIG_FILE = "config.private.json"

  // Public repo defaults only. Real values should live in assets/config.private.json.
  const val DEFAULT_BASE_URL = "https://example.invalid/"
  const val DEFAULT_AUTH_LOGIN_URL = "https://example.invalid/login"
  const val DEFAULT_AUTH_REDIRECT_PREFIX = "https://example.invalid/app/#/home"
  const val DEFAULT_WEEKLY_ENDPOINT = "api/schedule/weekly"
  const val DEFAULT_WATER_LIST_ENDPOINT = "api/attendance/water-list"
  const val DEFAULT_CURRENT_TERM_ENDPOINT = "api/term/current"
  const val DEFAULT_COMPETITION_BASE_URL = "https://example.invalid/"
  const val DEFAULT_COMPETITION_ENDPOINT = "api/competition"
  const val DEFAULT_COMPETITION_API_KEY = ""

  private var cachedConfig: Config? = null

  fun getConfig(context: Context): Config {
    return cachedConfig ?: readConfig(context).also { cachedConfig = it }
  }

  /**
   * 获取 Base URL，确保以 / 结尾
   */
  fun getBaseUrl(context: Context): String {
    val baseUrl = getConfig(context).baseUrl
    return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
  }

  fun getCompetitionBaseUrl(context: Context): String {
    val baseUrl = getConfig(context).competitionBaseUrl
    return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
  }

  /**
   * 获取默认 Base URL（不读取配置文件）
   */
  fun getDefaultBaseUrl(): String = DEFAULT_BASE_URL

  fun clearCache() {
    cachedConfig = null
  }

  private fun readConfig(context: Context): Config {
    val defaults = Config(
      baseUrl = DEFAULT_BASE_URL,
      authLoginUrl = DEFAULT_AUTH_LOGIN_URL,
      authRedirectPrefix = DEFAULT_AUTH_REDIRECT_PREFIX,
      weeklyEndpoint = DEFAULT_WEEKLY_ENDPOINT,
      waterListEndpoint = DEFAULT_WATER_LIST_ENDPOINT,
      currentTermEndpoint = DEFAULT_CURRENT_TERM_ENDPOINT,
      competitionBaseUrl = DEFAULT_COMPETITION_BASE_URL,
      competitionEndpoint = DEFAULT_COMPETITION_ENDPOINT,
      competitionApiKey = DEFAULT_COMPETITION_API_KEY,
      termNo = null,
      week = null
    )

    val publicJson = readJsonConfig(context, CONFIG_FILE)
    val privateJson = readJsonConfig(context, PRIVATE_CONFIG_FILE)

    return Config(
      baseUrl = resolveString("base_url", defaults.baseUrl, privateJson, publicJson),
      authLoginUrl = resolveString("auth_login_url", defaults.authLoginUrl, privateJson, publicJson),
      authRedirectPrefix = resolveString("auth_redirect_prefix", defaults.authRedirectPrefix, privateJson, publicJson),
      weeklyEndpoint = normalizeEndpoint(resolveString("weekly_endpoint", defaults.weeklyEndpoint, privateJson, publicJson)),
      waterListEndpoint = normalizeEndpoint(resolveString("water_list_endpoint", defaults.waterListEndpoint, privateJson, publicJson)),
      currentTermEndpoint = normalizeEndpoint(resolveString("current_term_endpoint", defaults.currentTermEndpoint, privateJson, publicJson)),
      competitionBaseUrl = resolveString("competition_base_url", defaults.competitionBaseUrl, privateJson, publicJson),
      competitionEndpoint = normalizeEndpoint(resolveString("competition_endpoint", defaults.competitionEndpoint, privateJson, publicJson)),
      competitionApiKey = resolveString("competition_api_key", defaults.competitionApiKey, privateJson, publicJson),
      termNo = resolveInt("termNo", privateJson, publicJson),
      week = resolveInt("week", privateJson, publicJson)
    )
  }

  private fun readJsonConfig(context: Context, fileName: String): JSONObject? {
    return try {
      context.assets.open(fileName).use { stream ->
        Log.d(TAG, "Loaded config from assets/$fileName")
        JSONObject(InputStreamReader(stream, Charsets.UTF_8).readText())
      }
    } catch (e: Exception) {
      Log.d(TAG, "assets/$fileName not loaded: ${e.message}")
      null
    }
  }

  private fun resolveString(
    key: String,
    defaultValue: String,
    privateJson: JSONObject?,
    publicJson: JSONObject?
  ): String {
    val privateValue = privateJson?.optString(key, "")?.trim().orEmpty()
    if (privateValue.isNotEmpty()) return privateValue

    val publicValue = publicJson?.optString(key, "")?.trim().orEmpty()
    if (publicValue.isNotEmpty()) return publicValue

    return defaultValue
  }

  private fun resolveInt(key: String, privateJson: JSONObject?, publicJson: JSONObject?): Int? {
    if (privateJson?.has(key) == true) return privateJson.optInt(key)
    if (publicJson?.has(key) == true) return publicJson.optInt(key)
    return null
  }

  private fun normalizeEndpoint(path: String): String {
    return path.trim().trimStart('/')
  }

  data class Config(
    val baseUrl: String,
    val authLoginUrl: String,
    val authRedirectPrefix: String,
    val weeklyEndpoint: String,
    val waterListEndpoint: String,
    val currentTermEndpoint: String,
    val competitionBaseUrl: String,
    val competitionEndpoint: String,
    val competitionApiKey: String,
    val termNo: Int?,
    val week: Int?
  )
}
