package com.cityray.edge

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ShortcutCreateActivity : Activity() {
    private lateinit var repo: EdgeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = EdgeRepository(this)
        if (intent?.action != Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }
        showShortcutPicker()
    }

    private fun showShortcutPicker() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(0xFF070A0F.toInt())
            addView(TextView(this@ShortcutCreateActivity).apply {
                text = "Add EDGE Shortcut"
                setTextColor(Color.WHITE)
                textSize = 30f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@ShortcutCreateActivity).apply {
                text = "Choose what the HU launcher shortcut should open."
                setTextColor(0xFFA7B0C0.toInt())
                textSize = 18f
                setPadding(0, dp(4), 0, dp(18))
            })
        }

        root.addView(shortcutButton("Start EDGE Handle") {
            finishWithShortcut("Start EDGE", Intent(this, MainActivity::class.java).setAction(CityRayActions.ACTION_START_OVERLAY))
        })
        root.addView(shortcutButton("Open App Control Center") {
            finishWithShortcut("EDGE Control", Intent(this, MainActivity::class.java).setAction(CityRayActions.ACTION_OPEN_CONTROL))
        })
        root.addView(shortcutButton("Open Edge Pro") {
            finishWithShortcut("Geely Edge Pro", Intent(this, MainActivity::class.java))
        })

        repo.dockShortcuts().forEachIndexed { index, shortcut ->
            if (shortcut.packageName.isNotBlank()) {
                root.addView(shortcutButton("EDGE App ${index + 1}: ${shortcut.label}") {
                    finishWithShortcut(
                        shortcut.label,
                        Intent(this, MainActivity::class.java).apply {
                            action = CityRayActions.ACTION_LAUNCH_DOCK
                            putExtra(CityRayActions.EXTRA_DOCK_INDEX, index)
                        }
                    )
                })
            }
        }

        root.addView(shortcutButton("Cancel") { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun finishWithShortcut(label: String, launchIntent: Intent) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val result = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_NAME, label)
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this@ShortcutCreateActivity, R.drawable.ic_cityray_edge)
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun shortcutButton(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 21f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
        background = getDrawable(R.drawable.tile_button_background)
        minHeight = dp(96)
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(7), 0, dp(7))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
