package org.xjtuai.kqchecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.xjtuai.kqchecker.util.VersionInfo

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primary
) {
    val buttonShape = CircleShape
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = buttonShape,
        elevation = ButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Transparent,
            contentColor = MaterialTheme.colors.onPrimary
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.9f),
                            backgroundColor.copy(alpha = 1.0f)
                        )
                    ),
                    shape = buttonShape
                )
                .padding(vertical = 14.dp, horizontal = 16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.button
            )
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    Card(
        elevation = if (isDark) 4.dp else 10.dp,
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .clip(cardShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(MaterialTheme.colors.surface, MaterialTheme.colors.background)
                        } else {
                            listOf(Color.White, MaterialTheme.colors.surface)
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colors.primary.copy(alpha = if (isDark) 0.1f else 0.05f),
                    shape = cardShape
                )
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
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
    currentVersion: String,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
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
                    text = "当前版本: $currentVersion",
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
                onClick = onUpdate,
                enabled = !isUpdating
            ) {
                Text(if (isUpdating) "下载中..." else "下载并安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
