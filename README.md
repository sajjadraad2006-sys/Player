# Auralis Studio

Auralis Studio is an offline-first Android music player and real-time audio studio. The checked-in build targets Android 15 (`targetSdk 35`), supports Android 8+ (`minSdk 26`), and does not request Internet access.

## Implemented application surface

- Real MediaStore scanning with `READ_MEDIA_AUDIO`, duration filters, and efficient cached `StateFlow` state.
- Persisted Storage Access Framework folders with recursive audio discovery.
- Media3 `MediaLibraryService`, `MediaLibrarySession`, ExoPlayer, system media notification, audio focus, noisy-output pause, media buttons, queue restoration, and Android Auto browse/search hierarchy.
- Independent pitch and speed, pitch-preserving tempo mode, and mathematically linked vinyl mode.
- Live PCM DSP: preamp, 10-band EQ, bass, treble, balance, stereo width/channel routing, smoothed equal-power 8D orbit, reverb, and safety limiter.
- DSP before/after bypass and Pure Playback reset.
- Room-backed favorites, history, playlists, playlist membership, presets schema, and bookmarks schema.
- Home, Library, local search, Audio Lab, Settings, full Now Playing, queue, mini-player, A–B repeat, and sleep timer.
- Glass, AMOLED, Minimal, Material, Neon, Dynamic Art, and Classic appearance styles with customization.
- English and Arabic resources with RTL support.
- Original adaptive/monochrome icon, Android 12+ splash configuration, edge-to-edge UI, and accessibility labels on primary controls.

## Build

Use a full JDK 17 or newer and Android SDK Platform 35:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Honest scope boundaries

Formant preservation is not exposed because this build does not contain a validated formant backend. Parametric EQ, lyrics/provider integration, waveform/BPM/key analysis, online providers/downloads, processed export, tag writing, widgets, Cast, crossfade, ReplayGain, backup-file import/export, and stem separation are not implemented in version 0.1. The UI does not claim these are active. The current architecture keeps playback, persistence, provider-facing data, and DSP separated so those features can be added without replacing the player core.

Local files are never modified or deleted by this version.
