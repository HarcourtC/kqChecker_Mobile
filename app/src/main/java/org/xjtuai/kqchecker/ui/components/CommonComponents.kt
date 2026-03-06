package org.xjtuai.kqchecker.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.util.VersionInfo

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor)
    ) {
        Text(text = text)
    }
}

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        elevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

/**
 * 版本更新对话框
 * 当检测到新版本时显示
 */
@Composable
fun UpdateDialog(
    versionInfo: VersionInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("发现新版本: v${versionInfo.latestVersion}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "当前版本: ${versionInfo.latestVersion}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (versionInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = "更新内容:",
                        style = MaterialTheme.typography.subtitle2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = versionInfo.releaseNotes,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 打开 GitHub Release 页面
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(versionInfo.releaseUrl))
                    context.startActivity(intent)
                    onUpdate()
                }
            ) {
                Text("更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
