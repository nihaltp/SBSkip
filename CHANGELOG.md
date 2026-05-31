# Changelog

All notable changes to this project will be documented in this file.

---

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
