# External media resolver engineering decision

Date: 2026-08-25

## Decision

Lyrune now has a small `ExternalMediaResolver` contract for a future, explicit **Import from link** flow. It is separate from catalog search, returns inspection metadata and permission-aware capabilities, and has no implementation enabled today. Audius, Jamendo, iTunes, Deezer, local search, and the verified WorkManager download pipeline do not depend on it.

Do not add a generic YouTube download action. YouTube may later be a supported Watch/discovery source, while export must remain limited to sources and media for which the user has permission and the source explicitly allows it. DRM bypass and hidden protected-media extraction are outside this architecture.

## Android yt-dlp options reviewed

### `yausername/youtubedl-android`

- Current documented coordinate is `io.github.junkfood02.youtubedl-android` 0.18.1. The project wraps yt-dlp as an Android subprocess and offers separate library, FFmpeg, and aria2c artifacts.
- Its README lists `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, recommends ABI splits, requires extracted native libraries, and documents Android scoped-storage constraints.
- Releases updated the bundled runtime to CPython 3.11.10 in 0.17.1, added 16 KiB page-size support in 0.18.0, and added QuickJS in 0.18.1. The README still contains an older Python 3.8 statement, so runtime details must be verified from the exact release artifact during any spike.
- yt-dlp can be updated in-app on stable/nightly channels. That improves extractor freshness but creates a supply-chain and compatibility boundary that needs signature/hash verification, rollback, and controlled storage.
- FFmpeg is optional at dependency level but required for merges, conversion, metadata embedding, and many audio extraction workflows. aria2c is separately optional.

Primary sources: [wrapper README](https://github.com/yausername/youtubedl-android), [wrapper releases](https://github.com/yausername/youtubedl-android/releases).

### Seal

- Seal is a mature Compose application built on `youtubedl-android`, with Room-backed job management, per-ABI APK outputs, FFmpeg/aria2c integration, command templates, and metadata/thumbnail post-processing.
- Its architecture is useful as an operational reference, not as a library boundary for Lyrune. It demonstrates that extractor updates, native executable discovery, storage, cancellation, and process output handling are a subsystem rather than a simple HTTP provider.
- A representative Seal 1.13.1 arm64 APK is about 43 MiB. This is not an isolated dependency delta and must not be presented as the exact cost to Lyrune, but it establishes that the combined Python/yt-dlp/FFmpeg stack is material compared with a normal catalog client.
- Open issues show continuing fragility around site changes, browser impersonation/curl-cffi, and Android executable/runtime constraints. These failures must never affect normal music search.

Primary sources: [Seal repository](https://github.com/JunkFood02/Seal), [Seal Gradle ABI setup](https://github.com/JunkFood02/Seal/blob/main/app/build.gradle.kts), [F-Droid package](https://f-droid.org/packages/com.junkfood.seal/).

### Maintained alternative wrapper

`ffmpegkit-maintained/yt-dlp-android` 2.x replaces the older wrapper dependency with embedded Chaquopy/Python 3.13 and currently targets arm64-v8a and x86_64. Its documented trade-off is no in-app yt-dlp update; updates arrive through library releases. This is a cleaner reproducibility story but narrows ABI coverage and still carries Python plus optional media tooling.

Primary source: [yt-dlp-android README](https://github.com/ffmpegkit-maintained/yt-dlp-android/blob/main/README.md).

## Cost and reliability assessment

- **APK/install size:** substantial. CPython, standard-library assets, native libraries, yt-dlp code, and optionally FFmpeg are materially larger than Lyrune's Ktor providers. Exact incremental compressed/install sizes must be measured with a dedicated release-build spike and Android App Bundle analyzer; the current pass did not add dependencies, so no fabricated delta is reported.
- **Memory/startup:** Python initialization and a subprocess/runtime working set are non-trivial and bursty. FFmpeg adds a separate native process/load and buffers. Measure peak RSS, cold initialization, cancellation cleanup, and low-memory behavior on representative arm64 devices before adoption.
- **ABI:** the established wrapper covers four ABIs and relies on splits; the newer Chaquopy alternative documents arm64-v8a/x86_64. Lyrune must not silently drop an ABI merely to add link import.
- **Updates:** extractor freshness is essential. Runtime self-update is responsive but needs cryptographic verification and rollback; release-pinned updates are reproducible but slower. A future optional module should pin a known-good built-in version and permit only verified signed updates with rollback.
- **Reliability:** individual websites change without notice, geo/login challenges vary, and FFmpeg/runtime errors are independent failure domains. Resolver jobs require strict timeouts, cancellation, sanitized errors, and their own optional worker boundary.
- **Licensing:** `youtubedl-android` and Seal are GPL-3.0. Distribution and source obligations require legal review before including or adapting them in this MIT-licensed application.

## Recommendation

Do not integrate yt-dlp in Lyrune's core APK or provider search. If link import becomes a confirmed product requirement, prototype it as an optional, independently installable module behind `ExternalMediaResolver`, initially for explicitly permitted sources/content only. The spike must measure per-ABI APK/AAB delta, installed size, cold/warm RSS, initialization latency, cancellation, update verification, and FFmpeg-free versus FFmpeg-enabled behavior. Normal search and playback must remain fully functional when the module is absent or broken.

No yt-dlp, CPython, QuickJS, aria2c, or FFmpeg dependency was added in this pass.
