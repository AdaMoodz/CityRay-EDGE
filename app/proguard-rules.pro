-keep class com.cityray.edge.MainActivity
-keep class com.cityray.edge.EdgeOverlayService
-keep class com.cityray.edge.CityRayAccessibilityService
-keep class com.cityray.edge.BootReceiver
-keep class com.cityray.edge.MacroDroidReceiver
-keep class com.cityray.edge.SplitWidgetProvider
-keep class com.cityray.edge.ShortcutCreateActivity

-keepclassmembers class * {
    public static final int *;
}

-dontwarn org.json.**
