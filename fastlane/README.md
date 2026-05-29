fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android increment_patch

```sh
[bundle exec] fastlane android increment_patch
```

Increment patch version

### android increment_minor

```sh
[bundle exec] fastlane android increment_minor
```

Increment minor version

### android increment_major

```sh
[bundle exec] fastlane android increment_major
```

Increment major version

### android increment_version_code

```sh
[bundle exec] fastlane android increment_version_code
```

Increment version code

### android version

```sh
[bundle exec] fastlane android version
```

Print the current version name and code

### android clean

```sh
[bundle exec] fastlane android clean
```

Clean the project

### android build_debug

```sh
[bundle exec] fastlane android build_debug
```

Build the debug APK

### android build_release

```sh
[bundle exec] fastlane android build_release
```

Build the release APK

### android github_release

```sh
[bundle exec] fastlane android github_release
```

Build release artifacts for GitHub Releases

### android test

```sh
[bundle exec] fastlane android test
```

Run tests

### android screenshots

```sh
[bundle exec] fastlane android screenshots
```

Capture screenshots of the application using Screengrab

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
