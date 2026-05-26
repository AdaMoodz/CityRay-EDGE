package com.cityray.edge

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class CityRayAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        var instance: CityRayAccessibilityService? = null
            private set
    }
}
