package com.almara.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** A searchable file record; all fields are platform-independent. */
data class FileRecord(
    val name: String,
    val path: String = name,
    val source: Source = detectSource(path),
    val date: LocalDate? = parseDateFromFilename(name),
    val type: FileType = FileType.fromName(name),
    val sizeBytes: Long = 0L
)

enum class Source { WHATSAPP, TELEGRAM, CAMERA, SCREENSHOT, DOWNLOAD, OTHER, ALL;
    companion object {
        fun parse(value: String): Source? = when (value.lowercase(Locale.ROOT)) {
            "whatsapp", "wa" -> WHATSAPP
            "telegram", "tg" -> TELEGRAM
            "camera" -> CAMERA
            "screenshot", "screenshots" -> SCREENSHOT
            "download", "downloads" -> DOWNLOAD
            "other" -> OTHER
            "all" -> ALL
            else -> null
        }
    }
}

enum class FileType(val extensions: Set<String>) {
    IMAGE(setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")),
    VIDEO(setOf("mp4", "mkv", "mov", "webm", "3gp", "avi")),
    AUDIO(setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus")),
    PDF(setOf("pdf")), DOCUMENT(setOf("doc", "docx", "odt", "rtf", "txt", "md")),
    SPREADSHEET(setOf("xls", "xlsx", "csv", "ods")), PRESENTATION(setOf("ppt", "pptx", "odp")), ARCHIVE(setOf("zip", "rar", "7z", "gz", "tar")), OTHER(emptySet()), ALL(emptySet());

    companion object {
        fun fromName(name: String): FileType {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return entries.firstOrNull { ext in it.extensions } ?: OTHER
        }
        /** Accepts common short forms, plural labels, and enum names. */
        fun parse(value: String): FileType? = when (value.lowercase(Locale.ROOT).removeSuffix("s")) {
            "image", "photo", "picture", "jpg", "png" -> IMAGE
            "video", "movie" -> VIDEO
            "audio", "music" -> AUDIO
            "pdf" -> PDF
            "doc", "document", "text" -> DOCUMENT
            "spreadsheet", "sheet", "csv", "excel" -> SPREADSHEET
            "presentation", "slide", "powerpoint" -> PRESENTATION
            "archive", "zip" -> ARCHIVE
            "other" -> OTHER
            "all" -> ALL
            else -> null
        }
    }
}

/** Detects a source using path components and filename prefixes, case-insensitively. */
fun detectSource(pathOrFilename: String): Source {
    val text = pathOrFilename.replace('\\', '/').lowercase(Locale.ROOT)
    return when {
        Regex("(^|/)whatsapp(/|$)|whatsapp[-_ ]", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Source.WHATSAPP
        Regex("(^|/)telegram(/|$)|telegram[-_ ]", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Source.TELEGRAM
        Regex("(^|/)pictures/screenshots?(/|$)|(^|/)screenshots?(/|$)").containsMatchIn(text) -> Source.SCREENSHOT
        Regex("(^|/)dcim/camera(/|$)|(^|/)camera(/|$)").containsMatchIn(text) -> Source.CAMERA
        Regex("(^|/)downloads?(/|$)").containsMatchIn(text) -> Source.DOWNLOAD
        else -> Source.OTHER
    }
}

/** WhatsApp and Telegram media filenames commonly embed either yyyyMMdd or yyyy-MM-dd. */
fun parseDateFromFilename(filename: String): LocalDate? {
    val patterns = listOf(
        Regex("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)"),
        Regex("(?<!\\d)(20\\d{2})[-_](\\d{2})[-_](\\d{2})(?!\\d)")
    )
    for (pattern in patterns) {
        val m = pattern.find(filename) ?: continue
        val date = m.groupValues.drop(1).joinToString("-")
        try { return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: DateTimeParseException) { /* Ignore malformed date and try another match. */ }
    }
    return null
}

/** Filename date first; otherwise convert a modification timestamp in the requested zone. */
fun dateFromFilenameOrModified(filename: String, modifiedEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    parseDateFromFilename(filename) ?: java.time.Instant.ofEpochMilli(modifiedEpochMillis).atZone(zone).toLocalDate()

/** Returns null when input is blank, or parses common decimal/binary size suffixes; rejects malformed/overflowing values. */
fun parseSizeBytes(input: String): Long? {
    val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(b|kb|kib|mb|mib|gb|gib|tb|tib)?\\s*$", RegexOption.IGNORE_CASE).matchEntire(input) ?: return null
    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].lowercase(Locale.ROOT)
    val power = when (unit) { "", "b" -> 0; "kb" -> 1; "kib" -> 1; "mb" -> 2; "mib" -> 2; "gb" -> 3; "gib" -> 3; "tb" -> 4; "tib" -> 4; else -> return null }
    val base = if (unit.endsWith("ib")) 1024.0 else 1000.0
    val multiplier = Math.pow(base, power.toDouble())
    val result = number * multiplier
    if (!result.isFinite() || result > Long.MAX_VALUE || result < 0) return null
    return result.toLong()
}

sealed interface QueryClause {
    data class Date(val value: DateQuery) : QueryClause
    data class SourceIs(val value: Source) : QueryClause
    data class TypeIs(val value: FileType) : QueryClause
    data class SourceAny(val values: Set<Source>) : QueryClause
    data class TypeAny(val values: Set<FileType>) : QueryClause
    data class NameContains(val value: String) : QueryClause
    data class Size(val operator: Comparison, val bytes: Long) : QueryClause
    data class Text(val value: String) : QueryClause
}

enum class Comparison { LT, LTE, EQ, GTE, GT }
sealed interface DateQuery {
    data object Today : DateQuery
    data object Yesterday : DateQuery
    data object ThisWeek : DateQuery
    data object ThisMonth : DateQuery
    data object LastWeek : DateQuery
    data object LastMonth : DateQuery
    data class Month(val value: YearMonth) : DateQuery
    data class On(val date: LocalDate) : DateQuery
    data class Range(val start: LocalDate, val end: LocalDate) : DateQuery
}

data class ParsedQuery(val clauses: List<QueryClause>) {
    fun matches(file: FileRecord, today: LocalDate = LocalDate.now()): Boolean = clauses.all { it.matches(file, today) }
}

/** A UI-friendly saved rule with a stable display name and reusable matching criteria. */
data class Rule(val name: String, val query: ParsedQuery) {
    fun matches(file: FileRecord, today: LocalDate = LocalDate.now()): Boolean = query.matches(file, today)
}

data class QueryParseResult(val query: ParsedQuery?, val error: String?) { val isValid: Boolean get() = query != null }

/** Supported strict prefixes: date:, source:, type:, name:, size:. Unprefixed words are free-text terms. */
fun parseQuery(input: String): QueryParseResult {
    val tokens = tokenize(input) ?: return QueryParseResult(null, "Unclosed quote in query")
    val clauses = mutableListOf<QueryClause>()
    for (token in tokens) {
        val colon = token.indexOf(':')
        if (colon < 0) { clauses += QueryClause.Text(token); continue }
        val prefix = token.substring(0, colon).lowercase(Locale.ROOT)
        val value = token.substring(colon + 1)
        if (prefix !in setOf("date", "source", "type", "name", "size")) return QueryParseResult(null, "Unknown query prefix '$prefix'. Use date, source, type, name, or size.")
        if (value.isBlank()) return QueryParseResult(null, "Missing value for '$prefix:' clause")
        when (prefix) {
            "date" -> {
                val date = when (value.lowercase(Locale.ROOT)) {
                    "today" -> DateQuery.Today; "yesterday" -> DateQuery.Yesterday
                    "this-week", "week" -> DateQuery.ThisWeek; "this-month", "month" -> DateQuery.ThisMonth
                    "last-week" -> DateQuery.LastWeek; "last-month" -> DateQuery.LastMonth
                    else -> if (".." in value) { val parts = value.split("..", limit = 2); val start = runCatching { LocalDate.parse(parts[0]) }.getOrNull(); val end = runCatching { LocalDate.parse(parts[1]) }.getOrNull(); if (start == null || end == null || end.isBefore(start)) return QueryParseResult(null, "Invalid date range. Use YYYY-MM-DD..YYYY-MM-DD with the end on or after the start."); DateQuery.Range(start, end) } else if (Regex("^\\d{4}-\\d{2}$").matches(value)) try { DateQuery.Month(YearMonth.parse(value)) } catch (_: DateTimeParseException) { return QueryParseResult(null, "Invalid month '$value'.") } else try { DateQuery.On(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)) } catch (_: DateTimeParseException) { return QueryParseResult(null, "Invalid date '$value'. Use YYYY-MM-DD, YYYY-MM, today, yesterday, last-week, this-week, or this-month.") }
                }
                clauses += QueryClause.Date(date)
            }
            "source" -> {
                val sources = value.split(',').map { Source.parse(it) ?: return QueryParseResult(null, "Unknown source '$it'. Try whatsapp, telegram, camera, download, or other.") }.toSet()
                clauses += if (sources.size == 1) QueryClause.SourceIs(sources.first()) else QueryClause.SourceAny(sources)
            }
            "type" -> {
                val types = value.split(',').map { FileType.parse(it) ?: return QueryParseResult(null, "Unknown file type '$it'.") }.toSet()
                clauses += if (types.size == 1) QueryClause.TypeIs(types.first()) else QueryClause.TypeAny(types)
            }
            "name" -> clauses += QueryClause.NameContains(value)
            "size" -> {
                val m = Regex("^(>=|<=|>|<|=)?(.+)$").matchEntire(value)!!
                val bytes = parseSizeBytes(m.groupValues[2]) ?: return QueryParseResult(null, "Invalid size '$value'. Use a number with B, KB/MB/GB or KiB/MiB/GiB, optionally prefixed by <, <=, =, >=, or >.")
                val op = when (m.groupValues[1]) { "<" -> Comparison.LT; "<=" -> Comparison.LTE; ">" -> Comparison.GT; ">=" -> Comparison.GTE; else -> Comparison.EQ }
                clauses += QueryClause.Size(op, bytes)
            }
        }
    }
    return QueryParseResult(ParsedQuery(clauses), null)
}

private fun tokenize(input: String): List<String>? {
    val tokens = mutableListOf<String>(); val current = StringBuilder(); var quote: Char? = null; var escaping = false
    for (c in input) {
        if (escaping) { current.append(c); escaping = false; continue }
        if (c == '\\' && quote != null) { escaping = true; continue }
        if (quote != null) { if (c == quote) quote = null else current.append(c); continue }
        if (c == '\'' || c == '"') { quote = c; continue }
        if (c.isWhitespace()) { if (current.isNotEmpty()) { tokens += current.toString(); current.setLength(0) } } else current.append(c)
    }
    if (quote != null || escaping) return null
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private fun QueryClause.matches(file: FileRecord, today: LocalDate): Boolean = when (this) {
    is QueryClause.Text -> file.name.contains(value, ignoreCase = true) || file.path.contains(value, ignoreCase = true)
    is QueryClause.NameContains -> file.name.contains(value, ignoreCase = true)
    is QueryClause.SourceIs -> value == Source.ALL || file.source == value
    is QueryClause.TypeIs -> value == FileType.ALL || file.type == value
    is QueryClause.SourceAny -> Source.ALL in values || file.source in values
    is QueryClause.TypeAny -> FileType.ALL in values || file.type in values
    is QueryClause.Size -> when (operator) { Comparison.LT -> file.sizeBytes < bytes; Comparison.LTE -> file.sizeBytes <= bytes; Comparison.EQ -> file.sizeBytes == bytes; Comparison.GTE -> file.sizeBytes >= bytes; Comparison.GT -> file.sizeBytes > bytes }
    is QueryClause.Date -> {
        val date = file.date
        date != null && when (val d = value) {
            DateQuery.Today -> date == today
            DateQuery.Yesterday -> date == today.minusDays(1)
            DateQuery.ThisWeek -> date >= today.minusDays((today.dayOfWeek.value - 1).toLong()) && date <= today
            DateQuery.ThisMonth -> date.year == today.year && date.month == today.month
            DateQuery.LastWeek -> { val start = today.minusDays((today.dayOfWeek.value - 1).toLong() + 7); date in start..start.plusDays(6) }
            DateQuery.LastMonth -> YearMonth.from(date) == YearMonth.from(today).minusMonths(1)
            is DateQuery.Month -> YearMonth.from(date) == d.value
            is DateQuery.On -> date == d.date
            is DateQuery.Range -> date in d.start..d.end
        }
    }
}
