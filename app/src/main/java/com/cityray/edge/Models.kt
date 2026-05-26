package com.cityray.edge

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val installed: Boolean = true
)

data class FavoritePair(
    val name: String,
    val leftLabel: String,
    val leftPackage: String,
    val rightLabel: String,
    val rightPackage: String,
    val showInOverlay: Boolean = true,
    val showInWidget: Boolean = true,
    val lastUsed: Long = 0L
)

data class DockShortcut(
    val label: String,
    val packageName: String,
    val lastUsed: Long = 0L
)

data class LaunchRecord(
    val label: String,
    val packageName: String,
    val timestamp: Long,
    val pairName: String? = null
)

object CityRayActions {
    const val ACTION_LAUNCH_PAIR = "com.cityray.edge.LAUNCH_PAIR"
    const val ACTION_LAUNCH_LAST = "com.cityray.edge.LAUNCH_LAST"
    const val ACTION_OPEN_CONTROL = "com.cityray.edge.OPEN_CONTROL"
    const val ACTION_HIDE_OVERLAY = "com.cityray.edge.HIDE_OVERLAY"
    const val ACTION_STOP_OVERLAY = "com.cityray.edge.STOP_OVERLAY"
    const val ACTION_START_OVERLAY = "com.cityray.edge.START_OVERLAY"
    const val ACTION_TOGGLE_OVERLAY = "com.cityray.edge.TOGGLE_OVERLAY"
    const val ACTION_OPEN_OVERLAY_PERMISSION = "com.cityray.edge.OPEN_OVERLAY_PERMISSION"
    const val ACTION_LAUNCH_APP = "com.cityray.edge.LAUNCH_APP"
    const val ACTION_LAUNCH_DOCK = "com.cityray.edge.LAUNCH_DOCK"
    const val ACTION_EDIT_DOCK = "com.cityray.edge.EDIT_DOCK"
    const val ACTION_DOCK_OPTIONS = "com.cityray.edge.DOCK_OPTIONS"
    const val EXTRA_PAIR_INDEX = "pair_index"
    const val EXTRA_DOCK_INDEX = "dock_index"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_APP_LABEL = "app_label"

    const val MACRODROID_START_EDGE = "com.cityray.edge.macrodroid.START_EDGE"
    const val MACRODROID_TOGGLE_EDGE = "com.cityray.edge.macrodroid.TOGGLE_EDGE"
    const val MACRODROID_HIDE_EDGE = "com.cityray.edge.macrodroid.HIDE_EDGE"
    const val MACRODROID_OPEN_LAST_PAIR = "com.cityray.edge.macrodroid.OPEN_LAST_PAIR"
    const val MACRODROID_OPEN_CONTROL = "com.cityray.edge.macrodroid.OPEN_CONTROL"
    const val MACRODROID_LAUNCH_PAIR = "com.cityray.edge.macrodroid.LAUNCH_PAIR"
    const val MACRODROID_LAUNCH_DOCK = "com.cityray.edge.macrodroid.LAUNCH_DOCK"
    const val MACRODROID_OVERLAY_PERMISSION = "com.cityray.edge.macrodroid.OVERLAY_PERMISSION"
}

class PairRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("cityray_edge", Context.MODE_PRIVATE)

    fun favorites(): MutableList<FavoritePair> {
        val raw = prefs.getString("favorites", null) ?: return defaultFavorites().toMutableList().also { saveFavorites(it) }
        val result = mutableListOf<FavoritePair>()
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result += FavoritePair(
                name = item.optString("name"),
                leftLabel = item.optString("leftLabel"),
                leftPackage = item.optString("leftPackage"),
                rightLabel = item.optString("rightLabel"),
                rightPackage = item.optString("rightPackage"),
                showInOverlay = item.optBoolean("showInOverlay", true),
                showInWidget = item.optBoolean("showInWidget", true),
                lastUsed = item.optLong("lastUsed", 0L)
            )
        }
        if (isOldPresetDefaults(result)) {
            return defaultFavorites().toMutableList().also { saveFavorites(it) }
        }
        return result
    }

    fun saveFavorite(index: Int, pair: FavoritePair) {
        val list = favorites()
        while (list.size <= index) list += emptyPair("Custom Pair ${list.size + 1}")
        list[index] = pair
        saveFavorites(list)
        SplitWidgetProvider.updateAll(context)
    }

    fun markUsed(index: Int) {
        val list = favorites()
        if (index in list.indices) {
            val used = list[index].copy(lastUsed = System.currentTimeMillis())
            list[index] = used
            prefs.edit().putInt("last_pair_index", index).apply()
            saveFavorites(list)
            SplitWidgetProvider.updateAll(context)
        }
    }

    fun lastPairIndex(): Int = prefs.getInt("last_pair_index", 0)

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
    }

    fun clearDockShortcut(index: Int) {
        val list = dockShortcuts()
        if (index in list.indices) {
            list[index] = emptyDockShortcut(index)
            saveDockShortcuts(list)
        }
    }

    fun markDockUsed(index: Int) {
        val list = dockShortcuts()
        if (index in list.indices) {
            list[index] = list[index].copy(lastUsed = System.currentTimeMillis())
            saveDockShortcuts(list)
        }
    }

    fun saveFavorites(list: List<FavoritePair>) {
        val array = JSONArray()
        list.forEach { pair ->
            array.put(JSONObject().apply {
                put("name", pair.name)
                put("leftLabel", pair.leftLabel)
                put("leftPackage", pair.leftPackage)
                put("rightLabel", pair.rightLabel)
                put("rightPackage", pair.rightPackage)
                put("showInOverlay", pair.showInOverlay)
                put("showInWidget", pair.showInWidget)
                put("lastUsed", pair.lastUsed)
            })
        }
        prefs.edit().putString("favorites", array.toString()).apply()
    }

    fun saveDockShortcuts(list: List<DockShortcut>) {
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

    fun addLaunch(record: LaunchRecord) {
        val records = launchHistory().toMutableList()
        records.add(0, record)
        val array = JSONArray()
        records.distinctBy { it.packageName + it.timestamp }.take(60).forEach {
            array.put(JSONObject().apply {
                put("label", it.label)
                put("packageName", it.packageName)
                put("timestamp", it.timestamp)
                put("pairName", it.pairName ?: "")
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
                pairName = item.optString("pairName").ifBlank { null }
            )
        }
        return result
    }

    fun settings() = prefs

    private fun defaultFavorites(): List<FavoritePair> = listOf(
        emptyPair("Split 1"),
        emptyPair("Split 2"),
        emptyPair("Split 3"),
        emptyPair("Split 4"),
        emptyPair("Split 5"),
        emptyPair("Split 6"),
        FavoritePair("Last Split", "", "", "", "", showInWidget = true)
    )

    private fun emptyPair(name: String) = FavoritePair(name, "Select top app", "", "Select bottom app", "")

    private fun defaultDockShortcuts(): List<DockShortcut> = List(10) { emptyDockShortcut(it) }

    private fun emptyDockShortcut(index: Int) = DockShortcut("Pick App ${index + 1}", "")

    private fun isOldPresetDefaults(list: List<FavoritePair>): Boolean {
        if (list.size < 4) return false
        val names = list.take(4).map { it.name }
        val packages = list.take(4).flatMap { listOf(it.leftPackage, it.rightPackage) }
        return names == listOf("Waze + YouTube", "Waze + Spotify", "Waze + Vanced", "Spotify + Waze") &&
            packages.any { it == "com.waze" || it == "com.google.android.youtube" }
    }
}

object AppScanner {
    private val known = listOf(
        "Waze" to "com.waze",
        "YouTube" to "com.google.android.youtube",
        "Spotify" to "com.spotify.music",
        "HUR / Headunit Reloaded" to "gb.xxy.hr",
        "Google Maps" to "com.google.android.apps.maps",
        "Cx File Explorer" to "com.cxinventor.file.explorer"
    )

    fun launcherApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            AppEntry(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm),
                installed = true
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

        return installed.distinctBy { it.packageName.ifBlank { it.label } }
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

    fun isVideoApp(label: String, packageName: String): Boolean {
        val value = "$label $packageName".lowercase()
        return listOf("youtube", "vanced", "revanced", "video", "player").any(value::contains)
    }

    fun commonBatteryList(context: Context): List<AppEntry> {
        val base = launcherApps(context)
        val wanted = listOf("cityray", "hur", "headunit", "waze", "android auto", "youtube", "vanced", "revanced", "applauncher", "cx file")
        return base.filter { entry -> wanted.any { "${entry.label} ${entry.packageName}".lowercase().contains(it) } }
            .ifEmpty { base.take(8) }
    }
}

object SplitLauncher {
    private const val TAG = "CityRayEdge"
    private const val KEY_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"
    private const val KEY_SPLIT_SCREEN_CREATE_MODE = "android:activity.splitScreenCreateMode"
    private const val SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT = 0
    const val MODE_NORMAL = "normal"
    const val MODE_FREEFORM = "freeform"
    const val MODE_ACCESSIBILITY = "accessibility"

    fun launchPair(context: Context, index: Int) {
        val repo = PairRepository(context)
        val favorites = repo.favorites()
        val realIndex = if (index == 6) repo.lastPairIndex() else index
        val pair = favorites.getOrNull(realIndex) ?: return
        launchPair(context, pair, realIndex)
    }

    fun launchDockShortcut(context: Context, index: Int) {
        val repo = PairRepository(context)
        val shortcut = repo.dockShortcuts().getOrNull(index) ?: return
        if (shortcut.packageName.isBlank()) {
            toast(context, "Pick an app for this EDGE slot first.")
            return
        }
        if (launchPackage(context, shortcut.packageName)) {
            repo.markDockUsed(index)
        }
    }

    fun launchAppInSplitSlot(context: Context, packageName: String, top: Boolean): Boolean {
        if (packageName.isBlank()) {
            toast(context, "Pick an app first.")
            return false
        }
        val label = AppScanner.labelFor(context, packageName)
        val launched = launchPackage(context, packageName)
        if (launched) {
            PairRepository(context).addLaunch(LaunchRecord(label, packageName, System.currentTimeMillis(), if (top) "Top live split" else "Bottom live split"))
        }
        return launched
    }

    fun launchPair(context: Context, pair: FavoritePair, index: Int? = null) {
        val repo = PairRepository(context)
        if (pair.leftPackage.isBlank() || pair.rightPackage.isBlank()) {
            toast(context, "Choose both apps before launching this pair.")
            return
        }
        val missing = listOf(pair.leftPackage, pair.rightPackage).filterNot { AppScanner.isInstalled(context, it) }
        if (missing.isNotEmpty()) {
            toast(context, "Not installed: ${missing.joinToString()}")
            return
        }
        maybeWarnVideo(context, pair)
        if (repo.settings().getString("clean_mode", "off") == "kill") {
            safeClose(context, pair.leftPackage)
            safeClose(context, pair.rightPackage)
        }
        val mode = repo.settings().getString("split_mode", MODE_FREEFORM) ?: MODE_FREEFORM
        val delay = repo.settings().getLong("launch_delay", 800L)
        if (mode == MODE_ACCESSIBILITY) {
            launchPairWithAccessibilitySplit(context, repo, pair, index, delay)
            return
        }
        if (mode == MODE_FREEFORM) {
            launchPairSmart(context, repo, pair, index, delay)
            return
        }
        launchPairSequential(context, repo, pair, index, delay)
    }

    private fun launchPairSmart(
        context: Context,
        repo: PairRepository,
        pair: FavoritePair,
        index: Int?,
        delay: Long
    ) {
        if (launchPairWithPlatformSplit(context, repo, pair, index, delay)) return

        if (CityRayAccessibilityService.instance != null) {
            launchPairWithAccessibilitySplit(context, repo, pair, index, delay)
            return
        }

        launchPairSequential(context, repo, pair, index, delay)
    }

    private fun launchPairWithPlatformSplit(
        context: Context,
        repo: PairRepository,
        pair: FavoritePair,
        index: Int?,
        delay: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val topWarmup = context.packageManager.getLaunchIntentForPackage(pair.leftPackage) ?: return false
        val topFinal = context.packageManager.getLaunchIntentForPackage(pair.leftPackage) ?: return false
        val bottomFinal = context.packageManager.getLaunchIntentForPackage(pair.rightPackage) ?: return false
        return try {
            topWarmup.addCategory(Intent.CATEGORY_LAUNCHER)
            topWarmup.flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_TASK_ON_HOME
            context.startActivity(topWarmup)

            android.os.Handler(context.mainLooper).postDelayed({
                runCatching {
                    val home = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(home)
                }

                android.os.Handler(context.mainLooper).postDelayed({
                    try {
                        topFinal.addCategory(Intent.CATEGORY_LAUNCHER)
                        topFinal.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        bottomFinal.addCategory(Intent.CATEGORY_LAUNCHER)
                        bottomFinal.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK

                        val options = ActivityOptions.makeBasic().toBundle().apply {
                            putInt(KEY_LAUNCH_WINDOWING_MODE, SplitWindowMode.PRIMARY.raw)
                            putInt(KEY_SPLIT_SCREEN_CREATE_MODE, SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT)
                        }
                        context.startActivities(arrayOf(bottomFinal, topFinal), options)
                        val now = System.currentTimeMillis()
                        repo.addLaunch(LaunchRecord(pair.leftLabel, pair.leftPackage, now, pair.name))
                        repo.addLaunch(LaunchRecord(pair.rightLabel, pair.rightPackage, now, pair.name))
                        if (index != null) repo.markUsed(index)
                        Log.i(TAG, "Platform top/bottom split launched for ${pair.leftPackage} + ${pair.rightPackage}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Platform split handoff failed; trying backup path.", e)
                        if (CityRayAccessibilityService.instance != null) {
                            launchPairWithAccessibilitySplit(context, repo, pair, index, delay)
                        } else {
                            launchPairSequential(context, repo, pair, index, delay)
                        }
                    }
                }, 500L)
            }, delay.coerceAtLeast(500L))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Platform split warmup failed.", e)
            false
        }
    }

    private fun launchPairSequential(
        context: Context,
        repo: PairRepository,
        pair: FavoritePair,
        index: Int?,
        delay: Long
    ) {
        launchPackage(context, pair.leftPackage, forceNewTask = true)
        android.os.Handler(context.mainLooper).postDelayed({
            launchPackage(context, pair.rightPackage, forceNewTask = true)
            val now = System.currentTimeMillis()
            repo.addLaunch(LaunchRecord(pair.leftLabel, pair.leftPackage, now, pair.name))
            repo.addLaunch(LaunchRecord(pair.rightLabel, pair.rightPackage, now, pair.name))
            if (index != null) repo.markUsed(index)
        }, delay)
    }

    private fun launchPairWithAccessibilitySplit(
        context: Context,
        repo: PairRepository,
        pair: FavoritePair,
        index: Int?,
        delay: Long
    ) {
        launchPackage(context, pair.leftPackage)
        android.os.Handler(context.mainLooper).postDelayed({
            val service = CityRayAccessibilityService.instance
            if (service == null) {
                toast(context, "Enable Accessibility Assisted Mode, then try this split mode again.")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val accepted = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                Log.i(TAG, "Accessibility split-screen global action accepted=$accepted")
                if (!accepted) toast(context, "HU blocked the Accessibility split-screen action.")
            }
            android.os.Handler(context.mainLooper).postDelayed({
                launchPackage(
                    context,
                    pair.rightPackage,
                    forceNewTask = true,
                    splitWindowMode = null
                )
                val now = System.currentTimeMillis()
                repo.addLaunch(LaunchRecord(pair.leftLabel, pair.leftPackage, now, pair.name))
                repo.addLaunch(LaunchRecord(pair.rightLabel, pair.rightPackage, now, pair.name))
                if (index != null) repo.markUsed(index)
            }, delay)
        }, delay)
    }

    fun launchPackage(
        context: Context,
        packageName: String,
        side: SplitSide? = null,
        forceNewTask: Boolean = false,
        clearTask: Boolean = false,
        splitWindowMode: SplitWindowMode? = null
    ): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast(context, "Not installed: $packageName")
            return false
        }
        val baseFlags = if (forceNewTask) {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        intent.addFlags(baseFlags)
        return try {
            val options = when {
                side != null -> landscapeSplitOptions(context, side, splitWindowMode)
                splitWindowMode != null -> windowingModeOptions(splitWindowMode)
                else -> null
            }
            if (splitWindowMode != null && options == null) {
                toast(context, "HU blocked hidden split mode. Try Accessibility Assisted mode.")
                return false
            }
            if (options != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            Log.i(TAG, "Launched $packageName side=$side forceNewTask=$forceNewTask clearTask=$clearTask windowMode=$splitWindowMode")
            PairRepository(context).addLaunch(LaunchRecord(AppScanner.labelFor(context, packageName), packageName, System.currentTimeMillis(), null))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launch failed for $packageName", e)
            toast(context, "Could not open $packageName")
            false
        }
    }

    private fun windowingModeOptions(splitWindowMode: SplitWindowMode): ActivityOptions? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return ActivityOptions.makeBasic().apply {
            if (!applyWindowingModeIfAvailable(this, splitWindowMode)) return null
        }
    }

    private fun landscapeSplitOptions(context: Context, side: SplitSide, splitWindowMode: SplitWindowMode?): ActivityOptions? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val bounds = displayBounds(context)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val mid = bounds.height() / 2
        val launchBounds = when (side) {
            SplitSide.TOP -> Rect(0, 0, bounds.width(), mid)
            SplitSide.BOTTOM -> Rect(0, mid, bounds.width(), bounds.height())
        }
        return ActivityOptions.makeBasic().apply {
            setLaunchBounds(launchBounds)
            applyWindowingModeIfAvailable(this, splitWindowMode)
        }
    }

    private fun applyWindowingModeIfAvailable(options: ActivityOptions, mode: SplitWindowMode?): Boolean {
        if (mode == null) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, mode.raw)
            true
        }.getOrElse {
            Log.d(TAG, "Launch windowing mode not available on this build: ${it.message}")
            false
        }
    }

    private fun displayBounds(context: Context): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    enum class SplitSide {
        TOP,
        BOTTOM
    }

    enum class SplitWindowMode(val raw: Int) {
        PRIMARY(3),
        SECONDARY(4),
        FREEFORM(5)
    }

    fun openHome(context: Context) {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun performNavigation(context: Context, action: String) {
        val service = CityRayAccessibilityService.instance
        if (service == null) {
            toast(context, "Enable Accessibility Assisted Mode to use Back/Home/Recents controls.")
            return
        }
        when (action) {
            "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            "split" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            }
        }
    }

    fun openPreviousApp(context: Context): Boolean {
        val target = previousAppCandidate(context)
        if (target == null) {
            toast(context, "Open an app from EDGE first, or enable Usage Access.")
            return false
        }
        return launchPackage(context, target.packageName)
    }

    fun closeLastOpenedApp(context: Context): Boolean {
        val target = previousAppCandidate(context)
        if (target == null) {
            toast(context, "No recent app found.")
            return false
        }
        safeClose(context, target.packageName)
        return true
    }

    private fun previousAppCandidate(context: Context): LaunchRecord? {
        val own = context.packageName
        val usage = runCatching { recentUsage(context) }.getOrDefault(emptyList())
            .firstOrNull { isUserReturnCandidate(it.packageName, own) }
        if (usage != null) return usage
        return PairRepository(context).launchHistory()
            .firstOrNull { isUserReturnCandidate(it.packageName, own) }
    }

    private fun isUserReturnCandidate(packageName: String, ownPackage: String): Boolean {
        val p = packageName.lowercase()
        if (packageName.isBlank() || packageName == ownPackage) return false
        if (p.contains("launcher") || p.contains("systemui")) return false
        return true
    }

    fun safeClose(context: Context, packageName: String) {
        if (isProtectedPackage(packageName)) {
            toast(context, "Blocked close for protected car/system package.")
            return
        }
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            toast(context, "Requested background close for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Could not close $packageName", e)
            toast(context, "Android blocked closing this app.")
        }
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

    private fun maybeWarnVideo(context: Context, pair: FavoritePair) {
        val prefs = PairRepository(context).settings()
        if (!prefs.getBoolean("video_warning", true) || prefs.getBoolean("video_warning_seen", false)) return
        if (AppScanner.isVideoApp(pair.leftLabel, pair.leftPackage) || AppScanner.isVideoApp(pair.rightLabel, pair.rightPackage)) {
            prefs.edit().putBoolean("video_warning_seen", true).apply()
            toast(context, "Use video apps only when parked or for passenger use.")
        }
    }

    private fun isProtectedPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        if (p.startsWith("com.geely") || p.startsWith("com.ecarx")) return true
        return listOf("systemui", "car", "camera", "parking", "hvac", "climate", "launcher").any { p.contains(it) }
    }

    private fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
