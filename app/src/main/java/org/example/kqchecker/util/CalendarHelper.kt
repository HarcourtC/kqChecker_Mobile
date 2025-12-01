package org.example.kqchecker.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.util.*

object CalendarHelper {
    fun findExistingEvent(context: Context, title: String, startMillis: Long): Long? {
        val selection = "(${CalendarContract.Events.TITLE} = ?) AND (${CalendarContract.Events.DTSTART} = ?)"
        val selectionArgs = arrayOf(title, startMillis.toString())
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

    fun insertEvent(context: Context, calendarId: Long, title: String, startMillis: Long, endMillis: Long, description: String? = null): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!description.isNullOrEmpty()) put(CalendarContract.Events.DESCRIPTION, description)
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
