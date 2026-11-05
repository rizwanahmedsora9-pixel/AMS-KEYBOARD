# AMS-KEYBOARD — GitHub Actions CI

## Workflows

| File | Trigger | What it does |
|------|---------|--------------|
| `.github/workflows/ci.yml` | Push to `main`, or merged PR to `main` | Builds the debug APK, runs lint, uploads APK + tool chain report artifacts |

## Artifacts produced on every successful merge build

1. **`ams-keyboard-debug-apk`** — `app-debug.apk` (installable on emulator/device)
2. **`ams-keyboard-lint-report`** — `lint-results-debug.html`
3. **`ams-keyboard-toolchain-report`** — full tool chain / build report (`build-reports/`)

## How to view the reports

1. Open the **Actions** tab in this repository.
2. Select the workflow run for your merged PR / push.
3. Scroll to the **Artifacts** section and download the artifact you want.
4. Open `lint-results-debug.html` (or the files under `tool-chain-report`) to check issues and see where they are.

## Merge rule

The build only ignites when changes actually land on `main`:
- a direct push to `main`, **or**
- a pull request merged into `main` (`pull_request.closed` with `merged == true`)

No artifacts are produced for unmerged PRs.
