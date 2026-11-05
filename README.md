# AMS Keyboard

An Android soft keyboard (IME) with a dark QWERTY layout inspired by the screenshots in `Reference Images/`, a compact shortcut toolbar, emoji and text-editing tools, and persistent clipboard history.

## Keyboard

- Dark charcoal keyboard with light labels and number hints above the QWERTY row.
- QWERTY, Shift, Backspace, Enter, Space, and a symbols/numbers layout.
- Toolbar shortcuts for Quick tools, Emoji, Clipboard, Copy, Paste, and Keyboard.
- Quick tools include Select all, Copy, Cut, and Paste.
- No ads or copied third-party branding.

## Clipboard history

- Saves up to the **50 most recent text clips** in a local SQLite database.
- Stores each clip's complete text, including long text and line breaks. The list only shortens the on-screen preview; tapping a clip pastes the full stored text.
- Long pastes are sent to the host editor in small chunks to avoid oversized Android Binder transactions.
- Tap a clip to paste it, swipe left to remove an item, or use the trash button to clear the history. The switch in the Clipboard header pauses/resumes collection. Clips marked sensitive by newer Android versions are ignored.
- Android restricts background clipboard access. Like other Android keyboards, AMS Keyboard can observe clipboard changes while it is the active, visible input method; it cannot silently monitor clipboard copies while hidden.

## Project structure

```
AMS-KEYBOARD/
├── README.md
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/ams/keyboard/
│   │   ├── AmsKeyboardService.java
│   │   ├── ClipboardHistoryStore.java
│   │   └── MainActivity.java
│   └── res/
│       ├── drawable/           # key states and toolbar vector icons
│       ├── layout/             # launcher and IME views
│       ├── values/              # colors, dimensions, strings, theme
│       └── xml/                 # keyboard layouts and IME descriptor
├── Reference Images/
└── GITHUB_ACTIONS.md
```

## Build and install

Requires JDK 17 and an Android SDK with platform 34 and build-tools 34.0.0 (Android Studio can install these). The official Gradle 8.2 wrapper is included, so no separate Gradle installation is needed:

```bash
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install the APK, then enable **AMS Keyboard** in Android Settings → Language & input → On-screen keyboard. Select it in an editable field to use it.

## Continuous integration

The workflow in `.github/workflows/ci.yml` builds the debug APK and uploads build/lint reports for pull requests targeting `main`, pushes to `main`, and manual runs. See [`GITHUB_ACTIONS.md`](GITHUB_ACTIONS.md).
