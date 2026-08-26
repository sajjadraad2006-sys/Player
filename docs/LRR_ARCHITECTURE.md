# Lyrune Resolver Runtime (LRR) Architecture

**Date:** 2026-08-27  
**Status:** Implemented & Verified (Phase 1, Phase 2, Phase 3)  
**Module Area:** `dev.auralis.studio.resolver`

---

## 1. Why LRR Exists

Lyrune is a private, high-fidelity music player with real-time DSP, lyrics synchronization, and local/online catalog integration (Audius, Jamendo, iTunes, Deezer).

LRR (Lyrune Resolver Runtime) provides an additive, modular external-media resolution architecture for media sources the user is authorized to import or resolve.

### Key Architectural Invariants:
1. **LRR is a Resolver, NOT a Downloader:** LRR does not download files, create database rows, manage notifications, or duplicate WorkManager. It strictly handles metadata inspection and format resolution (`download=False`).
2. **Official Providers Remain Untouched:** Audius, Jamendo, iTunes, and Deezer catalog search and streaming do not route through or depend on LRR.
3. **No Direct Wrapper Coupling:** Third-party monolithic wrappers (such as `youtubedl-android` or Seal) are not embedded. Lyrune owns its public API contract, process isolation, normalization, and IPC boundaries.
4. **No DRM or Restriction Bypass:** LRR is designed strictly for permitted content and authorized media resolution. If content cannot be downloaded or is restricted, structured error codes (`DownloadNotPermitted`, `AccessRestricted`) are returned.

---

## 2. Process Isolation & Topology

Heavy runtimes (CPython 3.14, standard libraries, extractor modules, and QuickJS-NG) are isolated into a dedicated private Android process:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                              MAIN PROCESS                              │
│                                                                        │
│  Compose UI • Media3 • DSP • Lyrics • Room Database                    │
│  Existing Providers (Audius, Jamendo, iTunes, Deezer)                  │
│  Existing Download Pipeline (WorkManager / Room / MediaStore)          │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │             LRR Pure Kotlin API (resolver.api)                   │  │
│  │  MediaResolver • ResolverRequest • ResolvedMedia                 │  │
│  │  ResolvedFormat • ResolverError • RuntimeHealth                  │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                        AndroidProcessMediaResolver                     │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │  Asynchronous AIDL / Binder IPC
                                      │  (Compact normalized JSON DTOs)
┌─────────────────────────────────────┼──────────────────────────────────┐
│                   RESOLVER PROCESS (:resolver)                         │
│                                                                        │
│                        LrrResolverService                              │
│                                     │                                  │
│                            ChaquopyYtDlpEngine                         │
│                                     │                                  │
│                       Chaquopy 17 / Python 3.14.7                      │
│                   lrr_extractor.py (yt-dlp==2026.8.19)                 │
│                   yt-dlp-ejs==0.8.0 / QuickJS-NG 0.15.1                │
│                       extract_info(download=False)                     │
└────────────────────────────────────────────────────────────────────────┘
```

### Process Resilience & Performance:
- `android:process=":resolver"` with `android:exported="false"`.
- Python initializes lazily inside `LrrResolverService` in `:resolver`. The main player process never initializes Python.
- If the resolver process crashes or is killed by the OS, `AndroidProcessMediaResolver` detects Binder death via `IBinder.DeathRecipient`, maps in-flight calls to `ResolverErrorCode.RuntimeCrashed`, and seamlessly rebinds on the next call.
- Media3 playback, DSP, and UI in the main process continue uninterrupted with 0 MB memory leakage into the main heap.

---

## 3. Asynchronous AIDL IPC Design

Extraction involves network operations and Python execution. Long-running synchronous IPC methods are strictly avoided.

### AIDL Contracts:
```aidl
// ILrrResolverCallback.aidl
package dev.auralis.studio.resolver.android;

interface ILrrResolverCallback {
    void onSuccess(String requestId, String resultJson);
    void onFailure(String requestId, String errorJson);
}

// ILrrResolverService.aidl
package dev.auralis.studio.resolver.android;

import dev.auralis.studio.resolver.android.ILrrResolverCallback;

interface ILrrResolverService {
    void inspect(String requestJson, ILrrResolverCallback callback);
    void resolve(String requestJson, ILrrResolverCallback callback);
    void cancelRequest(String requestId);
    String getHealthJson();
}
```

### IPC Rules:
- **Asynchronous Execution:** Service dispatches jobs onto a coroutine background thread pool (`Dispatchers.IO`).
- **Cancellation by ID:** `cancelRequest(requestId)` cancels the active coroutine job and notifies the callback.
- **Main-Process Bridge:** `AndroidProcessMediaResolver` wraps AIDL calls in Kotlin `suspendCancellableCoroutine` with coroutine cancellation propagation, timeout enforcement (25s), and response size validation (< 512 KB).
- **Compact DTOs:** Only normalized metadata is passed across Binder; raw extractor dictionary trees are discarded in Python before IPC.

---

## 4. Phase 3: JavaScript Runtime & EJS Architecture

Modern media sources require executing JavaScript extraction helpers (`yt-dlp-ejs` and a runtime JavaScript interpreter).

### Strategy A: APK-Bundled Native Executable (Verified)
- **Binary Placement:** Packaged in APK under `lib/<abi>/libqjs.so`.
- **Extraction & Permissions:** Extracted by Android package installer into `context.applicationInfo.nativeLibraryDir` with mode `0755` (`-rwxr-xr-x`).
- **W^X Compliance:** `nativeLibraryDir` is read-only for the application UID at runtime (managed by system/installd). It does not write to app storage and does not violate W^X policy on targetSdk 36.
- **Runtime Execution:** `lrr_extractor.py` configures `yt-dlp`'s `js_runtimes` param: `{"quickjs": {"path": qjs_path}}`.
- **Verification on Android 15 (targetSdk 36, API 35):** Tested and verified on real Android emulator `x86_64` with exit code 0 (`QuickJS-ng version 0.15.1`, `yt-dlp` runtime detection `true`).

### 16KB Page-Size Alignment Verification
- Built with Android NDK r28b (Clang 19.0.1) targeting `arm64-v8a` and `x86_64` (`-fPIC`, modern Bionic API 26+).
- Inspected with `llvm-readelf -l`: Every ELF `LOAD` segment exhibits `Align = 0x4000` (16,384 bytes).
- Both `arm64-v8a` and `x86_64` binaries are 16KB page-safe.

---

## 5. Structured Error Taxonomy

LRR replaces generic strings with a typed error taxonomy:

| Technical Code | Retryable | Safe User Message |
| :--- | :---: | :--- |
| `UnsupportedUrl` | No | The provided link is not supported. |
| `NotFound` | No | The requested media could not be found. |
| `NetworkUnavailable` | Yes | Network connection unavailable. Please check your internet connection. |
| `DnsFailure` | Yes | Could not resolve server address. |
| `Timeout` | Yes | The request timed out. Please try again. |
| `RateLimited` | Yes | Rate limit reached. Please try again in a few minutes. |
| `AuthenticationRequired` | No | Authentication is required to access this resource. |
| `AccessRestricted` | No | Access to this content is restricted by the provider. |
| `DownloadNotPermitted` | No | Downloading this content is not permitted. |
| `RuntimeUnavailable` | Yes | Resolver service is currently unavailable. |
| `PythonUnavailable` | No | Resolver engine is not initialized. |
| `JsRuntimeUnavailable` | No | JavaScript runtime required for this provider is not installed. |
| `EjsIncompatible` | No | Extractor JavaScript bundle is incompatible. |
| `ChallengeFailed` | Yes | Provider challenge could not be resolved. |
| `ExpiredResource` | Yes | The temporary media link has expired. |
| `FormatUnavailable` | No | The requested audio format is not available. |
| `ProviderChanged` | No | The media provider updated its playback contract. |
| `RuntimeCrashed` | Yes | The resolver background process encountered an error and was restarted. |
| `Cancelled` | No | The resolution operation was cancelled. |
| `Unknown` | No | Unable to resolve media from this source. |

---

## 6. Runtime Stack & Pinned Dependencies

| Component | Pinned Version | Scope |
| :--- | :--- | :--- |
| Chaquopy Plugin | `17.0.0` | Build plugin applied only in `:app` |
| Python Runtime | `3.14.7` (CPython 3.14) | Embedded in `:resolver` process |
| yt-dlp Python Library | `2026.8.19` | Pinned in Chaquopy `pip` block |
| yt-dlp-ejs Python Package | `0.8.0` | Pinned in Chaquopy `pip` block (EJS v0.8.0) |
| QuickJS-NG | `0.15.1` (commit `fd0a021`) | Bundled native binary in `lib/<abi>/libqjs.so` |
| Android NDK | `28.2.13676358` (NDK r28b) | C/C++ compilation toolchain (16KB page-safe) |
| ABI Architectures | `arm64-v8a`, `x86_64` | 16KB-page-size aligned |

---

## 7. Security & Temporary File Handling

1. **No Writable-App-Storage Executable Execution:** No binary is copied to `filesDir` or `cacheDir` for `chmod +x` execution.
2. **Temporary JS Scripts:** EJS generates ephemeral `.js` files using `tempfile.NamedTemporaryFile` strictly as input data to QuickJS. Files are deleted in `finally` blocks immediately upon subprocess exit.
3. **Sensitive Headers Policy (Recommendation for Phase 4):**
   - Cookies and Authorization tokens are stripped from persistent Room DTOs and public IPC responses.
   - For legitimate authenticated playback/download in Phase 4, evaluate an ephemeral in-memory capability handle with strict TTL, or designate streams requiring non-standard headers as `ENGINE_REQUIRED`.

---

## 8. Licensing & Governance

- Lyrune's LRR implementation is pure clean-room Kotlin and Python code licensed under MIT.
- No GPL-licensed wrapper code (e.g. `youtubedl-android`, Seal) is copied into Lyrune.
- `yt-dlp` and `yt-dlp-ejs` are licensed under the Unlicense / CC0-1.0.
- `QuickJS-NG` is licensed under the MIT License.
- CPython is licensed under the Python Software Foundation License (PSFL).
