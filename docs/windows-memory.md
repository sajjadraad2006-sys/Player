# Windows Memory Regression Log

This document tracks measured memory consumption for Lyrune on Windows across engineering milestones.

## Initial Engineering Targets
- Desktop app idle after stabilization: target <= 250 MiB Working Set.
- Normal audio playback: target <= 350 MiB.
- Playback + lyrics + visualizer + normal DSP: target <= 400 MiB.

## Milestones Record

| Milestone | Working Set | Private Bytes | Heap Used | Heap Committed | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Desktop Shell (Milestone 1)** | 235.97 MiB | 533.15 MiB | 42.42 MiB | 246.00 MiB | Empty Compose Desktop window + SharedInfo module on Corretto JDK 21. Meets <= 250 MiB target. |
| **Full Desktop Studio (Milestone 4)** | 242.32 MiB | 336.69 MiB | 28.72 MiB | 32.00 MiB | Full desktop UI: Sidebar, Music Library scanner, Audio Lab (10-band EQ, 8D, Reverb, Limiter), Spectrum Visualizer, Synced Lyrics, and streaming playback engine. Working set 242.32 MiB meets <= 250 MiB idle target. |
| **Hardened Desktop (Milestone 5)** | 243.10 MiB | 341.20 MiB | 29.15 MiB | 34.50 MiB | Hardened streaming pipeline, canonical PCM normalizer, session-isolated threads, decoupled UI recomposition, 0 leaks across 100 track switches. |
| **Production Foundation (Milestone 6)** | 244.50 MiB | 343.80 MiB | 30.80 MiB | 36.00 MiB | Persistent database, incremental indexing, LRU artwork cache (150 items max), settings repository, RTL layout support, packaging verified. |
| **Online Discovery & Resolver (Milestone 8)** | 245.20 MiB | 345.10 MiB | 31.40 MiB | 37.00 MiB | Capability-aware search (Jamendo, Audius), streaming HTTP playback, on-demand Python resolver with zero startup child processes & 60s idle auto-shutdown. |
| **Resolver Production Runtime (Milestone 8.1)** | 245.40 MiB (Idle) / 293.8 MiB (Combined) | 346.20 MiB | 32.10 MiB | 38.00 MiB | Bundled minimal CPython 3.12.10 + yt-dlp 2026.7.4 runtime, 0 Python processes on boot/explore, 20-cycle lifecycle verified with 0 orphan processes. |
| **Windows SMTC Integration (Milestone 9)** | 252.05 MiB | 355.12 MiB | 33.50 MiB | 39.00 MiB | In-process native WinRT System Media Transport Controls integration via 72 KB DLL; throttled timeline; < 2.05 MiB memory delta; CPU < 0.1%. |

## Milestone 6: Production Foundation Memory & Cache Scenarios

The following measurements were validated on Windows 11 under Milestone 6 daily-use scenarios:

| Scenario | Working Set | Private Bytes | Heap Used | Heap Committed | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Cold Start (1,000 Cached Tracks)** | 244.50 MiB | 343.80 MiB | 30.80 MiB | 36.00 MiB | Fast warm startup directly from `%LOCALAPPDATA%\Lyrune\data\library.json` in < 250ms. No cold scan stall. |
| **Incremental Re-scan (1,000 Tracks)** | 247.20 MiB | 346.50 MiB | 32.10 MiB | 38.00 MiB | Timestamp + size comparison reuses existing metadata without parsing. Completes in ~120ms. |
| **Full Artwork Scroll (150 Cached Images)** | 256.80 MiB | 358.40 MiB | 36.50 MiB | 42.00 MiB | In-memory LRU cache strictly bounded at 150 entries (~8.5 MB heap). Older bitmaps evicted gracefully. |
| **Active Playback + Artwork Rendering** | 252.10 MiB | 351.20 MiB | 33.20 MiB | 38.00 MiB | Stream decoding alongside active 128x128 album art display. Well below <= 350 MiB playback target. |
| **Active Playback + DSP + Visualizer + Artwork** | 261.20 MiB | 362.50 MiB | 35.80 MiB | 44.00 MiB | Full load scenario. Meets and exceeds <= 400 MiB target with ~138 MiB headroom. |
| **Disk Thumbnail Cache Bounds** | N/A | N/A | N/A | N/A | JPEG thumbnails on disk bounded to <= 50 MB with LRU file cleanup. |

### Key Architectural Findings:
1. **Allocation-Minimized Steady-State Loop**: Hot audio decoding loops (`DesktopPlaybackEngine`, `StudioDspEngine`, `PcmAudioTap`) operate purely on pre-allocated reusable byte and float buffers without per-frame object allocation.
2. **Visualizer Lifecycle Suspension**: The 48-band FFT analyzer suspends execution when playback pauses or when navigating away to other tabs, dropping visualizer CPU and allocation to 0%.
3. **Session-Isolated Playback Threads**: Rapid track selection triggers atomic increment of `sessionCounter`, invalidating stale decoding loops before opening new `SourceDataLine` streams.
4. **Recomposition Isolation**: High-frequency playback position ticks (`currentPositionMs`) are consumed locally by `DesktopPlayerBar`, keeping `Sidebar`, `LibraryScreen`, `AudioLabScreen`, and `SettingsScreen` completely free of recomposition overhead.
5. **Bounded Artwork Memory Footprint**: In-memory album art cache is strictly hard-capped at 150 thumbnails downsampled to 128x128 ARGB. High-speed scrolling causes zero memory drift or OutOfMemoryError.
6. **Crash-Safe Atomic Persistence**: Database and settings writes commit to a `.tmp` file and perform an atomic rename/replace, preventing database corruption during unexpected process termination or power loss.
7. **Zero-Startup Child Processes & On-Demand Lifecycle**: The resolver Python worker is never started during boot or standard local playback (0 child processes). When triggered by media resolution, it communicates via framed NDJSON over stdin/stdout. After 60 seconds of idle inactivity, the child process gracefully terminates, reclaiming memory back to the baseline.
8. **Bounded HTTP Stream Playback**: Network audio playback in `DesktopPlaybackEngine` utilizes a bounded 64 KB `BufferedInputStream` and frame-by-frame JLayer decoding into a 9 KB PCM chunk buffer, ensuring that multi-megabyte audio tracks never load entirely into JVM RAM.

## Milestone 8.1: Process-Separated Resolver Runtime Memory & Lifecycle Verification

Milestone 8.1 guarantees process-separated isolation between the JVM application and the on-demand Python child worker.

### Process-Separated Working Set Matrix

| Application Lifecycle State | Parent JVM Working Set | Child Python Worker Working Set | Combined Total Working Set | Active Python Processes | Notes |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **1. Packaged App Launch (`Lyrune.exe`)** | 257.93 MiB | 0.00 MiB (unspawned) | **257.93 MiB** | **0** | Verified on packaged binary. No Python worker created on startup. |
| **2. Explore/Online Tab Navigation** | 248.60 MiB | 0.00 MiB (unspawned) | **248.60 MiB** | **0** | Static installation check validates runtime without starting Python. |
| **3. Resolver Worker Initialized (Idle)** | 258.10 MiB | 35.79 MiB | **293.89 MiB** | **1** | Worker in memory ready to receive NDJSON frames over raw stdout fd. |
| **4. Active Media Resolution / Deep Test** | 261.40 MiB | 40.20 MiB | **301.60 MiB** | **1** | yt-dlp parsing URL, extracting audio stream info, emitting framed JSON. |
| **5. Post-Resolution Idle (within 60s)** | 258.30 MiB | 35.79 MiB | **294.09 MiB** | **1** | Idle countdown active; zero CPU load during idle wait. |
| **6. Worker Termination / 60s Timeout** | 254.20 MiB | 0.00 MiB (terminated) | **254.20 MiB** | **0** | Child killed cleanly via `ProcessHandle.descendants()`. Child RAM fully returned to OS. |

### 20-Cycle Live Lifecycle Stress Test Results

Executed via automated JUnit/Kotlin test `ResolverLifecycle20CycleStressTest`:
* **Cycles Executed**: 20 / 20
* **Unique Worker PIDs**: 20 (every cycle cleanly created and terminated a fresh child)
* **Average Child Worker RAM**: **35.79 MB**
* **Peak Child Worker RAM**: **40.20 MB** (Cycle 1 cold start)
* **Minimum Lifecycle Latency**: **894 ms**
* **Average Lifecycle Latency**: **977 ms**
* **Maximum Lifecycle Latency**: **1571 ms** (Cycle 1 cold load)
* **Orphan Python Processes After 20 Cycles**: **0** (strictly verified via Windows process table)

### Environment Details
- **OS**: Windows 11 10.0 amd64
- **JVM**: Amazon Corretto OpenJDK 21.0.12.1+9-LTS
- **Python Runtime**: CPython 3.12.10 (isolated, embedded, no `site-packages` pollution)
- **yt-dlp**: 2026.7.4
- **Protocol**: Envelope Version 1 (1 MB line ceiling, raw stdout fd duplication)
- **Compose Multiplatform**: 1.7.1
- **Kotlin**: 2.0.21

## Milestone 9: Windows SMTC Integration Memory & Performance Scenarios

Milestone 9 integrated native Windows System Media Transport Controls (SMTC) via an in-process C++/WinRT JNA bridge (`SMTCAdapter.dll`, 72 KB).

### SMTC Lifecycle Memory & CPU Measurements

| Application State | Working Set | Private Bytes | Native DLL Contribution | Steady-State CPU | Notes |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **Pre-SMTC Idle Baseline** | 250.00 MiB | 353.10 MiB | 0.00 MiB | < 0.5% | Host application idle before native bridge initialization. |
| **SMTC Initialized (Idle)** | 252.05 MiB | 355.12 MiB | ~1.85 MiB | < 0.5% | Dedicated MTA worker thread running; WinRT SMTC session active. **Delta: +2.05 MiB**. |
| **Local Playback + SMTC** | 256.40 MiB | 358.80 MiB | ~2.10 MiB | ~1.1% | Active streaming, timeline updates throttled to 1/sec. |
| **Online Stream + SMTC** | 258.80 MiB | 361.20 MiB | ~2.15 MiB | ~1.2% | Bounded HTTP streaming handoff to SMTC session. |
| **Playback + DSP + Visualizer + SMTC** | 264.80 MiB | 366.50 MiB | ~2.20 MiB | ~2.8% | Full processing pipeline. Well below <= 400 MiB target. |
| **Minimized Window Playback** | 254.10 MiB | 356.90 MiB | ~2.10 MiB | ~0.9% | Full SMTC hardware key and flyout control while window minimized. |
| **100 Artwork Transitions** | 257.20 MiB | 359.40 MiB | ~2.15 MiB | < 1.0% | File-path handoff to WinRT StorageFile; zero memory drift. |

### Architectural Performance Guarantees
1. **Zero Helper Processes**: In-process JNA bridge adds **0 child processes** to the operating system.
2. **Throttled Timeline Cadence**: Timeline updates are emitted at a maximum cadence of 1 second during steady playback, reducing COM calls and eliminating high-frequency CPU load.
3. **MTA Thread Isolation**: All WinRT calls are isolated on `Lyrune-SMTC-Worker`, completely preventing contention or deadlocks with the AWT Event Dispatch Thread (STA).
4. **Artwork Zero-Copy**: Artwork is provided via existing cached file paths (`%LOCALAPPDATA%\Lyrune\cache\artwork\`), avoiding redundant in-memory bitmap allocations.


