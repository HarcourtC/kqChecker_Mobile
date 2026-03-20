package org.xjtuai.kqchecker.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.ui.components.AppButton
import org.xjtuai.kqchecker.ui.components.InfoCard

@Composable
fun SettingsScreen(
    onPostEvent: (String) -> Unit,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var eventLogEnabled by remember { mutableStateOf(prefs.getBoolean("event_log_enabled", true)) }
    var hideWeekendsEnabled by remember { mutableStateOf(prefs.getBoolean("hide_weekends", false)) }

    fun toggleEventLog(enabled: Boolean) {
        prefs.edit().putBoolean("event_log_enabled", enabled).apply()
        eventLogEnabled = enabled
        onPostEvent("事件日志已${if (enabled) "开启" else "关闭"}")
    }

    fun toggleHideWeekends(enabled: Boolean) {
        prefs.edit().putBoolean("hide_weekends", enabled).apply()
        hideWeekendsEnabled = enabled
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        InfoCard(title = "界面设置") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "显示事件日志面板",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = eventLogEnabled,
                    onCheckedChange = { checked ->
                        toggleEventLog(checked)
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "课表隐藏周末",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hideWeekendsEnabled,
                    onCheckedChange = { checked ->
                        toggleHideWeekends(checked)
                    }
                )
            }
        }

        InfoCard(title = "应用更新") {
            Text("检查是否有新版本，并在应用内下载并安装。")
            Spacer(modifier = Modifier.height(10.dp))
            AppButton(
                text = if (isCheckingUpdate) "正在检查更新..." else "检查更新",
                onClick = {
                    if (!isCheckingUpdate) onCheckUpdate()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (isCheckingUpdate) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("正在向 GitHub 检查最新版本...")
                }
            }
        }
    }
}
