# AMS-KEYBOARD — GitHub Actions CI

## Workflows

| File | Trigger | What it does |
|------|---------|--------------|
| `.github/workflows/ci.yml` | Pull request activity targeting `main`, push to `main`, or manual dispatch | Validates the Gradle wrapper, builds the debug APK, runs lint, and uploads APK + tool chain report artifacts |

## Artifacts produced on every successful build

1. **`ams-keyboard-debug-apk`** — `app-debug.apk` (installable on emulator/device)
2. **`ams-keyboard-lint-report`** — lint results (`lint-results-debug.html` and any other generated lint reports)
3. **`ams-keyboard-toolchain-report`** — build outputs plus Java, Gradle, SDK, commit, and timestamp information (`build-reports/`)

The APK is uploaded immediately after `assembleDebug`, before lint runs. A missing or empty APK fails the workflow instead of silently uploading nothing. Lint findings are still reported but do not discard an already-built APK.

## How to view the reports

1. Open the **Actions** tab in this repository.
2. Select the workflow run for your pull request, push, or manual run.
3. Scroll to the **Artifacts** section and download the artifact you want.
4. Open `lint-results-debug.html` (or the files under `build-reports`) to check issues and tool chain details.

## Validation rule

- Pull requests targeting `main` are built when opened, updated, or reopened, so failures are caught before merge.
- Pushes to `main` (including merges) are built.
- Any branch can be built manually with **Run workflow** (`workflow_dispatch`).
- Closing a pull request without merging does not start another build.

## SDK setup and Gradle wrapper

The workflow uses `android-actions/setup-android@v4` and explicitly installs `platform-tools`, `platforms;android-34`, and `build-tools;34.0.0`. The older `setup-android@v3` default requested Google's retired `tools` SDK package, which caused CI to fail before compilation or artifact upload.

The repository includes the official Gradle 8.2 wrapper (`gradlew`, `gradlew.bat`, and `gradle/wrapper/`). CI validates the wrapper JAR before running it, and the wrapper properties pin the Gradle distribution checksum.
