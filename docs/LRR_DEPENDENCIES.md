# LRR Dependency Inventory

**Date:** 2026-08-27  
**Build Scope:** Lyrune Resolver Runtime (LRR) Phase 1 & Phase 2

---

## Direct Dependencies

| Dependency / Tool | Exact Coordinate / Version | License | Purpose | Owning Module |
| :--- | :--- | :--- | :--- | :--- |
| **Chaquopy Gradle Plugin** | `com.chaquo.python:17.0.0` | MIT / Proprietary Runtime | CPython embedding in Android Gradle builds | Top-level `build.gradle.kts` & `:app` |
| **CPython** | `3.14.7` (Python 3.14) | PSF License | Embedded Python interpreter for metadata extraction | `:resolver` private process |
| **yt-dlp** | `yt-dlp==2026.8.19` | The Unlicense / CC0-1.0 | Metadata extraction and format inspection via Python API | `:resolver` private process |
| **Kotlin Coroutines Android** | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Apache-2.0 | Asynchronous non-blocking AIDL bridge and job management | `:app` |
| **Kotlinx Serialization JSON** | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | Apache-2.0 | Compact IPC DTO serialization between processes | `:app` |
| **AndroidX Core KTX** | `androidx.core:core-ktx:1.15.0` | Apache-2.0 | Android system service binding and platform utilities | `:app` |

---

## Phase 3 Planned Dependencies (Design Gate)

| Component | Target Version / Coordinate | License | Purpose |
| :--- | :--- | :--- | :--- |
| **QuickJS-NG** | Latest Stable NDK r28+ Build | MIT | Lightweight embedded JavaScript runtime for signature extraction |
| **yt-dlp-ejs** | Paired release with yt-dlp | The Unlicense / CC0-1.0 | Extractor JavaScript scripts bundle |

---

## Excluded / Prohibited Components (This Pass)

| Component | Status | Rationale |
| :--- | :--- | :--- |
| **FFmpeg** | `NOT_INCLUDED` | Metadata inspection is FFmpeg-free. FFmpeg is deferred to a future optional module. |
| **aria2c** | `PROHIBITED` | Existing WorkManager HTTP download pipeline is sufficient for direct streams. |
| **youtubedl-android** | `PROHIBITED` | GPL-licensed monolithic wrapper; replaced by Lyrune clean-room AIDL runtime. |
| **Seal** | `PROHIBITED` | GPL-licensed application code; architecture studied only for system context. |
| **Runtime Self-Updater** | `DEFERRED` | Executable code downloads after installation violate reproducible build policies. |
