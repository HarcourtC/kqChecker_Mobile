package org.xjtuai.kqchecker.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.BuildConfig
import org.xjtuai.kqchecker.auth.AuthRequiredException
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard

/**
 * 工具屏幕
 * 提供调试、同步、日历写入等开发工具功能
 */
@Composable
fun ToolsScreen(
    onPostEvent: (String) -> Unit,
    onLoginRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDebugBuild = BuildConfig.DEBUG
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }

    val weeklyRepository = remember { RepositoryProvider.getWeeklyRepository() }
    val waterListRepository = remember { RepositoryProvider.getWaterListRepository() }
    val weeklyCleaner = remember { RepositoryProvider.getWeeklyCleaner() }
    val tokenManager = remember { org.xjtuai.kqchecker.auth.TokenManager(context) }

    val activityScoreKey = "group_activity_score_tenths"
    val maxScoreTenths = 30
    var activityScoreTenths by remember {
        mutableStateOf(
            prefs.getInt(activityScoreKey, 0).coerceIn(0, maxScoreTenths)
        )
    }

    suspend fun postEventOnMain(message: String) {
        withContext(Dispatchers.Main) {
            onPostEvent(message)
        }
    }

    fun handleAuthRequired(tag: String, message: String, error: AuthRequiredException) {
        Log.w(tag, message, error)
        scope.launch {
            postEventOnMain("$message。")
            onLoginRequired()
        }
    }

    fun launchRepositoryAction(
        startMessage: String,
        successMessage: String,
        emptyMessage: String,
        tag: String,
        action: suspend () -> Any?
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                postEventOnMain(startMessage)
                val result = action()
                postEventOnMain(if (result != null) successMessage else emptyMessage)
            } catch (e: AuthRequiredException) {
                handleAuthRequired(tag, "需要重新登录", e)
            } catch (e: Exception) {
                postEventOnMain("$tag 失败: ${e.message ?: e.toString()}")
            }
        }
    }

    fun formatActivityScore(scoreTenths: Int): String {
        val safeScore = scoreTenths.coerceIn(0, maxScoreTenths)
        val integerPart = safeScore / 10
        val decimalPart = safeScore % 10
        return if (decimalPart == 0) integerPart.toString() else "$integerPart.$decimalPart"
    }

    fun updateActivityScore(newScoreTenths: Int) {
        val clamped = newScoreTenths.coerceIn(0, maxScoreTenths)
        activityScoreTenths = clamped
        prefs.edit().putInt(activityScoreKey, clamped).apply()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        InfoCard(title = "工具") {
            Text(
                text = if (isDebugBuild) "同步、诊断与开发工具" else "账号与同步工具",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f)
            )
        }

        InfoCard(title = "账号") {
            AppButton(text = "退出登录", onClick = {
                onPostEvent("正在清除登录状态...")
                try {
                    tokenManager.clear()
                    onPostEvent("已清除登录状态")
                } catch (e: Exception) {
                    onPostEvent("清除登录状态失败: ${e.message}")
                }
            })
        }

        InfoCard(title = "集体活动分助手") {
            Text(
                text = "${formatActivityScore(activityScoreTenths)}/3",
                style = MaterialTheme.typography.h5,
                color = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "重置", onClick = {
                updateActivityScore(0)
                onPostEvent("集体活动分已重置：0/3")
            })

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "+0.3", onClick = {
                updateActivityScore(activityScoreTenths + 3)
                onPostEvent("集体活动分已更新：${formatActivityScore(activityScoreTenths)}/3")
            })

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(text = "+0.6", onClick = {
                updateActivityScore(activityScoreTenths + 6)
                onPostEvent("集体活动分已更新：${formatActivityScore(activityScoreTenths)}/3")
            })
        }

        InfoCard(title = "同步") {
            AppButton(text = "同步课表", onClick = {
                launchRepositoryAction(
                    startMessage = "正在同步课表...",
                    successMessage = "课表同步完成",
                    emptyMessage = "课表同步失败（空结果）",
                    tag = "课表同步"
                ) {
                    weeklyRepository.refreshWeeklyData()
                }
            })

            if (isDebugBuild) {
                Spacer(modifier = Modifier.height(8.dp))

                AppButton(text = "同步 API2 水课单（调试）", onClick = {
                    launchRepositoryAction(
                        startMessage = "正在执行 API2 同步...",
                        successMessage = "API2 数据同步完成",
                        emptyMessage = "API2 同步失败（空结果）",
                        tag = "API2 同步"
                    ) {
                        waterListRepository.refreshWaterListData()
                    }
                })
            }
        }

        if (isDebugBuild) {
            InfoCard(title = "调试与原始数据") {
                AppButton(text = "拉取课表原始响应", onClick = {
                    scope.launch {
                        onPostEvent("正在拉取课表原始响应...")
                        try {
                            val rawResp = withContext(Dispatchers.IO) {
                                weeklyRepository.fetchWeeklyRawFromApi()
                            }
                            if (!rawResp.isNullOrBlank()) {
                                onPostEvent("拉取成功（${rawResp.length} bytes）")
                                onPostEvent(rawResp.take(500) + "...")
                            } else {
                                onPostEvent("原始响应为空")
                            }
                        } catch (e: Exception) {
                            onPostEvent("拉取失败: ${e.message}")
                        }
                    }
                })
                Spacer(modifier = Modifier.height(8.dp))

                AppButton(text = "输出 weekly.json 预览", onClick = {
                    scope.launch {
                        onPostEvent("正在输出 weekly 文件...")
                        try {
                            val previews = withContext(Dispatchers.IO) {
                                weeklyRepository.getWeeklyFilePreviews()
                            }
                            if (previews.isEmpty()) {
                                onPostEvent("没有可输出的 weekly 文件")
                            }
                            for (p in previews) {
                                onPostEvent("文件: ${p.name} (${p.size} bytes)")
                                onPostEvent(p.preview.take(200) + "...")
                            }
                        } catch (e: Exception) {
                            onPostEvent("输出失败: ${e.message}")
                        }
                    }
                })

                Spacer(modifier = Modifier.height(8.dp))

                AppButton(text = "生成 cleaned weekly（调试）", onClick = {
                    scope.launch {
                        onPostEvent("正在生成 cleaned weekly...")
                        try {
                            val ok = withContext(Dispatchers.IO) {
                                weeklyCleaner.generateCleanedWeekly()
                            }
                            if (ok) {
                                onPostEvent("生成成功")
                            } else {
                                onPostEvent("生成失败")
                            }
                        } catch (e: Exception) {
                            onPostEvent("异常: ${e.message}")
                        }
                    }
                })

                Spacer(modifier = Modifier.height(8.dp))

                AppButton(text = "输出 API2 日志预览", onClick = {
                    scope.launch {
                        onPostEvent("正在输出 API2 调试文件...")
                        try {
                            val previews = withContext(Dispatchers.IO) {
                                waterListRepository.getApi2FilePreviews()
                            }
                            if (previews.isEmpty()) {
                                onPostEvent("没有可输出的 API2 文件")
                            }
                            for (p in previews) {
                                onPostEvent("文件: ${p.name} (${p.size} bytes)")
                                onPostEvent(p.preview.take(300) + "...")
                            }
                        } catch (e: Exception) {
                            onPostEvent("输出 API2 调试文件失败: ${e.message}")
                        }
                    }
                })
            }
        }
    }
}
