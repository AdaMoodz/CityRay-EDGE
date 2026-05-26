package com.cityray.edge

import android.Manifest
import android.app.Activity
import android.content.Context
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
    private lateinit var repo: PairRepository
    private var editingIndex = 0
    private var editingDockIndex = 0
    private var selectedLeft: AppEntry? = null
    private var selectedRight: AppEntry? = null
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = PairRepository(this)
        requestNotificationPermission()
        if (!handleAction(intent)) showHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::repo.isInitialized && repo.settings().getBoolean("overlay_enabled", true) && Settings.canDrawOverlays(this)) {
            startOverlay(showToast = false)
        }
    }

    private fun handleAction(intent: Intent?): Boolean {
        return when (intent?.action) {
            CityRayActions.ACTION_LAUNCH_PAIR -> {
                SplitLauncher.launchPair(this, intent.getIntExtra(CityRayActions.EXTRA_PAIR_INDEX, 0))
                finish()
                true
            }
            CityRayActions.ACTION_LAUNCH_LAST -> {
                SplitLauncher.launchPair(this, repo.lastPairIndex())
                finish()
                true
            }
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
                SplitLauncher.launchDockShortcut(this, intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0))
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
                showHome()
                window.decorView.post { showDockShortcutOptions(editingDockIndex) }
                true
            }
            CityRayActions.ACTION_LAUNCH_APP -> {
                val packageName = intent.getStringExtra(CityRayActions.EXTRA_PACKAGE_NAME).orEmpty()
                if (packageName.isNotBlank()) SplitLauncher.launchPackage(this, packageName)
                finish()
                true
            }
            else -> false
        }
    }

    private fun showHome() {
        val root = screen("EDGE_PRO", "")
        root.addView(tileGrid().apply {
            repo.dockShortcuts().take(10).forEachIndexed { index, shortcut ->
                if (shortcut.packageName.isNotBlank()) {
                    addView(dockTile(index, shortcut), tileParams())
                }
            }
            addView(iconTile("+", R.drawable.ic_edge_add) {
                val empty = firstEmptyDockIndex()
                editingDockIndex = empty
                showInstalledApps("dock:$empty")
            }, tileParams())
            addView(iconTile("EDGE", R.drawable.cityray_edge_logo) {
                repo.settings().edit().putBoolean("overlay_enabled", true).apply()
                startOverlay()
            }, tileParams())
            addView(iconTile("PERMIT", R.drawable.tile_phone) {
                repo.settings().edit().putBoolean("overlay_enabled", true).apply()
                SplitLauncher.openOverlaySettings(this@MainActivity)
            }, tileParams())
        })

        root.addView(tileGrid().apply {
            addView(iconTile("EDIT", R.drawable.tile_apps) {
                selectedLeft = null
                selectedRight = null
                showPairEditor(0)
            }, tileParams())
            addView(iconTile("EDGE", R.drawable.cityray_edge_logo) { showQuickSettings() }, tileParams())
            addView(iconTile("PERMIT", R.drawable.tile_phone) { showPermissions() }, tileParams())
            addView(iconTile("SPLIT", R.drawable.tile_split_vertical) { showSplitSettings() }, tileParams())
        })
        setContentView(wrap(root))
    }

    private fun showPairEditor(index: Int) {
        val slotChanged = editingIndex != index
        editingIndex = index
        val pairs = repo.favorites()
        val pair = pairs.getOrNull(index) ?: pairs.first()
        if (slotChanged || selectedLeft == null || selectedRight == null) {
            selectedLeft = AppEntry(pair.leftLabel, pair.leftPackage, installed = AppScanner.isInstalled(this, pair.leftPackage))
            selectedRight = AppEntry(pair.rightLabel, pair.rightPackage, installed = AppScanner.isInstalled(this, pair.rightPackage))
        }
        val root = screen("Add / Edit Favorite Pair", "Choose the slot, pair name, top app, bottom app, overlay, and widget visibility.")

        val slotSpinner = Spinner(this)
        slotSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, pairs.mapIndexed { i, p -> "${i + 1}. ${p.name}" })
        slotSpinner.setSelection(index)
        slotSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != editingIndex) showPairEditor(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(slotSpinner, fullWidth())

        val nameInput = EditText(this).apply {
            setText(pair.name)
            hint = "Pair Name"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9AA6B8.toInt())
        }
        root.addView(nameInput, fullWidth())

        val leftButton = bigButton("Select Top App\n${selectedLeft?.label ?: "None"}") { showInstalledApps("left") }
        val rightButton = bigButton("Select Bottom App\n${selectedRight?.label ?: "None"}") { showInstalledApps("right") }
        root.addView(horizontal().apply {
            addView(leftButton, rowWeight())
            addView(rightButton, rowWeight())
        })

        val overlaySwitch = Switch(this).apply {
            text = "Show in Floating Overlay"
            isChecked = pair.showInOverlay
            setTextColor(Color.WHITE)
            textSize = 19f
        }
        val widgetSwitch = Switch(this).apply {
            text = "Show in Home Widget"
            isChecked = pair.showInWidget
            setTextColor(Color.WHITE)
            textSize = 19f
        }
        root.addView(overlaySwitch, fullWidth())
        root.addView(widgetSwitch, fullWidth())

        root.addView(horizontal().apply {
            addView(bigButton("Save Pair") {
                val left = selectedLeft
                val right = selectedRight
                if (left == null || right == null || left.packageName.isBlank() || right.packageName.isBlank()) {
                    Toast.makeText(this@MainActivity, "Select both apps first.", Toast.LENGTH_LONG).show()
                    return@bigButton
                }
                repo.saveFavorite(editingIndex, FavoritePair(
                    name = nameInput.text.toString().ifBlank { "${left.label} + ${right.label}" },
                    leftLabel = left.label,
                    leftPackage = left.packageName,
                    rightLabel = right.label,
                    rightPackage = right.packageName,
                    showInOverlay = overlaySwitch.isChecked,
                    showInWidget = widgetSwitch.isChecked,
                    lastUsed = pair.lastUsed
                ))
                Toast.makeText(this@MainActivity, "Favorite saved.", Toast.LENGTH_SHORT).show()
                showHome()
            }, rowWeight())
            addView(bigButton("Pin Shortcut") {
                ShortcutHelper.pinPairShortcut(this@MainActivity, editingIndex)
            }, rowWeight())
            addView(bigButton("Launch Pair") {
                val left = selectedLeft ?: return@bigButton
                val right = selectedRight ?: return@bigButton
                SplitLauncher.launchPair(this@MainActivity, FavoritePair(
                    nameInput.text.toString().ifBlank { "${left.label} + ${right.label}" },
                    left.label,
                    left.packageName,
                    right.label,
                    right.packageName
                ))
            }, rowWeight())
            addView(bigButton("Back") { showHome() }, rowWeight())
        })
        setContentView(wrap(root))
    }

    private fun showInstalledApps(selectionTarget: String?) {
        val subtitle = when {
            selectionTarget == null -> "Scanned launcher apps. Open apps or pin launcher shortcuts."
            selectionTarget.startsWith("dock:") -> "Select any installed app for EDGE dock slot ${selectionTarget.substringAfter(":").toIntOrNull()?.plus(1) ?: ""}."
            selectionTarget == "live_top" -> "Pick any app for the top split window."
            selectionTarget == "live_bottom" -> "Pick any app for the bottom split window."
            else -> "Select app for $selectionTarget side."
        }
        val root = screen("Installed Apps", subtitle)
        val search = EditText(this).apply {
            hint = "Search label or package"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9AA6B8.toInt())
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
        root.addView(bigButton("Back") {
            if (selectionTarget == null || selectionTarget.startsWith("dock:") || selectionTarget.startsWith("live_")) showHome() else showPairEditor(editingIndex)
        }, fullWidth())
        render()
        setContentView(wrap(root))
    }

    private fun appRow(app: AppEntry, selectionTarget: String?): View {
        val row = horizontal().apply {
            setPadding(12, 10, 12, 10)
            background = getDrawable(R.drawable.panel_background)
        }
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon ?: getDrawable(R.drawable.ic_cityray_edge))
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
        row.addView(TextView(this).apply {
            text = "${app.label}\n${app.packageName.ifBlank { "No package selected" }}\n${if (app.installed) "Installed" else "Not installed"}"
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(16, 0, 16, 0)
        }, rowWeight())
        if (selectionTarget != null) {
            row.addView(bigButton("Select") {
                if (!app.installed) Toast.makeText(this, "Not installed", Toast.LENGTH_SHORT).show()
                if (selectionTarget.startsWith("dock:")) {
                    val index = selectionTarget.substringAfter(":").toIntOrNull()?.coerceIn(0, 9) ?: editingDockIndex
                    repo.saveDockShortcut(index, DockShortcut(app.label, app.packageName, System.currentTimeMillis()))
                    Toast.makeText(this, "EDGE slot ${index + 1} saved.", Toast.LENGTH_SHORT).show()
                    showHome()
                } else if (selectionTarget == "live_top" || selectionTarget == "live_bottom") {
                    val top = selectionTarget == "live_top"
                    saveLiveSplitSlot(app, top)
                    launchLiveSplitOrSingle(app, top)
                } else {
                    if (selectionTarget == "left") selectedLeft = app else selectedRight = app
                    showPairEditor(editingIndex)
                }
            }, LinearLayout.LayoutParams(dp(150), dp(72)))
        } else {
            row.addView(bigButton("Open") { SplitLauncher.launchPackage(this, app.packageName) }, LinearLayout.LayoutParams(dp(130), dp(72)))
            row.addView(bigButton("Shortcut") { ShortcutHelper.pinAppShortcut(this, app) }, LinearLayout.LayoutParams(dp(160), dp(72)))
        }
        return row.withMargins()
    }

    private fun showControlCenter() {
        val root = screen("RUNNING APPS", "")
        val recent = runCatching { SplitLauncher.recentUsage(this) }.getOrDefault(emptyList())
        val records = if (recent.isNotEmpty()) recent.take(24) else repo.launchHistory().take(24)
        if (records.isEmpty()) {
            root.addView(body("Enable Usage Access to show recently used apps."))
            root.addView(bigButton("Usage Access") { SplitLauncher.openUsageSettings(this) }, fullWidth())
        } else {
            records.forEach { root.addView(recordRow(it)) }
        }
        root.addView(bigButton("‹") { showHome() }, fullWidth())
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
            text = "${record.label}\n${record.packageName}\n${dateFormat.format(Date(record.timestamp))}${record.pairName?.let { " - $it" } ?: ""}"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(14), 0, 0, 0)
        }, rowWeight())
        return row.withMargins()
    }

    private fun showQuickSettings() {
        val root = screen("Quick Access Settings", "Floating overlay, widget notes, notification shortcut, and boot behavior.")
        val prefs = repo.settings()
        val overlayEnabled = switchRow("Enable Floating Edge Button", prefs.getBoolean("overlay_enabled", true)) {
            prefs.edit().putBoolean("overlay_enabled", it).apply()
            if (it) startOverlay() else stopService(Intent(this, EdgeOverlayService::class.java))
        }
        val bootOverlay = switchRow("Show overlay on boot", prefs.getBoolean("overlay_boot", true)) {
            prefs.edit().putBoolean("overlay_boot", it).apply()
        }
        val bootNotification = switchRow("Show notification shortcut on boot", prefs.getBoolean("boot_notification", true)) {
            prefs.edit().putBoolean("boot_notification", it).apply()
        }
        val leftSide = switchRow("Panel handle on left side", prefs.getString("overlay_side", "right") == "left") {
            prefs.edit()
                .putString("overlay_side", if (it) "left" else "right")
                .remove("overlay_x")
                .apply()
            if (prefs.getBoolean("overlay_enabled", true)) {
                stopService(Intent(this, EdgeOverlayService::class.java))
                startOverlay(showToast = false)
            }
        }
        root.addView(overlayEnabled)
        root.addView(bootOverlay)
        root.addView(bootNotification)
        root.addView(leftSide)
        root.addView(horizontal().apply {
            addView(bigButton("Overlay Permission") {
                repo.settings().edit().putBoolean("overlay_enabled", true).apply()
                SplitLauncher.openOverlaySettings(this@MainActivity)
            }, rowWeight())
            addView(bigButton("Start Overlay Now") { startOverlay() }, rowWeight())
            addView(bigButton("Update Widget") { SplitWidgetProvider.updateAll(this@MainActivity) }, rowWeight())
        })
        root.addView(horizontal().apply {
            addView(bigButton("Small Handle") {
                prefs.edit().putInt("overlay_width", dp(84)).putInt("overlay_height", dp(168)).remove("overlay_x").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
            addView(bigButton("Large Handle") {
                prefs.edit().putInt("overlay_width", dp(112)).putInt("overlay_height", dp(224)).remove("overlay_x").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
            addView(bigButton("Clear Position") {
                prefs.edit().remove("overlay_x").remove("overlay_y").apply()
                restartOverlayIfEnabled()
            }, rowWeight())
        })
        root.addView(body("Some Geely launchers may not support Android home screen widgets. Use Floating Edge mode instead."))
        root.addView(bigButton("Back") { showHome() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showSplitSettings() {
        val root = screen("Split", "")
        val prefs = repo.settings()
        val modes = listOf(
            "Top / Bottom" to SplitLauncher.MODE_FREEFORM,
            "Open One After Another" to SplitLauncher.MODE_NORMAL,
            "Accessibility Assist" to SplitLauncher.MODE_ACCESSIBILITY
        )
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        modes.forEach { (label, value) ->
            group.addView(RadioButton(this).apply {
                text = label
                tag = value
                setTextColor(Color.WHITE)
                textSize = 19f
                isChecked = prefs.getString("split_mode", SplitLauncher.MODE_FREEFORM) == value
            })
        }
        group.setOnCheckedChangeListener { g, id ->
            prefs.edit().putString("split_mode", g.findViewById<RadioButton>(id).tag.toString()).apply()
        }
        root.addView(group)

        val delay = Spinner(this)
        val delays = listOf(500L, 800L, 1200L, 2000L)
        delay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, delays.map { "${it}ms" })
        delay.setSelection(delays.indexOf(prefs.getLong("launch_delay", 800L)).coerceAtLeast(0))
        delay.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putLong("launch_delay", delays[position]).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(sectionTitle("Delay"))
        root.addView(delay, fullWidth())
        root.addView(switchRow("Show video safety warning", prefs.getBoolean("video_warning", true)) {
            prefs.edit().putBoolean("video_warning", it).apply()
        })
        root.addView(bigButton("Back") { showHome() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showPermissions() {
        val root = screen("👉🏽EDGE_PRO👈🏽", "Permissions")
        root.addView(bigButton("Display over other apps") { SplitLauncher.openOverlaySettings(this) }, fullWidth())
        root.addView(bigButton("Usage Access") { SplitLauncher.openUsageSettings(this) }, fullWidth())
        root.addView(bigButton("Accessibility Assisted Mode") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, fullWidth())
        root.addView(bigButton("Battery Optimization Settings") { SplitLauncher.openBatterySettings(this) }, fullWidth())
        root.addView(sectionTitle("Recommended Apps To Disable Battery Optimization"))
        AppScanner.commonBatteryList(this).forEach { root.addView(body("${it.label} - ${it.packageName}")) }
        root.addView(bigButton("Back") { showHome() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun showHelp() {
        val root = screen("Help / Troubleshooting", "Quick fixes for common Geely head-unit behavior.")
        val text = """
            App opens full screen instead of split: enable Accessibility Assist and try again.
            Second app replaces first app: increase launch delay or reverse top/bottom app.
            App not found: scan installed apps and select any installed launchable app manually.
            Floating overlay not showing: enable Display over other apps and disable battery optimization.
            Widget not available: some Geely launchers do not support widgets. Use Floating Edge mode.
            App Control Center cannot see all apps: enable Usage Access.
            Close app does not work: Android may block killing foreground apps. Use Recents / Accessibility mode.
            Back/Home/Recents buttons require Accessibility Assisted Mode.
            Video warning is safety-only. Use video apps only while parked or for passenger use.
        """.trimIndent()
        root.addView(body(text))
        root.addView(bigButton("Back") { showHome() }, fullWidth())
        setContentView(wrap(root))
    }

    private fun startOverlay(showToast: Boolean = true) {
        if (!Settings.canDrawOverlays(this)) {
            if (showToast) Toast.makeText(this, "Allow Display over other apps, then return and tap Start Floating Edge.", Toast.LENGTH_LONG).show()
            SplitLauncher.openOverlaySettings(this)
            return
        }
        val intent = Intent(this, EdgeOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        if (showToast) Toast.makeText(this, "Floating Edge started.", Toast.LENGTH_SHORT).show()
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

    private fun wrap(content: View): FrameLayout = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.cityray_vip_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 1f
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(ScrollView(this@MainActivity).apply {
            setBackgroundColor(0xD9030509.toInt())
            addView(content)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF2CE6D0.toInt())
        textSize = 21f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, 20, 0, 8)
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

    private fun dockTile(index: Int, shortcut: DockShortcut): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@MainActivity).apply {
            if (shortcut.packageName.isBlank()) {
                setImageResource(R.drawable.cityray_edge_logo)
                alpha = 0.72f
            } else {
                setImageDrawable(appIcon(shortcut.packageName))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(78), dp(78)))
        addView(TextView(this@MainActivity).apply {
            text = if (shortcut.packageName.isBlank()) "PICK APP ${index + 1}" else shortcut.label
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setOnClickListener {
            if (shortcut.packageName.isBlank()) {
                editingDockIndex = index
                showInstalledApps("dock:$index")
            } else {
                SplitLauncher.launchDockShortcut(this@MainActivity, index)
            }
        }
        setOnLongClickListener {
            showDockShortcutOptions(index)
            true
        }
    }

    private fun showDockShortcutOptions(index: Int) {
        val shortcut = repo.dockShortcuts().getOrNull(index)
        val actions = if (shortcut?.packageName.isNullOrBlank()) {
            arrayOf("Change Shortcut")
        } else {
            arrayOf("Change Shortcut", "Remove Shortcut")
        }
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
                        showHome()
                    }
                }
            }
            .show()
    }

    private fun liveSplitBox(top: Boolean): LinearLayout {
        val prefs = repo.settings()
        val prefix = if (top) "live_top" else "live_bottom"
        val label = prefs.getString("${prefix}_label", "").orEmpty()
        val packageName = prefs.getString("${prefix}_package", "").orEmpty()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = getDrawable(R.drawable.tile_button_background)
            isClickable = true
            isFocusable = true
            setOnClickListener { showInstalledApps(prefix) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.tile_split_vertical)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(106), dp(82)))
            addView(TextView(this@MainActivity).apply {
                text = if (top) "TOP" else "BOTTOM"
                setTextColor(Color.WHITE)
                textSize = 23f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }, LinearLayout.LayoutParams(dp(145), ViewGroup.LayoutParams.MATCH_PARENT))
            if (packageName.isNotBlank()) {
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(appIcon(packageName))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
                    setMargins(dp(12), 0, dp(12), 0)
                })
                addView(TextView(this@MainActivity).apply {
                    text = label.ifBlank { AppScanner.labelFor(this@MainActivity, packageName) }
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 1
                    setShadowLayer(5f, 0f, 2f, Color.BLACK)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            } else {
                addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_edge_add)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = getDrawable(R.drawable.panel_background)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener { showInstalledApps(prefix) }
            }, LinearLayout.LayoutParams(dp(92), dp(92)))
        }
    }

    private fun saveLiveSplitSlot(app: AppEntry, top: Boolean) {
        val prefix = if (top) "live_top" else "live_bottom"
        repo.settings().edit()
            .putString("${prefix}_label", app.label)
            .putString("${prefix}_package", app.packageName)
            .apply()
    }

    private fun launchLiveSplitOrSingle(selected: AppEntry, top: Boolean) {
        val prefs = repo.settings()
        val topLabel = prefs.getString("live_top_label", "").orEmpty()
        val topPackage = prefs.getString("live_top_package", "").orEmpty()
        val bottomLabel = prefs.getString("live_bottom_label", "").orEmpty()
        val bottomPackage = prefs.getString("live_bottom_package", "").orEmpty()
        if (topPackage.isNotBlank() && bottomPackage.isNotBlank()) {
            SplitLauncher.launchPair(
                this,
                FavoritePair(
                    "Live Split",
                    topLabel.ifBlank { AppScanner.labelFor(this, topPackage) },
                    topPackage,
                    bottomLabel.ifBlank { AppScanner.labelFor(this, bottomPackage) },
                    bottomPackage
                )
            )
        } else {
            SplitLauncher.launchAppInSplitSlot(this, selected.packageName, top)
        }
    }

    private fun splitTile(index: Int, pair: FavoritePair, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = getDrawable(R.drawable.tile_button_background)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.tile_split_vertical)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(96), dp(72)))
        addView(TextView(this@MainActivity).apply {
            text = if (index == 6) "LAST" else pair.name.uppercase(Locale.getDefault())
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
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

    private fun gridParams() = ViewGroup.MarginLayoutParams(dp(360), dp(145)).apply {
        setMargins(8, 8, 8, 8)
    }

    private fun tileParams() = ViewGroup.MarginLayoutParams(dp(245), dp(160)).apply {
        setMargins(10, 10, 10, 10)
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
