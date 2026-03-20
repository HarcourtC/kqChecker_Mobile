package org.xjtuai.kqchecker.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.util.*

object CalendarHelper {
    fun findExistingEvent(
        context: Context,
        title: String,
        startMillis: Long,
        eventId: String? = null
    ): Long? {
        // 构建查询条件：优先使用eventId进行精确匹配，否则使用标题和时间范围匹配
        val selection: String
        val selectionArgs: Array<String>

        if (!eventId.isNullOrEmpty()) {
            // Check description (new location for ID) OR location (legacy location for ID)
            selection = "(${CalendarContract.Events.DESCRIPTION} LIKE ?) OR (${CalendarContract.Events.EVENT_LOCATION} LIKE ?)"
            selectionArgs = arrayOf("%ID:$eventId%", "%ID:$eventId%")
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
            cursor = context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Events._ID),
                selection,
                selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    fun insertEvent(
        context: Context,
        calendarId: Long,
        title: String,
        startMillis: Long,
        endMillis: Long,
        description: String? = null,
        eventId: String? = null,
        location: String? = null
    ): Long? {
        val values = createEventValues(
            calendarId,
            title,
            startMillis,
            endMillis,
            description,
            eventId,
            location
        )
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    fun upsertEvent(
        context: Context,
        calendarId: Long,
        title: String,
        startMillis: Long,
        endMillis: Long,
        description: String? = null,
        eventId: String,
        location: String? = null
    ): Long? {
        val existingEventRowId = findExistingEvent(context, title, startMillis, eventId)
        val values = createEventValues(
            calendarId,
            title,
            startMillis,
            endMillis,
            description,
            eventId,
            location
        )
        return if (existingEventRowId != null) {
            val eventUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendPath(existingEventRowId.toString())
                .build()
            context.contentResolver.update(eventUri, values, null, null)
            existingEventRowId
        } else {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        }
    }

    private fun createEventValues(
        calendarId: Long,
        title: String,
        startMillis: Long,
        endMillis: Long,
        description: String? = null,
        eventId: String? = null,
        location: String? = null
    ): ContentValues {
        val descriptionBuilder = StringBuilder()
        if (!description.isNullOrEmpty()) {
            descriptionBuilder.append(description)
        }
        if (!eventId.isNullOrEmpty()) {
            if (descriptionBuilder.isNotEmpty()) descriptionBuilder.append("\n\n")
            // Append standard ID marker for findExistingEvent
            descriptionBuilder.append("ID:$eventId")
        }
        val finalDesc = if (descriptionBuilder.isNotEmpty()) descriptionBuilder.toString() else null

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (finalDesc != null) put(CalendarContract.Events.DESCRIPTION, finalDesc)
            if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
        }
    }

    fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
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
