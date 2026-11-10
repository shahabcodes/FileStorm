# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

FileStorm is an offline Android file manager (Kotlin + Jetpack Compose), published on Google Play as **File Storm: File Manager**, package `com.shahaabapps.filestorm`. Single `:app` module.

## Build and test

There is no Gradle wrapper in the repo; use a system Gradle **between 8.13 and 9.5** (CI uses 8.14.5). Gradle 9.6+ removed an internal API that AGP 8.11.1 needs and fails at plugin apply. JDK 17 is required.

```bash
gradle :app:assembleDebug                      # debug APK
gradle :app:testDebugUnitTest                  # all JVM unit tests
gradle :app:testDebugUnitTest --tests "com.shahaabapps.filestorm.data.vault.VaultCrashTest"   # one class
gradle :app:testDebugUnitTest --tests "*VaultEngineTest.someTestName"                        # one test
```

- `local.properties` needs `sdk.dir` with forward slashes (`C:/Users/.../Android/Sdk`); backslashes break the build on Windows.
- There are no instrumented tests and no lint config beyond Android defaults.
- Release builds are minified (R8 + resource shrinking). Anything reached by name instead of by a call R8 can see needs a rule in `app/proguard-rules.pro`.
- The README still says compileSdk 34; the real values are in `app/build.gradle.kts` (compileSdk/targetSdk 36, minSdk 26).

## Release flow

- `.github/workflows/android.yml` runs on every push to `main`. It builds a signed release **AAB and APK** and replaces the assets on the `latest` GitHub release (`FileStorm-v<versionName>.apk/.aab`). That AAB is what goes to Play.
- Signing only happens when `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` are set (CI restores them from secrets). Locally, `keystore/` (gitignored) holds the Play upload key. Never commit it or print its password.
- A release means bumping both `versionCode` and `versionName` in `app/build.gradle.kts`. Play rejects a reused versionCode.

## Architecture

**Navigation.** One activity (`MainActivity`, a `FragmentActivity` so biometric prompts work) hosts a single Navigation Compose `NavHost` with string routes (`home`, `browse`, `category/{kind}`, `vault?path=`, `archive?path=`, `arrange?path=&mode=`, `settings/{page}`, …). Screens live under `ui/<feature>/`. Shared visual building blocks are in `ui/components/` and `ui/theme/Theme.kt`.

**Long-running work = singleton engine + foreground service.** Each kind of background job is split the same way:

| Engine (`object`, owns a `StateFlow`) | Foreground service (keeps the process alive, mirrors state into a notification) |
|---|---|
| `transfer/TransferManager` (copy/move) | `transfer/TransferService` |
| `data/jobs/JobRunner` (organize-by-date jobs, persisted via `JobStore`) | `transfer/JobService` |
| `data/jobs/VerifyRunner` | `transfer/VerifyService` |
| `data/vault/VaultSession` → `VaultEngine` | `data/vault/VaultService` |
| `data/audio/AudioPlayer` | `data/audio/AudioService` (mediaPlayback) |

The UI collects the engine's `StateFlow` directly. It does not bind to the service. When you add a job type, follow this pattern and declare the service in the manifest with a `foregroundServiceType`.

**Vault (encrypted folders), `data/vault/`.** This is the most safety-critical code.
- The core (`VaultCrypto`, `VaultContainer`, `VaultEngine`, `VaultJournal`, `VaultFolder`) deliberately has **no Android imports**, so the unit tests can prove it on a plain JVM. Keep it that way. Android-specific pieces (thumbnails, moving originals to Trash) come in through `VaultOptions` callbacks.
- Encrypted files are stored under opaque two-character shard folders. The original path lives inside each file's encrypted header, which is how unlock rebuilds the tree.
- Crash safety rests on `VaultJournal`'s per-file state machine (`STARTED → WRITTEN → VERIFIED → PLACED → DONE`, or `FAILED`). The invariant the tests enforce is that after recovery, either the original is intact or the encrypted copy is complete and decrypts to the exact original bytes. `VaultCrashTest` injects a failure at every step boundary. Any change to the write/verify/place/delete order must keep that test passing.
- Master keys exist only in memory in `VaultSession`. Locking a vault means forgetting the key. Keystore is used only when the user opts into biometric unlock.

**Other data layer.** `FileRepository` handles listing and file operations; `ListingCache` lets folder listings survive the app being closed; `TrashManager` keeps trash on the file's own volume; `Prefs` holds settings; `DashboardCards` controls home-card order and visibility. `archive/`, `dup/`, `meta/` (EXIF/MP4 metadata, dates from filenames) and `arrange/` are self-contained feature engines.

**Switchable launcher icon/name.** `IconManager` enables exactly one `<activity-alias>` in `AndroidManifest.xml`. There is one alias per (icon × label) combination, named `L_<iconKey>_<labelKey>`, and only `L_opt25_storm` is enabled by default. Adding an icon or label means adding the full row of aliases, the matching `mipmap-anydpi-v26/ic_launcher_<key>.xml`, and an entry in `IconManager`.

**Settings search.** `ui/settings/SettingsIndex` powers settings search, and `SettingsIndexTest` pins down which everyday words must find which setting and which developer tools must stay hidden. Update both when you add or rename a setting.

## Store assets and copy

- `store/` holds Play graphics and screenshots, `STORE_LISTING.md` the listing text, and `PRIVACY.md` the privacy policy (the app is fully offline).
- `tools/StoreArt.java` is a standalone Java2D program that renders the Play icon and feature graphic from the app's own VectorDrawable path data. Re-run it after changing the Glass icon so the store art matches.
- Earlier iOS/Apple-styled wording was removed on purpose before launch. Don't bring it back into UI copy or store text.
