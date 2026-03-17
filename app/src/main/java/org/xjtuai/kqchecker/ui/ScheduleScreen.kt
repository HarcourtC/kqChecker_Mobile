package org.xjtuai.kqchecker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.xjtuai.kqchecker.model.HomeworkRecord
import org.xjtuai.kqchecker.model.ScheduleItem

import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xjtuai.kqchecker.repository.HomeworkRepository
import org.xjtuai.kqchecker.repository.RepositoryProvider
import org.xjtuai.kqchecker.sync.WriteCalendar
import org.xjtuai.kqchecker.util.CalendarHelper
import org.xjtuai.kqchecker.util.ScheduleParser
import org.xjtuai.kqchecker.util.ScheduleTimeHelper
import org.xjtuai.kqchecker.util.WorkManagerHelper
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
    val weeklyCleaner = remember { RepositoryProvider.getWeeklyCleaner() }
    val homeworkRepository = remember { HomeworkRepository(context) }
    var scheduleItems by remember { mutableStateOf(emptyList<ScheduleItem>()) }
    var homeworkRecords by remember { mutableStateOf(emptyList<HomeworkRecord>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCourse by remember { mutableStateOf<ScheduleItem?>(null) }
    var homeworkDraft by remember { mutableStateOf<HomeworkDraft?>(null) }
    var pendingCalendarHomework by remember { mutableStateOf<HomeworkRecord?>(null) }
    var pendingManualCalendarSync by remember { mutableStateOf(false) }

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

    lateinit var startCalendarSyncPipeline: () -> Unit

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        val pendingRecord = pendingCalendarHomework
        val pendingSync = pendingManualCalendarSync
        pendingCalendarHomework = null
        pendingManualCalendarSync = false
        if (granted) {
            if (pendingRecord != null) {
                writeHomeworkToCalendar(pendingRecord)
            }
            if (pendingSync) {
                startCalendarSyncPipeline()
            }
        } else {
            if (pendingRecord != null) {
                onPostEvent("Homework saved, but calendar permission was denied.")
            }
            if (pendingSync) {
                onPostEvent("Calendar permission denied.")
            }
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

    startCalendarSyncPipeline = {
        onPostEvent("Starting forced calendar sync pipeline...")
        scope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    repository.refreshWeeklyData()
                }
                if (refreshed == null) {
                    onPostEvent("Step 1/3 failed: unable to refresh latest weekly data")
                    return@launch
                }
                onPostEvent("Step 1/3 done: latest weekly data refreshed")

                val cleanedOk = withContext(Dispatchers.IO) {
                    weeklyCleaner.generateCleanedWeekly()
                }
                if (!cleanedOk) {
                    onPostEvent("Step 2/3 failed: cleaned weekly generation failed")
                    return@launch
                }
                onPostEvent("Step 2/3 done: cleaned weekly regenerated")

                val request = OneTimeWorkRequestBuilder<WriteCalendar>().build()
                val workId = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).enqueue(request)
                    request.id
                }
                onPostEvent("Step 3/3 started: writing to calendar...")
                WorkManagerHelper.observeWorkStatus(
                    context = context,
                    workId = workId,
                    onStatusChange = { _, statusMessage ->
                        onPostEvent(statusMessage)
                    },
                    taskName = "Calendar write"
                )
            } catch (e: Exception) {
                onPostEvent("Calendar write error: ${e.message ?: e.toString()}")
            }
        }
    }

    fun requestCalendarWriteForSync() {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            startCalendarSyncPipeline()
        } else {
            pendingManualCalendarSync = true
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
        backgroundColor = MaterialTheme.colors.background,
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = { requestCalendarWriteForSync() },
                    backgroundColor = MaterialTheme.colors.secondary,
                    contentColor = MaterialTheme.colors.onSecondary
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "写入日历")
                }
                FloatingActionButton(onClick = { refreshData(true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(30.dp)) // Placeholder for period numbers
                val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
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
            Spacer(modifier = Modifier.height(8.dp))

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
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                // Horizontal line
                                Divider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 59.dp),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Light vibrant palette for the side bar
    val lightPalette = listOf(
        androidx.compose.ui.graphics.Color(0xFF03A9F4), // Light Blue
        androidx.compose.ui.graphics.Color(0xFF9C27B0), // Purple
        androidx.compose.ui.graphics.Color(0xFF009688), // Teal
        androidx.compose.ui.graphics.Color(0xFFFFB300), // Amber
        androidx.compose.ui.graphics.Color(0xFFFF5722), // Deep Orange
        androidx.compose.ui.graphics.Color(0xFF8BC34A), // Light Green
        androidx.compose.ui.graphics.Color(0xFF607D8B)  // Blue Grey
    )

    // Dark vibrant palette for the side bar
    val darkPalette = listOf(
        androidx.compose.ui.graphics.Color(0xFF4FC3F7),
        androidx.compose.ui.graphics.Color(0xFFBA68C8),
        androidx.compose.ui.graphics.Color(0xFF4DB6AC),
        androidx.compose.ui.graphics.Color(0xFFFFD54F),
        androidx.compose.ui.graphics.Color(0xFFFF8A65),
        androidx.compose.ui.graphics.Color(0xFFAED581),
        androidx.compose.ui.graphics.Color(0xFF90A4AE)
    )

    val colorPalette = if (isDark) darkPalette else lightPalette
    val baseColor = colorPalette[item.colorIndex % colorPalette.size]
    
    // Background is a very washed out version of the base color
    val backgroundColor = baseColor.copy(alpha = if (isDark) 0.15f else 0.1f)
    val textColor = MaterialTheme.colors.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .offset(y = topOffset)
            .clickable(onClick = onClick),
        backgroundColor = backgroundColor,
        elevation = 0.dp, // Flat design
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Colored left indicator bar
            Spacer(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(baseColor)
            )
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                if (homeworkCount > 0) {
                    Surface(
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "作业",
                            color = MaterialTheme.colors.onSecondary,
                            style = MaterialTheme.typography.overline,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp
                        )
                    }
                }
                Text(
                    text = item.courseName,
                    style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                if (item.location.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.location,
                        style = MaterialTheme.typography.overline.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (draft.photoPath == null) "上传作业照片" else "重新选择照片")
                }
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "作业图片",
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
