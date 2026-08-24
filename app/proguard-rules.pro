-keep class androidx.media3.session.** { *; }
-keep class * extends androidx.room.RoomDatabase

# JLargeArrays exposes an optional off-heap path for arrays above JVM limits. Lyrune's MDX DSP
# uses JTransforms' ordinary FloatArray API (6144-point FFT), so this desktop-only cleaner is
# unreachable on Android while R8 still sees the signature.
-dontwarn sun.misc.Cleaner
