package com.cityray.edge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = PairRepository(context).settings()
        if (prefs.getBoolean("overlay_boot", true) && Settings.canDrawOverlays(context)) {
            val service = Intent(context, EdgeOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
        } else if (prefs.getBoolean("boot_notification", true)) {
            showBootNotification(context)
        }
    }

    private fun showBootNotification(context: Context) {
        val channelId = "cityray_edge_boot"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Geely Edge Pro shortcuts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pending = PendingIntent.getActivity(
            context,
            301,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_cityray_edge)
            .setContentTitle("Geely Edge Pro")
            .setContentText("Tap to open Edge Pro.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(77, notification)
    }
}
