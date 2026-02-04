package org.xjtuai.kqchecker.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

/**
 * BootReceiver: 设备重启后检查是否需要自动重启前台轮询服务（基于 SharedPreferences 标记）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                val prefs: SharedPreferences = context.getSharedPreferences("kq_prefs", Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean("api2_foreground_enabled", false)
                if (enabled) {
                    try {
                        val svc = Intent(context, Api2PollingService::class.java)
                        // default interval read from prefs
                        val interval = prefs.getInt("api2_foreground_interval_min", 5)
                        svc.putExtra(Api2PollingService.EXTRA_INTERVAL_MIN, interval)
                        context.startForegroundService(svc)
                    } catch (e: Exception) {
                        Log.w("BootReceiver", "Failed to restart Api2PollingService on boot", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BootReceiver", "onReceive failed", e)
        }
    }
}
