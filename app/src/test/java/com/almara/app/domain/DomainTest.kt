package com.almara.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun parsesFreeTextAndAllSupportedClauses() {
        val result = parseQuery("family name:\"vacation photo\" source:whatsapp type:image size:>=2MiB date:2024-03-05")
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(6, result.query!!.clauses.size)
        val file = FileRecord("Family Vacation Photo.jpg", "/WhatsApp/Media/IMG.jpg", Source.WHATSAPP, LocalDate.parse("2024-03-05"), FileType.IMAGE, 3L * 1024 * 1024)
        assertTrue(result.query.matches(file, LocalDate.parse("2024-03-05")))
    }

    @Test fun unknownPrefixesAndInvalidValuesAreErrors() {
        listOf("foo:bar", "source:", "source:signal", "type:banana", "date:2024-02-30", "size:huge", "size:>=").forEach { input ->
            val result = parseQuery(input)
            assertFalse("Expected invalid query: $input", result.isValid)
            assertTrue("Expected clear error: $input", result.error!!.isNotBlank())
        }
        assertFalse(parseQuery("unclosed\" quote").isValid)
    }

    @Test fun dateClausesMatchRelativeAndAbsoluteDates() {
        val today = LocalDate.parse("2024-06-12")
        val file = FileRecord("report.pdf", date = today, type = FileType.PDF)
        assertTrue(parseQuery("date:today").query!!.matches(file, today))
        assertTrue(parseQuery("date:this-week").query!!.matches(file, today))
        assertTrue(parseQuery("date:this-month").query!!.matches(file, today))
        assertTrue(parseQuery("date:2024-06-12").query!!.matches(file, today))
        assertFalse(parseQuery("date:yesterday").query!!.matches(file, today))
    }

    @Test fun supportsPrdMonthAndLastWeekDateFormsAndAllValues() {
        val today = LocalDate.parse("2024-06-12")
        val lastWeekFile = FileRecord("screen.png", "/Pictures/Screenshots/screen.png", Source.SCREENSHOT, LocalDate.parse("2024-06-05"), FileType.IMAGE)
        val lastMonthFile = FileRecord("report.pdf", date = LocalDate.parse("2024-05-18"), type = FileType.PDF)
        assertTrue(parseQuery("date:last-week").query!!.matches(lastWeekFile, today))
        assertTrue(parseQuery("source:screenshot").query!!.matches(lastWeekFile, today))
        assertTrue(parseQuery("date:2024-05").query!!.matches(lastMonthFile, today))
        assertTrue(parseQuery("date:last-month").query!!.matches(lastMonthFile, today))
        assertTrue(parseQuery("source:all type:all").query!!.matches(lastMonthFile, today))
        assertEquals(Source.SCREENSHOT, detectSource("Pictures/Screenshots/capture.png"))
    }

    @Test fun detectsSourcesFromDirectoriesAndFilenamePrefixes() {
        assertEquals(Source.WHATSAPP, detectSource("/storage/emulated/0/WhatsApp/Media/a.jpg"))
        assertEquals(Source.WHATSAPP, detectSource("WhatsApp Image 20240101.jpg"))
        assertEquals(Source.TELEGRAM, detectSource("/Telegram/Telegram Images/pic.jpg"))
        assertEquals(Source.TELEGRAM, detectSource("Telegram Photo_2024-01-02.jpg"))
        assertEquals(Source.CAMERA, detectSource("DCIM/Camera/IMG.jpg"))
        assertEquals(Source.DOWNLOAD, detectSource("/Downloads/file.pdf"))
        assertEquals(Source.OTHER, detectSource("/Documents/file.pdf"))
    }

    @Test fun extractsWhatsAppAndTelegramDatesAndUsesFallback() {
        assertEquals(LocalDate.parse("2024-02-29"), parseDateFromFilename("WhatsApp Image 20240229 at 120000.jpg"))
        assertEquals(LocalDate.parse("2024-03-01"), parseDateFromFilename("Telegram Photo_2024-03-01_10-20-30.jpg"))
        assertNull(parseDateFromFilename("image 20240230.jpg"))
        val zone = ZoneId.of("UTC")
        assertEquals(LocalDate.parse("2020-09-13"), dateFromFilenameOrModified("plain.jpg", 1_600_000_000_000L, zone))
        assertEquals(LocalDate.parse("2024-03-01"), dateFromFilenameOrModified("photo_20240301.jpg", 0L, zone))
    }

    @Test fun parsesTypeAndSizeAndMatchesRule() {
        assertEquals(FileType.PDF, FileType.fromName("doc.PDF"))
        assertEquals(FileType.IMAGE, FileType.parse("images"))
        assertEquals(1_500_000L, parseSizeBytes("1.5 MB"))
        assertEquals(1_048_576L, parseSizeBytes("1 MiB"))
        assertNull(parseSizeBytes("1XB"))
        val parsed = parseQuery("source:telegram type:pdf size:>1MB")
        val rule = Rule("Large Telegram PDFs", parsed.query!!)
        val matching = FileRecord("manual.pdf", "/Telegram/manual.pdf", Source.TELEGRAM, type = FileType.PDF, sizeBytes = 2_000_000)
        val other = matching.copy(sizeBytes = 100)
        assertTrue(rule.matches(matching))
        assertFalse(rule.matches(other))
    }

    @Test fun multiSelectSourceAndTypeFiltersUseOrWithinEachCategory() {
        val query = parseQuery("source:whatsapp,telegram type:pdf,document").query!!
        assertTrue(query.matches(FileRecord("report.pdf", source = Source.TELEGRAM, type = FileType.PDF)))
        assertTrue(query.matches(FileRecord("notes.docx", source = Source.WHATSAPP, type = FileType.DOCUMENT)))
        assertFalse(query.matches(FileRecord("report.pdf", source = Source.CAMERA, type = FileType.PDF)))
        assertFalse(query.matches(FileRecord("photo.jpg", source = Source.TELEGRAM, type = FileType.IMAGE)))
    }
}
