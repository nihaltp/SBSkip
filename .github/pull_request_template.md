# Pull Request Template

## Description

Provide a clear description of the problem solved, what changes were made, and the expected user-facing impact.

---

## 🛠 Type of Change

*Please select the appropriate option(s):*

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 🎨 UI/UX design enhancement
- [ ] ⚙️ Performance optimization
- [ ] 📚 Documentation update / Fastlane adjustments
- [ ] 🧪 Testing improvement

---

## 🧪 Validation & Testing

*Please describe the verification process and validation checks you performed:*

- **Manual Verification**: *Describe step-by-step how you manually verified this change (e.g. settings screen, share sheet flow, queue updates).*
- **Automated Tests**: *List any unit/instrumented tests updated or executed (e.g. `ScreenshotTest` or `./gradlew test`).*

---

## 📸 Screenshots & Visual Diffs (If applicable)

*If your change alters the user interface (UI/UX) or layout, please attach screenshots or screen recordings (Before/After):*

| Before | After |
| --- | --- |
| *Upload / Describe* | *Upload / Describe* |

---

## 📋 Quality Checklist

- [ ] My code follows the code style guidelines in `CONTRIBUTING.md`.
- [ ] I have formatted my code locally using `./gradlew ktlintFormat` and run lint checks (`./gradlew ktlintCheck`).
- [ ] My changes compile cleanly without warnings or errors (`./gradlew compileDebugKotlin`).
- [ ] I have updated the documentation (`README.md`, `CHANGELOG.md`) if necessary.
- [ ] My changes do not introduce new security/privacy concerns or telemetry.
