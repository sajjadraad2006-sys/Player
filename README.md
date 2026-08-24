<div align="center">

<img src="docs/assets/lyrune-logo.png" alt="Lyrune" width="420" />

# Lyrune

**A modern Android music player and audio laboratory built around real-time DSP, adaptive visual design, synchronized lyrics, and truthful audio visualization.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![CI](https://github.com/sajjadraad2006-sys/Player/actions/workflows/android.yml/badge.svg)](https://github.com/sajjadraad2006-sys/Player/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

## What Lyrune does

- Plays an on-device music library through AndroidX Media3 and a media-session foreground service.
- Applies pitch, tempo, EQ, tone, stereo, 8D orbit, reverb, and limiting to actual decoded PCM.
- Renders a 24/48/64-band spectrum from a real post-DSP PCM tap—never from random or decorative data.
- Resolves embedded, sidecar, cached, and LRCLIB lyrics and follows genuine LRC timestamps.
- Offers independent System, Light, Dark, and true-black AMOLED appearance modes.
- Provides Glass, Minimal, Material, Neon, Dynamic Art, and Classic visual languages through shared design tokens.
- Supports in-app System Default, English, and Arabic locale selection with RTL resources.
- Searches and downloads optional online music sources without making network access a playback requirement.

## Audio Lab

The processing chain is:

```text
Media source → decoder → Media3 Sonic pitch/tempo → PCM studio processor
→ EQ/tone → stereo/8D → reverb → limiter → post-DSP PCM tap → AudioTrack
```

DSP bypass immediately restores Media3 default playback parameters and copies PCM without studio effects; it does not restart the track. The 8D modes use smoothed equal-power panning and crossfeed, including genuinely reversed clockwise/counter-clockwise motion and triangular ping-pong motion.

See [Audio engine notes](docs/AUDIO_ENGINE.md) for implementation details.

## Visualizer and synchronized lyrics

The visualizer analyzer runs away from the UI thread, reads the processor’s real sample rate, applies a Hann-windowed 2048-point FFT, maps logarithmic frequency bands, and emits real targets at a quality-dependent analysis rate. Compose interpolates those targets with elapsed frame time, so display refresh rate does not create or change spectrum energy.

Synced lyrics use a binary timestamp lookup with a separately stored per-track timing offset. The current line auto-follows playback; manual scrolling suspends follow mode, and tapping a timed line seeks to its real timestamp. Plain lyrics remain plain and missing lyrics are never generated.

## Themes

- **Glass** — translucent layered surfaces, artwork bleed, light borders, soft highlights, and floating navigation.
- **Minimal** — borderless cards, flat hierarchy, square artwork, restrained decoration, and quiet navigation.
- **Material** — opaque tonal containers, Material elevation, familiar shapes, and native selection states.
- **Neon** — dark surfaces with selective luminous edges and stronger active-control/visualizer emphasis.
- **Dynamic Art** — a cached palette extracted from current album artwork drives ambient surfaces and accents.
- **Classic** — compact outlined cards, traditional artwork corners, conventional separation, and restrained motion.

Appearance luminance and visual style are independent. AMOLED is an appearance mode, not a style override.

## Stem separation status

Lyrune does **not** ship or download an unverified neural model. The old center-channel and frequency-filter approximation was removed because it was not real source separation.

The codebase now defines a model/version/capability-aware separation-engine contract, checksum-verified optional-model manager, versioned cache identity, cancellation boundary, and clipping-safe mixer math. Actual vocal/instrumental/bass controls remain unavailable until a model with verified provenance, license, Android operators, memory use, and device benchmarks is selected. No filtering is marketed as AI separation.

## Architecture

```text
app/src/main/java/dev/auralis/studio/
├── audio/dsp/          # realtime PCM effects and limiter
├── audio/visualizer/   # lock-free PCM tap, FFT, temporal smoothing
├── audio/stems/        # pluggable offline separation/model contracts
├── core/               # models, Room, DataStore, repositories
├── lyrics/             # embedded/sidecar/cache/LRCLIB resolution
├── playback/           # Media3 connection, service and session
├── designsystem/       # luminance, semantic colors and style tokens
└── ui/                 # Compose screens, player, lyrics and visualizer
```

The legacy `dev.auralis.studio` namespace, application ID, database names, DataStore names, and compatibility keys are intentionally retained so the public rebrand does not break installed data.

## Technology

- Kotlin 2.0.21, coroutines and StateFlow
- Jetpack Compose and Material 3
- AndroidX Media3 1.5.1 / ExoPlayer
- Room 2.6.1 and DataStore 1.1.1
- Ktor 3.0.3 and Kotlin serialization
- Coil 2.7.0 and AndroidX Palette
- JDK 17 and Gradle wrapper

## Build

Requirements: JDK 17 and Android SDK Platform 36 for the current checked-in configuration.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Debug output:

```text
app/build/outputs/apk/debug/Lyrune-debug.apk
```

CI runs unit tests, Android lint, and the debug build without signing credentials.

## Status and roadmap

Working today: local library, queues, playlists, favorites, MediaSession controls, PCM DSP, real FFT visualization, offline-first lyrics lookup, synced lyric following, themes, and English/Arabic app locales.

Roadmap:

- benchmark a legally redistributable mobile source-separation model on representative Android hardware;
- add model-backed cached stem playback only after synchronization and thermal tests;
- expand device/emulator UI regression coverage, especially RTL and OEM adaptive-icon masks;
- publish committed product screenshots after device capture.

## Known limitations

- No neural stem inference model is bundled or downloadable in this release.
- Neural inference speed, GPU/NPU delegation, blur performance, and headphone perception require physical-device measurement.
- Online search availability depends on third-party providers and their terms.

## License

Lyrune is distributed under the [MIT License](LICENSE).
