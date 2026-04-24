# YT Background Play

Play YouTube audio with the screen locked — a floating button appears over YouTube and locks the screen while audio continues.

## How it works

1. **Accessibility Service** detects when YouTube is open
2. A floating **🔴 red button** appears on screen
3. Tap the button → screen locks instantly
4. YouTube audio keeps playing in background

## Setup (First Time)

After installing the APK, grant 3 permissions inside the app:

| Step | Permission | Why |
|------|-----------|-----|
| 1 | Overlay (Draw over apps) | Show floating button |
| 2 | Device Admin | Lock the screen |
| 3 | Accessibility Service | Detect YouTube |

## Build via GitHub Actions

Push to `main` or `master` branch → APK is built automatically.

Download from: **Actions → latest run → Artifacts → YTBackgroundPlay-debug**

## Build Locally

```bash
# Requires Android Studio + JDK 17
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Permissions Explained

- `SYSTEM_ALERT_WINDOW` — floating button overlay
- `FOREGROUND_SERVICE` — keeps service alive
- `BIND_DEVICE_ADMIN` — screen lock only (`lockNow()`)
- `BIND_ACCESSIBILITY_SERVICE` — detects YouTube foreground

## Notes

- Works with the standard YouTube app (`com.google.android.youtube`)
- The floating button is draggable
- Service auto-restarts after device reboot
- No root required
