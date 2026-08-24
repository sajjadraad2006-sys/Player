<div align="center">

# 🎧 Aura Studio — Modern Android Music Player & Audio DSP Studio

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-blue.svg?style=for-the-badge&logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_M3-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Audio-Media3_ExoPlayer-green.svg?style=for-the-badge)](https://developer.android.com/guide/topics/media/media3)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

**Aura Studio** is a state-of-the-art, high-performance Android music player and real-time audio engineering laboratory. Engineered with a pure Kotlin/Compose architecture, low-latency PCM DSP pipelines, real-time vocal & stem isolation, universal music discovery, high-contrast dynamic themes, and offline-first design.

---

[ English ] · [ [العربية](#-باللغة-العربية) ]

</div>

---

## ✨ Key Features

### 🎛️ 1. Studio-Grade Real-Time DSP Audio Engine
- **Independent Pitch & Tempo**: Shift pitch ($\pm 12$ semitones) and speed ($0.25\times - 3.0\times$) independently without acoustic distortion.
- **Vinyl Emulation**: Mathematically coupled pitch-speed scaling.
- **10-Band Graphic Equalizer**: Precise frequency shaping ($31\text{ Hz} - 16\text{ kHz}$) with dedicated Preamp, Bass, and Treble controls.
- **8D Spatial Orbit Audio**: Smoothed equal-power binaural circular orbit with configurable rate and strength.
- **Studio Reverb & Stereo Tools**: Room wet mix, stereo width expansion ($0\% - 200\%$), and channel balance.
- **Safe Dynamic Limiter**: Hardware-grade soft limiter preventing clipping and digital distortion.

### 🎤 2. Real-Time Audio Stem Separation & Vocal Isolation
- **🎤 Vocal Solo (Acapella)**: Extract human singing/recitation from any track in real time.
- **🎹 Karaoke / Instrumental**: Cancel center vocals to isolate backing music and melodies.
- **🎸 Bassline Solo**: Isolate sub-bass and bass instruments.
- **🎚️ Custom Multi-Stem Mixer**: Free 3-channel volume sliders (Vocals, Melody, Bass: $0\% - 200\%$).

### 🔍 3. Universal Online Music Search & Free Downloads
- **Open Discovery (SnapTube-Style)**: Search millions of global and Arabic songs, latmiyat, anasheed, adiyah, ziyarat, and recitations.
- **Instant Background Streaming**: Stream any track directly with full DSP sound effects applied.
- **One-Tap MP3 Downloads**: Download files directly to your device storage.
- **Customizable Download Directory**: Choose any custom folder (e.g. `Music`, `Downloads`, or external SD card) via Android SAF.

### 📊 4. Live Audio Spectrum Visualizer
- **32-Band Frequency Analyzer**: Real-time bouncing EQ bars with dynamic neon gradients and peak response.
- **Center Artwork Aura**: Pulsating ambient glow synchronized with playback tempo.

### 🎨 5. Modern High-Contrast Design System
- **Curated Themes**: Glassmorphism, AMOLED True Black, Minimal, Neon, and Dynamic Artwork.
- **Full Dark & Light Mode Support**: High-contrast, crystal-clear buttons, sliders, and segmented pills.
- **Full Arabic / RTL Support**: Complete native Arabic localization and mirror layouts.

---

## 🌍 باللغة العربية

**أورا ستوديو (Aura Studio)** هو تطبيق مشغل موسيقى واستوديو صوتي متقدم واحترافي للأندرويد:
- **عزل الصوت واللحن والبيس (Stem Isolator)**: عزل صوت المنشد/المغني (Acapella)، أو عزل اللحن والموسيقى (كاريوكي)، أو عزل صوت البيس Bass في الوقت الفعلي مع 3 سلايدرز تحكم حر.
- **استوديو المعالجة الصوتية (DSP Audio Lab)**: تحكم كامل في طبقة الصوت (Pitch)، تسريع وتبطيء الصوت، صوت ثلاثي الأبعاد 8D Audio، إيكوالايزر 10 نطاقات، صدى Reverb، وحماية من التشويش Safe Limiter.
- **محرك بحث وتحميل مجاني شامل**: ابحث عن أي أغنية، لطمية، نشيد، دعاء، أو تلاوة واستمع فوراً مع إمكانية التحميل المباشر بضغطة زر.
- **تحديد مسار ومجلد التنزيل**: اختيار أي مجلد تريده في ذاكرة الهاتف لحفظ الملفات الصوتية.
- **محلل طيف صوتي تفاعلي (Visualizer)**: 32 شريط ترددي متحرك ومتفاعل مع إيقاع الصوت.
- **واجهة عصرية وأنيقة**: دعم كامل للثيم الداكن والفاتح، زجاجية Glassmorphism، وتباين ألوان فائق الوضوح.

---

## 🏗️ Architecture & Technology Stack

```
Aura Studio
├── core/
│   ├── database/       # Room Database (Playlists, Favorites, History, Downloads)
│   └── model/          # Domain Models & AudioConfig DSP state
├── audio/dsp/          # Biquad Filters, Stem Separation, 8D Orbit, Safe Limiter
├── playback/           # Media3 / ExoPlayer MediaLibraryService, AudioFocus, Notifications
├── online/             # Free Universal Discovery Provider (iTunes + Deezer + Ktor)
├── designsystem/       # Material 3 Design Tokens, Dynamic Palettes, Glass UI
└── ui/                 # Jetpack Compose UI (Screens, Sheets, Visualizer, MiniPlayer)
```

- **Language**: Kotlin 2.1.0 (100% Coroutines & StateFlow)
- **UI Framework**: Jetpack Compose with Material 3
- **Audio Pipeline**: AndroidX Media3 1.5.0 + Custom Real-Time PCM AudioProcessors
- **Networking**: Ktor Client with OkHttp & Kotlinx Serialization
- **Image Loading**: Coil 3 with Compose integration
- **Storage**: Room 2.6.1 + Storage Access Framework (SAF)

---

## 🚀 Building & Running

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 35
- Gradle 8.11.1+

### Build Debug APK
```bash
./gradlew assembleDebug
```
The APK will be generated at:
```text
app/build/outputs/apk/debug/AuraStudio.apk
```

### Run Unit Tests
```bash
./gradlew test
```

---

## 📄 License & Copyright

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.

Developed with ❤️ by **[Sajjad Raad (sajjadraad2006-sys)](https://github.com/sajjadraad2006-sys)**.
