package org.xjtuai.kqchecker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.xjtuai.kqchecker.model.HomeworkRecord
import org.xjtuai.kqchecker.model.ScheduleItem

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.repository.HomeworkRepository
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.util.CalendarHelper
import org.xjtuai.kqchecker.util.ScheduleParser
import org.xjtuai.kqchecker.util.ScheduleTimeHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class HomeworkDraft(
    val title: String = "",
    val dueDateMillis: Long = Calendar.getInstance().timeInMillis,
    val photoPath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Composable
fun ScheduleScreen(
    onPostEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { RepositoryProvider.getWeeklyRepository() }
    val homeworkRepository = remember { HomeworkRepository(context) }
    var scheduleItems by remember { mutableStateOf(emptyList<ScheduleItem>()) }
    var homeworkRecords by remember { mutableStateOf(emptyList<HomeworkRecord>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCourse by remember { mutableStateOf<ScheduleItem?>(null) }
    var homeworkDraft by remember { mutableStateOf<HomeworkDraft?>(null) }
    var pendingCalendarHomework by remember { mutableStateOf<HomeworkRecord?>(null) }

    fun loadHomeworkRecords() {
        scope.launch(Dispatchers.IO) {
            val records = homeworkRepository.getAllRecords()
            withContext(Dispatchers.Main) {
                homeworkRecords = records
            }
        }
    }

    fun writeHomeworkToCalendar(record: HomeworkRecord) {
        scope.launch(Dispatchers.IO) {
            try {
                val calendarId = CalendarHelper.getDefaultCalendarId(context)
                if (calendarId == null) {
                    withContext(Dispatchers.Main) {
                        onPostEvent("Homework saved, but no calendar is available.")
                    }
                    return@launch
                }

                val timeRange = ScheduleTimeHelper.buildClassTimeRange(
                    dueDateMillis = record.dueDateMillis,
                    startPeriod = record.startPeriod,
                    endPeriod = record.endPeriod
                )
                if (timeRange == null) {
                    withContext(Dispatchers.Main) {
                        onPostEvent("Homework saved, but class time could not be resolved.")
                    }
                    return@launch
                }

                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                CalendarHelper.upsertEvent(
                    context = context,
                    calendarId = calendarId,
                    title = "${record.courseName} 作业: ${record.title}",
                    startMillis = timeRange.first,
                    endMillis = timeRange.second,
                    description = buildString {
                        append("课程内提交作业\n")
                        append("课程: ${record.courseName}\n")
                        append("提交日期: ${formatter.format(record.dueDateMillis)}\n")
                        if (record.teacher.isNotBlank()) append("教师: ${record.teacher}\n")
                        if (record.photoPath != null) append("附件: 已保存照片\n")
                    },
                    eventId = "homework_${record.id}",
                    location = record.location.ifBlank { null }
                )
                withContext(Dispatchers.Main) {
                    onPostEvent("Homework calendar event updated: ${record.title}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onPostEvent("Failed to update homework calendar event: ${e.message}")
                }
            }
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        val pendingRecord = pendingCalendarHomework
        pendingCalendarHomework = null
        if (granted && pendingRecord != null) {
            writeHomeworkToCalendar(pendingRecord)
        } else if (!granted && pendingRecord != null) {
            onPostEvent("Homework saved, but calendar permission was denied.")
        }
    }

    fun requestCalendarWrite(record: HomeworkRecord) {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            writeHomeworkToCalendar(record)
        } else {
            pendingCalendarHomework = record
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val copiedPath = homeworkRepository.copyImageToAppStorage(uri)
            withContext(Dispatchers.Main) {
                if (copiedPath != null) {
                    homeworkDraft = homeworkDraft?.copy(photoPath = copiedPath)
                    onPostEvent("Homework photo attached.")
                } else {
                    onPostEvent("Failed to attach homework photo.")
                }
            }
        }
    }

    fun openHomeworkEditor(course: ScheduleItem) {
        selectedCourse = course
        val existingRecord = homeworkRecords.firstOrNull {
            it.courseName == course.courseName &&
                it.dayOfWeek == course.dayOfWeek &&
                it.startPeriod == course.startPeriod &&
                it.endPeriod == course.endPeriod
        }
        homeworkDraft = if (existingRecord == null) {
            HomeworkDraft()
        } else {
            HomeworkDraft(
                title = existingRecord.title,
                dueDateMillis = existingRecord.dueDateMillis,
                photoPath = existingRecord.photoPath,
                createdAtMillis = existingRecord.createdAtMillis
            )
        }
    }

    fun saveHomework(course: ScheduleItem, draft: HomeworkDraft) {
        val title = draft.title.trim()
        if (title.isEmpty()) {
            onPostEvent("Please enter a homework title.")
            return
        }
        scope.launch(Dispatchers.IO) {
            val existingRecord = homeworkRecords.firstOrNull {
                it.courseName == course.courseName &&
                    it.dayOfWeek == course.dayOfWeek &&
                    it.startPeriod == course.startPeriod &&
                    it.endPeriod == course.endPeriod
            }
            val record = if (existingRecord == null) {
                homeworkRepository.createRecord(
                    courseName = course.courseName,
                    location = course.location,
                    teacher = course.teacher,
                    dayOfWeek = course.dayOfWeek,
                    startPeriod = course.startPeriod,
                    endPeriod = course.endPeriod,
                    title = title,
                    dueDateMillis = draft.dueDateMillis,
                    photoPath = draft.photoPath
                )
            } else {
                existingRecord.copy(
                    title = title,
                    dueDateMillis = draft.dueDateMillis,
                    photoPath = draft.photoPath,
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
            val saved = homeworkRepository.saveRecord(record)
            val updatedRecords = homeworkRepository.getAllRecords()
            withContext(Dispatchers.Main) {
                homeworkRecords = updatedRecords
                selectedCourse = null
                homeworkDraft = null
                onPostEvent("Homework saved for ${course.courseName}")
                requestCalendarWrite(saved)
            }
        }
    }

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
        loadHomeworkRecords()
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
                                    val homeworkCount = homeworkRecords.count {
                                        it.courseName == item.courseName &&
                                            it.dayOfWeek == item.dayOfWeek &&
                                            it.startPeriod == item.startPeriod &&
                                            it.endPeriod == item.endPeriod
                                    }
                                    ScheduleItemCard(
                                        item = item,
                                        homeworkCount = homeworkCount,
                                        onClick = { openHomeworkEditor(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCourse != null && homeworkDraft != null) {
        HomeworkDialog(
            course = selectedCourse!!,
            draft = homeworkDraft!!,
            onDismiss = {
                selectedCourse = null
                homeworkDraft = null
            },
            onDraftChange = { homeworkDraft = it },
            onPickPhoto = { imagePickerLauncher.launch("image/*") },
            onSave = { saveHomework(selectedCourse!!, it) }
        )
    }
}

@Composable
fun ScheduleItemCard(
    item: ScheduleItem,
    homeworkCount: Int,
    onClick: () -> Unit
) {
    // 60.dp per period
    val height = ((item.endPeriod - item.startPeriod + 1) * 60).dp
    val topOffset = ((item.startPeriod - 1) * 60).dp
    
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
            .offset(y = topOffset)
            .clickable(onClick = onClick),
        backgroundColor = cardColor,
        elevation = 2.dp,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            if (homeworkCount > 0) {
                Surface(
                    color = MaterialTheme.colors.secondary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "$homeworkCount 作业",
                        color = Color.White,
                        style = MaterialTheme.typography.overline,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeworkDialog(
    course: ScheduleItem,
    draft: HomeworkDraft,
    onDismiss: () -> Unit,
    onDraftChange: (HomeworkDraft) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: (HomeworkDraft) -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val previewBitmap = remember(draft.photoPath) {
        draft.photoPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "记录作业")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${course.courseName} 第${course.startPeriod}-${course.endPeriod}节提交",
                    style = MaterialTheme.typography.body2
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onDraftChange(draft.copy(title = it)) },
                    label = { Text("作业标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = draft.dueDateMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val selected = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onDraftChange(draft.copy(dueDateMillis = selected.timeInMillis))
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("截止日期: ${formatter.format(draft.dueDateMillis)}")
                }
                Button(
                    onClick = onPickPhoto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (draft.photoPath == null) "上传作业照片" else "重新选择照片")
                }
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Homework photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                } else if (draft.photoPath != null) {
                    Text(
                        text = File(draft.photoPath).name,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存并写入日历")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
