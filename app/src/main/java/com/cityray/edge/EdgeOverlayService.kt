package com.cityray.edge

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EdgeOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var headlightAnimator: ObjectAnimator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val repo by lazy { EdgeRepository(this) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        acquireServiceWakeLock()
        startForeground(7, notification())
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CityRayActions.ACTION_LAUNCH_DOCK -> EdgeLauncher.launchDockShortcut(this, intent.getIntExtra(CityRayActions.EXTRA_DOCK_INDEX, 0))
            CityRayActions.ACTION_LAUNCH_QUICK -> EdgeLauncher.launchQuickSwap(this, intent.getStringExtra(CityRayActions.EXTRA_QUICK_KEY).orEmpty())
            CityRayActions.ACTION_OPEN_CONTROL -> openControlCenter()
            CityRayActions.ACTION_START_OVERLAY -> showBubble()
            CityRayActions.ACTION_TOGGLE_OVERLAY -> {
                showBubble()
                toggleMenu()
            }
            CityRayActions.ACTION_HIDE_OVERLAY, CityRayActions.ACTION_STOP_OVERLAY -> hideOverlayAndStop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        headlightAnimator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        menu?.let { runCatching { windowManager.removeView(it) } }
        bubble?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubble != null) return
        val prefs = repo.settings()
        val width = prefs.getInt("overlay_width", dp(104)).coerceIn(dp(82), dp(126))
        val height = prefs.getInt("overlay_height", dp(104)).coerceIn(dp(82), dp(126))
        val side = prefs.getString("overlay_side", "right") ?: "right"
        val defaultX = if (side == "left") dp(22) else (displayWidth() - width - dp(44)).coerceAtLeast(dp(22))
        val defaultY = ((displayHeight() - height) / 2).coerceAtLeast(dp(48))
        val params = overlayParams(width, height).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampX(prefs.getInt("overlay_x", defaultX), width)
            y = clampY(prefs.getInt("overlay_y", defaultY), height)
        }
        val button = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isLongClickable = true
            alpha = prefs.getInt("overlay_alpha", 100) / 100f
        }
        val glowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFF2D2D.toInt())
        }
        val glow = View(this).apply {
            background = glowDrawable
            alpha = 0.50f
        }
        button.addView(glow, FrameLayout.LayoutParams((width * 0.58f).toInt(), (height * 0.58f).toInt(), Gravity.CENTER))
        button.addView(ImageView(this).apply {
            setImageResource(R.drawable.cityray_edge_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            alpha = 0.98f
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER).apply {
            setMargins(dp(8), dp(8), dp(8), dp(8))
        })
        headlightAnimator = ObjectAnimator.ofArgb(
            glowDrawable,
            "color",
            0xFFFF2D2D.toInt(),
            0xFF3A7BFF.toInt(),
            0xFFFFD447.toInt(),
            0xFFFF2D2D.toInt()
        ).apply {
            duration = 12000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = clampX(startX + (event.rawX - touchX).toInt(), width)
                    params.y = clampY(startY + (event.rawY - touchY).toInt(), height)
                    bubbleParams = params
                    windowManager.updateViewLayout(button, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply()
                    if (kotlin.math.abs(event.rawX - touchX) < 8 && kotlin.math.abs(event.rawY - touchY) < 8) toggleMenu()
                    true
                }
                else -> false
            }
        }
        button.setOnLongClickListener {
            openControlCenter()
            true
        }
        bubble = button
        bubbleParams = params
        windowManager.addView(button, params)
    }

    private fun toggleMenu() {
        menu?.let {
            windowManager.removeView(it)
            menu = null
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
            background = getDrawable(R.drawable.widget_background)
        }
        layout.addView(TextView(this).apply {
            text = "EDGE"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
            setPadding(0, 0, 0, dp(8))
        })
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        grid.addView(quickSwapButton(EdgeRepository.QUICK_NAV, R.drawable.tile_maps))
        grid.addView(quickSwapButton(EdgeRepository.QUICK_MEDIA, R.drawable.tile_spotify))
        val shortcuts = repo.dockShortcuts()
        shortcuts.forEachIndexed { index, shortcut ->
            if (shortcut.packageName.isNotBlank()) {
                grid.addView(dockButton(index, shortcut))
            }
        }
        grid.addView(addButton {
            openDockEditor(firstEmptyDockIndex())
            closeMenuOnly()
        })
        while (grid.childCount < 6) {
            grid.addView(emptySlot())
        }
        layout.addView(grid)
        layout.addView(edgeControls())
        layout.addView(backIconButton {
            EdgeLauncher.smartBack(this@EdgeOverlayService)
            closeMenuOnly()
        })

        var menuTouchX = 0f
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(layout)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        menuTouchX = event.rawX
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.rawX - menuTouchX
                        val opensRight = (bubbleParams?.x ?: 0) < displayWidth() / 2
                        val shouldClose = if (opensRight) delta < -dp(80) else delta > dp(80)
                        if (shouldClose) {
                            closeMenuOnly()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        val menuWidth = dp(188)
        val anchor = bubbleParams
        val params = overlayParams(menuWidth, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (anchor != null) {
                if (anchor.x < displayWidth() / 2) {
                    clampMenuX(anchor.x + anchor.width + dp(10), menuWidth)
                } else {
                    clampMenuX(anchor.x - menuWidth - dp(10), menuWidth)
                }
            } else if ((repo.settings().getString("overlay_side", "right") ?: "right") == "left") {
                dp(120)
            } else {
                (displayWidth() - menuWidth - dp(120)).coerceAtLeast(dp(8))
            }
            y = clampMenuY(anchor?.y ?: dp(72))
        }
        menu = scroll
        windowManager.addView(scroll, params)
        scroll.alpha = 0f
        scroll.scaleX = 0.94f
        scroll.scaleY = 0.94f
        scroll.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
    }

    private fun quickSwapButton(key: String, fallbackIcon: Int) = FrameLayout(this).apply {
        val slot = repo.quickSlot(key)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@EdgeOverlayService).apply {
            if (slot.packageName.isBlank()) setImageResource(fallbackIcon) else setImageDrawable(appIcon(slot.packageName))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setOnClickListener {
            if (slot.packageName.isBlank()) openQuickEditor(key) else EdgeLauncher.launchQuickSwap(this@EdgeOverlayService, key)
            closeMenuOnly()
        }
        setOnLongClickListener {
            openQuickEditor(key)
            closeMenuOnly()
            true
        }
        layoutParams = android.widget.GridLayout.LayoutParams().apply {
            width = dp(64)
            height = dp(58)
            setMargins(dp(4), dp(3), dp(4), dp(3))
        }
    }

    private fun dockButton(index: Int, shortcut: DockShortcut) = FrameLayout(this).apply {
        setPadding(0, 0, 0, 0)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@EdgeOverlayService).apply {
            if (shortcut.packageName.isNotBlank()) {
                setImageDrawable(appIcon(shortcut.packageName))
            } else {
                setImageResource(R.drawable.cityray_edge_logo)
                alpha = 0.72f
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        setOnClickListener {
            EdgeLauncher.launchDockShortcut(this@EdgeOverlayService, index)
            toggleMenu()
        }
        setOnLongClickListener {
            openDockOptions(index)
            toggleMenu()
            true
        }
        layoutParams = android.widget.GridLayout.LayoutParams().apply {
            width = dp(64)
            height = dp(58)
            setMargins(dp(4), dp(3), dp(4), dp(3))
        }
    }

    private fun addButton(click: () -> Unit) = FrameLayout(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@EdgeOverlayService).apply {
            setImageResource(R.drawable.ic_edge_add)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setOnClickListener { click() }
        layoutParams = android.widget.GridLayout.LayoutParams().apply {
            width = dp(64)
            height = dp(58)
            setMargins(dp(4), dp(3), dp(4), dp(3))
        }
    }

    private fun emptySlot() = FrameLayout(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = android.widget.GridLayout.LayoutParams().apply {
            width = dp(64)
            height = dp(58)
            setMargins(dp(4), dp(3), dp(4), dp(3))
        }
    }

    private fun edgeControls() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(3), 0, 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(controlIcon(R.drawable.tile_apps) {
            openMainApp()
            closeMenuOnly()
        })
        addView(controlIcon(R.drawable.tile_settings) {
            openControlCenter()
            closeMenuOnly()
        })
        addView(controlIcon(R.drawable.tile_power) {
            closeMenuOnly()
        })
    }

    private fun controlIcon(iconRes: Int, click: () -> Unit) = FrameLayout(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        addView(ImageView(this@EdgeOverlayService).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(42)).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }
    }

    private fun backIconButton(click: () -> Unit) = TextView(this).apply {
        text = "‹"
        textSize = 42f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
        setPadding(0, 0, 0, dp(4))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        setOnClickListener { click() }
    }

    private fun firstEmptyDockIndex(): Int =
        repo.dockShortcuts().indexOfFirst { it.packageName.isBlank() }.takeIf { it >= 0 } ?: 0

    private fun closeMenuOnly() {
        menu?.let {
            runCatching { windowManager.removeView(it) }
            menu = null
        }
    }

    private fun hideOverlayAndStop() {
        closeMenuOnly()
        bubble?.let {
            runCatching { windowManager.removeView(it) }
            bubble = null
        }
        stopSelf()
    }

    private fun openControlCenter() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_OPEN_CONTROL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun openDockEditor(index: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_EDIT_DOCK
            putExtra(CityRayActions.EXTRA_DOCK_INDEX, index)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun openDockOptions(index: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_DOCK_OPTIONS
            putExtra(CityRayActions.EXTRA_DOCK_INDEX, index)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun openQuickEditor(key: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = CityRayActions.ACTION_EDIT_QUICK
            putExtra(CityRayActions.EXTRA_QUICK_KEY, key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun appIcon(packageName: String) = runCatching {
        packageManager.getApplicationIcon(packageName)
    }.getOrElse {
        getDrawable(R.drawable.ic_cityray_edge)
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun notification(): Notification {
        val channelId = "cityray_edge_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Geely Edge Pro", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = pendingActivity(Intent(this, MainActivity::class.java))
        val nav = pendingService(Intent(this, EdgeOverlayService::class.java).setAction(CityRayActions.ACTION_LAUNCH_QUICK).putExtra(CityRayActions.EXTRA_QUICK_KEY, EdgeRepository.QUICK_NAV), 101)
        val control = pendingService(Intent(this, EdgeOverlayService::class.java).setAction(CityRayActions.ACTION_OPEN_CONTROL), 102)
        val hide = pendingService(Intent(this, EdgeOverlayService::class.java).setAction(CityRayActions.ACTION_HIDE_OVERLAY), 103)
        val stop = pendingService(Intent(this, EdgeOverlayService::class.java).setAction(CityRayActions.ACTION_STOP_OVERLAY), 104)
        return Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_cityray_edge)
            .setContentTitle("Geely Edge Pro")
            .setContentText("Edge dock is active.")
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_cityray_edge, "NAV APP", nav)
            .addAction(R.drawable.ic_cityray_edge, "App Control", control)
            .addAction(R.drawable.ic_cityray_edge, "Hide Overlay", hide)
            .addAction(R.drawable.ic_cityray_edge, "Stop Service", stop)
            .setOngoing(true)
            .build()
    }

    private fun pendingActivity(intent: Intent): PendingIntent =
        PendingIntent.getActivity(this, 1, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun pendingService(intent: Intent, request: Int): PendingIntent =
        PendingIntent.getService(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun displayWidth(): Int = resources.displayMetrics.widthPixels

    private fun displayHeight(): Int = resources.displayMetrics.heightPixels

    private fun clampX(value: Int, width: Int): Int = value.coerceIn(dp(20), (displayWidth() - width - dp(36)).coerceAtLeast(dp(20)))

    private fun clampY(value: Int, height: Int): Int = value.coerceIn(dp(28), (displayHeight() - height - dp(28)).coerceAtLeast(dp(28)))

    private fun clampMenuX(value: Int, width: Int): Int = value.coerceIn(dp(8), (displayWidth() - width - dp(8)).coerceAtLeast(dp(8)))

    private fun clampMenuY(value: Int): Int = value.coerceIn(dp(28), (displayHeight() - dp(430)).coerceAtLeast(dp(28)))

    private fun acquireServiceWakeLock() {
        runCatching {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CityRayEdge:OverlayService").apply {
                setReferenceCounted(false)
                acquire(6L * 60L * 60L * 1000L)
            }
        }
    }
}
