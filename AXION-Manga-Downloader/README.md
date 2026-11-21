# AXION Manga Downloader

**AXION Manga Downloader** is a Kotlin and Jetpack Compose Android application designed for browsing **Thunder Scans** (`https://en-thunderscans.com/`) and **ManhwaHub** (`https://manhwahub.to/`) in an integrated WebView browser, automatically detecting authorized chapter pages, filtering duplicates, and archiving chapters into sequentially numbered ZIP files saved directly to your phone's `Download` folder.

---

## Main Features

- **In-App WebView Browser with Corner Source Switch**: Browse Thunder Scans directly inside the app and tap the top-right corner switch button (`ManhwaHub` / `Thunder`) at any time to instantly switch between `https://en-thunderscans.com/` and `https://manhwahub.to/`. Includes JavaScript support, zoom controls, session/cookie preservation across tabs, Back/Forward/Refresh/Home controls, loading progress indicator, and network error recovery.
- **Automatic Chapter Page Detection**: Inspects loaded, authorized chapter pages on both Thunder Scans and ManhwaHub in reading order while filtering out navigation icons, logos, avatars, ads, and unrelated thumbnails, with an interactive review sheet before downloading.
- **Sequential 3-Digit Numbering**: Saves pages in original reading order as `001.jpg`, `002.jpg`, `003.jpg`, etc., validating `JPG`, `JPEG`, `PNG`, and `WebP` image headers.
- **Streaming SHA-256 Duplicate Detection**: Skips byte-identical duplicate assets while preserving distinct chapter pages (including separate pages with repeated panels) and reports skipped duplicates.
- **One-Click ZIP Archiving**: Streams downloaded pages directly into a ZIP archive (`Manga_Name_Chapter_01.zip`) in your device's `Download` folder via Android `MediaStore.Downloads` or Storage Access Framework (SAF) with low memory usage.
- **Dedicated Download Manager**: Real-time page counts, progress percentage, transfer speed, ZIP creation status, plus **Cancel**, **Resume**, and **Retry Failed** controls.
- **Download History**: Displays manga/manhwa name, chapter number, ZIP filename, total pages, date, and file status, with **Open**, **Share**, and safe **Delete** options (preserving the ZIP file unless deletion is explicitly chosen).
- **Settings**: Dark Obsidian / Light theme toggle, SAF download folder selection, ZIP filename format templates, notification controls, history management, and privacy details.

---

## App & Build Information

- **Application Name**: AXION Manga Downloader
- **Package Name (`applicationId`)**: `com.aistudio.axionmanga.krxvmz`
- **Current Version**: `1.1.0` (`versionCode: 2`)
- **Minimum Android Version**: Android 7.0 (API 24) — designed and optimized for Android 8.0 (API 26) and higher (Target SDK 36)
- **Target GitHub Repository**: `https://github.com/Avinashkongari/Manhwa-apk`
- **APK Path**: `AXION-Manga-Downloader/APK/AXION-Manga-Downloader.apk`

---

## APK Download & Installation Instructions

### Download
1. Open [`AXION-Manga-Downloader/APK/AXION-Manga-Downloader.apk`](./APK/AXION-Manga-Downloader.apk) in the repository.
2. Click **Download raw file** (or **Raw**) to save `AXION-Manga-Downloader.apk` to your Android device.

### Install on Android
1. Open your device's **Files** app or browser downloads and tap `AXION-Manga-Downloader.apk`.
2. If prompted, enable **Install unknown apps** for your browser or file manager in Android Settings.
3. Tap **Install** and confirm when installation finishes.
4. Launch **AXION Manga Downloader** from your app drawer, browse Thunder Scans or tap the top-right corner switch button to open ManhwaHub (`https://manhwahub.to/`), open any chapter, and tap **Download Chapter** to save your chapter ZIP.
