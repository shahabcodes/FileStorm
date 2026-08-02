# FileStorm ⚡

A beautiful file manager for Android — smooth, premium, and built for moving files in bulk with total visibility.

## Features

- **Refined UI** — inset-grouped cards, large titles, clean typography, spring press animations, full light/dark mode.
- **Date-to-date selection** — pick a date range and every file modified in that window is selected instantly.
- **Bulk transfers with full visibility** — move or copy any number of files/folders and watch:
  - overall completion percentage with an animated progress ring
  - live transfer speed (EMA-smoothed) and estimated time remaining
  - per-file status: waiting, in progress (with its own progress bar), done, failed (with the reason), or skipped
  - a summary of what succeeded and what failed when the job ends
- **Smart move** — same-volume moves use instant rename; cross-volume falls back to copy + delete. Name conflicts auto-rename (`photo (1).jpg`).
- **Background-safe** — transfers run in a foreground service with a live progress notification; leaving the app doesn't kill the job.
- **Browse & organize** — storage overview ring, category views (Images, Videos, Audio, Docs, Archives, APKs), search, five sort modes, multi-select, rename, delete, new folder, image/video thumbnails.

## Install

Grab `FileStorm-vX.Y.apk` from the [latest release](../../releases/latest). Every push to `main` rebuilds it automatically.

## Build

```
gradle :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 34). Release builds are signed in CI from repo secrets.

## Stack

Kotlin · Jetpack Compose (Material 3, custom theme) · Coil (thumbnails) · single-activity Navigation Compose · foreground service + StateFlow transfer engine.
