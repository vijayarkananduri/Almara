# Almara

**Smart Sedha.** A privacy-first Android document hub: organize files into virtual views without moving them.

> Remember when maa said “apna almara seedha karo”? This is that nudge, digitized.

## About

Almara is a native Android app built with Kotlin and Jetpack Compose. It indexes permitted local media, shows a month heatmap, supports searching by name/source/type/date/size, and saves lightweight virtual views on-device. Files remain in their original locations. The app requests no `INTERNET` permission and has no account, cloud sync, or analytics.

## Current build status

This branch contains the first full-v1 implementation pass derived from the [product requirements](docs/PRD.md). It is an **unsigned release candidate**, not a tested/public v1 release: GitHub Actions and real-device testing must pass before publishing a `v1.0.0` tag.

Implemented in this MVP:

- Native Compose interface with Home, Search, Folders, and Settings destinations.
- Optional-name first-run onboarding and system, light, dark, AMOLED, and Android 12+ wallpaper-derived theme choices.
- Permission-scoped MediaStore indexing and recursive read-only indexing of user-selected SAF folders, persisted in a Keystore-protected SQLCipher/Room database and reconciled by WorkManager.
- Source/date metadata, month heatmap, and local search including `source:`, `type:`, `date:`, `name:`, and `size:` terms.
- Rule-backed virtual folders, match-count preview, expiry timers, notification actions, and optional seven-day archive; file references only.
- Offline image re-encoding, image-to-PDF, PDF text extraction/page rasterization, Markdown-to-HTML/PDF, and bounded ZIP operations. Output is saved only through Android's document picker.
- Native month-heatmap and virtual-folder home-screen widgets.
- Unit-tested platform-independent query/domain helpers and a nostalgic custom adaptive Almara icon (not a file/folder drawing).
- Custom vintage-inspired Almara adaptive icon (arched sunburst/A monogram; not a file or folder illustration).
- Platform-independent query, file-type, source, date, and size domain helpers with JUnit tests.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact; publishing a GitHub Release attaches the APK.

Still to verify or complete against every PRD acceptance criterion: exact query grammar/date coverage, incremental-versus-full index behavior on large libraries, extraction destination UI, robust low-storage/large-file UX, duplicate detection (if retained in scope), Hindi localization, accessible radial file-action menu, and performance/usability instrumentation. Android notification delivery, encryption at rest, SAF permission restoration, widget rendering, and unsigned release installation have not yet been verified on physical devices. Do not treat the candidate as Play Store-ready until CI and device testing pass.

## Privacy and permissions

The manifest deliberately contains no `INTERNET` permission and does not request `MANAGE_EXTERNAL_STORAGE`. Android permission prompts govern media access, and document folders are accessed only when explicitly selected through the system document picker. Almara does not copy, rename, or delete originals. Index metadata and virtual-folder rules are encrypted in the app-private database; converted outputs are temporary until the user explicitly selects an export destination. Uninstalling or clearing app storage removes the index and rules, not source files.

## Build locally

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and APK releases

Every push to `main` runs unit tests and builds both a debug APK and an **unsigned release APK**. Successful builds publish both as a 30-day downloadable Actions artifact. Publishing a GitHub Release also attaches both APKs to that release.

The release variant intentionally has no signing configuration and is emitted as `app-release-unsigned.apk`; you will sign it after installing and testing it. The debug APK is a separate development artifact. No signing credentials are stored in GitHub.

## Project details

- Application ID: `com.almara.app`
- Minimum Android: 8.0 (API 26)
- Target/compile SDK: 35
- Version: `1.0.0` / version code `2` (unsigned release candidate; not yet tagged or device-validated)
- License: **not yet selected**. The PRD leaves GPL-3.0 vs Apache-2.0 open; no license grant is implied until that decision is made.

## Contributing

Issues and pull requests are welcome. Please preserve the privacy contract: no network access, no cloud upload, and no physical file move without an explicit user action.
