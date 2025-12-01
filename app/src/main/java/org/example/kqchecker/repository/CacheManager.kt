package org.example.kqchecker.repository

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 缓存管理器，负责处理数据缓存的读写和过期检查
 */
class CacheManager(private val context: Context) {
    companion object {
        private const val TAG = "CacheManager"
        const val WEEKLY_CACHE_FILE = "weekly.json"
        const val WEEKLY_RAW_CACHE_FILE = "weekly_raw.json"
        const val WEEKLY_RAW_META_FILE = "weekly_raw_meta.json"
        const val WATER_LIST_CACHE_FILE = "api2_waterlist_response.json"
    }
    
    /**
     * 检查周课表缓存是否过期
     */
    fun isWeeklyCacheExpired(): Boolean {
        try {
            val cacheFile = File(context.filesDir, WEEKLY_CACHE_FILE)
            if (!cacheFile.exists()) {
                Log.d(TAG, "Weekly cache file does not exist")
                return true
            }
            
            val jsonString = cacheFile.readText()
            val jsonObject = JSONObject(jsonString)
            
            // 检查是否包含必要的字段
            if (!jsonObject.has("expires")) {
                Log.d(TAG, "Weekly cache file does not have expires field")
                return true
            }
            
            val expiresStr = jsonObject.getString("expires")
            if (expiresStr.isBlank()) {
                Log.d(TAG, "Weekly cache expires field is blank")
                return true
            }
            
            // 解析过期日期
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val expiresDate = sdf.parse(expiresStr)
                ?: return true // 解析失败，认为已过期
            
            // 检查是否已过期
            val currentDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val isExpired = currentDate.after(expiresDate)
            Log.d(TAG, "Weekly cache expires check: $expiresStr, isExpired=$isExpired")
            return isExpired
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking weekly cache expiration", e)
            return true // 发生错误，认为已过期
        }
    }
    
    /**
     * 获取周课表缓存的过期日期
     */
    fun getWeeklyCacheExpiresDate(): String? {
        try {
            val cacheFile = File(context.filesDir, WEEKLY_CACHE_FILE)
            if (!cacheFile.exists()) {
                return null
            }
            
            val jsonString = cacheFile.readText()
            val jsonObject = JSONObject(jsonString)
            return jsonObject.optString("expires", null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading weekly cache expires date", e)
            return null
        }
    }
    
    /**
     * 保存数据到缓存文件
     */
    fun saveToCache(filename: String, content: String): Boolean {
        try {
            val file = File(context.filesDir, filename)
            file.writeText(content)
            Log.d(TAG, "Saved cache to ${file.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache to $filename", e)
            return false
        }
    }
    
    /**
     * 从缓存文件读取数据
     */
    fun readFromCache(filename: String): String? {
        try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                Log.d(TAG, "Cache file $filename does not exist")
                return null
            }
            
            val content = file.readText()
            Log.d(TAG, "Read cache from ${file.absolutePath}")
            return content
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache from $filename", e)
            return null
        }
    }
    
    /**
     * 获取当前周末日期（用于设置缓存过期时间）
     */
    fun getCurrentWeekendDate(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }
    
    /**
     * 获取当前日期
     */
    fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
    
    /**
     * 检查缓存文件是否存在
     */
    fun cacheFileExists(filename: String): Boolean {
        return File(context.filesDir, filename).exists()
    }
    
    /**
     * 获取缓存文件信息
     */
    fun getCacheFileInfo(filename: String): CacheFileInfo? {
        try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                return null
            }
            
            return CacheFileInfo(
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache file info for $filename", e)
            return null
        }
    }
}

/**
 * 缓存文件信息数据类
 */
data class CacheFileInfo(
    val path: String,
    val size: Long,
    val lastModified: Long
) {
    /**
     * 获取格式化的最后修改时间
     */
    fun getFormattedLastModified(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(lastModified))
    }
}