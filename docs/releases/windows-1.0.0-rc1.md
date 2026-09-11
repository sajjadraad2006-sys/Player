# Lyrune Windows Desktop — Release Notes (v1.0.0-rc1)

**Release Version**: 1.0.0-rc1  
**Build Date**: 2026-09-09  
**Target Platform**: Microsoft Windows 10 / 11 (x64)  
**Distribution Format**: Native Windows MSI Installer & Standalone Portable Distribution  

---

## Overview

Lyrune Windows Desktop v1.0.0-rc1 is the initial Release Candidate for the Lyrune local and online music player on Microsoft Windows. It combines a local audio library, real-time studio-grade DSP (10-band equalizer, 8D spatial sound, reverb, safe limiter), interactive 48-band FFT spectrum visualizer, synchronized lyrics, native Windows System Media Transport Controls (SMTC), and capability-aware online streaming with an on-demand, child-process media resolver.

---

## Key Features & Capabilities

### 1. Audio Playback & Formats
* **MP3**: Full MPEG-1 Audio Layer III CBR and VBR playback (128–320 kbps) via streaming bitstream decoding.
* **WAV**: Uncompressed linear PCM 16-bit stereo (44.1 kHz, 48.0 kHz) and mono upmixing.
* **FLAC**: First-class decoding for 16-bit and 24-bit FLAC files via official reference `libFLAC.dll` (v1.5.0) loaded securely via JNA.
* **HTTP/HTTPS Streaming**: Direct bounded streaming playback for online media without buffering complete tracks in RAM.

### 2. Audio Pipeline & Normalization Disclosure
* **Canonical 16-bit PCM Architecture**:
  * The internal `StudioDspEngine` and audio output line operate on signed 16-bit stereo PCM.
  * **24-bit FLAC Normalization**: High-bit-depth sources (24-bit/32-bit FLAC and WAV) are decoded losslessly by the codec and then arithmetically downshifted (`sample >> 8`) to preserve linear DC balance before entering the 16-bit DSP and audio output stage.
  * Native audio sample rates (44.1 kHz, 48.0 kHz, 88.2 kHz, 96.0 kHz, 192.0 kHz) are preserved throughout the pipeline.
  * **Transparency**: This release does NOT claim 24-bit end-to-end output. It provides lossless codec decoding normalized to Lyrune's canonical 16-bit studio DSP chain.

### 3. Studio DSP Engine & Visualizer
* **10-Band Graphic Equalizer**: Precise biquad peaking filters across 31 Hz to 16 kHz.
* **8D Spatial Audio**: Continuous smoothed sample-by-sample orbital panning and crossfeed.
* **Feedback Reverb**: Configurable room simulation with bounded decay.
* **Safe Peak Limiter**: Soft-knee limiter preventing hard clipping and digital distortion.
* **FFT Spectrum Analyzer**: 48-band log-spaced frequency visualizer operating post-DSP.

### 4. Windows System Media Transport Controls (SMTC)
* **Native In-Process WinRT Bridge**: Direct C++/WinRT integration (`SMTCAdapter.dll`) running in Multi-Threaded Apartment (MTA) mode with zero child processes.
* **Global Background Media Keys**: Full hardware and keyboard media key support (Play, Pause, Next, Previous, Stop) when Lyrune is minimized, unfocused, or in background.
* **Timeline & Metadata**: Synchronizes track title, artist, album, embedded artwork, and playback scrubber directly to the Windows 10/11 taskbar and lock screen flyout.

### 5. Online Discovery & On-Demand Resolver
* **Multi-Provider Search**: Real-time catalog search across Jamendo and Audius with Arabic text normalization (Alef folding, Tashkeel stripping).
* **Capability-Aware Playback**: Accurately classifies tracks into `FULL_STREAM`, `PREVIEW (30s)`, `DOWNLOADABLE`, and `UNAVAILABLE`.
* **Zero-Boot Python Architecture**: Launching Lyrune spawns zero Python processes. The bundled self-contained CPython 3.12 + yt-dlp worker is only invoked when external resolution is requested.
* **60-Second Idle Shutdown**: Child worker process automatically shuts down after 60 seconds of inactivity, returning RAM to baseline.
* **Process Tree Isolation**: JVM shutdown hooks guarantee clean termination of all child processes with zero process leaks.

### 6. Persistence & Storage Architecture
* **Factual Architecture: Pure JSON Store**:
  * Lyrune uses crash-resilient atomic JSON persistence (`ATOMIC_MOVE`) with automatic corrupted-file `.bak` recovery. It does not use SQLite.
  * Library database: `%LOCALAPPDATA%\Lyrune\library.json`
  * Playback session: `%LOCALAPPDATA%\Lyrune\session.json` (decoupled to eliminate SSD write amplification during continuous playback)
  * Settings & preferences: `%LOCALAPPDATA%\Lyrune\settings.json`

---

## Installation & Packaging Details

### Release Artifacts
* **MSI Installer**: `Lyrune-1.0.0.msi`
  * Size: **120,594,920 bytes** (115.01 MB)
  * SHA-256: `F4933F41D0425B951B38902CAFD2AABE99F257FE0B388C3BCAF7FBFDBBEC56A5`
* **Standalone Portable Distribution**: `Lyrune\`
  * Size: **193,561,408 bytes** (184.59 MB)
  * Includes bundled OpenJDK 21 stripped modular runtime (`runtime\`) — **No Java installation required**.

### Windows Version Compatibility
* **Windows 11 x64 (Build 10.0.26200)**: **VERIFIED** (physical MSI installation, launch, playback, SMTC, persistence, and uninstallation tested).
* **Windows 10 1809+**: **NOT VERIFIED** (runtime contracts target Win10 1809+ for SMTC/Universal CRT, but physical execution was performed on Windows 11 only; Windows 10 clean VM test is pending).

### Windows SmartScreen Notice
* This Release Candidate installer is **unsigned** (no commercial Authenticode certificate attached).
* When running `Lyrune-1.0.0.msi` for the first time, Windows Defender SmartScreen will display:
  *"Windows protected your PC — Microsoft Defender SmartScreen prevented an unrecognized app from starting."*
* To proceed: Click **"More info"**, then click **"Run anyway"**.
* Note on Mark-of-the-Web: When downloading via a web browser, SmartScreen reputation prompts appear; manual local file copies in a VM or test folder may omit MotW prompts, but the installer remains strictly unsigned.
* Microsoft Defender local scan completed with **0 threats detected**.

### Installation & Uninstallation Behavior
* **Install Location**: `%LOCALAPPDATA%\Programs\Lyrune\` (standard per-user installation directory).
* **Start Menu**: Installs a shortcut under `Start Menu -> Programs -> Lyrune -> Lyrune`.
* **Registry Registration**: `HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{A3BCB02B-0D2F-38AC-B472-557228099717}`.
* **Uninstall**: Uninstalling via Windows "Installed apps" cleanly removes all executable, runtime, and dependency files from `%LOCALAPPDATA%\Programs\Lyrune\` and removes Start Menu shortcuts.
* **User Data Retention**: User preferences, local library index (`library.json`), session state (`session.json`), and artwork caches in `%LOCALAPPDATA%\Lyrune\` are intentionally preserved upon uninstall so reinstalling does not lose library state. User audio files in `Music\` are NEVER modified or deleted.

---

## Known Limitations

1. **24-bit FLAC Normalization**: As disclosed above, 24-bit FLAC is normalized down to 16-bit PCM for the DSP pipeline. True 24-bit float/integer end-to-end DSP is planned for future major releases.
2. **Audio Device Hotplugging**: Disconnecting an active audio device (e.g. Bluetooth headset) during active playback safely stops audio output without crashing; resuming on a newly selected default device requires pressing Play.
3. **Formal File Associations**: Launching Lyrune via command line (`Lyrune.exe "song.flac"`) or Drag & Drop is fully supported; automatic Windows Shell "Open With" file associations require manual registration by the user in Windows Settings.
4. **Platform Support**: 64-bit Windows 10 (version 1809+) and Windows 11 only. 32-bit Windows and ARM64 Windows (without x64 emulation) are not natively targeted in this release.
5. **Android Ignored Test**: The shared Android test suite contains 1 pre-existing ignored test (`MdxReferenceFixtureTest`) requiring external proprietary model weights; all other 305 Android unit tests pass.

---

## Verification & Test Results

* **Desktop Unit & Integration Tests**: **94 executed, 0 failures, 0 skipped (100% pass)**
* **Android Regression Baseline Tests**: **306 total, 305 executed, 0 failures, 1 skipped (100% pass)**
* **Steady-State Memory Footprint**:
  * Idle: ~245–255 MiB
  * Local MP3/WAV/FLAC Playback: ~255–268 MiB
  * Streaming + DSP + FFT Visualizer: ~265–278 MiB
* **Resolver Lifecycle**: 0 Python child processes on boot; exactly 1 controlled child during active resolution; 0 child processes after 60s idle timeout.
