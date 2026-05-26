package com.cityray.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

class MacroDroidReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CityRayActions.MACRODROID_START_EDGE -> startOverlay(context, toggle = false)
            CityRayActions.MACRODROID_TOGGLE_EDGE -> startOverlay(context, toggle = true)
            CityRayActions.MACRODROID_HIDE_EDGE -> context.stopService(Intent(context, EdgeOverlayService::class.java))
            CityRayActions.MACRODROID_OPEN_LAST_PAIR -> SplitLauncher.launchPair(context, PairRepository(context).lastPairIndex())
            CityRayActions.MACRODROID_OPEN_CONTROL -> openControlCenter(context)
            CityRayActions.MACRODROID_LAUNCH_PAIR -> {
                val index = intent.getIntExtra(CityRayActions.EXTRA_PAIR_INDEX, 0).coerceIn(0, 6)
                SplitLauncher.launchPair(context, index)
            }
            CityRayActions.MACRODROID_LAUNCH_DOCK -> {
                val index = intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0).coerceIn(0, 9)
                SplitLauncher.launchDockShortcut(context, index)
            }
            CityRayActions.MACRODROID_OVERLAY_PERMISSION -> SplitLauncher.openOverlaySettings(context)
        }
    }

    private fun startOverlay(context: Context, toggle: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Allow Display over other apps for Geely Edge Pro.", Toast.LENGTH_LONG).show()
            SplitLauncher.openOverlaySettings(context)
            return
        }
        PairRepository(context).settings().edit().putBoolean("overlay_enabled", true).apply()
        val service = Intent(context, EdgeOverlayService::class.java).apply {
            if (toggle) action = CityRayActions.ACTION_TOGGLE_OVERLAY else action = CityRayActions.ACTION_START_OVERLAY
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
    }

    private fun openControlCenter(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_OPEN_CONTROL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}
