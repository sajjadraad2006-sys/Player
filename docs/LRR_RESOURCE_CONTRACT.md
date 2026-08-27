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
| **Expiry** | N/A | Evaluated via `isExpired` / `knownExpiryEpochMs` (wall-clock epoch) |

---

## 3. Format Delivery Classification Matrix & Block Reasons

An existing HTTP/HTTPS URL does **NOT** imply `DIRECT_RESOURCE`. Delivery modes and block reasons are classified strictly via `FormatDeliveryClassifier`:

```text
                                  ┌──────────────────────────┐
                                  │  Is format DRM-protected?│──YES──► ENGINE_REQUIRED (DRM_PROTECTED)
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Requires Cookie / Auth?  │──YES──► ENGINE_REQUIRED (SENSITIVE_AUTH_REQUIRED)
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │  Has POST / request_data?│──YES──► ENGINE_REQUIRED (UNSUPPORTED_POST_DATA)
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Requires browser target? │──YES──► ENGINE_REQUIRED (UNSUPPORTED_BROWSER_TARGET)
                                  │   (e.g. impersonation)   │
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Is segmented? (HLS/DASH/ │──YES──► SEGMENTED_RESOURCE (SEGMENTED_STREAM)
                                  │  manifests/fragments/MSS)│
                                  └─────────────┬────────────┘
                                                │ NO
                                  ┌─────────────▼────────────┐
                                  │ Plain HTTP/HTTPS stream? │──YES──► DIRECT_RESOURCE (NONE)
                                  └─────────────┬────────────┘
                                                │ NO
                                                ▼
                                         ENGINE_REQUIRED
```

### DRM Protection Invariant
Formats with `drmState != DrmState.NONE` or `blockReason == DeliveryBlockReason.DRM_PROTECTED` can **NEVER** be selected for direct download, regardless of bitrate or format quality.

---

## 4. Header Security & Range Header Ownership

### Safe Headers Whitelist
Only non-sensitive routing headers are forwarded in ephemeral leases:
* `User-Agent`
* `Referer`
* `Origin`
* `Accept`
* `Accept-Language`

### Range Header Ownership
The `Range` header is **excluded** from extractor-forwarded headers. The downstream HTTP downloader (`OkHttp`, `DownloadWorker`) owns byte-range and resume semantics.

### Sensitive Headers Blacklist
The following headers are treated as sensitive and are **NEVER** forwarded or logged:
* `Cookie` / `Set-Cookie`
* `Authorization` / `Proxy-Authorization`
* `X-Api-Key` / `X-Auth-Token`
* Any header containing `token`, `secret`, or `password`

### URL Redaction & Userinfo
* Query parameters: `https://cdn.example.com/audio?<redacted>`
* Userinfo credentials: `https://user:password@example.com/` stripped to `https://example.com/` or rejected before processing.

---

## 5. SSRF & Scheme Hardening

LRR validates all input URLs before dispatching extraction:
* **Allowed Schemes**: `http://`, `https://`
* **Forbidden Schemes**: `file:`, `content:`, `javascript:`, `data:`, `ftp:`, `about:`, `intent:`, `blob:`
* **Literal IP Inspection**:
  * IPv4: Rejects `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `0.0.0.0/8`.
  * IPv6: Rejects `::1`, `fc00::/7` (ULA), `fe80::/10` (link-local), `::ffff:0:0/96`.
* **DNS Resolution Check**: Resolves hostnames via `socket.getaddrinfo` and inspects resolved IP addresses against private and loopback ranges.
* **DNS Rebinding Limitation**: Pre-flight DNS validation prevents resolution of hostnames pointing to private ranges; subsequent socket connect behavior is bounded by OS networking.

---

## 6. extraOptions Policy & Bounding

To prevent arbitrary command execution or TLS bypass, `extraOptions` are strictly bounded:
* **Max Entries**: 10
* **Max Key Length**: 64 characters
* **Max Value Length**: 256 characters
* **Allowed Options Whitelist**: `preferred_quality`, `user_agent`, `geo_bypass_country`, `subtitles_lang`, `audio_format`
* **Forbidden Blacklist**: Any key containing `exec`, `command`, `path`, `file`, `proxy`, `tls`, `ssl`, `cert`, `cookie`, `auth`, `token`, `password`, `template`, `outtmpl`, `postprocessor`, `downloader`, `runtime`, `script` is immediately rejected.

---

## 7. Bounded IPC & Binder Preflight

1. **Preflight Payload Ceiling**: Strict `512 KB` limit enforced locally inside `lrr_extractor.py` and `LrrResolverService.kt` prior to Binder transmission.
2. **Format Bounding**: Formats are ranked, deduplicated, and bounded to the top 20 candidates.
3. **Cancellation Truth**: Client cancellation drops request bookkeeping and discards any late-arriving results without crashing or dangling state.
4. **Timeout Recovery**: 30-second operation timeout returns `ResolverErrorCode.Timeout` cleanly.

---

## 8. 16KB Page-Size Alignment Status

* **Build Compatibility**: QuickJS `libqjs.so` binaries for `arm64-v8a` and `x86_64` are built with NDK r28b and ELF `Align 0x4000` (16KB alignment verified via `readelf -l`).
* **Runtime Verification**: Runtime execution is verified on Android API 35 (`auralis_api35` with 4KB kernel page size). Physical 16KB Android kernel runtime execution remains to be tested on a 16KB-kernel device.
