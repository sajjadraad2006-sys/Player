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
    val downloadCapability: DownloadCapability = DownloadCapability.UNKNOWN,
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

### Authorization Invariant
The persisted descriptor defaults to `DownloadCapability.UNKNOWN` and NEVER manufactures download permission. Authorization is determined strictly at worker execution time via fresh resolution and `DownloadCapabilityEvaluator.annotateMedia(media)`.

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
2. **Conservative Identity Verification**:
   - Matching `sourceId` $\rightarrow$ pass.
   - Differing `sourceId` $\rightarrow$ fail with `ProviderChanged`.
   - Missing / null `sourceId` on either or both $\rightarrow$ compares normalized `artist`, core `title`, and `VersionQualifier` extracted profiles via `IdentityNormalization`. Insufficient or mismatched evidence halts with `ProviderChanged`.
3. **Capability & Permission Gate**: `DownloadCapabilityEvaluator.annotateMedia` validates current legal permission (`DownloadCapability.PERMITTED`). Technical resolution never bypasses permission checks.
4. **Format Invariant**: Requires `ResolutionMode.DIRECT_RESOURCE`, `DrmState.NONE`, `DeliveryBlockReason.NONE`, and `!hasDrm`. Formats requiring segmented streaming or engine handling are rejected with typed errors.

---

## 5. Ephemeral Headers, Range Ownership & Redirect SSRF Safety

1. **Whitelisted Safe Headers Only**: The direct downloader receives only non-sensitive routing headers (`User-Agent`, `Referer`, `Origin`, `Accept`, `Accept-Language`).
2. **Sensitive Headers Excluded**: `Cookie`, `Authorization`, and tokens are never forwarded to the downloader.
3. **Range Header Ownership**: The `Range` header is controlled entirely by OkHttp / download engine. Extractor-supplied Range headers are discarded.
4. **Redirect SSRF Validation**: `SafeRedirectInterceptor` and `SafeDownloadUrlValidator` inspect all HTTP redirect destinations up to max 5 hops, strictly rejecting private IPv4 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`, `169.254.0.0/16`), IPv6 loopback (`::1`), link-local (`fe80::/10`), ULA (`fc00::/7`), and non-HTTP schemes (`file://`, `content://`, etc.).

---

## 6. Conservative Expired Resource Recovery

1. **No Generic 401/403 Loop**: Generic HTTP 401/403 responses are NOT classified as expired leases. Generic 401 halts with `AuthenticationRequired`, and generic 403 halts with standard download error.
2. **Structured Evidence Required**: `ExpiredResourceClassifier.shouldRefreshExpiredLease` permits a single fresh re-resolution retry ONLY when:
   - `lease.isExpired` is true or `knownExpiryEpochMs` has elapsed, or
   - Explicit CDN expiration text is returned (e.g. `request has expired`, `url expired`, `token expired`).
3. **Loop Prevention**: Bounded at max **1** automatic re-resolution retry per Worker execution.

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

## 10. Platform & Android 16 Review

* **Android 15 Runtime (API 35)**: VERIFIED on real emulator (`auralis_api35`, x86_64, PAGESIZE=4096).
* **Android 16 WorkManager / JobScheduler Implications**: ARCHITECTURALLY REVIEWED. Standard WorkManager background workers operate within bounded execution windows; future user-initiated data transfer jobs may be leveraged if foreground transfer quota is needed on API 36+.
* **Android 16 Runtime Behavior**: NOT VERIFIED (API 36 execution environment not configured).
