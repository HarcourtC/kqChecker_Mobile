package org.xjtuai.kqchecker.sync

import android.app.Service
import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 可选的前台轮询服务。服务周期性地把一次性 WorkRequest 送到 WorkManager 去执行 Api2AttendanceQueryWorker。
 * 启动时会显示常驻通知，停止时取消。
 */
class Api2PollingService : Service() {
    companion object {
        const val CHANNEL_ID = "api2_polling_channel"
        const val EXTRA_INTERVAL_MIN = "interval_minutes"
        const val ACTION_STOP = "org.xjtuai.kqchecker.ACTION_STOP_POLLING"
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val intervalMin = intent?.getIntExtra(EXTRA_INTERVAL_MIN, 5) ?: 5

        val notif = buildNotification(intervalMin)
        startForeground(10010, notif)

        if (!running) {
            running = true
            scope.launch {
                try {
                    while (running) {
                        // enqueue a single work request to perform Api2 query pass
                        val req = OneTimeWorkRequestBuilder<Api2AttendanceQueryWorker>().build()
                        WorkManager.getInstance(applicationContext).enqueue(req)

                        // sleep for interval
                        delay(TimeUnit.MINUTES.toMillis(intervalMin.toLong()))
                    }
                } catch (_: Exception) {
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, "API2 Polling Service", NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(ch)
            }
        } catch (_: Exception) {}
    }

    private fun buildNotification(intervalMin: Int): Notification {
        val title = "kqChecker: 前台轮询已启用"
        val text = "每 $intervalMin 分钟执行一次 API2 查询。请注意会增加电量消耗。"

        // 构建停止服务的 PendingIntent（点击动作将发送到 Service，自行处理 ACTION_STOP）
        val stopIntent = Intent(this, Api2PollingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 10011, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止轮询", stopPending)
            .build()
    }
}
