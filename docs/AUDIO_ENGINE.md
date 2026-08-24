# Audio engine

## Playback chain

The verified local PCM chain is:

`Media source → decoder → Media3 Sonic tempo/pitch → preamp → 10-band biquad EQ/tone → channel/stereo matrix → smoothed 8D orbit/crossfeed → feedback reverb → soft safety limiter → AudioTrack`

Float output and audio offload are disabled while the studio chain is in use because Media3 cannot apply custom PCM processors to passthrough/offloaded audio. The decoder output is normalized to signed 16-bit PCM for a consistent device-independent processing path.

## Pitch and tempo

- Independent mode sends the selected speed and `2^(semitones/12)` pitch factor to Sonic.
- Tempo Lock sends the selected speed and a pitch factor of `1.0`.
- Vinyl mode sends the same factor for speed and pitch. Its displayed semitone change is `12 × log2(speed)`.
- DSP bypass sends `PlaybackParameters.DEFAULT` and copies PCM unchanged through the custom processor.

## Gain safety

User preamp is capped at +6 dB, each EQ/tone control is bounded, intermediate reverb feedback is bounded, and the final limiter has a ceiling below full scale. This is protection against hard clipping, not a loudness-compliance or true-peak mastering processor.

## Real-time behavior

Audio parameters are sent to the service through an app-only custom MediaSession command. DSP parameters use an atomic snapshot, pan/reverb values are smoothed sample-by-sample, and no song restart or seek is performed. Playback-parameter changes may cause Media3 to drain a short processor buffer, while the media position remains intact.

## Visualizer tap

The processor writes post-DSP stereo-to-mono samples (or unchanged samples while bypassed) to a lock-free ring buffer at the decoder's real sample rate. The analyzer reads that tap off the audio thread, applies a Hann-windowed 2048-point FFT and log-spaced dBFS bands, then exposes targets through `StateFlow` at 25/45/60 frames per second according to quality. Analyzer attack/release coefficients and the Compose display interpolator both use elapsed time, so 60/90/120 Hz displays converge consistently. Neither stage creates spectrum energy; silence converges to zero.

## Stem separation

No frequency-domain approximation is labeled as stem separation. This build has no bundled neural model, so stem controls remain unavailable and the processor leaves the original mix untouched.

`audio/stems` defines the offline boundary for a future implementation: engine/model/version capabilities, model availability, checksum-verified optional installation, cancellation, versioned cache identity, and clipping-safe gain math. A genuine engine must decode and chunk a track in a worker, run inference away from the Media3 audio thread, reconstruct complete synchronized files, atomically mark the cache complete, and only then expose stems for synchronous playback. The intended order after that work is:

`decoded cached stems → synchronized stem mixer → DSP → post-DSP PCM tap → visualizer → AudioTrack`
