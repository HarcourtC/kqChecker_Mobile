package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogDisplay(events: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var isExpanded by remember { mutableStateOf(true) }

    // Auto-scroll to bottom when new events are added
    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colors.surface,
        elevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    if (isExpanded) it.height(200.dp) else it.wrapContentHeight()
                }
                .border(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.15f))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.03f))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "事件日志",
                    color = MaterialTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colors.primary
                )
            }

            if (isExpanded) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(events) { event ->
                        Text(
                            text = "> $event",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
