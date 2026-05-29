package com.cityray.edge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var repo: EdgeRepository
    private var editingDockIndex = 0
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = EdgeRepository(this)
        requestNotificationPermission()
        if (!handleAction(intent)) showHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!handleAction(intent)) showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && repo.settings().getBoolean("overlay_enabled", true) && Settings.canDrawOverlays(this)) {
            startOverlay(showToast = false)
        }
    }

    private fun handleAction(intent: Intent?): Boolean {
        return when (intent?.action) {
            CityRayActions.ACTION_OPEN_CONTROL -> {
                showControlCenter()
                true
            }
            CityRayActions.ACTION_START_OVERLAY -> {
                repo.settings().edit().putBoolean("overlay_enabled", true).apply()
                startOverlay(showToast = false)
                finish()
                true
            }
            CityRayActions.ACTION_TOGGLE_OVERLAY -> {
                repo.settings().edit().putBoolean("overlay_enabled", true).apply()
                startOverlay(showToast = false)
                startService(Intent(this, EdgeOverlayService::class.java).setAction(CityRayActions.ACTION_TOGGLE_OVERLAY))
                finish()
                true
            }
            CityRayActions.ACTION_LAUNCH_DOCK -> {
                EdgeLauncher.launchDockShortcut(this, intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0))
                finish()
                true
            }
            CityRayActions.ACTION_LAUNCH_QUICK -> {
                EdgeLauncher.launchQuickSwap(this, intent.getStringExtra(CityRayActions.EXTRA_QUICK_KEY).orEmpty())
                finish()
                true
            }
            CityRayActions.ACTION_EDIT_DOCK -> {
                editingDockIndex = intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0).coerceIn(0, 9)
                showInstalledApps("dock:$editingDockIndex")
                true
            }
            CityRayActions.ACTION_DOCK_OPTIONS -> {
                editingDockIndex = intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0).coerceIn(0, 9)
                showDockPanel()
                window.decorView.post { showDockShortcutOptions(editingDockIndex) }
                true
            }
            CityRayActions.ACTION_EDIT_QUICK -> {
                showInstalledApps("quick:${intent.getStringExtra(CityRayActions.EXTRA_QUICK_KEY).orEmpty()}")
                true
            }
            CityRayActions.ACTION_LAUNCH_APP -> {
                val packageName = intent.getStringExtra(CityRayActions.EXTRA_PACKAGE_NAME).orEmpty()
                if (packageName.isNotBlank()) EdgeLauncher.launchPackage(this, packageName, "Launcher Shortcut")
                finish()
                true
            }
            else -> false
        }
    }

    private fun showHome() {
        showDockPanel()
    }

    private fun edgeHomeCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(22), dp(20), dp(22), dp(20))
        background = getDrawable(R.drawable.widget_background)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.cityray_edge_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(96), dp(96)))
        addView(TextView(this@MainActivity).apply {
            text = "EDGE"
            setTextColor(Color.WHITE)
            textSize = 29f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(10), 0, 0)
        })
        addView(TextView(this@MainActivity).apply {
            text = "CityRay floating dock"
            setTextColor(0xFF9EEFE8.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(18))
        })
    }

    private fun showDockPanel() {
        val root = screen("EDGE", "Fast shortcuts, quick swap, and floating dock controls.")
        root.gravity = Gravity.START
        root.setPadding(dp(22), dp(18), dp(22), dp(18))
        root.addView(edgeHomeCard(), LinearLayout.LayoutParams(dp(390), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dp(12))
        })
        root.addView(GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            addView(compactQuickTile(EdgeRepository.QUICK_NAV, R.drawable.tile_maps), compactTileParams())
            addView(compactQuickTile(EdgeRepository.QUICK_MEDIA, R.drawable.tile_spotify), compactTileParams())
            repo.dockShortcuts()
                .mapIndexed { index, shortcut -> index to shortcut }
                .filter { it.second.packageName.isNotBlank() }
                .forEach { (index, shortcut) -> addView(compactDockTile(index, shortcut), compactTileParams()) }
            addView(compactActionTile("", R.drawable.ic_edge_add) {
                showInstalledApps("dock:${firstEmptyDockIndex()}")
            }, compactTileParams())
        })
        root.addView(horizontal().apply {
            addView(compactActionTile("", R.drawable.tile_apps) { showInstalledApps(null) }, compactIconParams())
            addView(compactActionTile("", R.drawable.tile_settings) { showControlCenter() }, compactIconParams())
            addView(compactActionTile("", R.drawable.tile_phone) { showPermissions() }, compactIconParams())
        })
        root.addView(horizontal().apply {
            addView(compactActionTile("", R.drawable.cityray_edge_logo) { showQuickSettings() }, compactIconParams())
            addView(compactActionTile("", R.drawable.ic_edge_camera) { startActivity(Intent(this@MainActivity, EdgeCameraActivity::class.java)) }, compactIconParams())
            addView(compactActionTile("‹", R.drawable.tile_power) { EdgeLauncher.smartBack(this@MainActivity) }, compactIconParams())
        })
        setContentView(wrap(root, overlayColor = 0x99030509.toInt(), scroll = true))
    }

    private fun showSettingsPanel() {
        val root = screen("SETTINGS", "")
        root.addView(tileGrid().apply {
            addView(iconTile("EDGE", R.drawable.cityray_edge_logo) { showQuickSettings() }, tileParams())
            addView(iconTile("PERMIT", R.drawable.tile_phone) { showPermissions() }, tileParams())
            addView(iconTile("RUNNING", R.drawable.tile_settings) { showControlCenter() }, tileParams())
            addView(iconTile("BATTERY", R.drawable.tile_battery) { showBatteryHelp() }, tileParams())
            addView(iconTile("HELP", R.drawable.tile_help) { showHelp() }, tileParams())
            addView(iconTile("‹", R.drawable.tile_power) { showDockPanel() }, tileParams())
        })
        setContentView(wrap(root, overlayColor = 0xAA030509.toInt()))
    }

    private fun showInstalledApps(selectionTarget: String?) {
        val subtitle = when {
            selectionTarget == null -> "Choose any installed app to open or pin as a launcher shortcut."
            selectionTarget.startsWith("dock:") -> "Select any installed app for EDGE slot ${selectionTarget.substringAfter(":").toIntOrNull()?.plus(1) ?: ""}."
            selectionTarget == "quick:${EdgeRepository.QUICK_NAV}" -> "Select the NAV APP for Quick Swap."
            selectionTarget == "quick:${EdgeRepository.QUICK_MEDIA}" -> "Select the MEDIA APP for Quick Swap."
            else -> "Select app."
        }
        val root = screen("INSTALLED APPS", subtitle)
        val search = EditText(this).apply {
            hint = "Search app"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9AA6B8.toInt())
            textSize = 20f
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun render(query: String = "") {
            list.removeAllViews()
            AppScanner.launcherApps(this)
                .filter { query.isBlank() || "${it.label} ${it.packageName}".contains(query, ignoreCase = true) }
                .forEach { app -> list.addView(appRow(app, selectionTarget)) }
        }
        search.addTextChangedListener(SimpleTextWatcher { render(it) })
        root.addView(search, fullWidth())
        root.addView(list)
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        render()
        setContentView(wrap(root))
    }

    private fun appRow(app: AppEntry, selectionTarget: String?): View {
        val row = horizontal().apply {
            setPadding(12, 10, 12, 10)
            background = getDrawable(R.drawable.panel_background)
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(app.icon ?: getDrawable(R.drawable.ic_cityray_edge))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(62), dp(62)))
        row.addView(TextView(this).apply {
            text = "${app.label}\n${app.packageName.ifBlank { "No package selected" }}"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(16, 0, 16, 0)
        }, rowWeight())
        if (selectionTarget != null) {
            row.addView(bigButton("Select") {
                if (!app.installed) {
                    Toast.makeText(this, "Not installed", Toast.LENGTH_SHORT).show()
                    return@bigButton
                }
                when {
                    selectionTarget.startsWith("dock:") -> {
                        val index = selectionTarget.substringAfter(":").toIntOrNull()?.coerceIn(0, 9) ?: editingDockIndex
                        repo.saveDockShortcut(index, DockShortcut(app.label, app.packageName, System.currentTimeMillis()))
                        Toast.makeText(this, "EDGE slot ${index + 1} saved.", Toast.LENGTH_SHORT).show()
                    }
                    selectionTarget.startsWith("quick:") -> {
                        val key = selectionTarget.substringAfter(":")
                        repo.saveQuickSlot(key, app)
                        Toast.makeText(this, "${repo.quickSlot(key).title} saved.", Toast.LENGTH_SHORT).show()
                    }
                }
                showDockPanel()
            }, LinearLayout.LayoutParams(dp(150), dp(76)))
        } else {
            row.addView(bigButton("Open") { EdgeLauncher.launchPackage(this, app.packageName, "Installed Apps") }, LinearLayout.LayoutParams(dp(130), dp(76)))
            row.addView(bigButton("Pin") { ShortcutHelper.pinAppShortcut(this, app) }, LinearLayout.LayoutParams(dp(130), dp(76)))
        }
        return row.withMargins()
    }

    private fun showControlCenter() {
        val root = screen("RUNNING APPS", "")
        val recent = runCatching { EdgeLauncher.recentUsage(this) }.getOrDefault(emptyList())
        val records = if (recent.isNotEmpty()) recent.take(24) else repo.launchHistory().take(24)
        if (records.isEmpty()) {
            root.addView(body("Enable Usage Access to show recently used apps."))
            root.addView(bigButton("Usage Access") { EdgeLauncher.openUsageSettings(this) }, fullWidth())
        } else {
            records.forEach { root.addView(recordRow(it)) }
        }
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun recordRow(record: LaunchRecord): View {
        val row = horizontal().apply {
            setPadding(12, 10, 12, 10)
            background = getDrawable(R.drawable.panel_background)
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(appIcon(record.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        row.addView(TextView(this).apply {
            text = "${record.label}\n${record.packageName}\n${dateFormat.format(Date(record.timestamp))}${record.source?.let { " - $it" } ?: ""}"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), 0, 0, 0)
        }, rowWeight())
        return row.withMargins()
    }

    private fun showQuickSettings() {
        val root = screen("EDGE SETTINGS", "")
        val prefs = repo.settings()
        root.addView(switchRow("Enable Floating Edge Button", prefs.getBoolean("overlay_enabled", true)) {
            prefs.edit().putBoolean("overlay_enabled", it).apply()
            if (it) startOverlay() else stopService(Intent(this, EdgeOverlayService::class.java))
        })
        root.addView(switchRow("Show overlay on boot", prefs.getBoolean("overlay_boot", true)) {
            prefs.edit().putBoolean("overlay_boot", it).apply()
        })
        root.addView(switchRow("Show notification shortcut on boot", prefs.getBoolean("boot_notification", true)) {
            prefs.edit().putBoolean("boot_notification", it).apply()
        })
        root.addView(switchRow("Panel handle on left side", prefs.getString("overlay_side", "right") == "left") {
            prefs.edit().putString("overlay_side", if (it) "left" else "right").remove("overlay_x").apply()
            restartOverlayIfEnabled()
        })
        root.addView(horizontal().apply {
            addView(bigButton("Overlay Permission") {
                prefs.edit().putBoolean("overlay_enabled", true).apply()
                EdgeLauncher.openOverlaySettings(this@MainActivity)
            }, rowWeight())
            addView(bigButton("Start EDGE") { startOverlay() }, rowWeight())
            addView(bigButton("Update Widget") { EdgeWidgetProvider.updateAll(this@MainActivity) }, rowWeight())
        })
        root.addView(horizontal().apply {
            addView(bigButton("Small Handle") {
                prefs.edit().putInt("overlay_width", dp(84)).putInt("overlay_height", dp(84)).remove("overlay_x").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
            addView(bigButton("Large Handle") {
                prefs.edit().putInt("overlay_width", dp(112)).putInt("overlay_height", dp(112)).remove("overlay_x").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
            addView(bigButton("Clear Position") {
                prefs.edit().remove("overlay_x").remove("overlay_y").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
        })
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showPermissions() {
        val root = screen("👉🏽EDGE_PRO👈🏽", "Permissions")
        root.addView(bigButton("Display over other apps") { EdgeLauncher.openOverlaySettings(this) }, fullWidth())
        root.addView(bigButton("Usage Access") { EdgeLauncher.openUsageSettings(this) }, fullWidth())
        root.addView(bigButton("Accessibility Assist") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, fullWidth())
        root.addView(bigButton("Battery Optimization Settings") { EdgeLauncher.openBatterySettings(this) }, fullWidth())
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showBatteryHelp() {
        val root = screen("BATTERY", "Recommended apps to keep awake on the head unit.")
        AppScanner.commonBatteryList(this).forEach { root.addView(body("${it.label} - ${it.packageName}")) }
        root.addView(bigButton("Battery Settings") { EdgeLauncher.openBatterySettings(this) }, fullWidth())
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showHelp() {
        val root = screen("HELP", "")
        root.addView(body("EDGE focuses on fast app access, quick switching, and a clean floating dock for Geely CityRay-compatible Android head units."))
        root.addView(body("Use EDGE shortcuts for the apps you open often. Long press a shortcut to change or remove it."))
        root.addView(body("The back icon returns to the previous app when EDGE can detect it from Usage Access or launch history."))
        root.addView(body("If the floating bubble does not appear, allow Display over other apps and disable battery optimization for EDGE_PRO."))
        root.addView(body("Accessibility Assist is optional and only used for system navigation helpers."))
        root.addView(bigButton("‹") { showDockPanel() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun startOverlay(showToast: Boolean = true) {
        if (!Settings.canDrawOverlays(this)) {
            if (showToast) Toast.makeText(this, "Allow Display over other apps, then return and tap Start EDGE.", Toast.LENGTH_LONG).show()
            EdgeLauncher.openOverlaySettings(this)
            return
        }
        val intent = Intent(this, EdgeOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        if (showToast) Toast.makeText(this, "Floating EDGE started.", Toast.LENGTH_SHORT).show()
    }

    private fun restartOverlayIfEnabled() {
        if (repo.settings().getBoolean("overlay_enabled", true)) {
            stopService(Intent(this, EdgeOverlayService::class.java))
            startOverlay(showToast = false)
        }
    }

    private fun screen(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(28), dp(22), dp(28), dp(22))
        setBackgroundColor(Color.TRANSPARENT)
        addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        addView(TextView(this@MainActivity).apply {
            text = subtitle
            setTextColor(0xFFA7B0C0.toInt())
            textSize = 18f
            visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            setPadding(0, 4, 0, 18)
        })
    }

    private fun wrap(content: View, overlayColor: Int = 0xD9030509.toInt(), scroll: Boolean = true): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.cityray_vip_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 1f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (scroll) {
            addView(ScrollView(this@MainActivity).apply {
                setBackgroundColor(overlayColor)
                addView(content)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            addView(FrameLayout(this@MainActivity).apply {
                setBackgroundColor(overlayColor)
                addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun bigButton(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 19f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
        gravity = Gravity.CENTER
        isAllCaps = false
        background = getDrawable(R.drawable.tile_button_background)
        minHeight = dp(92)
        setOnClickListener { click() }
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFDDE6F3.toInt())
        textSize = 17f
        setPadding(10, 8, 10, 8)
    }

    private fun switchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setTextColor(Color.WHITE)
        textSize = 19f
        setPadding(8, 10, 8, 10)
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun tileGrid() = GridLayout(this).apply {
        columnCount = 2
        useDefaultMargins = true
        alignmentMode = GridLayout.ALIGN_BOUNDS
    }

    private fun compactQuickTile(key: String, fallbackIcon: Int): FrameLayout = FrameLayout(this).apply {
        val slot = repo.quickSlot(key)
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@MainActivity).apply {
            if (slot.packageName.isBlank()) setImageResource(fallbackIcon) else setImageDrawable(appIcon(slot.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setOnClickListener {
            if (slot.packageName.isBlank()) showInstalledApps("quick:$key") else EdgeLauncher.launchQuickSwap(this@MainActivity, key)
        }
        setOnLongClickListener {
            showQuickSwapOptions(key)
            true
        }
    }

    private fun compactDockTile(index: Int, shortcut: DockShortcut): FrameLayout = FrameLayout(this).apply {
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@MainActivity).apply {
            setImageDrawable(appIcon(shortcut.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setOnClickListener { EdgeLauncher.launchDockShortcut(this@MainActivity, index) }
        setOnLongClickListener {
            showDockShortcutOptions(index)
            true
        }
    }

    private fun compactActionTile(label: String, iconRes: Int, click: () -> Unit): FrameLayout = FrameLayout(this).apply {
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        if (label == "‹") {
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 42f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        } else {
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        }
        setOnClickListener { click() }
    }

    private fun dockTile(index: Int, shortcut: DockShortcut): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@MainActivity).apply {
            setImageDrawable(appIcon(shortcut.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(78), dp(78)))
        addView(TextView(this@MainActivity).apply {
            text = shortcut.label
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setOnClickListener { EdgeLauncher.launchDockShortcut(this@MainActivity, index) }
        setOnLongClickListener {
            showDockShortcutOptions(index)
            true
        }
    }

    private fun iconTile(label: String, iconRes: Int, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(78), dp(78)))
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun showDockShortcutOptions(index: Int) {
        val shortcut = repo.dockShortcuts().getOrNull(index)
        val actions = if (shortcut?.packageName.isNullOrBlank()) arrayOf("Change Shortcut") else arrayOf("Change Shortcut", "Remove Shortcut")
        android.app.AlertDialog.Builder(this)
            .setTitle("EDGE SLOT ${index + 1}")
            .setItems(actions) { dialog, which ->
                dialog.dismiss()
                when (actions[which]) {
                    "Change Shortcut" -> {
                        editingDockIndex = index
                        showInstalledApps("dock:$index")
                    }
                    "Remove Shortcut" -> {
                        repo.clearDockShortcut(index)
                        Toast.makeText(this, "Shortcut removed.", Toast.LENGTH_SHORT).show()
                        showDockPanel()
                    }
                }
            }
            .show()
    }

    private fun showQuickSwapOptions(key: String) {
        val slot = repo.quickSlot(key)
        val actions = if (slot.packageName.isBlank()) arrayOf("Change App") else arrayOf("Change App", "Remove App")
        android.app.AlertDialog.Builder(this)
            .setTitle(slot.title)
            .setItems(actions) { dialog, which ->
                dialog.dismiss()
                when (actions[which]) {
                    "Change App" -> showInstalledApps("quick:$key")
                    "Remove App" -> {
                        repo.clearQuickSlot(key)
                        Toast.makeText(this, "${slot.title} removed.", Toast.LENGTH_SHORT).show()
                        showDockPanel()
                    }
                }
            }
            .show()
    }

    private fun appIcon(packageName: String) = runCatching {
        packageManager.getApplicationIcon(packageName)
    }.getOrElse {
        getDrawable(R.drawable.ic_cityray_edge)
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(0, 6, 0, 6)
    }

    private fun rowWeight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(6, 6, 6, 6)
    }

    private fun tileParams() = ViewGroup.MarginLayoutParams(dp(245), dp(160)).apply {
        setMargins(10, 10, 10, 10)
    }

    private fun compactTileParams() = ViewGroup.MarginLayoutParams(dp(180), dp(112)).apply {
        setMargins(dp(5), dp(5), dp(5), dp(5))
    }

    private fun compactIconParams() = LinearLayout.LayoutParams(dp(118), dp(74)).apply {
        setMargins(dp(4), dp(8), dp(4), 0)
    }

    private fun firstEmptyDockIndex(): Int =
        repo.dockShortcuts().indexOfFirst { it.packageName.isBlank() }.takeIf { it >= 0 } ?: 0

    private fun View.withMargins(): View = this.apply {
        layoutParams = fullWidth()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }
}

class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
