# Almara — Product Requirements Document (PRD) v2.0

**App Name:** Almara  
**Tagline:** **Smart Sedha.**  

**Platform:** Android (minSdk 26, target 35)  
**Language:** Kotlin + Jetpack Compose  
**License:** TBD (GPL-3.0 or Apache-2.0)  
**Status:** Draft — Ready for Development  
**Last Updated:** 2026-10-28

---

## 1. Executive Summary

### 1.1 Product Vision
Almara is a **privacy-first Android document hub** that organizes your files into **virtual folders** (references, not physical moves) and gives you a **heatmap calendar** to see file density at a glance. It is built on a nostalgic truth: **every Indian kid grew up hearing "apna almara seedha karo."** Almara is that nudge — digitized, smartened, and made private.

**No cloud. No account. No `INTERNET` permission. Files never move. Smart Sedha.**

### 1.2 The Problem
Android file managers fail in four ways:
- **Too technical** — full filesystem browsers overwhelm non-technical users
- **Too dumb** — Files by Google has no saved views, no cross-source grouping
- **Photo-only** — Google Photos ignores PDFs, voice notes, documents
- **Cloud-tied** — Drive/Dropbox require accounts and upload your data

**Nobody lets you say:** *"Show me everything from WhatsApp last week — images, PDFs, and voice notes together"* — without moving files, without cloud, without an account.

### 1.3 The Solution
| Pillar | What it does |
|--------|-------------|
| **Virtual folders** | Saved rules/queries that auto-update. Files stay in place. |
| **Heatmap calendar** | Month grid colored by file count. Tap a day → see that day's files. |
| **FAB creator** | Pick source + type + time → preview count → create folder. ≤ 5 taps. |
| **Query bar** | Prefix syntax (`source:whatsapp type:pdf date:last-week`) for power users. |
| **Radial menu** | Long-press any file → circular action menu including offline conversion. |
| **Expiry timer** | Optional per-folder auto-delete. Files untouched. Notification before expiry. |

### 1.4 Positioning & Brand Voice

| Element | Value |
|---------|-------|
| **Name** | Almara (digital cupboard) |
| **Tagline** | **Smart Sedha.** |

| **Voice** | Warm, slightly wry, confident. Like a mother who knows best — but never preachy. |
|

**Brand principles:**
1. **Organized, not relocated.** — The core philosophy, in four words.
2. **Smart Sedha.** — The tagline. Bilingual pun: smart + neat/straight.


### 1.5 Target User
- Privacy-conscious Android users (25–45)
- People with phones full of WhatsApp/Telegram files with ugly names
- Users who want organization without learning a query language
- Low-to-mid-range phone users (2–4 GB RAM)
- **Nostalgia-aware:** users who grew up with "almara seedha karo" and now manage digital clutter

---

## 2. Goals & Non-Goals

### 2.1 Goals (v1.0)

| # | Goal | Success Metric |
|---|------|---------------|
| G1 | Organize files without moving them | 0 physical file moves |
| G2 | Create a useful virtual folder in ≤ 5 taps | FAB flow ≤ 5 taps |
| G3 | Show file density per day at a glance | Heatmap renders < 300ms |
| G4 | Find any file by date/source/type in ≤ 3 interactions | Query → result ≤ 3 taps |
| G5 | Zero network access | No `INTERNET` permission in manifest |
| G6 | Work 100% offline | All features offline |
| G7 | Cold start < 1.5s on mid-range device | Macrobenchmark |
| G8 | Feel like "maa's nudge" | ≥ 80% of usability testers describe it as "familiar/organic" |

### 2.2 Non-Goals (v1.0)
- ❌ Cloud sync, account, login
- ❌ Video conversion
- ❌ DOCX ↔ PDF conversion
- ❌ OCR / AI tagging / face detection
- ❌ Full-screen photo editor
- ❌ System-wide overlay / accessibility hacks
- ❌ `MANAGE_EXTERNAL_STORAGE` (Play Store policy risk)
- ❌ Physical file moving/renaming (except explicit user action)

---

## 3. Core Concepts

### 3.1 File Reference
Every file is stored as a **URI reference** + metadata in a local encrypted DB. The file stays where it is on disk. Deleting the app's DB = deleting references only. **Zero data loss risk.**

### 3.2 Virtual Folder
A named view of files. Two types:
- **Rule-backed:** auto-populated by a saved rule (e.g., "WhatsApp · PDFs · This week")
- **Manual:** user explicitly picks files (v1.5)

Both are DB rows. Deleting a folder = deleting a row. **Files untouched.**

### 3.3 Rule
A saved set of conditions:
- **Source** (WhatsApp, Telegram, Camera, Screenshots, Downloads, Other, All)
- **Type** (Images, PDFs, Docs, Audio, Archives, Text, All)
- **Time range** (Today, This week, This month, Custom range)
- **Advanced** (query bar only): name contains, size range, regex

### 3.4 Heatmap Calendar
A month grid where each day's color intensity = file count. **5 discrete buckets.** Tap a day → file list scrolls to that day. Swipe left/right → change month. Colors derived from wallpaper seed.

### 3.5 Expiry Timer (opt-in)
A virtual folder can have an optional `expiresAt`. When set:
- Clock icon appears on folder tile
- Local notification fires before expiry (proportional lead time)
- On expiry: folder row deleted (or archived if opted in). **Files untouched.**

---

## 4. Design Language: "Tactile Clarity"

### 4.1 Design Pillars
1. **Warm, not cold** — Material 3 Expressive, soft shapes, spring motion.
2. **Personal, not generic** — Wallpaper-based theming. Your app, your colors.
3. **Quiet, not flashy** — AMOLED-first. Black is a feature, not a bug.
4. **Regional, not global** — Hindi + English. Maa's voice. Almara metaphor.
5. **Optional depth** — Liquid Glass is premium, off by default.

### 4.2 Typography
| Element | Spec |
|---------|------|
| **Primary font** | **Google Sans Flex** (variable font, SIL Open Font License — free for commercial use) |
| **Weights** | 400 (body), 500 (labels), 600 (headings), 700 (hero) |
| **Scale** | Material 3 type scale (Display, Headline, Title, Body, Label) |
| **Hindi** | Noto Sans Devanagari (fallback for Devanagari script) |

### 4.3 Core Aesthetic: Material 3 Expressive (Soft 3D Bubble)
- **3D bubble feel** from **tonal elevation** (surface color shifts) and **expressive shapes** (varied corner families, some asymmetric), not heavy shadows.
- **Motion**: physics-based spring animations. UI elements "bounce" gently.
- **Shape language**: `MaterialShapes` from Compose. Soft, varied corners. Some cards asymmetric for character.
- **No flat design.** Every surface has subtle depth through color, not shadow.

### 4.4 Theme Engine

| Feature | Spec |
|---------|------|
| **Wallpaper theming** | Android 12+ Dynamic Color API. Extract seed color from wallpaper → generate all UI colors. |
| **AMOLED Force** | Settings toggle. When ON: pure black (`#000000`) for all backgrounds. Accent colors and text only. Battery savings + premium look. |
| **Fallback palette** | Manual palette for Android < 12 or if user disables dynamic color. |
| **Dark/Light** | Both supported. AMOLED is a third mode (dark + pure black). |

### 4.5 Heatmap Calendar Colors

Colors derived from **wallpaper seed color**, not fixed green.

| Bucket | Light theme | Dark theme | AMOLED |
|--------|-------------|------------|--------|
| 0 files | Outline only (`#F5F5F5`) | Outline (`#2A2A2A`) | Pure black (`#000000`) |
| 1–3 | Seed @ 20% | Seed @ 30% | Seed @ 40% |
| 4–10 | Seed @ 40% | Seed @ 50% | Seed @ 60% |
| 11–25 | Seed @ 60% | Seed @ 70% | Seed @ 80% |
| 26+ | Seed @ 90% + dot | Seed @ 90% + dot | Seed @ 100% + dot |

**Accessibility:** Must pass WCAG AA contrast for text overlay. Color + number overlay on tap (not color-only).

### 4.6 Liquid Glass (Optional, Premium)

| Element | Spec |
|---------|------|
| **Default** | OFF |
| **Toggle** | Settings → "Premium effects" |
| **Libraries** | Compose libraries (`Prismal`, `Vitro`) for iOS-style liquid glass (backdrop blur, edge refraction). |
| **Use case** | **Only** FAB bottom sheet and radial menu. Not full app. |
| **Performance** | Automatic fallback for older Android versions. Never blocks UI. |

### 4.7 Motion & Animation
- **Spring physics** for all transitions (Compose `animateFloatAsState` with spring specs).
- **Radial menu** expands with a spring bounce.
- **Heatmap** cells fade in on month change.
- **FAB sheet** slides up with slight overshoot.
- **No linear animations.** Everything feels alive.

### 4.8 Shape & Elevation
- **Cards**: 16–24dp corner radius, asymmetric (top-left larger).
- **FAB**: 20dp radius, soft shadow.
- **Bottom sheet**: 28dp top corners.
- **Chips**: full pill (50% radius).
- **Elevation**: tonal, not shadow-heavy. Surfaces shift color slightly.

### 4.9 Iconography
- **Material Symbols** (rounded, outlined).
- **Custom icons** for: Almara logo, virtual folder, heatmap, expiry timer.
- **Almara logo**: A simple cupboard silhouette with a warm glow (heatmap nod). Minimal, geometric, memorable.

---

## 5. Features (v1.0)

### 5.1 File Indexing

| Requirement | Detail |
|-------------|--------|
| Sources | MediaStore (images, docs, audio) + SAF tree (user-approved folders) |
| Filter | Only docs/images/archives/text shown. No system files. |
| Diffing | Incremental — new URIs inserted, missing marked stale, changed metadata updated |
| Background | WorkManager periodic (6h) + on-demand on app open |
| Date resolution | Priority: filename date > EXIF `dateTaken` > `lastModified` |
| Date storage | UTC epoch millis + denormalized `date_day` ("2024-01-15") column, indexed |
| Thumbnails | Generated via Coil, cached in `cacheDir/thumbs/` |

**Acceptance Criteria:**
- 10k files indexed in < 15s on mid-range phone
- Heatmap month query < 300ms
- App usable while indexing (non-blocking)

### 5.2 Source Detection (deterministic, no AI)

Parse `MediaStore.RELATIVE_PATH` + filename pattern:

| Path pattern | Source label |
|-------------|-------------|
| `WhatsApp/Media/WhatsApp Images` | WhatsApp |
| `WhatsApp/Media/WhatsApp Documents` | WhatsApp |
| `WhatsApp/Media/WhatsApp Voice Notes` | WhatsApp |
| `Telegram/Telegram Images` | Telegram |
| `Telegram/Telegram Documents` | Telegram |
| `Pictures/Screenshots` | Screenshot |
| `DCIM/Camera` | Camera |
| `Download/` | Downloaded |
| Unknown | Other |

**WhatsApp filename parser (regex):**
```
IMG-YYYYMMDD-WA####.ext  → type=Image
VID-YYYYMMDD-WA####.ext  → type=Video
PTT-YYYYMMDD-WA####.ext  → type=VoiceNote
DOC-YYYYMMDD-WA####.ext  → type=Document
STK-YYYYMMDD-WA####.ext  → type=Sticker
```

**Telegram filename parser:**
```
photo_YYYY-MM-DD_HH-MM-SS.jpg  → type=Image
video_YYYY-MM-DD_HH-MM-SS.mp4  → type=Video
document_YYYY-MM-DD_HH-MM-SS.ext → type=Document
```

**Acceptance Criteria:**
- 95%+ of WhatsApp/Telegram files correctly source-tagged and date-parsed
- Unknown sources labeled "Other" (never crash)

### 5.3 Heatmap Calendar

**Layout:**
```
┌──────────────────────────────┐
│  ◀  January 2024  ▶     [📅] │
│  M  T  W  T  F  S  S         │
│  ░  ▓  ░  ▓▓ ░  ░  ▓▓▓      │
│  ▓  ░  ▓▓ ░  ░  ▓  ░        │
│  ░  ▓  ░  ░  ▓▓ ░  ░        │
│  ▓▓ ░  ░  ▓  ░  ▓▓ ░        │
│  ░  ░  ▓  ░  ░  ░  ░        │
├──────────────────────────────┤
│  Jan 15 · 7 files            │
│  [img] [img] [pdf] [voice]   │
│  Jan 14 · 3 files            │
│  [img] [pdf] [img]           │
└──────────────────────────────┘
```

**Interactions:**
- Tap day → file list scrolls to that day (does not replace list)
- Swipe left/right → previous/next month
- Tap month header → date picker jump
- Empty month → "No files this month" + "Jump to last active month" button
- Long-press day → quick preview bottom sheet with that day's files

**Acceptance Criteria:**
- Renders in < 300ms for any month
- Counts accurate after index update
- Accessible: color + number overlay on tap (not color-only)

### 5.4 FAB Virtual Folder Creator

**Flow:**
```
[+ FAB] → bottom sheet
  ┌────────────────────────────────┐
  │  New Virtual Folder            │
  │                                │
  │  Source:                       │
  │  [WhatsApp] [Telegram] [Camera]│
  │  [Screenshot] [Download] [All] │
  │                                │
  │  Type:                         │
  │  [Images] [PDFs] [Docs]        │
  │  [Audio] [Archives] [All]      │
  │                                │
  │  Time range:                   │
  │  [Today] [This week] [Month]   │
  │  [Custom range…]               │
  │                                │
  │  ────────────────────────────  │
  │  Preview: 47 files match       │
  │  ────────────────────────────  │
  │  Folder name: [WhatsApp · PDFs · This week] │
  │                                │
  │  [Advanced ▾]  (shows query)   │
  │                                │
  │  [Cancel]         [Create]     │
  └────────────────────────────────┘
```

**Rules:**
- Preview count updates live (debounced 300ms) as chips toggle
- Name auto-generated from selections, editable
- "Advanced" reveals generated query text (teaches query language by example)
- ≤ 5 taps from FAB to created folder
- Custom range opens a date range picker (start/end, not full calendar)
- **On create:** toast "Maa would be proud." (subtle, once per session)

**Acceptance Criteria:**
- User creates a folder without typing in < 15 seconds
- Preview count matches actual folder contents within 1 file

### 5.5 Query Bar

**Prefix syntax (v1.0):**
```
date:2024-01-15      date:2024-01        date:last-week
date:today           date:yesterday      date:this-month
source:whatsapp      source:telegram     source:camera
source:screenshot    source:download     source:other
type:image           type:pdf            type:doc
type:audio           type:archive        type:text
name:invoice         size:>5mb           size:<1mb
```

**Combinable:** `source:whatsapp type:pdf date:last-week size:>1mb`

**Query chips (tap-to-append):**
```
[📅 This week] [📱 WhatsApp] [📄 PDFs] [🔍 Recent] [⭐ Starred]
```
Non-technical users tap. Power users type. Both produce the same `Rule`.

**Save as virtual folder:** Any query → "Save as folder" → creates rule-backed virtual folder.

**Acceptance Criteria:**
- Query returns results in < 200ms for 10k files
- Invalid query shows helpful error ("Unknown prefix: `foo`. Try `source:`, `type:`, `date:`, `name:`, `size:`")
- Query bar visible on main screen (collapsible on scroll)

### 5.6 Long-Press Radial Menu

Long-press any file → circular menu around the file:

```
        [Open]
    [Share]  [Rename]
  [Convert]      [Add to folder]
    [Zip]    [Delete]
       [Extract]
```

| Action | Available for | Notes |
|--------|--------------|-------|
| Open | All | Uses system intent (gallery, PDF viewer, etc.) |
| Share | All | Android share sheet |
| Rename | All | Explicit user action, confirms |
| Add to folder | All | Opens folder picker |
| Convert | Images, PDFs, TXT, MD, ZIP | Opens format picker |
| Zip | Multi-select | Creates ZIP in cacheDir |
| Extract | ZIP only | Extracts to user-chosen folder |
| Delete | All | Confirms; deletes file OR just reference (user choice) |

**Animation:** Radial expands with spring bounce. Liquid Glass backdrop if enabled.

**Acceptance Criteria:**
- Radial opens in < 100ms
- All actions complete or show clear error
- Accessible via TalkBack (radial items announced)

### 5.7 Offline Conversion (v1.0 scope)

| Converter | Library | Ship |
|-----------|---------|------|
| Image → Image (PNG/JPG/WebP) | Android `ImageDecoder` + `Bitmap.compress` | ✅ |
| Images → PDF (multi-select) | PdfBox-Android | ✅ |
| PDF → Images (per page) | PdfBox-Android | ✅ |
| PDF text extract → TXT | PdfBox-Android | ✅ |
| ZIP create | `java.util.zip` | ✅ |
| ZIP extract | `java.util.zip` | ✅ |
| MD → HTML | commonmark-java | ✅ |
| MD → PDF | Custom + PdfBox | ✅ |
| DOCX → PDF | ❌ later | ❌ |
| PDF → DOCX | ❌ later | ❌ |
| Video anything | ❌ skip | ❌ |

**Rules:**
- Always explicit user action (never automatic)
- Output to `cacheDir` → user chooses save location via SAF
- Temp files deleted on app exit or after 1h TTL
- Clear progress + error UI
- No network permission = no accidental upload

### 5.8 Expiry Timer (opt-in)

**UI:** Clock icon on virtual folder tile. Tap → timer settings sheet:
```
┌────────────────────────────────┐
│  Auto-delete after:            │
│  [Off] [1 hour] [1 day] [1 week]│
│  [Custom…]                     │
│                                │
│  ☐ Archive instead of delete   │
│                                │
│  "This folder will disappear   │
│   in 1 day. Files stay on      │
│   your phone. You can recreate │
│   it anytime."                 │
└────────────────────────────────┘
```

**Notification (proportional lead time):**
| Timer | Notify at |
|-------|-----------|
| 1 hour | 10 min left |
| 1 day | 2 hours left |
| 1 week | 1 day left |
| Custom | 10% of remaining time |

**Notification content:** "Folder 'Trip Photos' expires in 2 hours. [Keep] [Let expire]"

**On expiry:**
- Default: delete folder row. Files untouched.
- If "Archive" checked: move to collapsed "Expired" section for 7 days, then delete.

**Rules:**
- Timer is **opt-in per folder**. Default = permanent.
- Never apply timer to manually curated folders without explicit user action.
- Files are **never** deleted by expiry. Only folder rows.

### 5.9 Duplicate Detection (v1.5)
- SHA-256 hash of files during indexing (images/docs < 50 MB)
- Group duplicates → "3 copies found" badge
- Actions: "Keep one, hide others" (hide = mark stale, not delete)

---

## 6. Architecture

### 6.1 High-Level Layers

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose + Material 3 Expressive) │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ Heatmap  │ │ FileList │ │ FAB      │ │ Radial │ │
│  │ Calendar │ │          │ │ Creator  │ │ Menu   │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ QueryBar │ │ Timer    │ │ Settings │             │
│  │          │ │ Sheet    │ │ (AMOLED, │             │
│  │          │ │          │ │  Glass)  │             │
│  └──────────┘ └──────────┘ └──────────┘             │
├─────────────────────────────────────────────────────┤
│  ViewModel Layer (AndroidX ViewModel + StateFlow)   │
├─────────────────────────────────────────────────────┤
│  Domain Layer (pure Kotlin, no Android deps)        │
│  ┌────────────┐ ┌──────────────┐ ┌───────────────┐ │
│  │ RuleEngine │ │ VirtualFolder│ │ Conversion    │ │
│  │            │ │ Resolver     │ │ Orchestrator  │ │
│  └────────────┘ └──────────────┘ └───────────────┘ │
│  ┌────────────┐ ┌──────────────┐ ┌───────────────┐ │
│  │ QueryParser│ │ DateResolver │ │ SourceDetector│ │
│  └────────────┘ └──────────────┘ └───────────────┘ │
├─────────────────────────────────────────────────────┤
│  Data Layer                                         │
│  ┌────────────────┐ ┌────────────┐ ┌─────────────┐ │
│  │ FileIndexRepo  │ │ RuleRepo   │ │ FolderRepo  │ │
│  └────────────────┘ └────────────┘ └─────────────┘ │
│  ┌────────────────────────────────────────────────┐ │
│  │ Room DB + SQLCipher (encrypted)                │ │
│  └────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────┤
│  Platform Layer                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ SAF      │ │MediaStore│ │WorkMgr   │ │Notif   │ │
│  │ Access   │ │ Access   │ │(indexing,│ │Manager │ │
│  │          │ │          │ │ expiry)  │ │        │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐             │
│  │ Coil     │ │ PdfBox   │ │ Keystore │             │
│  │ (thumbs) │ │ (PDF)    │ │ (DB key) │             │
│  └──────────┘ └──────────┘ └──────────┘             │
└─────────────────────────────────────────────────────┘
```

### 6.2 Data Model (Room + SQLCipher)

```kotlin
// ─── File reference (never a copy) ───
@Entity(tableName = "file_ref")
data class FileRef(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val dateDay: String,                   // "2024-01-15" (indexed)
    val dateEpochMillis: Long,
    val sourceLabel: String,
    val parsedDate: Long?,
    val contentHash: String?,
    val indexedAt: Long,
    val isStale: Boolean = false
)

// ─── Rule (saved query) ───
@Entity(tableName = "rule")
data class Rule(
    @PrimaryKey val id: String,
    val name: String,
    val priority: Int,
    val enabled: Boolean,
    val matchMode: MatchMode,              // ALL / ANY
    val conditionsJson: String,
    val isExclusive: Boolean,
    val createdAt: Long
)

// ─── Virtual folder ───
@Entity(tableName = "virtual_folder")
data class VirtualFolder(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?,
    val ruleId: String?,
    val expiresAt: Long?,
    val archiveOnExpiry: Boolean = false,
    val sortOrder: Int,
    val createdAt: Long
)

// ─── Manual folder references (v1.5) ───
@Entity(tableName = "virtual_folder_ref")
data class VirtualFolderRef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: String,
    val fileUri: String,
    val addedAt: Long
)
```

**Indexes:** `date_day`, `sourceLabel`, `mimeType`, `isStale`, `expiresAt`.

### 6.3 Rule Engine (Domain Layer — pure Kotlin)

```kotlin
sealed class Condition {
    data class SourceIs(val source: String) : Condition()
    data class ExtensionIn(val extensions: Set<String>) : Condition()
    data class MimeTypeIn(val patterns: Set<String>) : Condition()
    data class DateInRange(val start: Long, val end: Long) : Condition()
    data class FileNameContains(val text: String) : Condition()
    data class SizeRange(val minBytes: Long?, val maxBytes: Long?) : Condition()
    data class ModifiedAfter(val epochMillis: Long) : Condition()
}

enum class MatchMode { ALL, ANY }

class RuleEngine {
    fun matches(file: FileRef, rule: RuleModel): Boolean { /* ... */ }
    fun evaluate(files: List<FileRef>, rules: List<RuleModel>): Map<String, List<FileRef>> { /* ... */ }
}
```

*(Full implementation as in PRD v1.0, Section 5.3.)*

### 6.4 Query Parser (Domain Layer)

```kotlin
class QueryParser {
    fun parse(query: String): Result<RuleModel> {
        // Parses prefixes: date:, source:, type:, name:, size:
        // Free text → FileNameContains
        // Returns Result.success(RuleModel) or Result.failure(IllegalArgumentException)
    }
}
```

*(Full implementation as in PRD v1.0, Section 5.4.)*

### 6.5 Heatmap Query (Data Layer)

```kotlin
@Dao
interface FileRefDao {
    @Query("""
        SELECT dateDay, COUNT(*) as count
        FROM file_ref
        WHERE dateDay LIKE :monthPrefix || '%' AND isStale = 0
        GROUP BY dateDay
    """)
    suspend fun getDailyCounts(monthPrefix: String): List<DailyCount>
}

data class DailyCount(val dateDay: String, val count: Int)
```

### 6.6 Indexing Flow (WorkManager)

```
Worker: IndexWorker (periodic 6h + on-demand)
  ├── 1. Query MediaStore for images/docs/audio
  ├── 2. Query SAF tree for user-approved folders
  ├── 3. For each file: resolve source, date, dateDay → upsert
  ├── 4. Re-evaluate rules → update virtual folder cache
  └── 5. Emit Flow update to UI
```

### 6.7 Expiry Job (WorkManager)

```
Worker: ExpiryWorker (every 15 min)
  ├── Query folders where expiresAt < now
  ├── If archiveOnExpiry → move to "Expired" (7-day TTL)
  ├── Else → delete folder row
  └── Cancel pending notification
```

### 6.8 Privacy Architecture (structural)

| Constraint | Implementation | Verifiable by |
|-----------|---------------|---------------|
| No network | No `INTERNET` permission in manifest | Anyone (check manifest) |
| Encrypted DB | SQLCipher, key in Android Keystore | Code review |
| No telemetry | No analytics SDK, no cloud crash reporting | Dependency audit |
| No account | No login, no sync | UI |
| Temp files | `cacheDir`, deleted on exit or 1h TTL | Code review |
| Export | Encrypted backup, user-controlled | UI |

**AndroidManifest.xml (permissions):**
```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- NO INTERNET PERMISSION -->
<!-- NO MANAGE_EXTERNAL_STORAGE -->
```

---

## 7. UX Flows (with Brand Voice)

### 7.1 First Launch
1. **Splash:** Almara logo + *"Smart Sedha."*
2. **Welcome:** *"Your files stay on your phone. No cloud. No account. No internet permission."*
3. **Emotional hook:** *"Remember when maa said 'apna almara seedha karo'? Now it's digital."*
4. **Permission request:** "Allow access to your documents and images."
5. **Indexing progress** (non-blocking).
6. **Land on heatmap** with current month.

### 7.2 Create Virtual Folder (≤ 5 taps)
1. Tap FAB
2. Tap source chip (e.g., WhatsApp)
3. Tap type chip (e.g., PDFs)
4. Tap time chip (e.g., This week)
5. Tap "Create"
→ Folder appears. Toast: *"Maa would be proud."*

### 7.3 Find File by Date
1. Open app (heatmap visible)
2. Tap day on heatmap
3. File list scrolls to that day
→ 2 interactions

### 7.4 Set Expiry
1. Long-press folder tile → "Timer"
2. Select "1 day"
3. Optional: check "Archive instead of delete"
4. Done

### 7.5 Convert File
1. Long-press file → radial menu
2. Tap "Convert"
3. Pick output format
4. Confirm → progress → done

### 7.6 Empty States (Brand Voice)
- **No files yet:** *"Your almara is empty. Tap + to start — just like maa taught you."*
- **No folders:** *"No virtual folders yet. Create one with the + button."*
- **Empty month:** *"No files this month. [Jump to last active month]"*

---

## 8. Dependencies

| Category | Library | Purpose |
|----------|---------|---------|
| UI | Jetpack Compose (BOM) | All UI |
| UI | Material 3 | Design system (Expressive) |
| Font | Google Sans Flex | Typography (SIL OFL, free) |
| Font | Noto Sans Devanagari | Hindi fallback |
| Nav | Navigation Compose | Screen navigation |
| Async | Kotlin Coroutines + Flow | Async |
| DI | Hilt | Dependency injection |
| DB | Room | Local database |
| DB | SQLCipher for Android | Encryption |
| DB | Room-SQLCipher | Integration |
| Images | Coil | Thumbnail loading |
| PDF | PdfBox-Android | PDF rendering/conversion |
| MD | commonmark-java | Markdown parsing |
| Archive | java.util.zip (built-in) | ZIP create/extract |
| Background | WorkManager | Indexing, expiry |
| Security | AndroidX Security Crypto | Keystore key management |
| Glass (optional) | Vitro / Prismal | Liquid Glass effect |
| Testing | JUnit5, Turbine, MockK | Unit tests |
| Testing | Compose UI Test | UI tests |
| Testing | Macrobenchmark | Performance |

**No networking libraries.** No Retrofit, OkHttp, Ktor, Firebase.

---

## 9. Performance Targets

| Metric | Target | Device class |
|--------|--------|-------------|
| App cold start | < 1.5s | Mid-range (4 GB RAM) |
| Index 10k files | < 15s | Mid-range |
| Heatmap month render | < 300ms | Mid-range |
| Query result | < 200ms | Mid-range |
| Radial menu open | < 100ms | Mid-range |
| Memory (idle) | < 80 MB | Mid-range |
| APK size | < 15 MB | — |
| Battery (idle) | < 1%/hr | Mid-range |

---

## 10. Testing Strategy

### 10.1 Unit Tests (Domain Layer)
- RuleEngine: all condition types, ALL/ANY, exclusive/non-exclusive
- QueryParser: every prefix, invalid input, date formats, size parsing
- DateResolver: filename > EXIF > modified priority
- SourceDetector: WhatsApp/Telegram patterns, path patterns

### 10.2 Integration Tests (Data Layer)
- Room DAO queries (in-memory)
- Indexing diff logic (insert/update/stale)
- Heatmap query correctness
- SQLCipher encryption (DB unreadable without key)

### 10.3 UI Tests (Compose)
- FAB flow ≤ 5 taps
- Heatmap tap → list scroll
- Radial menu opens and actions fire
- Timer sheet set/cancel
- AMOLED toggle applies pure black
- Dynamic color extraction (mocked)

### 10.4 Manual Testing Matrix
| Device | Android | RAM | Test |
|--------|---------|-----|------|
| Pixel 4a | 13 | 6 GB | Baseline |
| Redmi 9 | 11 | 4 GB | Low-end |
| Samsung A14 | 14 | 4 GB | OEM skin |
| OnePlus Nord | 13 | 8 GB | OxygenOS |
| Vivo Y21 | 12 | 4 GB | FuntouchOS |

---

## 11. Release Plan

### v1.0
- File indexing (MediaStore + SAF)
- Source detection + filename date parsing
- Heatmap calendar (wallpaper-derived colors)
- FAB virtual folder creator
- Query bar + query chips
- Save query as virtual folder
- Long-press radial menu
- Offline conversion (images, PDF↔images, ZIP, MD)
- AMOLED toggle
- Dynamic color theming
- No network permission
- Expiry timer + notifications
- Archive section for expired folders
- Duplicate detection (hash)
- Manual virtual folders
- Widgets (heatmap widget, folder widget)
- Liquid Glass effect (optional)

### v2.0 (later)
- DOCX → PDF (text-only first)
- Natural language queries
- Encrypted export/import
- Tablet layout (two-pane)
- Wear OS companion (view-only)
- Regional language expansion (Tamil, Telugu, Bengali)

---

## 12. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| SAF edge cases | High | Medium | Test 5+ OEMs; fallback to MediaStore-only |
| Heatmap slow on huge libraries | Medium | High | Indexed `date_day`; paginated; cache |
| Expiry deletes folder users wanted | Low | High | Opt-in only; notification; archive option |
| Users expect file moving | Medium | Medium | Onboarding clarifies virtual folders |
| Conversion quality (PDF, MD) | Medium | Medium | Mark as "beta" in UI |
| Play Store policy (storage) | Low | High | No `MANAGE_EXTERNAL_STORAGE` |
| Low-end performance | High | Medium | Lazy loading, thumbnail cache |
| Liquid Glass performance | Medium | Medium | Off by default; fallback; limited use |
| Google Sans licensing | Low | Low | Verified SIL OFL; free for commercial |
| Dynamic color on old Android | Medium | Low | Manual palette fallback |

---

## 13. Open Questions

1. **License:** GPL-3.0 (copyleft) vs. Apache-2.0 (permissive)?
2. **PDF library:** PdfBox-Android (Apache 2.0) vs. iText (AGPL) — PdfBox recommended.
3. **Archive format:** ZIP only, or also TAR/GZ? (ZIP only for v1)
4. **Folder icons:** Emoji-only, or icon pack? (Emoji for v1)
5. **Monetization:** None (open source). Donation link? (TBD)
6. **Hindi localization depth:** UI only, or full content? (UI for v1)

---

## 14. Appendix A: Query Language Grammar (v1.0)

```
query        := clause (SPACE clause)*
clause       := prefix ":" value | free_text
prefix       := "date" | "source" | "type" | "name" | "size"
value        := date_value | source_value | type_value | text | size_value

date_value   := "YYYY-MM-DD" | "YYYY-MM"
              | "today" | "yesterday" | "last-week" | "last-month"
              | "this-week" | "this-month"
source_value := "whatsapp" | "telegram" | "camera" | "screenshot"
              | "download" | "other" | "all"
type_value   := "image" | "pdf" | "doc" | "audio" | "archive" | "text" | "all"
size_value   := (">" | "<") number ("kb" | "mb" | "gb")
free_text    := any non-whitespace string

Example: source:whatsapp type:pdf date:last-week size:>1mb invoice
```

---

## 15. Appendix B: Brand Voice Guidelines

| Context | Voice | Example |
|---------|-------|---------|
| **Splash** | Minimal, confident | *"Almara. Smart Sedha."* |
| **Onboarding** | Warm, nostalgic | *"Remember maa's nudge? Now it's digital."* |
| **Success toast** | Playful, brief | *"Maa would be proud."* |
| **Empty state** | Gentle, inviting | *"Your almara is empty. Tap + to start."* |
| **Error** | Clear, never blaming | *"Couldn't open this file. Try another app?"* |
| **Settings** | Plain, functional | *"AMOLED mode: pure black backgrounds."* |
| **Play Store** | Smart, emotional | *"Smart Sedha — your digital almara, organized like maa taught you."* |

**Rules:**
- Never preachy. Never "you should."
- Hindi for emotion, English for function.
- One nostalgic touch per screen, maximum.
- No exclamation marks in UI (except success toasts).

---

## 16. Appendix C: Play Store Listing (Draft)

**Title:** Almara — Smart Sedha

**Short description:**
> Smart Sedha — your digital almara. Virtual folders, heatmap calendar, offline. No cloud. No account.

**Full description:**
> **Remember when maa said "apna almara seedha karo"?**
>
> Almara is that nudge — digitized, smartened, and private.
>
> **Smart Sedha.** Your files, organized without being moved.
>
> **What Almara does:**
> • **Virtual folders** — saved filters that auto-update. Files stay where they are.
> • **Heatmap calendar** — see your file density at a glance. Tap any day to see what you received.
> • **One-tap folder creator** — pick source, type, and time. Preview. Create. Done.
> • **Offline conversion** — images, PDFs, ZIPs, Markdown. No upload, ever.
> • **Radial menu** — long-press any file for instant actions.
> • **Optional expiry** — filters that clean themselves up.
>
> **Privacy by design:**
> • No internet permission. Verified in the manifest.
> • No account. No cloud. No telemetry.
> • Encrypted local database.
> • Files never leave your phone.
>
> **Built for real phones:**
> • Works on 2–4 GB RAM devices.
> • AMOLED mode for battery savings.
> • Wallpaper-based theming.
> • Google Sans typography.
>
> **Smart Sedha.** Organized, not relocated. Just like maa's almara.

---

## 17. Appendix D: File Type Whitelist

| Category | Extensions | MIME types |
|----------|-----------|-----------|
| Images | jpg, jpeg, png, webp, gif, bmp, heic, heif | image/* |
| Documents | pdf, doc, docx, odt, rtf, txt, md | application/pdf, application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document, text/* |
| Audio | mp3, opus, ogg, wav, m4a, aac, flac | audio/* |
| Archives | zip, tar, gz, rar, 7z | application/zip, application/x-tar, application/gzip, application/x-rar-compressed, application/x-7z-compressed |
| Video (view only) | mp4, mkv, webm, mov, avi | video/* |

*Video files are indexed and viewable but NOT convertible in v1.*

---

**End of PRD v2.0**

---

This PRD now integrates:
- ✅ **Almara** branding
- ✅ **"Smart Sedha"** tagline
- ✅ **Full design language** (Google Sans, Material 3 Expressive, AMOLED, wallpaper theming, optional Liquid Glass)
- ✅ **All architecture** (data model, rule engine, query parser, indexing, expiry)
- ✅ **Nostalgic UX voice** in every flow

Ready to hand to developers, designers, and AI coding assistants. Want me to next generate the **Kotlin project skeleton**, the **Compose UI code** for the heatmap + FAB sheet, or the **Play Store assets checklist**?