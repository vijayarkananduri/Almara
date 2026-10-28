# Conversion service integration

`ConversionService` is a platform-only Kotlin utility (no added dependency) that creates outputs in the app's `cacheDir`. It does not scan files, start conversions automatically, move/delete source files, or grant itself storage permissions. It accepts `content://` URIs obtained by the UI's document/photo picker.

## Public API

- `ConversionService(context)` — retain an instance scoped to the app/UI.
- `reencodeImage(uri, format, quality = 90, control = Control()): File` — explicit image decode and re-encode as JPEG/PNG/WEBP.
- `imagesToPdf(uris, control = Control()): File` — one image per PDF page using `PdfDocument`.
- `createZip(inputs, control = Control()): File` — ZIP selected URI/display-name pairs.
- `extractZip(uri, limits = Limits(), control = Control()): List<File>` — extracts into a newly created cache subdirectory; default caps are 500 files, 256 MiB total, and 64 MiB per file.
- `rasterizePdf(uri, control = Control(), maxPages = 100): List<File>` — optional PDF-to-PNG page rasterization through `PdfRenderer`.
- `Control(progressListener)` — pass one control per operation; invoke `cancel()` to cooperatively stop it. Cancellation throws `CancellationException`; other failures throw the underlying exception (usually `IOException` or `IllegalArgumentException`). Progress is delivered on the calling thread.
- `Input(uri, displayName)`, `ImageFormat`, `Progress`, `ProgressListener`, and `Limits` are the supporting API types.

## UI usage

1. Let the user select files through the system picker (for example, `ACTION_OPEN_DOCUMENT` with the appropriate MIME types), and invoke a conversion only after an explicit user action/confirmation.
2. Run conversion work off the main thread (e.g. an app-owned executor or lifecycle coroutine `Dispatchers.IO`). `ConversionService` deliberately does not create threads or depend on coroutines.
3. Supply a `Control` whose listener posts progress to the main thread; retain it and call `cancel()` from the UI's cancel action. Cancellation is checked during stream copies and between image/PDF pages.
4. Present the returned cache file via the app's existing share/save flow. Cache files are temporary and may be removed by Android; copy/export them if durable storage is needed. Do not treat them as a durable user-visible destination.

Example (call from an IO worker):

```kotlin
val control = ConversionService.Control { p ->
    activity.runOnUiThread { progressBar.progress = p.completed.toInt() }
}
try {
    val output = ConversionService(context).reencodeImage(uri, ConversionService.ImageFormat.JPEG, 85, control)
    activity.runOnUiThread { showShareOrSave(output) }
} catch (e: java.util.concurrent.CancellationException) {
    // User cancelled; leave input untouched.
} catch (e: Exception) {
    activity.runOnUiThread { showConversionError(e.message ?: "Conversion failed") }
}
```

The service uses Android/JDK APIs available at min API 26. It intentionally does not implement video or DOCX conversion. Image decoding is bounded to 40 megapixels and 4096px decode-side sampling; the ZIP extractor canonicalizes and validates every entry path before writing, and deletes only its own partial output on failure.
