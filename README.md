# Almara

![Almara logo](logo/almara-logo.jpg)

## What is Almara?

Almara is a private, offline-first Android file organiser. It gives local files a calendar view, search, and virtual folders without moving the original files. Remember maa’s nudge: **apna almara seedha karo** — get your digital cupboard in order.

Almara has no account, cloud sync, analytics, or internet permission. It indexes only the files and folders you allow, keeps its index and saved views on the device, and treats virtual folders as saved rules rather than copies.

The product landing page and help guide are available at [vijayarkananduri.github.io/Almara](https://vijayarkananduri.github.io/Almara/). The Android app includes a month heatmap, local search, virtual folders, optional expiry reminders, sharing, and selected offline conversion tools.

## Repository

This repository contains the Kotlin + Jetpack Compose Android app, local encrypted metadata storage, WorkManager indexing/expiry workers, and the GitHub Pages landing page. The `main` branch workflow builds an unsigned V1 release APK and a debug APK.
