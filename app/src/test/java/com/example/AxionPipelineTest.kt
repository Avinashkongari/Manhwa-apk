package com.example

import com.example.data.model.ZipFilenameFormat
import com.example.domain.ChapterPageDetector
import com.example.domain.DuplicateDetector
import com.example.domain.ImageFormat
import com.example.domain.ImageValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AxionPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseDetectionResult preserves reading order and extracts metadata`() {
        val rawJs = """
            {
              "title": "Solo Hunter Chapter 7 - Thunder Scans",
              "h1": "Solo Hunter Chapter 7",
              "mangaName": "Solo Hunter",
              "chapterName": "Solo Hunter Chapter 7",
              "images": [
                {"url": "https://cdn.en-thunderscans.com/ch7/001.jpg", "width": 800, "height": 1200, "alt": "page 1"},
                {"url": "https://cdn.en-thunderscans.com/ch7/002.jpg", "width": 800, "height": 1200, "alt": "page 2"},
                {"url": "https://cdn.en-thunderscans.com/ch7/002.jpg#dup", "width": 800, "height": 1200, "alt": "dup"},
                {"url": "https://cdn.en-thunderscans.com/ch7/003.webp", "width": 800, "height": 1400, "alt": "page 3"}
              ],
              "excludedCount": 5
            }
        """.trimIndent()

        val result = ChapterPageDetector.parseDetectionResult(
            rawJsResult = rawJs,
            pageUrl = "https://en-thunderscans.com/solo-hunter-chapter-7/",
            filenameFormat = ZipFilenameFormat.MANGA_CHAPTER
        )

        assertNull(result.errorMessage)
        assertEquals("Solo Hunter", result.mangaTitle)
        assertEquals("07", result.chapterNumber)
        assertEquals("Solo_Hunter_Chapter_07.zip", result.suggestedZipName)
        assertEquals(3, result.pages.size)
        assertEquals("https://cdn.en-thunderscans.com/ch7/001.jpg", result.pages[0].url)
        assertEquals("https://cdn.en-thunderscans.com/ch7/002.jpg", result.pages[1].url)
        assertEquals("https://cdn.en-thunderscans.com/ch7/003.webp", result.pages[2].url)
        assertEquals(5, result.excludedCount)
    }

    @Test
    fun `formatPageFilename pads to 3 digits`() {
        assertEquals("001.jpg", ChapterPageDetector.formatPageFilename(1, "jpg"))
        assertEquals("015.png", ChapterPageDetector.formatPageFilename(15, "PNG"))
        assertEquals("104.webp", ChapterPageDetector.formatPageFilename(104, ".webp"))
    }

    @Test
    fun `detectFormatFromMagicBytes validates JPG PNG WebP and rejects HTML`() {
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val webp = "RIFF1234WEBP".toByteArray()
        val html = "<!DOCTYPE html>".toByteArray()

        assertEquals(ImageFormat.JPEG, ImageValidator.detectFormatFromMagicBytes(jpg))
        assertEquals(ImageFormat.PNG, ImageValidator.detectFormatFromMagicBytes(png))
        assertEquals(ImageFormat.WEBP, ImageValidator.detectFormatFromMagicBytes(webp))
        assertNull(ImageValidator.detectFormatFromMagicBytes(html))
    }

    @Test
    fun `duplicateDetector skips identical files and preserves distinct pages`() {
        val file1: File = tempFolder.newFile("p1.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val file2: File = tempFolder.newFile("p2.bin").apply { writeBytes(byteArrayOf(9, 8, 7, 6, 5)) }
        val file3Dup: File = tempFolder.newFile("p3.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        val detector = DuplicateDetector()
        assertFalse(detector.checkAndRegister(file1, 0, "https://example.com/1.jpg"))
        assertFalse(detector.checkAndRegister(file2, 1, "https://example.com/2.jpg"))
        assertTrue(detector.checkAndRegister(file3Dup, 2, "https://example.com/3.jpg"))

        assertEquals(1, detector.skippedDuplicates.size)
        assertEquals(2, detector.skippedDuplicates[0].sourceIndex)
        assertEquals(0, detector.skippedDuplicates[0].matchedOriginalIndex)
    }

    @Test
    fun `zipFilenameFormat sanitizes illegal filesystem characters`() {
        val name = ZipFilenameFormat.MANGA_CHAPTER.formatFilename("Shadow/Blade: Rise?", "3")
        assertEquals("Shadow_Blade_Rise_Chapter_03.zip", name)
    }

    @Test
    fun `parseDetectionResult handles ManhwaHub webtoon chapter URLs and titles`() {
        val rawJs = """
            {
              "title": "Solo Leveling Chapter 12 - ManhwaHub",
              "h1": "Chapter 12",
              "mangaName": "",
              "chapterName": "Chapter 12",
              "images": [
                {"url": "https://img.manhwahub.to/solo-leveling/12/01.webp", "width": 800, "height": 1400, "alt": ""},
                {"url": "https://img.manhwahub.to/solo-leveling/12/02.webp", "width": 800, "height": 1400, "alt": ""}
              ],
              "excludedCount": 2
            }
        """.trimIndent()

        val result = ChapterPageDetector.parseDetectionResult(
            rawJsResult = rawJs,
            pageUrl = "https://manhwahub.to/webtoon/solo-leveling/chapter-12",
            filenameFormat = ZipFilenameFormat.MANGA_CHAPTER
        )

        assertNull(result.errorMessage)
        assertEquals("Solo Leveling", result.mangaTitle)
        assertEquals("12", result.chapterNumber)
        assertEquals("Solo_Leveling_Chapter_12.zip", result.suggestedZipName)
        assertEquals(2, result.pages.size)
    }

    @Test
    fun `mangaSource toggles between Thunder Scans and ManhwaHub`() {
        assertEquals(com.example.ui.browser.MangaSource.MANHWA_HUB, com.example.ui.browser.MangaSource.THUNDER_SCANS.next())
        assertEquals(com.example.ui.browser.MangaSource.THUNDER_SCANS, com.example.ui.browser.MangaSource.MANHWA_HUB.next())
        assertEquals(
            com.example.ui.browser.MangaSource.MANHWA_HUB,
            com.example.ui.browser.MangaSource.detectFromUrl("https://manhwahub.to/webtoon/test/chapter-1")
        )
        assertEquals(
            com.example.ui.browser.MangaSource.THUNDER_SCANS,
            com.example.ui.browser.MangaSource.detectFromUrl("https://en-thunderscans.com/manga/test/")
        )
    }
}
