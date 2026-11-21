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
                function cleanUrl(u) {
                    if (!u) return '';
                    return String(u).replace(/[\r\n\t]+/g, '').trim();
                }

                function isPlaceholder(u) {
                    var l = cleanUrl(u).toLowerCase();
                    if (!l) return true;
                    if (l.indexOf('data:image') === 0) return true;
                    if (l.indexOf('blob:') === 0) return true;
                    if (l.indexOf('javascript:') === 0) return true;
                    if (/(loading|spinner|blank|spacer|loader|placeholder|lazy-load|dflazy)\.(gif|png|svg|jpg|webp)(\?|#|$)/i.test(l)) return true;
                    return false;
                }

                function pickFirstSrcsetUrl(srcset) {
                    if (!srcset) return '';
                    var parts = String(srcset).split(',');
                    for (var i = 0; i < parts.length; i++) {
                        var candidate = cleanUrl(parts[i]).split(/\s+/)[0];
                        if (candidate && !isPlaceholder(candidate)) return candidate;
                    }
                    return '';
                }

                function pickUrl(img) {
                    var lazyAttrs = [
                        'data-src',
                        'data-lazy-src',
                        'data-original',
                        'data-cfsrc',
                        'data-url',
                        'data-ll-src',
                        'data-echo',
                        'data-image',
                        'data-full-url'
                    ];
                    for (var i = 0; i < lazyAttrs.length; i++) {
                        var v = cleanUrl(img.getAttribute(lazyAttrs[i]));
                        if (v && !isPlaceholder(v)) {
                            return { url: v, fromLazy: true };
                        }
                    }
                    var dataSrcset = pickFirstSrcsetUrl(img.getAttribute('data-srcset') || img.getAttribute('data-lazy-srcset'));
                    if (dataSrcset) {
                        return { url: dataSrcset, fromLazy: true };
                    }
                    var parent = img.parentElement;
                    if (parent && parent.tagName && parent.tagName.toLowerCase() === 'picture') {
                        var sources = parent.querySelectorAll('source');
                        for (var p = 0; p < sources.length; p++) {
                            var sUrl = pickFirstSrcsetUrl(sources[p].getAttribute('data-srcset') || sources[p].getAttribute('srcset'));
                            if (sUrl) return { url: sUrl, fromLazy: true };
                        }
                    }
                    var cs = cleanUrl(img.currentSrc || img.getAttribute('src') || '');
                    if (cs && !isPlaceholder(cs)) {
                        return { url: cs, fromLazy: false };
                    }
                    var srcset = pickFirstSrcsetUrl(img.getAttribute('srcset'));
                    if (srcset) {
                        return { url: srcset, fromLazy: false };
                    }
                    return { url: '', fromLazy: false };
                }

                var excludedTokens = {
                    'sidebar': true,
                    'main-sidebar': true,
                    'widget': true,
                    'widget-area': true,
                    'comments': true,
                    'comments-area': true,
                    'comment-list': true,
                    'comment-respond': true,
                    'disqus_thread': true,
                    'related-posts': true,
                    'related-manga': true,
                    'recommended': true,
                    'popular-manga': true,
                    'site-header': true,
                    'site-footer': true,
                    'main-header': true,
                    'main-footer': true,
                    'c-header': true,
                    'c-sub-header-nav': true,
                    'navbar': true,
                    'nav-menu': true,
                    'social-share': true,
                    'share-buttons': true,
                    'ads': true,
                    'advert': true,
                    'advertisement': true,
                    'ad-container': true,
                    'ad-banner': true,
                    'ad-wrapper': true,
                    'banner-ad': true,
                    'popup': true,
                    'modal': true,
                    'author-box': true,
                    'footer-wrapper': true
                };

                function hasExcludedToken(str) {
                    if (!str) return false;
                    var tokens = String(str).toLowerCase().trim().split(/\s+/);
                    for (var i = 0; i < tokens.length; i++) {
                        var t = tokens[i];
                        if (!t) continue;
                        if (excludedTokens[t]) return true;
                        if (t.indexOf('ad-') === 0 || t.indexOf('ads-') === 0 || t.indexOf('widget_') === 0) return true;
                    }
                    return false;
                }

                function hasExcludedAncestor(el, isDedicatedReader) {
                    var cur = el.parentElement;
                    while (cur && cur.tagName) {
                        var tag = cur.tagName.toLowerCase();
                        // Never inspect <body> or <html> (WordPress/Madara adds classes like header-style-1, no-sidebar there)
                        if (tag === 'body' || tag === 'html') return false;
                        if (tag === 'header' || tag === 'footer' || tag === 'nav' || tag === 'aside') return true;
                        // Stop once we reach a known reader container root
                        var clsStr = typeof cur.className === 'string' ? cur.className : '';
                        var idStr = cur.id || '';
                        if (isDedicatedReader) {
                            var lowRoot = (clsStr + ' ' + idStr).toLowerCase();
                            if (lowRoot.indexOf('reading-content') !== -1 ||
                                lowRoot.indexOf('readerarea') !== -1 ||
                                lowRoot.indexOf('chapter-content') !== -1 ||
                                lowRoot.indexOf('chapter-images') !== -1 ||
                                lowRoot.indexOf('container-chapter-reader') !== -1) {
                                return false;
                            }
                        }
                        if (hasExcludedToken(clsStr) || hasExcludedToken(idStr)) {
                            return true;
                        }
                        cur = cur.parentElement;
                    }
                    return false;
                }

                function isExcludedUrlOrMeta(url, img, isDedicatedReader) {
                    var low = url.toLowerCase();
                    if (low.indexOf('http://') !== 0 && low.indexOf('https://') !== 0) return true;
                    if (/(\.svg|\.ico)(\?|#|$)/i.test(low)) return true;
                    if (/(doubleclick|googlesyndication|googleadservices|adservice|adserver|gravatar\.com|wp-emoji|\/emoji\/|favicon|1x1\.)/i.test(low)) return true;
                    if (!isDedicatedReader) {
                        if (/(logo|discord|patreon|ko-fi|kofi|paypal|\/icons\/|cover-small)/i.test(low)) return true;
                        var imgClsId = ((typeof img.className === 'string' ? img.className : '') + ' ' + (img.id || '')).toLowerCase();
                        if (/\b(custom-logo|site-logo|avatar|gravatar|emoji|wp-post-image)\b/.test(imgClsId)) return true;
                    }
                    return false;
                }

                function collectFromNodes(nodeList, strictSize, isDedicatedReader) {
                    var out = [];
                    var excluded = 0;
                    var seen = {};
                    for (var i = 0; i < nodeList.length; i++) {
                        var img = nodeList[i];
                        if (hasExcludedAncestor(img, isDedicatedReader)) { excluded++; continue; }
                        var picked = pickUrl(img);
                        if (!picked.url) { excluded++; continue; }
                        var absUrl = picked.url;
                        try { absUrl = new URL(picked.url, window.location.href).href; } catch (e) {}
                        if (isExcludedUrlOrMeta(absUrl, img, isDedicatedReader)) { excluded++; continue; }

                        var nw = img.naturalWidth || 0;
                        var nh = img.naturalHeight || 0;
                        var cw = img.clientWidth || 0;
                        var ch = img.clientHeight || 0;
                        var currentSrcClean = cleanUrl(img.currentSrc || '');
                        var isUnloadedLazy = picked.fromLazy && (!currentSrcClean || currentSrcClean !== absUrl);

                        // Only enforce pixel size filtering when the actual target image (not a 1x1 lazy placeholder) is loaded
                        if (!isUnloadedLazy) {
                            var w = nw > 0 ? nw : cw;
                            var h = nh > 0 ? nh : ch;
                            if (strictSize && w > 0 && h > 0 && (w < 180 || h < 180)) {
                                excluded++;
                                continue;
                            }
                            if (nw > 0 && nh > 0 && (nw < 120 || nh < 120)) {
                                excluded++;
                                continue;
                            }
                        }

                        var normKey = absUrl.split('#')[0];
                        if (seen[normKey]) { excluded++; continue; }
                        seen[normKey] = true;

                        out.push({
                            url: absUrl,
                            width: isUnloadedLazy ? 0 : (nw > 0 ? nw : cw),
                            height: isUnloadedLazy ? 0 : (nh > 0 ? nh : ch),
                            alt: (img.alt || '').substring(0, 80)
                        });
                    }
                    return { images: out, excluded: excluded };
                }

                var readerSelectors = [
                    '.reading-content img.wp-manga-chapter-img',
                    '.reading-content .page-break img',
                    '.reading-content img',
                    '.page-break img',
                    'img.wp-manga-chapter-img',
                    '#readerarea img',
                    '.chapter-content img',
                    '.chapter-images img',
                    '.container-chapter-reader img',
                    '#chapter-content img',
                    '.read-content img',
                    'div[id*="reader"] img',
                    'div[class*="reader"] img',
                    'div[class*="reading-content"] img',
                    'div[id*="chapter"] img',
                    'div[class*="chapter-img"] img',
                    'article .entry-content img'
                ];

                var best = { images: [], excluded: 0 };
                for (var s = 0; s < readerSelectors.length; s++) {
                    var nodes = document.querySelectorAll(readerSelectors[s]);
                    if (nodes && nodes.length > 0) {
                        var res = collectFromNodes(nodes, false, true);
                        if (res.images.length > best.images.length) {
                            best = res;
                        }
                        if (best.images.length >= 3) break;
                    }
                }

                if (best.images.length === 0) {
                    var mainImgs = document.querySelectorAll('main img, article img');
                    if (mainImgs && mainImgs.length > 0) {
                        best = collectFromNodes(mainImgs, true, false);
                    }
                }

                if (best.images.length === 0) {
                    var allImgs = document.querySelectorAll('img');
                    best = collectFromNodes(allImgs, true, false);
                }

                var mangaName = '';
                var breadcrumbLinks = document.querySelectorAll('.breadcrumb a, .ts-breadcrumb a, ol.breadcrumb a, nav[aria-label*="breadcrumb"] a');
                if (breadcrumbLinks && breadcrumbLinks.length >= 2) {
                    mangaName = (breadcrumbLinks[breadcrumbLinks.length - 1].textContent || '').trim();
                }
                if (!mangaName) {
                    var seriesLink = document.querySelector('.allc a, .chapter-info a, a[href*="/manga/"], a[href*="/manhwa/"], a[href*="/webtoon/"], a[href*="/series/"]');
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
            .replace(Regex("\\s*[-|–—]\\s*Manhwa\\s*Hub.*$", RegexOption.IGNORE_CASE), "")
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
            .replace(Regex("\\s*[-|–—]\\s*Manhwa\\s*Hub.*$", RegexOption.IGNORE_CASE), "")
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
            val segments = URI(pageUrl).path.trim('/').split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return@runCatching ""
            val ignoredCategories = setOf("webtoon", "manga", "manhwa", "series", "comic", "comics", "read", "title")
            val lastSeg = segments.last()
            val candidateSeg = if (
                Regex("^(?:chapter|ch)[-_]?[0-9]+(?:\\.[0-9]+)?$", RegexOption.IGNORE_CASE).matches(lastSeg)
            ) {
                segments.dropLast(1).lastOrNull { it.lowercase() !in ignoredCategories }.orEmpty()
            } else {
                lastSeg.replace(Regex("-chapter-.*$", RegexOption.IGNORE_CASE), "")
            }
            candidateSeg
                .replace('-', ' ')
                .replace('_', ' ')
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
