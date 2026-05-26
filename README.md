# Geely Edge Pro

Geely Edge Pro is a premium floating EDGE dock for Geely-compatible Android head units. It keeps a small always-available bubble on the screen and opens a clean shortcut panel for the apps you choose.

This project is independent software for compatible Android head units. It does not use official Geely branding, logos, or proprietary APIs.

## Highlights

- Floating edge handle with a large, driver-friendly shortcut dock
- Add any installed app as an EDGE shortcut
- Long-press shortcuts to change or remove them
- One-tap return to the previous app from the floating EDGE panel
- User-selected app slots from installed launchable apps
- Top/bottom split-launch mode for compatible firmware
- Home screen widget and launcher shortcut support
- Running/recent apps list when Usage Access is enabled
- Optional Accessibility navigation assist for Home, Back, Recents, and split actions
- Boot support for restoring the floating edge handle
- Local-only preferences; no network dependency

## Target Environment

- Android-based Geely-compatible head unit
- Portrait/vertical 2K-style display
- APK sideloading through file manager, USB, or an existing installer path
- Display over other apps permission for the floating edge dock

## Build

Open the project in Android Studio and build the `app` module.

Recommended local commands:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleRelease
```

The release build enables R8 shrinking and obfuscation. The current project signs the release with the debug signing config for direct head-unit sideload installs. Replace this with your own signing config before public distribution.

## Install

1. Build or copy the APK to USB.
2. Install it on the head unit.
3. Open `EDGE_PRO`.
4. Grant Display over other apps.
5. Tap `EDGE`.
6. Add the apps you want in the dock.
7. Use split only for app pairs you actually use.
8. Disable battery optimization for `EDGE_PRO` if the overlay is stopped by firmware.

## Split Launching

True split behavior depends on the head-unit firmware and on whether the target apps allow resizing. Top/bottom split is included as a beta feature because some Geely firmware builds block normal Android split-window requests.

## Privacy

Geely Edge Pro stores configuration locally on the head unit. It may read the installed launcher app list so the user can choose shortcuts. Usage Access and Accessibility are optional and are only used for the visible features described in the app.

No internet connection is required.

## Project Status

Release target: `v1.0`

Primary APK name for sideloading:

```text
CityRay_Edge_Pro.apk
```

See [RELEASE_V1_0.md](RELEASE_V1_0.md) for the short English and Arabic explanation.

## License

Private project unless a license file is added.
