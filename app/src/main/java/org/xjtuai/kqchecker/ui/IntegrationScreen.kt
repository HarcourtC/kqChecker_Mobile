package org.xjtuai.kqchecker.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.xjtuai.kqchecker.ui.components.InfoCard

@Composable
fun IntegrationScreen(
    onPostEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE) }
    var eventLogEnabled by remember { mutableStateOf(prefs.getBoolean("event_log_enabled", true)) }

    fun toggleEventLog(enabled: Boolean) {
        prefs.edit().putBoolean("event_log_enabled", enabled).apply()
        eventLogEnabled = enabled
        onPostEvent("事件日志已${if (enabled) "开启" else "关闭"}")
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
        }
    }
}
