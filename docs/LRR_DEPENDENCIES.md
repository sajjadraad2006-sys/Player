# Lyrune Resolver Runtime (LRR) Dependencies & Audit Matrix

**Date:** 2026-08-27  
**Status:** Implemented & Verified (Phase 3.5 Hardening)

---

## 1. Pinned Component Versions

| Component | Exact Version | Source / Path | Status |
| :--- | :--- | :--- | :--- |
| **Android AGP** | `8.10.1` | Root Gradle plugin | Active |
| **Android compileSdk** | `36` | `app/build.gradle.kts` | Active |
| **Android targetSdk** | `36` | `app/build.gradle.kts` | Active |
| **Chaquopy Gradle Plugin** | `17.0.0` | `com.chaquo.python:17.0.0` | Active |
| **Python Build Interpreter** | `3.14.7` | `~/.local/bin/python3.14` | Local build interpreter |
| **Embedded Android Python** | `3.14.0` | Chaquopy 17 runtime | Active |
| **yt-dlp** | `2026.8.19` | Pinned in `build.gradle.kts` pip block | Verified |
| **yt-dlp-ejs** | `0.8.0` | Pinned in `build.gradle.kts` pip block | Verified |
| **QuickJS-NG (libqjs.so)** | `0.15.1` | Built with NDK r28b (`arm64-v8a`, `x86_64`) | Verified (`0755` executable) |
| **Android NDK** | `r28b (28.1.13356767)` | `/home/sajjad/.local/android-sdk/ndk/28.1.13356767` | Toolchain |

---

## 2. ELF 16KB Page Alignment Verification

All 44 `.so` libraries packaged in `Lyrune-debug.apk` were audited for `0x4000` (16,384 byte) ELF segment alignment:

| Architecture | Total .so Libraries | Aligned to `0x4000` (16KB) | Alignment Percentage |
| :--- | :---: | :---: | :---: |
| **arm64-v8a** | 22 | 22 | **100%** |
| **x86_64** | 22 | 22 | **100%** |
| **Combined Total** | **44** | **44** | **100%** |

Zip Alignment: 4-byte zip alignment verified across all shared object entries.

---

## 3. Platform & Hardware Verification Status

- **API 35 (Android 15) x86_64 Emulator:** `VERIFIED` (Passed real extraction matrix, QuickJS EJS execution, memory breakdown, cancellation)
- **API 36 Runtime:** `API 36 runtime: NOT VERIFIED` (Local SDK has API 35 image installed; build target verified with compileSdk 36 / targetSdk 36)
- **16KB Runtime Device:** `16KB runtime: NOT VERIFIED` (All 44 binaries verified 16KB-aligned `0x4000`; local emulator kernel runs standard 4KB page size)
- **ARM64 Physical/Virtual Device:** `ARM64 build verified, ARM64 runtime unverified` (All 22 arm64-v8a `.so` libraries verified; tests executed on active x86_64 emulator)

---

## 4. Test Verification Totals

| Test Type | Total Tests | Passed | Failed | Errors | Skipped |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Unit Tests (testDebugUnitTest)** | 188 | 187 | 0 | 0 | 1 |
| **Android Tests (connectedDebugAndroidTest)** | 7 | 6 | 0 | 0 | 1 |
