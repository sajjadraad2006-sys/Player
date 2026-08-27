# LRR Resource Contract & Ephemeral Safety Specification

## 1. Architectural Principle

```text
LRR resolves; Lyrune owns downloading, storage, library state, and playback.
```

LRR operates strictly with `download=False` at all times. It is a metadata and format extraction engine running in an isolated Android process (`:resolver`). It does NOT own WorkManager, Room, MediaStore, or player storage.

---

## 2. Stable Metadata vs Ephemeral Delivery Resources

| Dimension | `ResolvedMedia` & `ResolvedFormatDescriptor` | `EphemeralResourceLease` |
| :--- | :--- | :--- |
| **Scope** | Persistent metadata / format catalog | Immediate execution lease (playback or download) |
| **Lifetime** | Bounded in memory or cached by canonical ID | Short-lived (minutes/hours); never persisted |
| **URLs** | Canonical webpage URLs only; NO signed/stream URLs | Fresh signed CDN / direct HTTP URL |
| **Headers** | Whitelist descriptor (`requiredSafeHeaderKeys`) | Safe runtime HTTP headers (`User-Agent`, `Referer`, etc.) |
| **Persistence** | Compatible with in-memory catalogs | **STRICTLY PROHIBITED** from Room, DataStore, or disk |
| **Expiry** | N/A | Evaluated via `isExpired` / `knownExpiryEpochMs` |

---

## 3. Format Delivery Classification Matrix

An existing HTTP/HTTPS URL does **NOT** imply `DIRECT_RESOURCE`. Delivery modes are classified strictly via `FormatDeliveryClassifier`:

```text
                                  ┌──────────────────────────┐
                                  │  Is format DRM-protected?│──YES──► ENGINE_REQUIRED
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │  Has POST / request_data?│──YES──► ENGINE_REQUIRED
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Requires browser target? │──YES──► ENGINE_REQUIRED
                                  │   (e.g. impersonation)   │
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Requires Cookie / Auth?  │──YES──► ENGINE_REQUIRED
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Is segmented? (HLS/DASH/ │──YES──► SEGMENTED_RESOURCE
                                  │  manifests/fragments/MSS)│
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Plain HTTP/HTTPS stream? │──YES──► DIRECT_RESOURCE
                                  └─────────────┬────────────┘
                                                │ NO
                                                ▼
                                         ENGINE_REQUIRED
```

---

## 4. Header Security & Secret Sanitization

### Safe Headers Whitelist
Only non-sensitive headers required for CDN stream routing are forwarded ephemerally:
* `User-Agent`
* `Referer`
* `Origin`
* `Accept`
* `Accept-Language`
* `Range`

### Sensitive Headers Blacklist
The following headers are treated as sensitive and are **NEVER** forwarded to direct HTTP downloaders or logged:
* `Cookie` / `Set-Cookie`
* `Authorization` / `Proxy-Authorization`
* `X-Api-Key` / `X-Auth-Token`
* Any custom header containing `token`, `secret`, or `password`

### URL Redaction
URLs containing query parameters are sanitized in logs and diagnostics via `SecretSanitizer.sanitizeUrl(url)`:
```text
https://cdn.example.com/audio/stream.mp3?token=SECRET123&expire=1780000000
    ==> https://cdn.example.com/audio/stream.mp3?<redacted>
```

---

## 5. Fresh Re-Resolution Contract (Phase 5 Boundary)

Persistent download workers must **never** persist temporary CDN URLs. Instead, the contract requires fresh re-resolution at execution time:

```text
[User enqueues download] ──► Persist ResolverDownloadDescriptor(canonicalUrl) to WorkManager
                                      │
                                      ▼ (Worker starts later)
                             MediaResolver.resolve(ResolverRequest(intent=DOWNLOAD))
                                      │
                                      ▼
                             Fresh EphemeralResourceLease(freshUrl, safeHeaders)
                                      │
                                      ▼
                             Lyrune standard OkHttp / File Downloader
```

---

## 6. SSRF & Scheme Hardening

LRR validates all input URLs before dispatching extraction:
* **Allowed Schemes**: `http://`, `https://`
* **Forbidden Schemes**: `file:`, `content:`, `javascript:`, `data:`, `ftp:`, `about:`, `intent:`
* **Loopback & Private Network Block**: Rejects `localhost`, `127.0.0.1`, `::1`, `0.0.0.0`, `10.0.0.0/8`, `192.168.0.0/16`, `172.16.0.0/12`, `169.254.0.0/16`.

---

## 7. Bounded IPC & Binder Constraints

1. **Preflight Payload Ceiling**: Strict `512 KB` limit on serialized IPC responses; normal responses are capped under `30 KB` by bounding descriptors to the top 20 ranked formats.
2. **Cancellation Truth**: Client cancellation terminates AIDL coroutines, removes pending request bookkeeping, and discards any late-arriving results.
3. **Timeout Recovery**: 30-second default operation timeout cleanly unbinds and fails the individual request while preserving resolver process health for subsequent calls.

---

## 8. 16KB Page-Size Alignment Status

* **Build Compatibility**: QuickJS `libqjs.so` binaries for `arm64-v8a` and `x86_64` are built with NDK r28b and ELF `Align 0x4000` (16KB alignment verified via `readelf -l`).
* **Runtime Verification**: Runtime execution is verified on Android API 35 (`auralis_api35` with 4KB kernel page size). Physical 16KB Android kernel runtime execution remains to be tested on a 16KB-kernel device.
