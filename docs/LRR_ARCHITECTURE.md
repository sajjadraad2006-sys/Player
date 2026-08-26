# Lyrune Resolver Runtime (LRR) Architecture

**Date:** 2026-08-27  
**Status:** Implemented (Phase 1 & Phase 2) • Phase 3 (QuickJS-NG/EJS) Architecturally Prepared  
**Module Area:** `dev.auralis.studio.resolver`

---

## 1. Why LRR Exists

Lyrune is a private, high-fidelity music player with real-time DSP, lyrics synchronization, and local/online catalog integration (Audius, Jamendo, iTunes, Deezer).

LRR (Lyrune Resolver Runtime) provides an additive, modular external-media resolution architecture for media sources the user is authorized to import or resolve.

### Key Architectural Invariants:
1. **LRR is a Resolver, NOT a Downloader:** LRR does not download files, create database rows, manage notifications, or duplicate WorkManager. It strictly handles metadata inspection and format resolution (`download=False`).
2. **Official Providers Remain Untouched:** Audius, Jamendo, iTunes, and Deezer catalog search and streaming do not route through or depend on LRR.
3. **No Direct Wrapper Coupling:** Third-party wrappers (such as `youtubedl-android` or Seal) are not embedded into the core architecture. Lyrune owns its public API contract, process isolation, normalization, and IPC boundaries.
4. **No DRM or Restriction Bypass:** LRR is designed strictly for permitted content and authorized media resolution. If content cannot be downloaded or is restricted, structured error codes (`DownloadNotPermitted`, `AccessRestricted`) are returned.

---

## 2. Process Isolation & Topology

Heavy runtimes (CPython, standard libraries, extractor modules, and future JS engines) are isolated into a dedicated private Android process:

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
│                       extract_info(download=False)                     │
└────────────────────────────────────────────────────────────────────────┘
```

### Process Resilience:
- `android:process=":resolver"` with `android:exported="false"`.
- Python initializes lazily inside `LrrResolverService` in `:resolver`. The main process never initializes Python.
- If the resolver process crashes or is killed by the OS, `AndroidProcessMediaResolver` detects Binder death via `IBinder.DeathRecipient`, maps in-flight calls to `ResolverErrorCode.RuntimeCrashed`, and seamlessly rebinds on the next call.
- Media3 playback, DSP, and UI in the main process continue uninterrupted.

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

## 4. Structured Error Taxonomy

LRR replaces generic strings (such as "Source Error") with a rich, typed error taxonomy:

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

Debug builds preserve `debugDetails` for diagnostics, while release UI displays only sanitized, safe user strings with zero token/cookie leakage.

---

## 5. Signed URLs & Fresh Resolution Invariant

Media CDN URLs frequently include expiration timestamps (e.g. `expire=...`, `exp=...`) and IP/token bindings.

**Canonical Rule:**
- Search and inspection store only the stable canonical source identity (`sourceUrl`, `sourceId`, `sourceType`).
- Signed CDN URLs are **never** persisted in Room or reused across sessions.
- When an authorized download begins, the resource is resolved fresh immediately prior to streaming or ingestion.

---

## 6. Runtime Stack & Pinned Dependencies

| Component | Pinned Version | Scope |
| :--- | :--- | :--- |
| Chaquopy Plugin | `17.0.0` | Build plugin applied only in `:app` |
| Python Runtime | `3.14.7` (CPython 3.14) | Embedded in `:resolver` process |
| yt-dlp Python Library | `2026.8.19` | Pinned via Chaquopy `pip` block |
| ABI Architectures | `arm64-v8a`, `x86_64` | 16KB-page-size aligned |

---

## 7. Phase 3 Gate: QuickJS-NG & EJS Technical Recommendation

### Context:
Modern web providers (e.g. YouTube n-sig / signature deciphering) require executing JavaScript extraction helpers (`yt-dlp-ejs` and a runtime JavaScript interpreter).

### Technical Recommendation for Phase 3:
1. **Engine Selection:** **QuickJS-NG** (Next Generation) built with Android NDK r28+ targeting `arm64-v8a` and `x86_64` with 16KB page alignment (`-Wl,-z,max-page-size=16384`).
2. **Avoid Heavy Runtimes:** Do NOT bundle Node.js, Deno, or Bun. QuickJS-NG's minimal memory footprint (< 3 MB RSS) fits Android mobile constraints.
3. **Paired Bundling:** `yt-dlp` and `yt-dlp-ejs` must be versioned and tested as an atomic `ResolverRuntimeBundle` pair.
4. **Health Reporting:** Success criteria for Phase 3 is runtime confirmation (`JS runtime detected: quickjs`). Unsupported environments must report `JsRuntimeUnavailable` rather than failing silently.

---

## 8. Licensing & Governance

- Lyrune's LRR implementation is pure clean-room Kotlin and Python code licensed under MIT.
- No GPL-licensed wrapper code (e.g. `youtubedl-android`, Seal) is copied into Lyrune.
- `yt-dlp` is licensed under the Unlicense / CC0-1.0 and is invoked via its standard Python API.
- CPython is licensed under the Python Software Foundation License (PSFL).
