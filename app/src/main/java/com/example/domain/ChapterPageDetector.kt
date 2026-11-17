package com.example.domain

import com.example.data.model.ChapterDetectionResult
import com.example.data.model.DetectedPage
import com.example.data.model.ZipFilenameFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

object ChapterPageDetector {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class JsImageCandidate(
        val url: String = "",
        val width: Int = 0,
        val height: Int = 0,
        val alt: String = ""
    )

    @Serializable
    private data class JsDetectionPayload(
        val title: String = "",
        val h1: String = "",
        val mangaName: String = "",
        val chapterName: String = "",
        val images: List<JsImageCandidate> = emptyList(),
        val excludedCount: Int = 0
    )

    /**
     * Evaluates the currently authorized, loaded WebView DOM to extract chapter page images
     * in reading order while filtering out navigation icons, logos, ads, and thumbnails.
     * Does not bypass authentication, DRM, paywalls, or anti-bot protections.
     */
    val CHAPTER_DETECTION_JS: String = """
        (function() {
            try {
                function pickUrl(img) {
                    var attrs = ['data-src', 'data-lazy-src', 'data-original', 'data-cfsrc', 'data-url'];
                    for (var i = 0; i < attrs.length; i++) {
                        var v = img.getAttribute(attrs[i]);
                        if (v && v.trim() && !isPlaceholder(v)) return v.trim();
                    }
                    var cs = img.currentSrc || img.getAttribute('src') || '';
                    if (cs && !isPlaceholder(cs)) return cs.trim();
                    var srcset = img.getAttribute('srcset') || '';
                    if (srcset) {
                        var first = srcset.split(',')[0].trim().split(' ')[0];
                        if (first && !isPlaceholder(first)) return first;
                    }
                    return '';
                }

                function isPlaceholder(u) {
                    var l = u.toLowerCase();
                    if (l.indexOf('data:image') === 0) return true;
                    if (l.indexOf('javascript:') === 0) return true;
                    if (l.indexOf('loading.gif') !== -1 || l.indexOf('spinner') !== -1 || l.indexOf('blank.gif') !== -1 || l.indexOf('spacer') !== -1) return true;
                    return false;
                }

                function hasExcludedAncestor(el) {
                    var cur = el;
                    while (cur && cur.tagName) {
                        var tag = cur.tagName.toLowerCase();
                        if (tag === 'header' || tag === 'footer' || tag === 'nav' || tag === 'aside') return true;
                        var cls = (typeof cur.className === 'string' ? cur.className : '') + ' ' + (cur.id || '');
                        var low = cls.toLowerCase();
                        if (/\b(sidebar|widget|comment|comments|related|recommend|popular|footer|header|navbar|menu|social|share|ad-|ads|advert|banner|popup|modal|avatar|author-box)\b/.test(low)) {
                            return true;
                        }
                        cur = cur.parentElement;
                    }
                    return false;
                }

                function isExcludedUrlOrMeta(url, img) {
                    var low = url.toLowerCase();
                    if (low.indexOf('http://') !== 0 && low.indexOf('https://') !== 0) return true;
                    if (/(\.svg|\.gif)(\?|#|$)/i.test(low)) return true;
                    if (/(logo|favicon|avatar|gravatar|wp-emoji|emoji|discord|patreon|ko-fi|kofi|paypal|banner|advert|doubleclick|googlesyndication|badge|icon-|\/icons\/|thumb-|thumbnail|cover-small|1x1)/i.test(low)) return true;
                    var cls = ((typeof img.className === 'string' ? img.className : '') + ' ' + (img.id || '') + ' ' + (img.alt || '')).toLowerCase();
                    if (/(logo|avatar|emoji|icon|banner|advert|thumbnail|wp-post-image)/i.test(cls)) return true;
                    return false;
                }

                function collectFromNodes(nodeList, strictSize) {
                    var out = [];
                    var excluded = 0;
                    var seen = {};
                    for (var i = 0; i < nodeList.length; i++) {
                        var img = nodeList[i];
                        if (hasExcludedAncestor(img)) { excluded++; continue; }
                        var rawUrl = pickUrl(img);
                        if (!rawUrl) { excluded++; continue; }
                        var absUrl = rawUrl;
                        try { absUrl = new URL(rawUrl, window.location.href).href; } catch (e) {}
                        if (isExcludedUrlOrMeta(absUrl, img)) { excluded++; continue; }

                        var nw = img.naturalWidth || 0;
                        var nh = img.naturalHeight || 0;
                        var cw = img.clientWidth || 0;
                        var ch = img.clientHeight || 0;
                        var w = nw > 0 ? nw : cw;
                        var h = nh > 0 ? nh : ch;

                        if (strictSize && w > 0 && h > 0 && (w < 180 || h < 180)) {
                            excluded++;
                            continue;
                        }
                        if (nw > 0 && nh > 0 && (nw < 150 || nh < 150)) {
                            excluded++;
                            continue;
                        }

                        var normKey = absUrl.split('#')[0];
                        if (seen[normKey]) { excluded++; continue; }
                        seen[normKey] = true;

                        out.push({
                            url: absUrl,
                            width: nw > 0 ? nw : cw,
                            height: nh > 0 ? nh : ch,
                            alt: (img.alt || '').substring(0, 80)
                        });
                    }
                    return { images: out, excluded: excluded };
                }

                var readerSelectors = [
                    '#readerarea img',
                    '.reading-content img',
                    '.chapter-content img',
                    '.page-break img',
                    'img.wp-manga-chapter-img',
                    'div[id*="reader"] img',
                    'div[class*="reader"] img',
                    'article .entry-content img'
                ];

                var best = { images: [], excluded: 0 };
                for (var s = 0; s < readerSelectors.length; s++) {
                    var nodes = document.querySelectorAll(readerSelectors[s]);
                    if (nodes && nodes.length > 0) {
                        var res = collectFromNodes(nodes, false);
                        if (res.images.length >= 2 || (res.images.length === 1 && best.images.length === 0)) {
                            if (res.images.length > best.images.length) {
                                best = res;
                            }
                            if (res.images.length >= 3) break;
                        }
                    }
                }

                if (best.images.length === 0) {
                    var allImgs = document.querySelectorAll('img');
                    best = collectFromNodes(allImgs, true);
                }

                var mangaName = '';
                var breadcrumbLinks = document.querySelectorAll('.breadcrumb a, .ts-breadcrumb a, nav[aria-label*="breadcrumb"] a');
                if (breadcrumbLinks && breadcrumbLinks.length >= 2) {
                    mangaName = (breadcrumbLinks[breadcrumbLinks.length - 1].textContent || '').trim();
                }
                if (!mangaName) {
                    var seriesLink = document.querySelector('.allc a, .chapter-info a, a[href*="/manga/"], a[href*="/series/"]');
                    if (seriesLink) mangaName = (seriesLink.textContent || '').trim();
                }

                var h1El = document.querySelector('h1.entry-title, h1#chapter-heading, .headpost h1, h1');
                var h1Text = h1El ? (h1El.textContent || '').trim() : '';

                return JSON.stringify({
                    title: document.title || '',
                    h1: h1Text,
                    mangaName: mangaName,
                    chapterName: h1Text,
                    images: best.images,
                    excludedCount: best.excluded
                });
            } catch (err) {
                return JSON.stringify({
                    title: document.title || '',
                    h1: '',
                    mangaName: '',
                    chapterName: '',
                    images: [],
                    excludedCount: 0
                });
            }
        })();
    """.trimIndent()

    fun parseDetectionResult(
        rawJsResult: String?,
        pageUrl: String,
        filenameFormat: ZipFilenameFormat
    ): ChapterDetectionResult {
        val cleanedJson = unescapeEvaluateJsString(rawJsResult)
        if (cleanedJson.isBlank()) {
            return ChapterDetectionResult(
                mangaTitle = "Manga",
                chapterTitle = "Chapter 01",
                chapterNumber = "01",
                pageUrl = pageUrl,
                pages = emptyList(),
                excludedCount = 0,
                suggestedZipName = filenameFormat.formatFilename("Manga", "01"),
                errorMessage = "Could not inspect the current page. Please make sure the chapter has finished loading."
            )
        }

        val payload = runCatching {
            json.decodeFromString(JsDetectionPayload.serializer(), cleanedJson)
        }.getOrNull()

        if (payload == null) {
            return ChapterDetectionResult(
                mangaTitle = "Manga",
                chapterTitle = "Chapter 01",
                chapterNumber = "01",
                pageUrl = pageUrl,
                pages = emptyList(),
                excludedCount = 0,
                suggestedZipName = filenameFormat.formatFilename("Manga", "01"),
                errorMessage = "Unable to parse chapter page elements."
            )
        }

        val (mangaTitle, chapterTitle, chapterNumber) = deriveMetadata(
            pageTitle = payload.title,
            h1Text = payload.h1,
            explicitManga = payload.mangaName,
            pageUrl = pageUrl
        )

        val pages = payload.images
            .filter { isSupportedHttpUrl(it.url) }
            .distinctBy { normalizeImageKey(it.url) }
            .mapIndexed { idx, candidate ->
                DetectedPage(
                    index = idx,
                    url = candidate.url,
                    width = candidate.width,
                    height = candidate.height,
                    alt = candidate.alt,
                    isSelected = true
                )
            }

        val suggestedZip = filenameFormat.formatFilename(mangaTitle, chapterNumber)
        val error = if (pages.isEmpty()) {
            "No eligible chapter pages were detected on this page. Open a manga chapter reader first."
        } else {
            null
        }

        return ChapterDetectionResult(
            mangaTitle = mangaTitle,
            chapterTitle = chapterTitle,
            chapterNumber = chapterNumber,
            pageUrl = pageUrl,
            pages = pages,
            excludedCount = payload.excludedCount,
            suggestedZipName = suggestedZip,
            errorMessage = error
        )
    }

    internal fun unescapeEvaluateJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        val trimmed = raw.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            runCatching { json.decodeFromString(kotlinx.serialization.serializer<String>(), trimmed) }
                .getOrDefault(trimmed.removeSurrounding("\""))
        } else {
            trimmed
        }
    }

    internal fun deriveMetadata(
        pageTitle: String,
        h1Text: String,
        explicitManga: String,
        pageUrl: String
    ): Triple<String, String, String> {
        val rawHeading = h1Text.ifBlank { pageTitle }
            .replace(Regex("\\s*[-|–—]\\s*Thunder\\s*Scans.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-|–—]\\s*Read\\s+Online.*$", RegexOption.IGNORE_CASE), "")
            .trim()

        val chapterMatch = Regex(
            "(?:chapter|ch\\.?)\\s*([0-9]+(?:\\.[0-9]+)?)",
            RegexOption.IGNORE_CASE
        ).find(rawHeading)
            ?: Regex(
                "(?:chapter|ch\\.?)\\s*[-_/]?\\s*([0-9]+(?:\\.[0-9]+)?)",
                RegexOption.IGNORE_CASE
            ).find(pageUrl)

        val chapterNum = chapterMatch?.groupValues?.getOrNull(1)?.let {
            ZipFilenameFormat.padChapterNumber(it)
        } ?: "01"

        val chapterTitle = if (chapterMatch != null) {
            "Chapter $chapterNum"
        } else {
            rawHeading.ifBlank { "Chapter 01" }
        }

        val derivedManga = explicitManga
            .replace(Regex("\\s*[-|–—]\\s*Thunder\\s*Scans.*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank {
                rawHeading
                    .replace(
                        Regex("\\s*[-–—:]?\\s*(?:chapter|ch\\.?)\\s*[0-9]+(?:\\.[0-9]+)?.*$", RegexOption.IGNORE_CASE),
                        ""
                    )
                    .trim()
            }
            .ifBlank { deriveMangaFromUrl(pageUrl) }
            .ifBlank { "Manga" }

        return Triple(
            ZipFilenameFormat.sanitizePathSegment(derivedManga),
            chapterTitle,
            chapterNum
        )
    }

    private fun deriveMangaFromUrl(pageUrl: String): String {
        return runCatching {
            val path = URI(pageUrl).path.trim('/')
            val seg = path.split('/').lastOrNull { it.isNotBlank() }.orEmpty()
            seg.replace(Regex("-chapter-.*$", RegexOption.IGNORE_CASE), "")
                .replace('-', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        }.getOrDefault("")
    }

    private fun isSupportedHttpUrl(url: String): Boolean {
        val low = url.lowercase()
        return low.startsWith("http://") || low.startsWith("https://")
    }

    private fun normalizeImageKey(url: String): String = url.substringBefore('#').trim()

    fun formatPageFilename(oneBasedIndex: Int, extension: String): String {
        val cleanExt = extension.lowercase().trimStart('.')
        return String.format("%03d.%s", oneBasedIndex, cleanExt)
    }
}
