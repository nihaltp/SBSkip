# Changelog

All notable changes to this project will be documented in this file.

---

## [1.8.0] - 2026-08-02

### Added

- dc03919  feat: enhance AndroidDownloadStorage to handle relative paths correctly
- 3f025fc  feat: add changelog dialog to settings screen and update build config to include latest changelog
- a4d919b  feat: add translation request template and update related strings in settings
- 4c48c8c  feat: integrate AboutLibraries plugin and update dependencies in build.gradle
- 11c0c78  feat: add Turkish localization for strings and update translatable attributes
- eb679ef  feat: add language selection feature and support for Turkish localization
- 77cef5e  feat: add Turkish localization support and update metadata for app descriptions
- 5fe2af4  feat: update ScreenshotTest to enhance screenshot capturing and scrolling functionality
- 745cb65  feat: enhance APK resolution logic and improve screenshot lane for debug builds

### Style

- aafbf97  style: add border styling to buttons in PendingDownloadCard for improved UI

### Refactor

- b4a9da2  refactor: update MainViewModel to pass relativePath and folderUri in enqueuePendingDownload
- f90a5c9  refactor: remove AudioSaveMode and related functionality from settings and models

### Tests

- 3ecdd89  test: add unit tests for DetectedFile and PendingEnqueueData relativePath and folderUri

### Maintenance

- 8bff1ab  chore: bump fastlane from 2.236.1 to 2.237.0
- 3864708  chore: bump com.squareup.retrofit2:retrofit from 2.11.0 to 3.0.0 in /app
- b0cadc4  chore: bump androidx.compose:compose-bom from 2024.02.02 to 2026.06.01
- 785ff06  chore: Refactor release lane and add GH retry
- dd715fa  chore: update Kotlin plugin versions to 2.0.21 and add compose plugin

## [1.7.0] - 2026-07-28

### Added

- a9b126b  feat: optimize media processing by handling empty keep ranges and improving processing plan validation
- 71ebf08  feat: implement stacked toast notifications for user feedback and restore actions
- 807b3b3  feat: conditionally hide report button in error details dialog based on error type
- d981664  feat: allow dismissing notifications by swiping
- 6ccdfbf  feat: update YouTube thumbnail URL handling to use custom thumbnails when available
- 73a3def  feat: centralize URL handling by introducing Constants for YouTube and SponsorBlock URLs

### Refactor

- d831c60  refactor: simplify log viewing button in settings screen
- 0593ad5  refactor: improve audio conversion logic in ProcessingPlanBuilder

### Maintenance

- 8eae0eb  chore: update JVM arguments for Gradle and Kotlin daemon to optimize memory usage

## [1.6.0] - 2026-07-13

### Added

- a9e58e3  feat: add relativePath support and permission revoked dialog for folder access
- 217e4a7  feat: add auto start cleaning feature with corresponding settings
- e048dcb  feat: enhance screenshot tests with scrolling functionality for settings section

### Documentation

- c1f549d  docs: update README to include download instructions and repository URL

### Maintenance

- 67feab5  chore: remove x86 apk build
- 85530bc  chore: add dependabot and mergify configuration files

## [1.5.0] - 2026-07-04

### Fixed

- 0762ecd  fix: expand supported media formats and update FFmpeg error handling
- 765b648  fix: add check for non-empty output file before deleting original video

## [1.4.0] - 2026-06-30

### Added

- 641b03b  feat: add options when downloading a video

### Maintenance

- 54830f1  chore: use html in fastlane files
- 90cc43f  chore: add repobeats

## [1.3.1] - 2026-06-13

### Fixed

- 7ea0d50  fix: improve FFmpeg timestamp handling and format detection

### Added

- 77f5eb7  feat: add cover image for audio files
- 7d63b43  feat: add metadata about video
- 82b0632  feat: add SB Skip specific metadata
- 667f8a0  feat: add youtube video metadata

### Style

- 7cb8169  style: move video to audio settings to a new card
- 1c5b2eb  style: format files

### Refactor

- 8ed14af  refactor: split DownloadWorker into focused helper classes
- a2e405b  refactor: split MainScreen into components, dialogs, and utilities

### Tests

- 09be200  test: add tests to verify generated commands across all format extensions and range configuration modes

### Maintenance

- f5521cd  chore: update fastlane

## [1.3.0] - 2026-06-10

### Added

- 5b5558d  feat: Add Folders to Watchlist for watching downloaded file
- 90bf43d  feat: add runtime segment selection

### Style

- ab6798c  style: Update Logging and Style Errors

### Refactor

- 213f207  refactor: remove reduntant new pipe folder watching
- 2e04df4  refactor: removed scanning for folders outside watchlist
- 54787c2  refactor: remove "keep temp files" option

### Maintenance

- 9d0b3be  chore: update fastfile to list the commits in reverse

## [1.2.3] - 2026-06-10

### Added

- 4ff3084  feat: add export for logs

### Style

- 57d3004  style: format files
- 5dd96e8  style: add editorconfig

### Maintenance

- fa2ba27  chore: update fastlane file
- 4802ea8  chore: update .gitignore
- 4c107ac  chore: fix gradle properties for low memory devices
- 54e83fc  chore: fix gradle compatability

## [1.2.2] - 2026-06-03

Commits included in this release:
- 5bfe48a  feat: bypass duration mismatches for small differences
- 3fc9c6c  fix: universal APK versionCode generation

## [1.2.1] - 2026-06-02

Commits included in this release:
- 5c87642  fix: set audio save mode default to runtime picker
- 0bc8446  feat: implement duration mismatch handling

## [1.2.0] - 2026-06-02

Commits included in this release:
- cdee80b  feat: silence app notifications during fastlane screenshot
- b882a68  feat: install specific apk files
- 4172f18  feat: enhance URL handling to support sbskip scheme in DownloadQueueItem and DownloadWorker
- 21ae290  feat: refactor suffix setting display logic in SettingsScreen
- 0078c50  feat: implement file conflict handling with options to replace or rename existing files
- 3472a8f  feat: implement deleteUri method in DownloadStorage interface and its usage in DownloadWorker
- b3575dc  feat: add audio save mode settings and implement folder picker for audio output
- 4eef473  feat: implement file permissions handling and UI updates for media access
- ac7e90f  feat: update URI path resolution to include context for better handling
- e2d3a09  feat: implement pending downloads management and UI updates
- 58a1402  feat: add support for newPipe video and audio folder URIs in settings
- e28b15e  feat: implement file log rotation and add tests
- 98249b2  feat: add ABI splits and version code mapping for multiple architectures
- d2707eb  feat: enhance screenshot test with error handling and logging

## [1.1.0] - 2026-05-31

### Added

- Enhance video conversion functionality and UI for direct audio conversion. (5b3993d)
- Add video conversion options and update related settings. (7a2b796)
- Implement file-finding functionality for YouTube URLs in `MainViewModel` and update UI accordingly. (e39c5d8)
- Add SponsorBlock status URL handling and implement a status check worker. (2b0842a)
- Replace outlined icons with filled icons in `MainScreen` and `SettingsScreen`. (025d88f)

## [1.0.0] - 2026-05-29

### Added

- **Core Media Trimming**: Full integration with `ffmpeg-kit-lts-16kb` to slice and clean files based on retrieved timestamps.
- **SponsorBlock API Client**: Fully integrated Retrofit + OkHttp pipeline to asynchronously fetch skip segments from the SponsorBlock API.
- **Background Processing Engine**: Built on top of Android `WorkManager` to run cleaning pipelines reliably in the background with persistent progress reporting and foreground system notifications.
- **Queue Database**: Local Room database setup to store, retry, delete, and detail asynchronous download queue history items.
- **Settings Store**: Jetpack DataStore-backed app settings dashboard supporting custom cleaned file suffixes, downloader selection, custom SponsorBlock server URLs, active skip category checklists, and storage destination picking.
- **Dynamic Material You Theme**: Visual engine with harmonious dark/light templates, HSL-curated custom color systems, and modern transitions.
- **Monochrome & Adaptive Icons**: Full modern launcher icon support responding dynamically to device custom theme engines.
- **Automated Versioning & screenshots**: Fastlane build automation configuration including automated patch, minor, and major bumps as well as adb-synchronized locale screenshot capturing.
