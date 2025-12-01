package org.example.kqchecker.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.util.*

object CalendarHelper {
    fun findExistingEvent(context: Context, title: String, startMillis: Long, eventId: String? = null): Long? {
        // 构建查询条件：优先使用eventId进行精确匹配，否则使用标题和时间范围匹配
        val selection: String
        val selectionArgs: Array<String>
        
        if (!eventId.isNullOrEmpty()) {
            // 如果提供了eventId（对应bh字段），优先使用它作为唯一标识
            selection = "${CalendarContract.Events.EVENT_LOCATION} LIKE ?"
            selectionArgs = arrayOf("%ID:$eventId%")
        } else {
            // 否则使用标题和开始时间进行匹配，但允许一定的时间误差（前后5分钟）
            val timeWindow = 5 * 60 * 1000 // 5分钟时间窗口（毫秒）
            val startTime = (startMillis - timeWindow).toString()
            val endTime = (startMillis + timeWindow).toString()
            selection = "(${CalendarContract.Events.TITLE} LIKE ?) AND (${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
            selectionArgs = arrayOf("%$title%", startTime, endTime)
        }
        
        val uri: Uri = CalendarContract.Events.CONTENT_URI
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(CalendarContract.Events._ID), selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getLong(0)
            } else null
        } finally {
            cursor?.close()
        }
    }

    fun insertEvent(context: Context, calendarId: Long, title: String, startMillis: Long, endMillis: Long, description: String? = null, eventId: String? = null): Long? {
        // 构建位置信息，包含原始地点描述和eventId（用于匹配）
        val locationBuilder = StringBuilder()
        if (!description.isNullOrEmpty()) {
            locationBuilder.append(description)
        }
        if (!eventId.isNullOrEmpty()) {
            // 在位置信息中添加ID标记，用于后续匹配
            if (locationBuilder.isNotEmpty()) {
                locationBuilder.append(" | ")
            }
            locationBuilder.append("ID:$eventId")
        }
        val location = if (locationBuilder.isNotEmpty()) locationBuilder.toString() else null
        
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!description.isNullOrEmpty()) put(CalendarContract.Events.DESCRIPTION, description)
            if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val uri = CalendarContract.Calendars.CONTENT_URI
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return null
    }
}
