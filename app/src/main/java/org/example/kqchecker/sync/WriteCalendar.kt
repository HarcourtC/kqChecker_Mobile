package org.example.kqchecker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.kqchecker.repository.WeeklyRepository
import org.example.kqchecker.util.CalendarHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import android.util.Log
import kotlinx.coroutines.flow.collect

private const val TAG = "WriteCalendar"


/**
 * 从后端获取weekly数据并写入日历的功能
 */
class WriteCalendar(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    
    private val weeklyRepository = WeeklyRepository(applicationContext)

    override suspend fun doWork(): Result {
        // 生成唯一的工作ID用于日志追踪
        val workId = System.currentTimeMillis().toString().takeLast(8)
        Log.d(TAG, "========== WriteCalendar Worker 开始执行 (追踪ID: $workId) ==========")
        Log.d(TAG, "工作ID: ${id}")
        
        // 记录开始时间，用于计算总执行时间
        val startTime = System.currentTimeMillis()
        
        return try {
            withContext(Dispatchers.IO) {
                try {
                    // 优先从缓存获取weekly数据，避免API调用异常
                    Log.d(TAG, "🔄 1. 优先从缓存获取weekly数据...")
                    val weeklyResponse = weeklyRepository.getWeeklyData(forceRefresh = false)
                    
                    Log.d(TAG, "🔄 2. 数据获取完成，响应状态检查中...")
                    if (weeklyResponse == null) {
                        Log.e(TAG, "❌ 获取数据失败：weeklyResponse为null")
                        Log.e(TAG, "❌ 根据用户要求，不进行API调用，直接尝试使用缓存")
                        Log.e(TAG, "❌ 缓存数据可能不存在或已损坏")
                        Log.e(TAG, "💡 建议：尝试使用'Print weekly.json'按钮验证缓存数据是否存在")
                        logWorkResult(Result.failure())
                        return@withContext Result.failure()
                    }
                    
                    try {
                        Log.d(TAG, "   - 响应对象不为null，检查success状态...")
                        if (!weeklyResponse.success) {
                            Log.e(TAG, "❌ 获取数据失败：success=false")
                            Log.e(TAG, "💡 建议：检查后端返回的错误信息")
                            logWorkResult(Result.failure())
                            return@withContext Result.failure()
                        }
                        
                        Log.d(TAG, "   - success=true，检查data字段...")
                        if (weeklyResponse.data == null) {
                            Log.e(TAG, "❌ 获取数据失败：data为null")
                            logWorkResult(Result.failure())
                            return@withContext Result.failure()
                        }

                        Log.d(TAG, "✅ 成功获取weekly数据，共${weeklyResponse.data.length()}条记录")
                        
                        try {
                            // 检查日历权限和获取日历ID
                            Log.d(TAG, "🔄 3. 开始检查日历权限和获取日历ID...")
                            val calId = CalendarHelper.getDefaultCalendarId(applicationContext)
                            if (calId == null) {
                                Log.e(TAG, "❌ 未找到日历或缺少权限")
                                Log.e(TAG, "❌ 可能缺少日历权限或系统日历服务异常")
                                Log.e(TAG, "💡 建议：确保应用已获得日历读写权限")
                                logWorkResult(Result.failure())
                                return@withContext Result.failure()
                            }

                            Log.d(TAG, "✅ 成功获取日历ID: $calId")
                            Log.d(TAG, "🔄 4. 开始处理数据并写入日历...")
                            
                            try {
                                // 处理后端返回的weekly数据
                                processWeeklyData(weeklyResponse.data, calId)
                                
                                // 计算总执行时间
                                val totalTime = System.currentTimeMillis() - startTime
                                Log.d(TAG, "✅ 5. 数据处理完成，日历更新成功")
                                Log.d(TAG, "⏱️  总执行时间: ${totalTime}ms")
                                Log.d(TAG, "📱 结果将显示在应用界面上")
                                logWorkResult(Result.success())
                                Result.success()
                            } catch (dataProcessingError: Exception) {
                                Log.e(TAG, "❌ 数据处理过程失败: ${dataProcessingError.message}")
                                Log.e(TAG, "❌ 错误详情: ${dataProcessingError.javaClass.simpleName}")
                                Log.e(TAG, "🔍 请查看堆栈信息进行调试:", dataProcessingError)
                                logWorkResult(Result.failure())
                                Result.failure()
                            }
                        } catch (calendarError: Exception) {
                            Log.e(TAG, "❌ 日历操作失败: ${calendarError.message}")
                            Log.e(TAG, "❌ 错误详情: ${calendarError.javaClass.simpleName}")
                            Log.e(TAG, "🔍 请查看堆栈信息进行调试:", calendarError)
                            logWorkResult(Result.failure())
                            Result.failure()
                        }
                    } catch (dataValidationError: Exception) {
                        Log.e(TAG, "❌ 数据验证失败: ${dataValidationError.message}")
                        Log.e(TAG, "❌ 错误详情: ${dataValidationError.javaClass.simpleName}")
                        Log.e(TAG, "🔍 请查看堆栈信息进行调试:", dataValidationError)
                        logWorkResult(Result.failure())
                        Result.failure()
                    }
                } catch (dataFetchError: Exception) {
                    Log.e(TAG, "❌ 数据获取失败: ${dataFetchError.message}")
                    Log.e(TAG, "❌ 错误详情: ${dataFetchError.javaClass.simpleName}")
                    Log.e(TAG, "❌ 根据用户要求，不尝试从API获取数据")
                    Log.e(TAG, "🔍 请查看堆栈信息进行调试:", dataFetchError)
                    logWorkResult(Result.failure())
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 执行过程发生异常: ${e.message}")
            Log.e(TAG, "❌ 错误详情: ${e.javaClass.simpleName}")
            Log.e(TAG, "🔍 请查看堆栈信息进行调试:", e)
            logWorkResult(Result.retry())
            Result.retry()
        } finally {
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "========== WriteCalendar Worker 执行结束 (总耗时: ${totalTime}ms) ==========")
        }
    }
    
    private fun logWorkResult(result: Result) {
        when (result) {
            is Result.Success -> Log.d(TAG, "工作结果: 成功")
            is Result.Failure -> Log.d(TAG, "工作结果: 失败")
            is Result.Retry -> Log.d(TAG, "工作结果: 重试")
        }
    }

    /**
     * 处理后端返回的weekly数据并写入日历
     * 根据新的数据结构，使用bh作为唯一标识，eqname作为标题，eqno作为地点，watertime作为时间
     */
    private fun processWeeklyData(weeklyData: JSONArray, calId: Long) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        var insertedCount = 0
        var skippedCount = 0
        var errors = 0
        
        Log.d(TAG, "📅 开始处理${weeklyData.length()}条日历数据...")
        // 遍历JSONArray中的每个元素
        for (i in 0 until weeklyData.length()) {
            Log.d(TAG, "🔄 处理事件 #${i+1}/${weeklyData.length()}")
            
            val item = weeklyData.optJSONObject(i)
            if (item != null) {
                try {
                    // 从新数据结构中解析关键字段
                    val bh = item.optString("bh", "") // 唯一标识，用于事件匹配
                    val eqname = item.optString("eqname", "未命名打卡") // 设备名称，作为标题
                    val eqno = item.optString("eqno", "") // 设备位置
                    var watertime = item.optString("watertime", "") // 打卡时间
                    // 回退解析：如果后端没有直接提供时间字段，但提供了节次/星期信息（如accountJtNo/accountWeeknum），
                    // 则尝试将节次映射为近似时间并生成一个本周的日期时间作为打卡时间，避免跳过事件。
                    if (watertime.isEmpty()) {
                        val accountJt = item.optString("accountJtNo", "").trim() // 例如 "7-8"
                        val accountWeeknum = item.optString("accountWeeknum", "").trim() // 例如 "1" 表示周一
                        if (accountJt.isNotEmpty() && accountWeeknum.isNotEmpty()) {
                            try {
                                // 取节次的起始节号（例如 "7-8" -> 7）
                                val firstPart = accountJt.split(Regex("\\D+"))[0]
                                val startPeriod = firstPart.toIntOrNull()

                                // 默认的节次->时间映射（可根据学校实际课表调整）
                                val periodToTime = mapOf(
                                    1 to "08:00",
                                    2 to "08:55",
                                    3 to "10:10",
                                    4 to "11:05",
                                    5 to "13:30",
                                    6 to "14:25",
                                    7 to "15:40",
                                    8 to "16:35",
                                    9 to "18:30",
                                    10 to "19:25"
                                )

                                if (startPeriod != null && periodToTime.containsKey(startPeriod)) {
                                    val timeStr = periodToTime[startPeriod]!!
                                    val parts = timeStr.split(":")
                                    val hour = parts[0].toIntOrNull() ?: 8
                                    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                    // 以本周为基准，计算对应的星期几日期（假定 accountWeeknum 中 1=周一）
                                    val cal = Calendar.getInstance()
                                    // 将日期移动到本周的周一
                                    cal.firstDayOfWeek = Calendar.MONDAY
                                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                                    val desiredOffset = (accountWeeknum.toIntOrNull() ?: 1) - 1
                                    cal.add(Calendar.DAY_OF_MONTH, desiredOffset)
                                    cal.set(Calendar.HOUR_OF_DAY, hour)
                                    cal.set(Calendar.MINUTE, minute)
                                    cal.set(Calendar.SECOND, 0)

                                    // 格式化为与现有逻辑一致的字符串形式
                                    watertime = fmt.format(cal.time)
                                    Log.d(TAG, "   - 使用回退解析生成时间: $watertime (由 accountJtNo=$accountJt accountWeeknum=$accountWeeknum)")
                                }
                            } catch (_: Exception) {
                                // 解析回退失败，则保留 watertime 为空，后面会跳过
                            }
                        }
                    }
                    val isdone = item.optString("isdone", "0") // 是否完成
                    val fromtype = item.optString("fromtype", "") // 来源类型
                    
                    Log.d(TAG, "   - 事件ID: $bh")
                    Log.d(TAG, "   - 标题: $eqname")
                    Log.d(TAG, "   - 时间: $watertime")
                    Log.d(TAG, "   - 地点: $eqno")
                    Log.d(TAG, "   - 状态: ${if (isdone == "1") "已完成" else "未完成"}")
                    
                    if (watertime.isNotEmpty()) {
                        val date = try {
                            fmt.parse(watertime)
                        } catch (e: ParseException) {
                            Log.e(TAG, "   - ❌ 解析日期失败：$watertime", e)
                            skippedCount++
                            continue
                        }
                        
                        val startMillis = date.time
                        // 设置30分钟的持续时间（打卡事件通常时间较短）
                        val endMillis = startMillis + 30 * 60 * 1000
                        
                        // 构建事件标题，包含状态信息
                        val eventTitle = buildString {
                            append(eqname)
                            if (isdone == "1") {
                                append(" (已完成)")
                            } else {
                                append(" (未完成)")
                            }
                        }
                        
                        // 构建详细描述信息
                        val description = buildString {
                            append("打卡信息：\n")
                            if (bh.isNotEmpty()) append("- 事件ID: $bh\n")
                            append("- 设备位置: $eqno\n")
                            append("- 打卡时间: $watertime\n")
                            append("- 完成状态: ${if (isdone == "1") "已完成" else "未完成"}\n")
                            if (fromtype.isNotEmpty()) append("- 来源类型: $fromtype\n")
                        }
                        
                        // 使用事件ID进行精确匹配，防止重复
                        Log.d(TAG, "   - 🔍 检查是否存在重复事件...")
                        val existing = CalendarHelper.findExistingEvent(applicationContext, eventTitle, startMillis, bh)
                        if (existing == null) {
                            Log.d(TAG, "   - ➕ 准备添加新事件到日历")
                            CalendarHelper.insertEvent(
                                applicationContext, 
                                calId, 
                                eventTitle, 
                                startMillis, 
                                endMillis, 
                                description, 
                                bh // 传递唯一标识用于后续匹配
                            )
                            insertedCount++
                            Log.d(TAG, "   - ✅ 成功创建日历事件: $eventTitle ($watertime) - $eqno")
                        } else {
                            skippedCount++
                            Log.d(TAG, "   - ⏩ 跳过重复事件: $eventTitle ($watertime)")
                        }
                    } else {
                        Log.d(TAG, "   - ⚠️ 跳过：时间字段为空")
                        skippedCount++
                    }
                } catch (e: Exception) {
                    skippedCount++
                    errors++
                    Log.e(TAG, "   - ❌ 处理单个事件失败", e)
                }
            } else {
                Log.d(TAG, "   - ⚠️ 跳过：无效的JSON对象")
                skippedCount++
                errors++
            }
        }
        
        // 输出详细的统计结果
        Log.d(TAG, "📊 日历事件处理统计:")
        Log.d(TAG, "   - 总计处理: ${weeklyData.length()} 条数据")
        Log.d(TAG, "   - 成功创建: ${insertedCount} 个事件")
        Log.d(TAG, "   - 跳过事件: ${skippedCount} 个事件")
        Log.d(TAG, "   - 处理错误: ${errors} 个错误")
        Log.d(TAG, "💡 处理总结：成功${insertedCount}条，失败${skippedCount}条，总计${insertedCount+skippedCount}条")
    }
}