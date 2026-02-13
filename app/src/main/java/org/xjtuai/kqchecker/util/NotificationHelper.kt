package org.xjtuai.kqchecker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import org.xjtuai.kqchecker.MainActivity

/**
 * Unified notification creation and management
 */
object NotificationHelper {
  private const val TAG = "NotificationHelper"
  private const val NO_ATTENDANCE_CHANNEL_ID = "api2_no_attendance_channel"
  private const val NO_ATTENDANCE_CHANNEL_NAME = "API2 Attendance Reminder"
  private const val DEADLINE_CHANNEL_ID = "competition_deadline_channel"
  private const val DEADLINE_CHANNEL_NAME = "Competition Deadline Reminder"

  fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        NO_ATTENDANCE_CHANNEL_ID,
        NO_ATTENDANCE_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Alerts when no attendance record is detected"
      }
      val deadlineChannel = NotificationChannel(
              DEADLINE_CHANNEL_ID,
              DEADLINE_CHANNEL_NAME,
              NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
          description = "Alerts when a competition deadline is approaching"
      }
      nm.createNotificationChannels(listOf(channel, deadlineChannel))
      Log.d(TAG, "Notification channels created")
    }
  }

  /**
   * Sends a notification for competition deadline
   */
  fun sendDeadlineNotification(
      context: Context,
      title: String,
      deadlineDate: String,
      url: String
  ) {
      try {
          createNotificationChannels(context)

          val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          val launchIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          val pendingIntent = PendingIntent.getActivity(
              context, url.hashCode(), launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )

          val notification = NotificationCompat.Builder(context, DEADLINE_CHANNEL_ID)
              .setSmallIcon(android.R.drawable.ic_dialog_info)
              .setContentTitle("竞赛截止提醒: $title")
              .setContentText("截止日期: $deadlineDate (明天)")
              .setContentIntent(pendingIntent)
              .setAutoCancel(true)
              .setPriority(NotificationCompat.PRIORITY_DEFAULT)
              .build()

          nm.notify(url.hashCode(), notification)
          Log.d(TAG, "Deadline notification sent: $title")
      } catch (e: Exception) {
          Log.e(TAG, "Failed to send deadline notification", e)
      }
  }

  /**
   * Sends a notification when no attendance record is found
   */
  fun sendNoAttendanceNotification(
    context: Context,
    key: String,
    date: String,
    subject: String? = null,
    timeDisplay: String? = null,
    location: String? = null
  ) {
    try {
      createNotificationChannels(context)

      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT
      )

      val title = subject ?: "No attendance record matched"
      val content = listOfNotNull(timeDisplay, location).joinToString(" @ ")
      val contentText = if (content.isBlank()) "Date: $date" else content

      val notification = NotificationCompat.Builder(context, NO_ATTENDANCE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

      val notificationId = (key + date).hashCode()
      nm.notify(notificationId, notification)
      Log.d(TAG, "Notification sent: key=$key, date=$date, subject=$subject")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send notification", e)
    }
  }

  fun cancelNotification(context: Context, key: String, date: String) {
    try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.cancel((key + date).hashCode())
      Log.d(TAG, "Notification canceled for key=$key, date=$date")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to cancel notification", e)
    }
  }

  fun cancelAllNotifications(context: Context) {
    try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.cancelAll()
      Log.d(TAG, "All notifications canceled")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to cancel all notifications", e)
    }
  }
}
