# Geely Edge Pro

Geely Edge Pro is a premium floating EDGE dock for Geely CityRay-compatible Android head units.

EDGE focuses on fast app access, quick switching, and a clean floating dock for compatible Android head units. It is independent software and does not use official Geely branding, logos, or proprietary APIs.

## Highlights

- Floating EDGE bubble overlay
- Compact aligned app shortcut grid
- NAV APP and MEDIA APP stored as normal dock icons
- Add any installed app as an EDGE shortcut from the main app
- Long-press shortcuts to change or remove them
- One-tap return to the previous app from the EDGE panel
- Running/recent apps list when Usage Access is enabled
- Display over other apps permission flow
- Boot support for restoring the floating EDGE handle
- Home screen widget and launcher shortcut support
- Optional Accessibility navigation assist
- Local-only preferences; no internet dependency

## Install

1. Build or copy the APK to USB.
2. Install it on the head unit.
3. Open `EDGE_PRO`.
4. Grant Display over other apps.
5. Tap `EDGE`.
6. Pick the apps you want in the dock.
7. Disable battery optimization for `EDGE_PRO` if the overlay is stopped by firmware.

## Usage

- Tap the floating EDGE bubble to open the dock.
- Tap any app icon to launch it.
- Long-press an icon to change or remove it.
- Use the back icon to return to the previous app when EDGE can detect it from Usage Access or launch history.
- Open the main app to add shortcuts, edit apps, and manage permissions.

## Build

Open the project in Android Studio and build the `app` module.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

Primary APK name for sideloading:

```text
CityRay_Edge_Pro.apk
```

## Privacy

Geely Edge Pro stores configuration locally on the head unit. It may read the installed launcher app list so the user can choose shortcuts. Usage Access and Accessibility are optional and only used for the visible helper features.

No internet connection is required.

## License

Private project unless a license file is added.
