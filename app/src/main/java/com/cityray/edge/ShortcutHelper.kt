package com.cityray.edge

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast

object ShortcutHelper {
    fun pinAppShortcut(context: Context, app: AppEntry) {
        if (app.packageName.isBlank() || !app.installed) {
            Toast.makeText(context, "App is not installed.", Toast.LENGTH_LONG).show()
            return
        }
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_LAUNCH_APP
            putExtra(CityRayActions.EXTRA_PACKAGE_NAME, app.packageName)
            putExtra(CityRayActions.EXTRA_APP_LABEL, app.label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        pinShortcut(context, "cityray_app_${app.packageName}", app.label, launchIntent)
    }

    private fun pinShortcut(context: Context, id: String, label: String, launchIntent: Intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(ShortcutManager::class.java)
            if (manager.isRequestPinShortcutSupported) {
                val callback = PendingIntent.getBroadcast(
                    context,
                    id.hashCode(),
                    Intent("${context.packageName}.SHORTCUT_PINNED"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ).intentSender
                val shortcut = ShortcutInfo.Builder(context, id)
                    .setShortLabel(label.take(18).ifBlank { "Edge Pro" })
                    .setLongLabel(label.ifBlank { "Geely Edge Pro Shortcut" })
                    .setIcon(Icon.createWithResource(context, R.drawable.cityray_edge_logo))
                    .setIntent(launchIntent)
                    .build()
                manager.requestPinShortcut(shortcut, callback)
                Toast.makeText(context, "Shortcut request sent to launcher.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val fallback = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_cityray_edge))
        }
        context.sendBroadcast(fallback)
        Toast.makeText(context, "Shortcut sent to launcher.", Toast.LENGTH_SHORT).show()
    }
}
