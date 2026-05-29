# Contributing to SB Skip

Thank you for your interest in contributing to **SB Skip**! Our goal is to build the most robust, lightweight, and visual media cleaning utility for Android.

Please review these guidelines before submitting a pull request.

---

## 🛠 Project Environment & Setup

### Requirements

* **IDE**: Android Studio Koala (or later version).
* **JDK**: Version 17 configured for your Gradle project.
* **Platform**: SDK 35 (Compile and Target levels).

### Coding Style & Formatting (`ktlint`)

We use `ktlint` to automatically keep the codebase uniformly formatted.

* Before committing, run format checking:

  ```bash
  ./gradlew ktlintCheck
  ```

* To automatically fix formatting issues:

  ```bash
  ./gradlew ktlintFormat
  ```

---

## 🚀 Fastlane Lane Automation

Our pipeline is automated using Fastlane. You can run these commands from the root directory:

* **Check Version Name**: `bundle exec fastlane android version`
* **Patch Version Increment**: `bundle exec fastlane android increment_patch` (increments `0.0.X`)
* **Minor Version Increment**: `bundle exec fastlane android increment_minor` (increments `0.X.0`)
* **Major Version Increment**: `bundle exec fastlane android increment_major` (increments `X.0.0`)
* **Run Tests**: `bundle exec fastlane android test`
* **Capture Screenshots**: `bundle exec fastlane android screenshots` (runs capturing on a connected emulator/device)

---

## 📥 Pull Request Workflow

1. **Discuss First**: For larger refactors or feature changes, open a GitHub Issue first to align on design.
2. **Stay Focused**: Keep your pull request focused on one specific layer or feature change. Broad, unrelated refactoring in the same PR is discouraged.
3. **Validate**:
   * Verify that your changes compile successfully (`./gradlew compileDebugKotlin`).
   * Run local unit tests to ensure they pass (`./gradlew test`).
   * Format your code via `./gradlew ktlintFormat`.
4. **Submit**: Create your PR using our standard pull request template and include screenshots/recordings of any visual changes.

---

## 🧭 Contribution Details

### Code of Conduct

We expect contributors to follow a respectful and inclusive Code of Conduct. Be kind, patient, and constructive. If you encounter unacceptable behavior, contact the maintainers via a GitHub issue or email listed in the project metadata.

### Filing Issues

* Provide a clear, descriptive title.
* Include steps to reproduce, expected vs actual behavior, and relevant device/emulator information (Android API, device model, Android version).
* Attach logs when applicable. Use `adb logcat` and paste the relevant excerpt.

### Branching & Commits

* Work on a feature branch named using the pattern `feature/<short-desc>` or `fix/<short-desc>`.
* Keep commits focused and atomic. Rebase interactively to squash WIP commits before opening a PR.
* Commit message format:

  * Short summary (max 72 chars)
  * Blank line
  * More detailed explanatory text as needed

  Example:

  ```text
  Add SponsorBlock client caching

  Cache SponsorBlock responses for 5 minutes to reduce network
  requests and improve offline behavior.
  ```

### Testing Expectations

* Unit tests: Add or update unit tests for business logic changes. Run `./gradlew test`.
* Instrumented/UI tests: If touching UI or WorkManager behaviour, add instrumentation tests and run them on an emulator or device.
* CI: All PRs must pass the continuous integration checks (lint, unit tests, and build). Fix failures before requesting review.

### Reviewing & Approvals

* Maintainters will request changes or approve within a reasonable timeframe. Please address review comments promptly.
* Large changes may require an accompanying design doc or issue to discuss tradeoffs.

### Small Contributions & Issues

* For translations, typo fixes, small UI tweaks, or build script updates, small focused PRs are preferred and will be merged faster.

### Thank You

Thanks for taking the time to improve SB Skip — we appreciate your contributions!
