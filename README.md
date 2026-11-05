# AMS-KEYBOARD

Android soft keyboard (Input Method Editor) project scaffolded to match the reference keyboard layout.

## Visual style (confirmed)

| Item | Value |
|------|-------|
| Keyboard background | Default **white** (`#FFFFFF`) |
| Key text / labels | **Black** (`#000000`) |
| All text | **Black** (`#000000`) |
| Layout | Matches the reference keyboard layout (QWERTY rows) |
| Icons | Not copied exactly from the reference |
| Ads | **None** — all ad/branding icons from the reference are ignored |

## Features included (from the reference, re-implemented without copying icons/ads)

- QWERTY key layout
- Shift / Caps toggle
- Backspace (delete)
- Enter / return
- Space bar
- Numbers & symbols toggle (`?123`)
- Settings / launcher activity
- No ads, no third-party icons

> Note: the exact icon set and ads shown in the reference images were deliberately ignored, per the project request.

## Project structure

```
AMS-KEYBOARD/
├── README.md
├── build.gradle
├── settings.gradle
├── gradle.properties
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ams/keyboard/
│       │   ├── AmsKeyboardService.java   # InputMethodService (IME)
│       │   └── MainActivity.java          # Launcher / settings screen
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml
│       │   │   └── keyboard_view.xml      # white bg, black keys/text
│       │   ├── xml/
│       │   │   ├── keyboard.xml           # QWERTY key map
│       │   │   └── method.xml             # IME descriptor
│       │   ├── values/
│       │   │   ├── colors.xml             # white background, black text
│       │   │   ├── strings.xml
│       │   │   └── styles.xml
│       │   └── drawable/
│       │       └── key_background.xml     # key states (no ad icons)
│       └── assets/
```

## Process log

1. `df1d62e` — Initial commit: placeholder README only (`# AMS-KEYBOARD`).
2. Scaffolded Android IME keyboard project with:
   - White keyboard background (`#FFFFFF`), black text (`#000000`), black keys text.
   - QWERTY layout with shift, caps, backspace, enter, space, and symbols toggle.
   - No ads and no exact icons copied from the reference.
   - README updated with design, features, structure, and build steps.
3. **Pending** — align the key layout exactly to the uploaded reference images once they are shared (none were attached to this conversation yet).

## Build

Requires Android Studio with the Android SDK and Gradle.

```bash
cd AMS-KEYBOARD
./gradlew assembleDebug
```

Install the debug APK on an emulator or device, then enable **AMS Keyboard** in Settings → Language & input → Keyboards.

## Continuous integration

See [`GITHUB_ACTIONS.md`](GITHUB_ACTIONS.md). The workflow in `.github/workflows/ci.yml` ignites on every merge to `main`, builds the debug APK, runs lint, and uploads:
- `ams-keyboard-debug-apk` — the built APK artifact
- `ams-keyboard-lint-report` — lint results
- `ams-keyboard-toolchain-report` — full tool chain / build report

## Next step

Please share the reference images (they did not come through in this conversation). Once received, I will adjust `app/src/main/res/xml/keyboard.xml` and `keyboard_view.xml` so the layout matches them exactly, keeping the white background and black text.
