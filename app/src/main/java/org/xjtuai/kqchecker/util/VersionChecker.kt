package org.xjtuai.kqchecker.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
                    val apkUrl = findApkAssetUrl(json.optJSONArray("assets"))

                    Log.d(TAG, "Latest version: $tagName, Current: $currentVersion")

                    VersionInfo(
                        latestVersion = tagName,
                        releaseNotes = body,
                        releaseUrl = htmlUrl,
                        apkUrl = apkUrl,
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
        return compareSemVer(latest, current) > 0
    }

    private fun compareSemVer(a: String, b: String): Int {
        val left = parseSemVer(a)
        val right = parseSemVer(b)

        // 若版本不满足 semver，回退到“仅数字段”比较，避免崩溃。
        if (left == null || right == null) return compareLegacyNumeric(a, b)

        if (left.major != right.major) return left.major.compareTo(right.major)
        if (left.minor != right.minor) return left.minor.compareTo(right.minor)
        if (left.patch != right.patch) return left.patch.compareTo(right.patch)

        return comparePreRelease(left.preRelease, right.preRelease)
    }

    private fun comparePreRelease(left: List<String>?, right: List<String>?): Int {
        // 无预发布标识的版本优先级更高（SemVer 2.0.0）。
        if (left == null && right == null) return 0
        if (left == null) return 1
        if (right == null) return -1

        val maxLength = maxOf(left.size, right.size)
        for (i in 0 until maxLength) {
            val l = left.getOrNull(i) ?: return -1
            val r = right.getOrNull(i) ?: return 1
            val cmp = comparePreReleaseIdentifier(l, r)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun comparePreReleaseIdentifier(left: String, right: String): Int {
        val lNum = left.toLongOrNull()
        val rNum = right.toLongOrNull()

        return when {
            lNum != null && rNum != null -> lNum.compareTo(rNum)
            lNum != null && rNum == null -> -1
            lNum == null && rNum != null -> 1
            else -> left.compareTo(right)
        }
    }

    private fun parseSemVer(raw: String): SemVer? {
        val cleaned = raw.trim().removePrefix("v")
        val match = SEMVER_REGEX.matchEntire(cleaned) ?: return null

        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        val pre = match.groupValues[4].takeIf { it.isNotBlank() }?.split(".")

        return SemVer(
            major = major,
            minor = minor,
            patch = patch,
            preRelease = pre
        )
    }

    private fun compareLegacyNumeric(a: String, b: String): Int {
        val left = extractNumericParts(a)
        val right = extractNumericParts(b)
        val maxLength = maxOf(left.size, right.size)
        for (i in 0 until maxLength) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun extractNumericParts(raw: String): List<Int> {
        return raw.trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    }

    private fun findApkAssetUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val contentType = item.optString("content_type", "")
            val name = item.optString("name", "")
            val downloadUrl = item.optString("browser_download_url", "")
            if (downloadUrl.isBlank()) continue

            val isApkContentType = contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
            val isApkFileName = name.endsWith(".apk", ignoreCase = true)
            if (isApkContentType || isApkFileName) return downloadUrl
        }
        return null
    }
}

private data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>?
)

private val SEMVER_REGEX = Regex(
    "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
        "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
        "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
)

/**
 * 版本信息数据类
 */
data class VersionInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val isUpdateAvailable: Boolean
)
