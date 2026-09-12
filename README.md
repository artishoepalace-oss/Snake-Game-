# Snake 2D Android

A lightweight native Android Snake game.

## Features
- 20x20 game board
- Swipe controls + on-screen D-pad
- Score and persistent high score
- Progressive speed increase
- Pause and restart
- Game-over overlay
- Offline; no permissions or ads
- Android 6.0+ (minSdk 23)

## Build
GitHub Actions automatically builds a debug APK on push. Download the `Snake-2D-APK` artifact from the workflow run.

Workflow: `.github/workflows/build-apk.yml`

Local build with Gradle 8.7 + JDK 17:

```bash
gradle :app:assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`
