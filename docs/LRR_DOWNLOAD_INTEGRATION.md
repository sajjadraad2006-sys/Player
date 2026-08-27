# LRR Download Integration Specification (Phase 5)

## 1. Architectural Invariant

```text
Persist stable intent.
Resolve fresh at worker execution.
Download only PERMITTED + DIRECT_RESOURCE.
Never persist the ephemeral lease.
```

Lyrune’s existing `DownloadWorker`, `DownloadIngestionManager`, Room database, and MediaStore pipeline remain the sole download and library owners. LRR acts exclusively as a metadata inspection and format resolution engine (`download=False`).

---

## 2. Stable Download Descriptor & Schema Versioning

Persistent download jobs are enqueued into WorkManager using `ResolverDownloadDescriptor`:

```kotlin
@Serializable
data class ResolverDownloadDescriptor(
    val schemaVersion: Int = 1,
    val requestId: String = UUID.randomUUID().toString(),
    val source: String,
    val sourceId: String? = null,
    val canonicalUrl: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val expectedDurationMs: Long? = null,
    val qualityPreference: AudioQualityPreference = AudioQualityPreference.BEST,
    val formatHint: String? = null,
    val recordingIdentity: String = deriveRecordingIdentity(artist, title),
    val downloadCapability: DownloadCapability = DownloadCapability.PERMITTED,
    val downloadCapabilityBasis: DownloadCapabilityBasis = DownloadCapabilityBasis.NONE,
)
```

### Prohibited Fields
The persistent descriptor MUST NOT contain:
* Signed temporary CDN / media URLs
* Stream or playback URLs
* `EphemeralResourceLease` objects
* `Cookie`, `Authorization`, or sensitive headers
* EJS solver tokens or solver state
* Manifest content or fragment lists

---

## 3. WorkManager Data Boundary

* **Payload Ceiling**: Android WorkManager enforces a hard ~10 KB limit on `androidx.work.Data`.
* **Payload Size**: The serialized `ResolverDownloadDescriptor` payload is strictly bounded under **2 KB** (typical size: ~350 bytes).
* **Lease-Free Boundary**: WorkManager `Data` contains only the serialized `ResolverDownloadDescriptor` under `KEY_RESOLVER_DESCRIPTOR_JSON`.

---

## 4. Fresh Resolution Lifecycle at Worker Execution Time

When `DownloadWorker.doWork()` starts:

```text
                  ┌──────────────────────────────┐
                  │  DownloadWorker.doWork()     │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Decode Descriptor (schema=1) │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ MediaResolver.resolve()      │
                  │ (intent = DOWNLOAD)          │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Re-validate Identity & Perms │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Select Format & Obtain Lease │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ IngestionManager.download()  │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Sniff Container & Duration   │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ MediaStore & Room Finalize   │
                  └──────────────────────────────┘
```

1. **Re-resolution Mandatory**: A fresh delivery URL is resolved at execution time. No historical URLs from search or previous sessions are reused.
2. **Identity Verification**: If `descriptor.sourceId != freshMedia.sourceId`, execution halts immediately with `ResolverErrorCode.ProviderChanged`.
3. **Capability & Permission Gate**: `DownloadCapabilityEvaluator.annotateMedia` validates current legal permission (`DownloadCapability.PERMITTED`). Technical resolution never bypasses permission checks.
4. **Format Invariant**: Requires `ResolutionMode.DIRECT_RESOURCE`, `DrmState.NONE`, `DeliveryBlockReason.NONE`, and `!hasDrm`. Formats requiring segmented streaming or engine handling are rejected with typed errors.

---

## 5. Ephemeral Headers & Range Ownership

1. **Whitelisted Safe Headers Only**: The direct downloader receives only non-sensitive routing headers (`User-Agent`, `Referer`, `Origin`, `Accept`, `Accept-Language`).
2. **Sensitive Headers Excluded**: `Cookie`, `Authorization`, and tokens are never forwarded to the downloader.
3. **Range Header Ownership**: The `Range` header is controlled entirely by OkHttp / download engine. Extractor-supplied Range headers are discarded.

---

## 6. One-Shot Expired Resource Recovery

If a direct HTTP transfer fails due to an expired CDN token (`HTTP 403`, `HTTP 401`, or `isExpired`):
1. The incomplete partial file is deleted immediately.
2. `DownloadWorker` calls `resolver.resolve(intent = DOWNLOAD)` for **exactly one** fresh re-resolution retry.
3. If the second attempt fails, the worker terminates with typed failure. No infinite re-resolution loops are permitted.

---

## 7. Partial File Cleanup & Validation

1. **No Incompatible Partial Resume**: Fresh re-resolutions may point to different CDN nodes or byte representations. Failed partial downloads are discarded before re-trying.
2. **Container Sniffing**: The genuine audio container (`mp3`, `m4a`, `ogg`, `flac`, `wav`) is sniffed from initial byte signatures (magic bytes) rather than relying blindly on file extensions.
3. **Duration Validation**: Full-track downloads are validated against the catalog duration ($\pm 10\%$, $2-15\text{ s}$). Truncated or 30-second preview clips are rejected with `PreviewDownloadRejectedException` and deleted.

---

## 8. Cancellation & Process Restart

1. **Process Restart**: When the app process is terminated and WorkManager awakens later, the worker reconstructs state solely from `ResolverDownloadDescriptor`, binds to `:resolver`, re-resolves, and downloads seamlessly.
2. **Worker Cancellation**: When cancelled by WorkManager, active resolver IPC jobs and HTTP transfer streams are cancelled immediately, temporary files are removed, and no success state is written to Room or MediaStore.

---

## 9. Official Provider Isolation

Official providers (`Audius`, `Jamendo`) continue to route directly through their dedicated catalog APIs and `OnlineTrack` models without invoking the Python `:resolver` process. Catalog/preview providers (`iTunes`, `Deezer`) continue to enforce preview protection.

---

## 10. Android 16 WorkManager Platform Note

On modern Android platforms (including API 35/36 and upcoming Android 16), standard WorkManager background workers operate within bounded execution windows. Long-running transfers remain compatible with standard `OneTimeWorkRequestBuilder` execution, with future platform-hardening pathways available for user-initiated data transfer jobs if execution quota limits require dedicated foreground service elevation.
