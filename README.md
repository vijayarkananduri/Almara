# Almara

**Smart Sedha.** A privacy-first Android document hub: organize files into virtual views without moving them.

> Remember when maa said “apna almara seedha karo”? This is that nudge, digitized.

## About

Almara is a native Android app built with Kotlin and Jetpack Compose. It indexes permitted local media, shows a month heatmap, supports searching by name/source/type/date/size, and saves lightweight virtual views on-device. Files remain in their original locations. The app requests no `INTERNET` permission and has no account, cloud sync, or analytics.

## Current build status

This branch contains the v1 implementation pass derived from the [product requirements](docs/PRD.md). It remains an unsigned release candidate; real-device testing is still required before publishing a public release.

Implemented in this MVP:

- Native Compose interface with Home, Search, Folders, and Settings destinations; long-press opens an accessible radial menu for Open, Share, Tools, and Remove-from-view.
- Optional-name first-run onboarding and system, light, dark, AMOLED, and Android 12+ wallpaper-derived theme choices.
- Permission-scoped MediaStore indexing and recursive read-only indexing of user-selected SAF folders, persisted in a Keystore-protected SQLCipher/Room database and reconciled by WorkManager.
- Source/date metadata, month heatmap, and local search including `source:`, `type:`, `date:`, `name:`, and `size:` terms.
- Rule-backed virtual folders, match-count preview, expiry timers, notification actions, and optional seven-day archive; file references only.
- Offline image re-encoding, image-to-PDF, PDF text extraction/page rasterization, Markdown-to-HTML/PDF, bounded ZIP creation/extraction, and a user-selected SAF destination for extracted files. Output is saved only through Android's document picker.
- Native month-heatmap and virtual-folder home-screen widgets.
- Unit-tested platform-independent query/domain helpers and a nostalgic custom adaptive Almara icon (not a file/folder drawing).
- Custom vintage-inspired Almara adaptive icon (arched sunburst/A monogram; not a file or folder illustration).
- Platform-independent query, file-type, source, date, and size domain helpers with JUnit tests.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact; publishing a GitHub Release attaches the APK.

The current release focuses on reliable all-file indexing, document/spreadsheet visibility, unified search behavior, name-aware smart folders, and clear virtual-folder semantics. Manual collections, duplicate detection, Hindi localization, and full integration/Compose/performance coverage remain follow-up work. The worker currently reconciles a full scan rather than demonstrating incremental indexing on large libraries. Extraction, search grammar, notification behavior, encryption at rest, SAF permission restoration, widget rendering, low-storage handling, and APK installation still need real-device verification. This is a device-test candidate, not a claim of complete v1 or Play Store readiness.

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
