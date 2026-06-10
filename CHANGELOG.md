# Changelog

All notable changes to this project will be documented in this file.

---

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
