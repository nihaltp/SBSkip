# AGENTS.md — SB Skip

## Project Overview
**SB Skip** — Android app that removes SponsorBlock segments from downloaded media (companion to NewPipe).
Stack: Jetpack Compose (Material 3), Hilt DI, Room, WorkManager, DataStore, Retrofit/OkHttp, ffmpeg-kit-lts-16kb.

## Build & Test Commands

| Task | Command |
|------|---------|
| Debug APK | `./gradlew assembleDebug` |
| Release APK | `./gradlew assembleRelease` |
| Unit tests | `./gradlew test` |
| Lint (ktlint) | `./gradlew ktlintCheck` |
| Format (ktlint) | `./gradlew ktlintFormat` |
| Clean | `./gradlew clean` |

**Fastlane lanes** (from repo root, requires `bundle exec`):
- `fastlane android version` — print version
- `fastlane android increment_patch|increment_minor|increment_major`
- `fastlane android test` — runs `./gradlew test`
- `fastlane android screenshots` — capture screengrab on device/emulator
- `fastlane android build_debug|build_release`
- `fastlane android github_release` — build, tag, push, create GH release

## Key Configurations
- **compileSdk/targetSdk**: 35 | **minSdk**: 26 | **Kotlin**: 2.2.0 | **AGP**: 8.10.1
- **ABI splits**: `arm64-v8a`, `armeabi-v7a` (no x86). VersionCode encoded per ABI.
- **Debug suffix**: `.debug` on `applicationId` and `versionName`.
- **Release**: minify + shrinkResources + ProGuard.
- **Signing**: `keystore.properties` (gitignored) must exist for release builds.
- **Room schema export**: `app/src/main/assets/schema/` (checked in).
- **Gradle JVM args** tuned for low-memory: see `gradle.properties`.

## Architecture Notes
- **MVVM + Clean** — ViewModels expose `StateFlow`; Hilt modules in `di/`.
- **WorkManager** — `DownloadWorker` orchestrates fetch → SponsorBlock → FFmpeg → save.
- **Storage** — `AndroidDownloadStorage` uses `MediaStore` + SAF (folder URIs persisted).
- **Settings** — `DataStoreSettingsRepository` (Preferences DataStore).
- **DB** — Room entities: `DownloadQueueEntity`, `DownloadedVideoEntity`.
- **Network** — Retrofit + OkHttp; SponsorBlock API + YouTube HTML parsing.
- **FFmpeg** — `FFmpegMediaProcessor` via `ffmpeg-kit-lts-16kb:6.1.7`.
- **Navigation** — Compose Navigation (`AppNavGraph`), single Activity.

## Testing
- **Unit**: JUnit 4 + MockK + Robolectric (`testOptions.unitTests.includeAndroidResources = true`).
- **Instrumented**: Espresso + Compose UI test (`androidTest/`). Screengrab for metadata screenshots.
- Fixtures in `app/build/test-fixtures/` (HTML snapshots for parser tests).

## Common Gotchas
- **No GitHub Actions CI** — validate locally: `./gradlew ktlintCheck test assembleDebug`.
- **keystore.properties** required for `assembleRelease`; debug builds work without it.
- **Gradle daemon disabled** (`org.gradle.daemon=false`) — builds start slower but use less RAM.
- **ABI-specific APKs** produced in `app/build/outputs/apk/<variant>/` — universal APK also built.
- **VersionCode scheme**: `versionCode * 1000 + abiSuffix` (see `app/build.gradle.kts:224-242`).
- **Changelog generation** — Fastlane reads git history since last tag; commits must follow conventional format (`type: message`).

## Directory Map (high-signal)
```
app/src/main/java/com/nihaltp/sbskip/
├── di/                 # Hilt modules
├── model/              # Data classes (Room entities, queue, settings)
├── workers/            # WorkManager workers + helpers
├── processing/         # FFmpegMediaProcessor, SegmentProcessor
├── storage/            # DownloadStorage, AndroidDownloadStorage
├── sponsorblock/       # SponsorBlockService (Retrofit)
├── util/
│   ├── fetcher/        # YouTubeDurationFetcher, YouTubePlaylistFetcher
│   └── parser/         # YouTube URL/title/metadata parsers
├── data/
│   ├── repository/     # QueueRepository, DownloadedVideoRepository, SettingsRepository
│   └── local/          # Room DB, DAOs, migrations
├── navigation/         # Compose destinations, ShareIntent handling
├── ui/
│   ├── main/           # MainScreen, ViewModel, dialogs, cards
│   ├── settings/       # SettingsScreen, ViewModel
│   └── history/        # DownloadedHistoryScreen
└── theme/              # Material3 theme, dynamic color
```

## PR Checklist (from CONTRIBUTING.md)
1. Discuss large changes in an Issue first.
2. `./gradlew compileDebugKotlin` passes.
3. `./gradlew test` passes.
4. `./gradlew ktlintFormat` applied.
5. Screenshots/recordings for UI changes.

## Release Flow
1. `fastlane android increment_patch` (or minor/major) — bumps versionName + versionCode, generates changelog, commits, tags.
2. `fastlane android github_release` — builds release APKs, pushes branch+tag, creates GH release with all ABI APKs.
