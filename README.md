# Almara

**Smart Sedha.** A privacy-first Android document hub: organize files into virtual views without moving them.

> Remember when maa said “apna almara seedha karo”? This is that nudge, digitized.

## About

Almara is a native Android app built with Kotlin and Jetpack Compose. It indexes permitted local media, shows a month heatmap, supports searching by name/source/type/date/size, and saves lightweight virtual views on-device. Files remain in their original locations. The app requests no `INTERNET` permission and has no account, cloud sync, or analytics.

## Current build status

This repository is the first runnable MVP derived from the [product requirements](docs/PRD.md), not a claim that every PRD v1.0 feature is complete.

Implemented in this MVP:

- Native Compose interface with warm/light and AMOLED-black themes.
- Permission-scoped MediaStore indexing for images, videos, and audio.
- On-device file metadata, source labels, and date-density calendar.
- Search by text and basic `source:`, `type:`, `date:`, `name:`, and `size:` terms.
- Saved virtual filter views; these do not move or copy files.
- Open/share actions through Android intents.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact; publishing a GitHub Release attaches the APK.

Not yet implemented: PDF/document/SAF tree indexing, encrypted Room database, background incremental indexing, accurate live virtual-folder evaluation, expiry notifications/archive, full rule grammar, conversions/ZIP tools, duplicate detection, manual folders, widgets, Hindi strings, and the advanced radial menu. Saved views currently persist in app preferences, not SQLCipher. Search operators not recognized by the current MVP are ignored; this is not yet the PRD's strict error-reporting parser.

## Privacy and permissions

The manifest deliberately contains no `INTERNET` permission and does not request `MANAGE_EXTERNAL_STORAGE`. Android permission prompts govern access to media. Document and PDF indexing is not included in this first build. Almara records media references in memory while running; it does not copy or rename indexed files. Saved filter names and options remain in app-private preferences. Uninstalling or clearing app storage removes those preferences.

## Build locally

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and APK releases

Every push to `main` runs **Android APK**. Use the workflow's **Run workflow** button for a manual run. Successful builds publish the APK as a 30-day downloadable Actions artifact. Publishing a GitHub Release also runs the build and attaches `Almara-<tag>-debug.apk` to that release.

The included APK is a debug build, unsigned for Play Store distribution. A production release needs a protected signing key and a separately configured signing workflow; never commit signing credentials.

## Project details

- Application ID: `com.almara.app`
- Minimum Android: 8.0 (API 26)
- Target/compile SDK: 35
- Version: `0.1.0` / version code `1`
- License: **not yet selected**. The PRD leaves GPL-3.0 vs Apache-2.0 open; no license grant is implied until that decision is made.

## Contributing

Issues and pull requests are welcome. Please preserve the privacy contract: no network access, no cloud upload, and no physical file move without an explicit user action.
