package org.xjtuai.kqchecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xjtuai.kqchecker.model.ScheduleItem

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.util.ScheduleParser

@Composable
fun ScheduleScreen(
    onPostEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { RepositoryProvider.getWeeklyRepository() }
    var scheduleItems by remember { mutableStateOf(emptyList<ScheduleItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshData(force: Boolean) {
        scope.launch {
            isLoading = true
            try {
                // Run IO in background
                val data = withContext(Dispatchers.IO) {
                    repository.getWeeklyData(force)
                }
                if (data != null && data.success) {
                    val parsed = ScheduleParser.parse(data.data)
                    scheduleItems = parsed
                    onPostEvent("Schedule loaded (${parsed.size} items)")
                } else {
                    onPostEvent("Failed to load schedule")
                }
            } catch (e: Exception) {
                onPostEvent("Error loading schedule: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData(false)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { refreshData(true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header: Days of week
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colors.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(30.dp)) // Placeholder for period numbers
                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                days.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
            Divider()

            if (isLoading && scheduleItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Timetable body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Period numbers and horizontal lines
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (i in 1..12) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp) // Height per period
                            ) {
                                // Period Number
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = i.toString(),
                                        style = MaterialTheme.typography.caption,
                                        color = Color.Gray
                                    )
                                }
                                // Horizontal line
                                Divider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 59.dp), 
                                    color = Color.LightGray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // Schedule Items
                    Row(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.width(30.dp))
                        for (day in 1..7) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(720.dp) // 12 * 60.dp
                            ) {
                                // Filter items for this day
                                val daysItems = scheduleItems.filter { it.dayOfWeek == day }
                                daysItems.forEach { item ->
                                    ScheduleItemCard(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemCard(item: ScheduleItem) {
    // 60.dp per period
    val height = (item.endPeriod - item.startPeriod + 1) * 60.dp
    val topOffset = (item.startPeriod - 1) * 60.dp
    
    // Choose color based on index
    val cardColor = when (item.colorIndex) {
        0 -> Color(0xFFE3F2FD) // Blue
        1 -> Color(0xFFF3E5F5) // Purple
        2 -> Color(0xFFE8F5E9) // Green
        3 -> Color(0xFFFFF3E0) // Orange
        4 -> Color(0xFFFFEBEE) // Red
        5 -> Color(0xFFE0F7FA) // Cyan
        6 -> Color(0xFFF1F8E9) // Light Green
        else -> Color(0xFFFFF8E1) // Amber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(1.dp)
            .offset(y = topOffset),
        backgroundColor = cardColor,
        elevation = 2.dp,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (item.location.isNotEmpty()) {
                Text(
                    text = "@${item.location}",
                    style = MaterialTheme.typography.overline.copy(fontSize = 8.sp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
