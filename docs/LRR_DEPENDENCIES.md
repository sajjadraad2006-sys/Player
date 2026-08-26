# LRR Dependency Inventory

**Date:** 2026-08-27  
**Build Scope:** Lyrune Resolver Runtime (LRR) Phase 1, Phase 2 & Phase 3 (Operational)

---

## Direct Dependencies

| Dependency / Tool | Exact Coordinate / Version | License | Purpose | Owning Module |
| :--- | :--- | :--- | :--- | :--- |
| **Chaquopy Gradle Plugin** | `com.chaquo.python:17.0.0` | MIT / Proprietary Runtime | CPython embedding in Android Gradle builds | Top-level `build.gradle.kts` & `:app` |
| **CPython** | `3.14.7` (Python 3.14) | PSF License | Embedded Python interpreter for metadata extraction | `:resolver` private process |
| **yt-dlp** | `yt-dlp==2026.8.19` | The Unlicense / CC0-1.0 | Metadata extraction and format inspection via Python API | `:resolver` private process |
| **yt-dlp-ejs** | `yt-dlp-ejs==0.8.0` | The Unlicense / CC0-1.0 | JavaScript solver script bundle for challenge execution | `:resolver` private process |
| **QuickJS-NG** | `0.15.1` (commit `fd0a021`) | MIT | Embedded JavaScript interpreter for signature extraction | `:resolver` (`libqjs.so`) |
| **Android NDK** | `28.2.13676358` (r28b) | Android Software Development Kit License | C/C++ native toolchain with default 16KB page alignment | Build Tool |
| **Kotlin Coroutines Android** | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` | Apache-2.0 | Asynchronous non-blocking AIDL bridge and job management | `:app` |
| **Kotlinx Serialization JSON** | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | Apache-2.0 | Compact IPC DTO serialization between processes | `:app` |
| **AndroidX Core KTX** | `androidx.core:core-ktx:1.15.0` | Apache-2.0 | Android system service binding and platform utilities | `:app` |

---

## Native Artifact Details

| Artifact | ABI | File Size | 16KB Alignment | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| `libqjs.so` | `arm64-v8a` | 1.19 MB | `Align = 0x4000` | `0df126559455229e97b41af9919e46db44eb7af89fbd8389290620c0a2b09536` |
| `libqjs.so` | `x86_64` | 1.25 MB | `Align = 0x4000` | `0c0c1e2ebf1dfea3044f359983a3557e315cbbda3802ab1c4f66593e60016c31` |

---

## Excluded / Prohibited Components (This Pass)

| Component | Status | Rationale |
| :--- | :--- | :--- |
| **FFmpeg** | `NOT_INCLUDED` | Metadata inspection is FFmpeg-free. FFmpeg is deferred to a future optional module. |
| **aria2c** | `PROHIBITED` | Existing WorkManager HTTP download pipeline is sufficient for direct streams. |
| **youtubedl-android** | `PROHIBITED` | GPL-licensed monolithic wrapper; replaced by Lyrune clean-room AIDL runtime. |
| **Seal** | `PROHIBITED` | GPL-licensed application code; architecture studied only for system context. |
| **Runtime Self-Updater** | `DEFERRED` | Executable code downloads after installation violate reproducible build policies. |
| **Helper APKs** | `REJECTED` | Strategy A (bundled `libqjs.so` via `nativeLibraryDir`) succeeded; helper APKs are unnecessary. |
