package com.almara.app.conversion

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Explicit, caller-invoked file conversions. All results are created under cacheDir. */
class ConversionService(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val cacheDir: File = appContext.cacheDir

    enum class ImageFormat { JPEG, PNG, WEBP }
    data class Input(val uri: Uri, val displayName: String)
    data class Progress(val completed: Long, val total: Long, val message: String)
    fun interface ProgressListener { fun onProgress(progress: Progress) }

    /** Per-operation cancellation and progress callback. */
    class Control(
        private val cancelled: AtomicBoolean = AtomicBoolean(false),
        val progressListener: ProgressListener? = null
    ) {
        fun cancel() { cancelled.set(true) }
        fun isCancelled(): Boolean = cancelled.get()
        internal fun check() { if (cancelled.get()) throw CancellationException("Conversion cancelled") }
        internal fun progress(done: Long, total: Long, message: String) {
            check(); progressListener?.onProgress(Progress(done, total, message))
        }
    }

    data class Limits(
        val maxFiles: Int = 500,
        val maxUncompressedBytes: Long = 256L * 1024 * 1024,
        val maxSingleFileBytes: Long = 64L * 1024 * 1024
    ) {
        init { require(maxFiles > 0 && maxUncompressedBytes > 0 && maxSingleFileBytes > 0) }
    }

    /** Removes only this service's abandoned cache outputs older than the retention window. */
    fun cleanupTemporaryOutputs(maxAgeMillis: Long = 60L * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis.coerceAtLeast(0L)
        cacheDir.listFiles().orEmpty().filter { (it.name.startsWith("almara-") || it.isDirectory && it.name.startsWith("extract-")) && it.lastModified() < cutoff }.forEach { it.deleteRecursively() }
    }

    /** Re-encodes an image only after the user explicitly invokes this method. */
    fun reencodeImage(uri: Uri, format: ImageFormat, quality: Int = 90, control: Control = Control()): File {
        require(quality in 0..100) { "quality must be between 0 and 100" }
        val bitmap = openBitmap(uri, control)
        try {
            control.check()
            val ext = when (format) { ImageFormat.JPEG -> "jpg"; ImageFormat.PNG -> "png"; ImageFormat.WEBP -> "webp" }
            val output = newCacheFile("image", ext)
            FileOutputStream(output).use { stream ->
                val androidFormat = when (format) {
                    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                }
                if (!bitmap.compress(androidFormat, quality, stream)) throw IOException("Image encoding failed")
                stream.flush()
            }
            control.progress(1, 1, "Image re-encoded")
            return output
        } catch (t: Throwable) { throw t } finally { bitmap.recycle() }
    }

    /** Places each selected image on its own PDF page. Inputs remain untouched. */
    fun imagesToPdf(uris: List<Uri>, control: Control = Control()): File {
        require(uris.isNotEmpty()) { "Select at least one image" }
        val pdf = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                control.check()
                val bitmap = openBitmap(uri, control)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1), index + 1).create()
                    val page = pdf.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page)
                } finally { bitmap.recycle() }
                control.progress(index + 1L, uris.size.toLong(), "Added image ${index + 1} of ${uris.size}")
            }
            val output = newCacheFile("images", "pdf")
            FileOutputStream(output).use { pdf.writeTo(it) }
            return output
        } catch (t: Throwable) { throw t } finally { pdf.close() }
    }

    /** Creates a ZIP from content URIs; display names are sanitized and duplicates uniquified. */
    fun createZip(inputs: List<Input>, control: Control = Control()): File {
        require(inputs.isNotEmpty()) { "Select at least one file" }
        require(inputs.size <= 500) { "Too many input files" }
        val output = newCacheFile("archive", "zip")
        val used = HashSet<String>()
        var totalInputBytes = 0L
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                inputs.forEachIndexed { index, input ->
                    control.check()
                    val base = safeLeafName(input.displayName).ifBlank { "file-${index + 1}" }
                    var name = base; var n = 2
                    while (!used.add(name.lowercase())) {
                        val dot = base.lastIndexOf('.')
                        name = if (dot > 0) "${base.substring(0, dot)} ($n)${base.substring(dot)}" else "$base ($n)"
                        n++
                    }
                    zip.putNextEntry(ZipEntry(name))
                    val remaining = (DEFAULT_LIMITS.maxUncompressedBytes - totalInputBytes).coerceAtLeast(1L)
                    val copied = resolver.openInputStream(input.uri)?.use { stream -> copyBounded(stream, zip, minOf(DEFAULT_LIMITS.maxSingleFileBytes, remaining), control) }
                        ?: throw IOException("Cannot open input: $name")
                    totalInputBytes += copied
                    if (totalInputBytes > DEFAULT_LIMITS.maxUncompressedBytes) throw IOException("Selected files exceed ZIP size limit")
                    zip.closeEntry()
                    control.progress(index + 1L, inputs.size.toLong(), "Added $name")
                }
            }
            return output
        } catch (t: Throwable) { output.delete(); throw t }
    }

    /** Packs generated cache outputs into one exportable ZIP without touching originals. */
    fun zipCacheFiles(inputs: List<File>, control: Control = Control()): File {
        require(inputs.isNotEmpty() && inputs.size <= 500) { "Select between 1 and 500 files" }
        val output = newCacheFile("converted-pages", "zip")
        var total = 0L
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                inputs.forEachIndexed { index, input ->
                    control.check()
                    if (!input.isFile || !input.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator)) throw IOException("Only generated cache files can be packed")
                    zip.putNextEntry(ZipEntry(safeLeafName(input.name).ifBlank { "file-${index + 1}" }))
                    FileInputStream(input).use { stream -> total += copyBounded(stream, zip, DEFAULT_LIMITS.maxUncompressedBytes - total, control) }
                    zip.closeEntry()
                }
            }
            return output
        } catch (t: Throwable) { output.delete(); throw t }
    }

    /** Extracts a ZIP into a new cache subdirectory. Rejects traversal, symlinks by omission, and oversized archives. */
    fun extractZip(zipUri: Uri, limits: Limits = Limits(), control: Control = Control()): List<File> {
        val outDir = File(cacheDir, "extract-${UUID.randomUUID()}")
        if (!outDir.mkdirs()) throw IOException("Could not create extraction directory")
        val outputs = ArrayList<File>()
        var total = 0L
        try {
            val raw = resolver.openInputStream(zipUri) ?: throw IOException("Cannot open archive")
            ZipInputStream(BufferedInputStream(raw)).use { zin ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    control.check()
                    val entry = zin.nextEntry ?: break
                    if (outputs.size >= limits.maxFiles) throw IOException("Archive exceeds ${limits.maxFiles} files")
                    val relative = validateZipPath(entry.name)
                    val target = File(outDir, relative).canonicalFile
                    val root = outDir.canonicalFile
                    if (target != root && !target.path.startsWith(root.path + File.separator)) throw IOException("Unsafe archive path")
                    if (entry.isDirectory) {
                        if (!target.exists() && !target.mkdirs()) throw IOException("Could not create directory")
                    } else {
                        val parent = target.parentFile ?: throw IOException("Invalid archive path")
                        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create directory")
                        if (target.exists()) throw IOException("Duplicate archive path: ${entry.name}")
                        var fileBytes = 0L
                        FileOutputStream(target).use { fout ->
                            while (true) {
                                control.check()
                                val count = zin.read(buffer)
                                if (count < 0) break
                                fileBytes += count; total += count
                                if (fileBytes > limits.maxSingleFileBytes || total > limits.maxUncompressedBytes) throw IOException("Archive exceeds extraction size limit")
                                fout.write(buffer, 0, count)
                            }
                        }
                        outputs.add(target)
                    }
                    zin.closeEntry()
                    control.progress(outputs.size.toLong(), limits.maxFiles.toLong(), "Extracted ${entry.name}")
                }
            }
            if (outputs.isEmpty()) throw IOException("Archive contains no files")
            control.progress(outputs.size.toLong(), outputs.size.toLong(), "Extraction complete")
            return outputs
        } catch (t: Throwable) {
            outDir.deleteRecursively()
            throw t
        }
    }

    /** Rasterizes PDF pages to PNG images using PdfRenderer (API 21+). */
    fun rasterizePdf(uri: Uri, control: Control = Control(), maxPages: Int = 100): List<File> {
        require(maxPages > 0)
        val staged = copyUriToCache(uri, "source", "pdf", 256L * 1024 * 1024, control)
        val results = ArrayList<File>()
        try {
            ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > maxPages) throw IOException("PDF exceeds $maxPages pages")
                    for (i in 0 until renderer.pageCount) {
                        control.check()
                        renderer.openPage(i).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val file = newCacheFile("page-${i + 1}", "png")
                                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                results.add(file)
                            } finally { bitmap.recycle() }
                        }
                        control.progress(i + 1L, renderer.pageCount.toLong(), "Rendered page ${i + 1}")
                    }
                }
            }
            return results
        } catch (t: Throwable) { results.forEach { it.delete() }; throw t }
        finally { staged.delete() }
    }

    /** Extracts selectable PDF text to UTF-8 TXT; scanned images require OCR and are not modified. */
    fun extractPdfText(uri: Uri, control: Control = Control()): File {
        val staged = copyUriToCache(uri, "source", "pdf", 256L * 1024 * 1024, control)
        try {
            control.check()
            PDFBoxResourceLoader.init(appContext)
            val text = PDDocument.load(staged).use { document -> PDFTextStripper().getText(document) }
            control.check()
            val output = newCacheFile("pdf-text", "txt")
            output.writeText(text, Charsets.UTF_8)
            control.progress(1, 1, "PDF text extracted")
            return output
        } finally { staged.delete() }
    }

    /** Renders local Markdown to HTML with raw HTML disabled by CommonMark's default parser. */
    fun markdownToHtml(uri: Uri, control: Control = Control()): File {
        val markdown = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: throw IOException("Cannot open Markdown file")
        control.check()
        val document = Parser.builder().build().parse(markdown)
        val html = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>${HtmlRenderer.builder().build().render(document)}</body></html>"
        val output = newCacheFile("markdown", "html")
        output.writeText(html, Charsets.UTF_8)
        control.progress(1, 1, "Markdown converted to HTML")
        return output
    }

    /** Creates a paginated plain-text PDF from Markdown source without changing the source file. */
    fun markdownToPdf(uri: Uri, control: Control = Control()): File {
        val markdown = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: throw IOException("Cannot open Markdown file")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.MONOSPACE }
        val pageWidth = 595; val pageHeight = 842; val margin = 42f; val lineHeight = 16f
        val maxWidth = pageWidth - margin * 2
        val lines = mutableListOf<String>()
        markdown.lineSequence().forEach { raw ->
            if (raw.isEmpty()) lines += "" else {
                var rest = raw
                while (rest.isNotEmpty()) {
                    var end = rest.length
                    while (end > 1 && paint.measureText(rest.substring(0, end)) > maxWidth) end--
                    if (end < rest.length) end = rest.lastIndexOf(' ', end).takeIf { it > 0 } ?: end
                    lines += rest.substring(0, end)
                    rest = rest.substring(end).trimStart()
                }
            }
        }
        val pdf = PdfDocument(); var pageNumber = 1; var index = 0
        try {
            while (index < lines.size.coerceAtLeast(1)) {
                control.check()
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdf.startPage(info); var y = margin + lineHeight
                while (index < lines.size && y < pageHeight - margin) { page.canvas.drawText(lines[index++], margin, y, paint); y += lineHeight }
                pdf.finishPage(page); pageNumber++
                control.progress(index.toLong(), lines.size.toLong().coerceAtLeast(1), "Rendered Markdown page")
            }
            val output = newCacheFile("markdown", "pdf")
            FileOutputStream(output).use { pdf.writeTo(it) }
            return output
        } catch (t: Throwable) { throw t } finally { pdf.close() }
    }

    private fun openBitmap(uri: Uri, control: Control): Bitmap {
        control.check()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Cannot open image")
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw IOException("Unsupported or invalid image")
        val pixels = opts.outWidth.toLong() * opts.outHeight.toLong()
        if (pixels > MAX_IMAGE_PIXELS) throw IOException("Image dimensions exceed safe limit")
        val sample = calculateInSampleSize(opts.outWidth, opts.outHeight, MAX_DECODE_SIDE)
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) } ?: throw IOException("Cannot reopen image")
    }

    private fun copyUriToCache(uri: Uri, prefix: String, ext: String, cap: Long, control: Control): File {
        val file = newCacheFile(prefix, ext)
        try {
            resolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { output -> copyBounded(input, output, cap, control) } }
                ?: throw IOException("Cannot open input")
            return file
        } catch (t: Throwable) { file.delete(); throw t }
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, cap: Long, control: Control): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var countTotal = 0L
        while (true) {
            control.check()
            val n = input.read(buffer); if (n < 0) break
            countTotal += n
            if (countTotal > cap) throw IOException("Input exceeds size limit")
            output.write(buffer, 0, n)
        }
        return countTotal
    }

    private fun newCacheFile(prefix: String, ext: String): File = File.createTempFile("almara-${prefix}-", ".$ext", cacheDir)

    private fun safeLeafName(name: String): String {
        val leaf = name.replace('\\', '/').substringAfterLast('/').trim()
        return leaf.filter { it >= ' ' && it != '\u007f' && it != ':' }.take(180)
    }

    private fun validateZipPath(name: String): String {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\') || name.contains('\\') || name.contains('\u0000')) throw IOException("Unsafe archive path")
        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.contains(':') }) throw IOException("Unsafe archive path")
        return segments.joinToString(File.separator)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / (sample * 2) > maxSide || height / (sample * 2) > maxSide) sample *= 2
        return sample
    }

    companion object {
        private val DEFAULT_LIMITS = Limits()
        private const val MAX_IMAGE_PIXELS = 40_000_000L
        private const val MAX_DECODE_SIDE = 4096
    }
}
