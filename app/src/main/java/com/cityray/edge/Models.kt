package com.cityray.edge

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val installed: Boolean = true
)

data class DockShortcut(
    val label: String,
    val packageName: String,
    val lastUsed: Long = 0L
)

data class QuickSwapSlot(
    val key: String,
    val title: String,
    val label: String,
    val packageName: String,
    val lastUsed: Long = 0L
)

data class LaunchRecord(
    val label: String,
    val packageName: String,
    val timestamp: Long,
    val source: String? = null
)

object CityRayActions {
    const val ACTION_OPEN_CONTROL = "com.cityray.edge.OPEN_CONTROL"
    const val ACTION_HIDE_OVERLAY = "com.cityray.edge.HIDE_OVERLAY"
    const val ACTION_STOP_OVERLAY = "com.cityray.edge.STOP_OVERLAY"
    const val ACTION_START_OVERLAY = "com.cityray.edge.START_OVERLAY"
    const val ACTION_TOGGLE_OVERLAY = "com.cityray.edge.TOGGLE_OVERLAY"
    const val ACTION_LAUNCH_APP = "com.cityray.edge.LAUNCH_APP"
    const val ACTION_LAUNCH_DOCK = "com.cityray.edge.LAUNCH_DOCK"
    const val ACTION_LAUNCH_QUICK = "com.cityray.edge.LAUNCH_QUICK"
    const val ACTION_EDIT_DOCK = "com.cityray.edge.EDIT_DOCK"
    const val ACTION_DOCK_OPTIONS = "com.cityray.edge.DOCK_OPTIONS"
    const val ACTION_EDIT_QUICK = "com.cityray.edge.EDIT_QUICK"
    const val EXTRA_DOCK_INDEX = "dock_index"
    const val EXTRA_QUICK_KEY = "quick_key"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_APP_LABEL = "app_label"

    const val MACRODROID_START_EDGE = "com.cityray.edge.macrodroid.START_EDGE"
    const val MACRODROID_TOGGLE_EDGE = "com.cityray.edge.macrodroid.TOGGLE_EDGE"
    const val MACRODROID_HIDE_EDGE = "com.cityray.edge.macrodroid.HIDE_EDGE"
    const val MACRODROID_OPEN_CONTROL = "com.cityray.edge.macrodroid.OPEN_CONTROL"
    const val MACRODROID_LAUNCH_DOCK = "com.cityray.edge.macrodroid.LAUNCH_DOCK"
    const val MACRODROID_LAUNCH_QUICK = "com.cityray.edge.macrodroid.LAUNCH_QUICK"
    const val MACRODROID_OVERLAY_PERMISSION = "com.cityray.edge.macrodroid.OVERLAY_PERMISSION"
}

class EdgeRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("cityray_edge", Context.MODE_PRIVATE)

    fun dockShortcuts(): MutableList<DockShortcut> {
        val raw = prefs.getString("dock_shortcuts", null) ?: return defaultDockShortcuts().toMutableList().also { saveDockShortcuts(it) }
        val result = mutableListOf<DockShortcut>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result += DockShortcut(
                label = item.optString("label"),
                packageName = item.optString("packageName"),
                lastUsed = item.optLong("lastUsed", 0L)
            )
        }
        while (result.size < 10) result += emptyDockShortcut(result.size)
        return result
    }

    fun saveDockShortcut(index: Int, shortcut: DockShortcut) {
        val list = dockShortcuts()
        while (list.size <= index) list += emptyDockShortcut(list.size)
        list[index] = shortcut
        saveDockShortcuts(list)
        EdgeWidgetProvider.updateAll(context)
    }

    fun clearDockShortcut(index: Int) {
        val list = dockShortcuts()
        if (index in list.indices) {
            list[index] = emptyDockShortcut(index)
            saveDockShortcuts(list)
            EdgeWidgetProvider.updateAll(context)
        }
    }

    fun markDockUsed(index: Int) {
        val list = dockShortcuts()
        if (index in list.indices) {
            list[index] = list[index].copy(lastUsed = System.currentTimeMillis())
            saveDockShortcuts(list)
        }
    }

    fun quickSlot(key: String): QuickSwapSlot {
        val title = if (key == QUICK_MEDIA) "MEDIA APP" else "NAV APP"
        return QuickSwapSlot(
            key = key,
            title = title,
            label = prefs.getString("${key}_label", "").orEmpty(),
            packageName = prefs.getString("${key}_package", "").orEmpty(),
            lastUsed = prefs.getLong("${key}_last_used", 0L)
        )
    }

    fun saveQuickSlot(key: String, app: AppEntry) {
        prefs.edit()
            .putString("${key}_label", app.label)
            .putString("${key}_package", app.packageName)
            .putLong("${key}_last_used", System.currentTimeMillis())
            .apply()
        EdgeWidgetProvider.updateAll(context)
    }

    fun clearQuickSlot(key: String) {
        prefs.edit()
            .remove("${key}_label")
            .remove("${key}_package")
            .remove("${key}_last_used")
            .apply()
        EdgeWidgetProvider.updateAll(context)
    }

    fun addLaunch(record: LaunchRecord) {
        val records = launchHistory().toMutableList()
        records.add(0, record)
        val array = JSONArray()
        records.distinctBy { it.packageName + it.timestamp }.take(60).forEach {
            array.put(JSONObject().apply {
                put("label", it.label)
                put("packageName", it.packageName)
                put("timestamp", it.timestamp)
                put("source", it.source ?: "")
            })
        }
        prefs.edit().putString("launch_history", array.toString()).apply()
    }

    fun launchHistory(): List<LaunchRecord> {
        val raw = prefs.getString("launch_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<LaunchRecord>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result += LaunchRecord(
                label = item.optString("label"),
                packageName = item.optString("packageName"),
                timestamp = item.optLong("timestamp"),
                source = item.optString("source").ifBlank { null }
            )
        }
        return result
    }

    fun settings() = prefs

    private fun saveDockShortcuts(list: List<DockShortcut>) {
        val array = JSONArray()
        list.forEach { shortcut ->
            array.put(JSONObject().apply {
                put("label", shortcut.label)
                put("packageName", shortcut.packageName)
                put("lastUsed", shortcut.lastUsed)
            })
        }
        prefs.edit().putString("dock_shortcuts", array.toString()).apply()
    }

    private fun defaultDockShortcuts(): List<DockShortcut> = List(10) { emptyDockShortcut(it) }

    private fun emptyDockShortcut(index: Int) = DockShortcut("Pick App ${index + 1}", "")

    companion object {
        const val QUICK_NAV = "quick_nav"
        const val QUICK_MEDIA = "quick_media"
    }
}

object AppScanner {
    fun launcherApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            AppEntry(
                label = info.loadLabel(pm).toString(),
                packageName = activityInfo.packageName,
                icon = info.loadIcon(pm),
                installed = true
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, packageName: String, fallback: String = packageName): String {
        if (packageName.isBlank()) return fallback
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            fallback
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun commonBatteryList(context: Context): List<AppEntry> {
        val base = launcherApps(context)
        val wanted = listOf("cityray", "edge", "hur", "headunit", "waze", "android auto", "youtube", "vanced", "revanced", "spotify", "cx file")
        return base.filter { entry -> wanted.any { "${entry.label} ${entry.packageName}".lowercase().contains(it) } }
            .ifEmpty { base.take(8) }
    }
}

object EdgeLauncher {
    private const val TAG = "CityRayEdge"

    fun launchDockShortcut(context: Context, index: Int) {
        val repo = EdgeRepository(context)
        val shortcut = repo.dockShortcuts().getOrNull(index) ?: return
        if (shortcut.packageName.isBlank()) {
            toast(context, "Pick an app for this EDGE slot first.")
            return
        }
        if (launchPackage(context, shortcut.packageName, "EDGE Shortcut")) {
            repo.markDockUsed(index)
        }
    }

    fun launchQuickSwap(context: Context, key: String) {
        val repo = EdgeRepository(context)
        val slot = repo.quickSlot(key)
        if (slot.packageName.isBlank()) {
            toast(context, "Pick ${slot.title} first.")
            return
        }
        launchPackage(context, slot.packageName, slot.title)
    }

    fun launchPackage(context: Context, packageName: String, source: String? = null): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast(context, "Not installed: $packageName")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            context.startActivity(intent)
            EdgeRepository(context).addLaunch(
                LaunchRecord(AppScanner.labelFor(context, packageName), packageName, System.currentTimeMillis(), source)
            )
            Log.i(TAG, "Launched $packageName source=$source")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed for $packageName", e)
            toast(context, "Could not open ${AppScanner.labelFor(context, packageName)}")
            false
        }
    }

    fun performNavigation(context: Context, action: String): Boolean {
        val service = CityRayAccessibilityService.instance
        if (service == null) {
            toast(context, "Enable Accessibility Assist to use Back, Home, or Recents.")
            return false
        }
        return when (action) {
            "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            else -> false
        }
    }

    fun smartBack(context: Context): Boolean {
        val service = CityRayAccessibilityService.instance
        if (service != null && service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) {
            return true
        }
        return openPreviousApp(context)
    }

    fun openPreviousApp(context: Context): Boolean {
        val target = previousAppCandidate(context)
        if (target == null) {
            toast(context, "Open an app from EDGE first, or enable Usage Access.")
            return false
        }
        return launchPackage(context, target.packageName, "Previous App")
    }

    private fun previousAppCandidate(context: Context): LaunchRecord? {
        val own = context.packageName
        val usage = runCatching { recentUsage(context) }.getOrDefault(emptyList())
            .firstOrNull { isUserReturnCandidate(it.packageName, own) }
        if (usage != null) return usage
        return EdgeRepository(context).launchHistory()
            .firstOrNull { isUserReturnCandidate(it.packageName, own) }
    }

    private fun isUserReturnCandidate(packageName: String, ownPackage: String): Boolean {
        val p = packageName.lowercase()
        if (packageName.isBlank() || packageName == ownPackage) return false
        if (p.contains("launcher") || p.contains("systemui")) return false
        return true
    }

    fun recentUsage(context: Context): List<LaunchRecord> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 1000L * 60L * 60L * 24L
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.lastTimeUsed > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .take(25)
            .map { LaunchRecord(AppScanner.labelFor(context, it.packageName), it.packageName, it.lastTimeUsed, null) }
    }

    fun openUsageSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
