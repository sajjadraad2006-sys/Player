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

No frequency-domain approximation is labeled as stem separation. The optional production engine uses the checksum-pinned `UVR-MDX-NET-Voc_FT.fp16acc.tflite` model and LiteRT CPU/XNNPACK. It operates at 44.1 kHz with `dim_t=256`, `hop=1024`, `dim_f=3072`, `n_fft=6144`, and a float32 `[1,4,3072,256]` complex-as-channel tensor ordered `[L_re,L_im,R_re,R_im]`.

The engine decodes/resamples off the Media3 audio thread, applies the upstream periodic-Hann centered STFT, runs real model inference, reconstructs with iSTFT and weighted overlap-add, and writes vocal and complementary instrumental WAVs. Only completed caches are exposed. Playing either generated stem sends it through the ordinary Media3 path:

`cached generated stem → Media3 → DSP → post-DSP PCM tap → visualizer → AudioTrack`

The model is never silently downloaded. Bass/drums/other remain unsupported because Voc_FT produces only vocals plus its instrumental residual.
