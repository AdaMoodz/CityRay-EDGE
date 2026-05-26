# Geely Edge Pro

Geely Edge Pro is a premium floating EDGE dock for Geely CityRay-compatible Android head units.

EDGE focuses on fast app access, quick switching, and a clean floating dock for Geely CityRay-compatible Android head units.

This project is independent software for compatible Android head units. It does not use official Geely branding, logos, or proprietary APIs.

## Highlights

- Floating EDGE bubble overlay
- Quick Swap mode with `NAV APP` and `MEDIA APP`
- Add any installed app as an EDGE shortcut
- Long-press shortcuts to change or remove them
- One-tap return to the previous app from the EDGE panel
- Running/recent apps list when Usage Access is enabled
- EDGE Voice Helper with Android Text-To-Speech
- EDGE Voice Identity personalization
- Experimental EDGE Auto Companion media service for Android Auto
- Display over other apps permission flow
- Boot support for restoring the floating EDGE handle
- Home screen widget and launcher shortcut support
- Optional Accessibility navigation assist for Back, Home, and Recents
- Local-only preferences; no internet dependency

## Target Environment

- Android-based Geely-compatible head unit
- Portrait/vertical 2K-style display
- APK sideloading through file manager, USB, or an existing installer path
- Display over other apps permission for the floating EDGE dock

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
6. Pick your `NAV APP` and `MEDIA APP`.
7. Add the apps you want in the EDGE dock.
8. Disable battery optimization for `EDGE_PRO` if the overlay is stopped by firmware.

## Quick Swap

Quick Swap is the stable replacement for unreliable firmware multitasking.

- `NAV APP`: choose your navigation or Android Auto app.
- `MEDIA APP`: choose your music or video app.
- Tap either button from the main app or floating EDGE panel to open it instantly.
- Use the previous-app icon in the EDGE panel to jump back to the last app.

## EDGE Voice Helper

EDGE Voice Helper lets you type text and play it aloud using Android Text-To-Speech.

- Supports Arabic, English, and French.
- Includes preset CityRay / driving phrases.
- Keeps the screen simple: driver name, language, message box, presets, Speak, Stop, and Test Greeting.
- Waits for the Android TTS engine before enabling Speak.
- Shows a TTS settings shortcut if the voice engine or selected language is missing.
- Stops speech safely when leaving the screen or closing the app.

## EDGE Voice Identity

EDGE Voice Identity lets you save a local driver name/nickname.

- TTS can speak personalized phrases like `Welcome {name}, EDGE is ready.`
- Arabic, English, and French preset greetings include the saved name.
- Works offline using Android Text-To-Speech.
- No cloud AI, account login, or microphone is used.

## EDGE Auto Companion

EDGE Auto Companion is an experimental Android Auto media companion concept.

- It does not show floating overlays inside Android Auto.
- Android Auto does not allow normal overlay windows inside its projected UI.
- EDGE exposes simple media-style categories through Android media APIs.
- Android Auto controls the UI.
- The current categories are `EDGE Quick Help`, `CityRay Tips`, `Drive Mode`, `Maintenance Tips`, and `Morocco Road Tips`.
- Voice Helper phrases are shared with the Auto Companion structure so they can be reused later.

## Privacy

Geely Edge Pro stores configuration locally on the head unit. It may read the installed launcher app list so the user can choose shortcuts. Usage Access and Accessibility are optional and are only used for the visible features described in the app.

No internet connection is required.

## Project Status

Release target: `v1.0`

Primary APK name for sideloading:

```text
CityRay_Edge_Pro.apk
```

## License

Private project unless a license file is added.
