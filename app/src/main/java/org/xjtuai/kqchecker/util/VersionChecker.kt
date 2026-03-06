package org.xjtuai.kqchecker.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本检查工具
 * 从 GitHub Releases 获取最新版本信息
 */
object VersionChecker {
    private const val TAG = "VersionChecker"
    private const val GITHUB_API = "https://api.github.com/repos/HarcourtC/kqChecker_Mobile/releases/latest"

    /**
     * 检查是否有新版本
     * @param currentVersion 当前版本号 (如 "1.0")
     * @return 版本检查结果
     */
    suspend fun checkForUpdate(currentVersion: String): VersionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    val tagName = json.getString("tag_name").removePrefix("v")
                    val body = json.optString("body", "")
                    val htmlUrl = json.getString("html_url")

                    Log.d(TAG, "Latest version: $tagName, Current: $currentVersion")

                    VersionInfo(
                        latestVersion = tagName,
                        releaseNotes = body,
                        releaseUrl = htmlUrl,
                        isUpdateAvailable = isNewerVersion(tagName, currentVersion)
                    )
                } else {
                    Log.w(TAG, "Failed to check version: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking version", e)
                null
            }
        }
    }

    /**
     * 比较版本号
     * @param latest 最新版本
     * @param current 当前版本
     * @return 是否有新版本
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }

            when {
                latestPart > currentPart -> return true
                latestPart < currentPart -> return false
            }
        }
        return false
    }
}

/**
 * 版本信息数据类
 */
data class VersionInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val isUpdateAvailable: Boolean
)
