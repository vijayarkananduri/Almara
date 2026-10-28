# Almara

**Smart Sedha.** A privacy-first Android document hub: organize files into virtual views without moving them.

> Remember when maa said “apna almara seedha karo”? This is that nudge, digitized.

## About

Almara is a native Android app built with Kotlin and Jetpack Compose. It indexes permitted local media, shows a month heatmap, supports searching by name/source/type/date/size, and saves lightweight virtual views on-device. Files remain in their original locations. The app requests no `INTERNET` permission and has no account, cloud sync, or analytics.

## Current build status

This repository is an evolving pre-release build derived from the [product requirements](docs/PRD.md). It is not yet a complete v1.0 release.

Implemented in this MVP:

- Native Compose interface with Home, Search, Folders, and Settings destinations.
- First-run privacy/onboarding message and system, light, dark, and AMOLED theme choices.
- Permission-scoped MediaStore indexing for images, videos, and audio, plus recursive read-only indexing of user-selected SAF folders.
- On-device source labels, date-density calendar, and searchable local references.
- Search by text and `source:`, `type:`, `date:`, `name:`, and `size:` terms with helpful invalid-query feedback.
- Saved rule-backed virtual views; the app does not move or delete originals.
- Open/share actions through Android intents.
- Custom vintage-inspired Almara adaptive icon (arched sunburst/A monogram; not a file or folder illustration).
- Platform-independent query, file-type, source, date, and size domain helpers with JUnit tests.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact; publishing a GitHub Release attaches the APK.

Still not implemented for the full PRD v1: encrypted persistent file-index database (current metadata and saved views are in memory/app preferences), background incremental indexing, notification-backed expiry/archive, offline conversions and ZIP tools, duplicate detection, manual curation, widgets, Hindi localization, and an accessible radial action menu. The current folder creator does not implement live result-count preview or expiry controls. The local build must pass CI and be tested on real Android devices before any v1 or Play Store readiness claim.

## Privacy and permissions

The manifest deliberately contains no `INTERNET` permission and does not request `MANAGE_EXTERNAL_STORAGE`. Android permission prompts govern media access, and document folders are accessed only when explicitly selected through the system document picker. Almara does not copy, rename, or delete originals. Index results are currently rebuilt on refresh/app launch; saved virtual-filter metadata remains in app-private preferences. Uninstalling or clearing app storage removes saved preferences.

## Build locally

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and APK releases

Every push to `main` runs unit tests and **Android APK**. Use the workflow's **Run workflow** button for a manual run. Successful builds publish the APK as a 30-day downloadable Actions artifact. Publishing a GitHub Release also runs the build and attaches `Almara-<tag>-debug.apk` to that release.

The included APK is a debug build, unsigned for Play Store distribution. A production release needs a protected signing key and a separately configured signing workflow; never commit signing credentials.

## Project details

- Application ID: `com.almara.app`
- Minimum Android: 8.0 (API 26)
- Target/compile SDK: 35
- Version: `0.2.0` / version code `2` (unsigned debug build; not a v1 release)
- License: **not yet selected**. The PRD leaves GPL-3.0 vs Apache-2.0 open; no license grant is implied until that decision is made.

## Contributing

Issues and pull requests are welcome. Please preserve the privacy contract: no network access, no cloud upload, and no physical file move without an explicit user action.
