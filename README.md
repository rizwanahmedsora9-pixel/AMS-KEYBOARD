# AMS Keyboard

An Android soft keyboard (IME) with a dark QWERTY layout, a PC-style shortcut layer
(`Ctrl+A/C/V/X`, `Alt` combinations), auto-correct and unlimited clipboard history.
No ads, no tracking, no third-party libraries at runtime.

```
Install → open the AMS Keyboard app → Enable → Choose AMS Keyboard
```

## What is new in this build (1.1, versionCode 2)

| Request | What changed |
|---|---|
| **New builds install smoothly and upgrade the old one** | The app id (`com.ams.keyboard`), the signing key and the launcher activity never change, and `versionCode` now increases (`1 → 2`). Android therefore replaces an installed copy in place: settings, clipboard history and the "enabled keyboard" state survive. The app also shows the correct install/update state and a **Check again** button, so nothing has to be guessed. |
| **Icon was transparent / no AMS icon** | The old icon was a translucent white glyph. It is replaced by a fully opaque adaptive icon (`mipmap-anydpi-v26`, API 26+) plus opaque PNG fallbacks for older devices: a charcoal plate with the **AMS** wordmark and a teal space bar. Generation is reproducible with `tools/generate_icons.py`. |
| **Capital (Shift) key looked off** | The old layout used `⇧` at 15% width with no sticky state, so it looked clipped and never lit up. Shift now has its own column slot (12.5%), an outlined vector arrow, and a real latched state: tap once for one capital, double-tap for Caps Lock. Pressing Shift while auto-capitalisation is active cancels the capital instead of locking it. |
| **Keyboard size controls in the app** | New **Keyboard height** slider (70–150%) and **Row spacing** slider (1–10 dp) on the settings screen, and a drag handle above the suggestions while typing (long-press it to reset to 100%). The height is applied by `AmsKeyboard`, which recomputes every key rectangle, reports the new total height and clears the cached touch grid so taps stay accurate after a resize. |
| **Settings on opening the app** | The launcher screen is now a full settings panel: auto-correct, suggestions bar, auto-capitalize, double-space period, long-press symbols, key sound, vibrate, key popup, Alt shortcut strip, keyboard height, row spacing, clipboard on/off and clipboard history size (10–200 clips), plus reset and clear-history actions. Every switch writes to the same preferences file the keyboard reads, so changes apply the next time the keyboard appears. |
| **Ctrl+A / Ctrl+C / Ctrl+V / Ctrl+X and Alt** | `Ctrl` and `Alt` keys sit on the bottom row. Tap `Ctrl`, then a letter to send a real `Ctrl+letter` `KeyEvent` (works for `A`, `C`, `V`, `X`, `Z`, `Y`, and anything else the app supports). `Alt` does the same for `Alt+letter`. The toolbar, Quick tools panel and the dedicated Shortcuts pad also expose **Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V, Undo, Redo, Copy all, line start/end, select word** and a full `Alt + a…z` pad for one-tap access. |
| **`Ctrl+A` copies everything, unlimited text** | **Copy all** reads the whole editor through `getExtractedText()` / `getSurroundingText()` instead of relying on a selection, so the complete field is copied even if the app never highlighted it. Clipboard entries have no length limit: history stores the full string, only the card preview is shortened, and pasting is chunked in 8 KiB pieces so multi-hundred-kilobyte texts fit through Android's binder. |

## Keyboard layers

- **Letters**: QWERTY with number/symbol hints; long-press a letter for its number or symbol
  (the hints above the keys are finally typeable).
- **Symbols**: `?123` opens digits and punctuation; `ABC` returns.
- **Modifier column**: Shift (one-shot, double-tap Caps Lock), `Ctrl`, `Alt` — all latched until the
  next key, like a PC.
- **Toolbar**: Quick tools, Emoji, Clipboard, Shortcuts, Copy, Paste, Keyboard, Settings.
- **Suggestions strip**: up to three predictions plus the correction for the current word; tapping
  one replaces the word. It also hosts the compact `Ctrl+A / Ctrl+C / Ctrl+X / Ctrl+V / ↶ / ↷`
  row when *Alt shortcuts toolbar* is enabled.

## Auto-correct

`WordDictionary` ships a 3 000-word frequency-ordered English list
(`res/raw/english_words.txt`). A word is corrected on space, punctuation or Enter when:

- exactly one letter is wrong (`hellp → hello`), missing (`wrld → world`) or extra
  (`friennd → friend`), or two adjacent letters are swapped (`recieve → receive`); or
- two letters are wrong **and** the word has six or more letters **and** only one candidate
  matches (`becuase → because`).

Unknown words are never "corrected" into something arbitrary: if two or more candidates exist, the
text is left alone. Everything runs in memory (≈0.3 ms per lookup). Unit tests live in
`app/src/test/java/com/ams/keyboard/WordDictionaryTest.java`.

## Copy / paste

| Action | Behaviour |
|---|---|
| `Ctrl+A` / Select all | Sends a real `Ctrl+A` key event; the app selects its own text. |
| `Ctrl+C`, `Ctrl+X` | Framework copy/cut of the current selection. |
| `Copy all` (toolbar, Quick tools, Shortcuts pad) | Copies the entire field without needing a selection, of any length. |
| `Ctrl+V` / Paste | Inserts the clipboard text in chunks; falls back to the app's own paste action. |
| Clipboard history | Stores the **complete** text of every clip (up to the configured number of entries), tap to paste, long-press to pin, swipe left to delete. |

Android restricts background clipboard reads: like every Android keyboard, AMS can only observe the
clipboard while it is the active, visible input method.

## Known Android limits (not bugs in the app)

- **`sendKeyEvent` is advisory.** `Ctrl+C`, `Ctrl+V`, `Ctrl+A`, `Ctrl+Z` and `Alt+letter` are
  delivered as real key events, but the focused app decides what to do with them. Most text editors
  handle them; some chat apps ignore them. The dedicated toolbar buttons always work because they
  use the editor's own copy/cut/paste actions.
- **The suggestions bar is not the system IME bar.** Android 11+ draws its own suggestion strip for
  input methods that implement the `InputMethod` suggestion API. This keyboard draws its own strip
  inside the keyboard view instead, which is what allows corrections, clip counts and the shortcut
  strip to be shown together.
- **No inline composing.** Text is committed directly and the last word is edited (deleted and
  re-inserted) when a correction is applied, so the app's own undo stack sees it as an edit. This
  keeps the keyboard compatible with every editor, at the cost of the system undo not knowing the
  misspelling.
- **Play Store is not used** for updates: new versions are installed as APKs. Debug and release APKs
  are signed with different keys, so switching between those once requires uninstalling the old
  copy (explained in the app's About card).

## Project structure

```
AMS-KEYBOARD/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/ams/keyboard/
│   │   ├── AmsKeyboardService.java      # the IME: layers, modifiers, suggestions, clipboard
│   │   ├── AmsKeyboard.java             # resizable Keyboard subclass
│   │   ├── WordDictionary.java          # auto-correct + predictions
│   │   ├── KeyboardPreferences.java     # one place for every setting
│   │   ├── ShortcutPadView.java         # Ctrl/Alt shortcut pad
│   │   ├── ClipboardHistoryStore.java   # SQLite clipboard history
│   │   └── MainActivity.java            # setup + settings screen
│   └── res/
│       ├── drawable/                    # key states, toolbar icons, adaptive-icon layers
│       ├── layout/                      # settings screen, keyboard view, settings row
│       ├── mipmap-*/                    # launcher icon (opaque)
│       ├── raw/english_words.txt        # word list for suggestions
│       ├── values/                      # colours, dimensions, strings, theme
│       └── xml/                         # keyboard layouts and IME descriptor
├── app/src/test/java/com/ams/keyboard/WordDictionaryTest.java
├── tools/generate_icons.py              # regenerates the legacy launcher PNGs
└── THIRD_PARTY_NOTICES.md               # word-list provenance and licence note
```

## Build and install

Requires JDK 17 and an Android SDK with platform 34 and build-tools 34.0.0. The Gradle 8.2 wrapper is
included:

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:testDebugUnitTest  # auto-correct unit tests
```

On Windows use `gradlew.bat`. The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

Install it, open **AMS Keyboard**, press **Enable in settings**, then **Choose AMS Keyboard**. For
later versions just install the new APK over the old one.

## Continuous integration

`.github/workflows/ci.yml` runs the unit tests, builds the debug APK and runs lint for pull requests
targeting `main`, pushes to `main`, and manual runs. The APK, test report, lint report and tool chain
report are uploaded as artifacts. See [`GITHUB_ACTIONS.md`](GITHUB_ACTIONS.md).
