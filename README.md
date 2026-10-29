&lt;div align="center"&gt;

# Almara

**A private, offline-first Android file organiser — a calendar view for your files, without moving a single one.**

&lt;img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License" /&gt;
&lt;img src="https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white" alt="Android" /&gt;
&lt;img src="https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin" /&gt;
&lt;img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg" alt="Jetpack Compose" /&gt;
&lt;img src="https://img.shields.io/badge/Architecture-MVVM-blue.svg" alt="MVVM" /&gt;
&lt;img src="https://img.shields.io/badge/Open%20Source-Yes-brightgreen.svg" alt="Open Source" /&gt;
&lt;img src="https://img.shields.io/badge/Internet%20Permission-None-red.svg" alt="No Internet Permission" /&gt;

*A calmer way to find what matters in your digital cupboard.*

&lt;a href="https://vijayarkananduri.github.io/Almara/"&gt;Landing Page&lt;/a&gt; · &lt;a href="#download"&gt;Download APK&lt;/a&gt; · &lt;a href="../../issues"&gt;Report an Issue&lt;/a&gt; · &lt;a href="../../issues/new"&gt;Request a Feature&lt;/a&gt;

&lt;/div&gt;

---

## About

Almara is a private, offline-first Android file organiser. It gives local files a calendar view, search, and virtual folders without moving the original files. A calmer way to find what matters in your digital cupboard.

Almara has no account, no cloud sync, no analytics, and no internet permission. It indexes only the files and folders you allow, keeps its index and saved views on the device, and treats virtual folders as saved rules rather than copies.

This repository contains the Kotlin + Jetpack Compose Android app, local encrypted metadata storage, WorkManager indexing/expiry workers, and the GitHub Pages landing page. The `main` branch workflow builds an unsigned V1 release APK and a debug APK.

## Why Almara

Most file managers show you a wall of folders. Almara shows you **when** — a month heatmap of your files, so you can find last month's invoice, last year's photo, or that PDF you downloaded "sometime in March" without digging.

&gt; No account. No cloud. No analytics. No internet permission. Your files stay exactly where they are.

## Features

| Feature | Description |
|---|---|
| Month Heatmap | See your files as a calendar — instantly spot when things landed |
| Local Search | Fast, on-device search across everything you've allowed |
| Virtual Folders | Saved rule-based views — not copies. Original files never move |
| Expiry Reminders | Optional reminders for files you don't want to keep forever |
| Offline Conversion | Selected local conversion tools — no server, no upload |
| Share | Share files straight from any view |
| Private by Design | Encrypted local metadata, zero telemetry, zero network calls |

## Screenshots

&lt;div align="center"&gt;

&lt;table&gt;
&lt;tr&gt;
&lt;td align="center"&gt;&lt;b&gt;Home&lt;/b&gt;&lt;/td&gt;
&lt;td align="center"&gt;&lt;b&gt;Calendar View&lt;/b&gt;&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;
&lt;td&gt;&lt;img src="assets/builder.jpg" alt="Home" width="220" /&gt;&lt;/td&gt;
&lt;td&gt;&lt;img src="assets/cal.jpg" alt="Calendar view" width="220" /&gt;&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;
&lt;td align="center"&gt;&lt;b&gt;Search&lt;/b&gt;&lt;/td&gt;
&lt;td align="center"&gt;&lt;b&gt;Settings&lt;/b&gt;&lt;/td&gt;
&lt;/tr&gt;
&lt;tr&gt;
&lt;td&gt;&lt;img src="assets/search.jpg" alt="Search" width="220" /&gt;&lt;/td&gt;
&lt;td&gt;&lt;img src="assets/settings.jpg" alt="Settings" width="220" /&gt;&lt;/td&gt;
&lt;/tr&gt;
&lt;/table&gt;

&lt;/div&gt;

## Download

Grab the latest unsigned V1 release APK or the debug APK from the &lt;a href="../../actions"&gt;Actions&lt;/a&gt; workflow artifacts on the `main` branch.

Requires Android **8.0 (API 26)+** — adjust to your actual `minSdk`.

## Tech Stack

- **Kotlin** — 100% Kotlin codebase
- **Jetpack Compose** — declarative, modern UI
- **Room + Encrypted Metadata** — on-device storage, nothing leaves the phone
- **WorkManager** — background indexing and expiry reminder workers
- **MVVM architecture** — clean, testable separation

## Project Structure

```
Almara/
├── app/                    # Android app (Kotlin + Jetpack Compose)
│   ├── ui/                 # Compose screens (heatmap, search, folders)
│   ├── data/               # Room DB + encrypted metadata
│   └── workers/            # WorkManager indexing / expiry jobs
├── assets/                 # Landing page & README images
│   ├── builder.jpg         # Home screen
│   ├── cal.jpg             # Month heatmap / calendar view
│   ├── day.jpg             # Day view
│   ├── search.jpg          # Local search
│   ├── settings.jpg        # Settings
│   ├── logo.jpg            # App logo
│   ├── og.png / og.svg     # Social preview image
│   ├── timer.jpg           # Expiry reminder / timer
│   ├── site.js             # Landing page scripts
│   └── style.css           # Landing page styles
└── .github/workflows/      # CI — builds release + debug APK
```

## Build from Source

```bash
# Clone
git clone https://github.com/vijayarkananduri/Almara.git
cd Almara

# Build release APK (unsigned)
./gradlew assembleRelease

# Build debug APK
./gradlew assembleDebug
```

APKs are output to `app/build/outputs/apk/`.

## Roadmap

- [ ] Material You dynamic theming
- [ ] Virtual folder templates (e.g. "All PDFs this year")
- [ ] Widgets: month heatmap on home screen
- [ ] Tag-based organisation
- [ ] i18n / localisation

## Contributing

Contributions are welcome — bug reports, feature ideas, and pull requests:

1. **Fork** the repository
2. Create a feature branch — `git checkout -b feat/my-idea`
3. Commit with clear messages — `git commit -m "Add: my idea"`
4. Push and open a **Pull Request**

Please keep the core promise intact: offline-first, no trackers, no internet permission.

## Privacy

Almara indexes only the files and folders you explicitly allow. Its index, saved views, and metadata live on your device (encrypted). Virtual folders are saved rules, not copies. There is no account, no sync, no analytics, and the app requests no internet permission — this is not a policy, it's the architecture.

## License

MIT License — Copyright (c) 2026 Vijay Ark Ananduri

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

&lt;div align="center"&gt;

Made with zero internet permissions.

Star this repo if Almara brought some calm to your digital cupboard.

&lt;/div&gt;
