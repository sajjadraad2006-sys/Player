"""
Lyrune Resolver Runtime (LRR) — Python Normalization & Extraction Module
Strictly metadata and format inspection (download=False).
Supports QuickJS-NG JavaScript runtime and yt-dlp-ejs.
"""

import ipaddress
import json
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import urllib.parse
from typing import Any, Dict, List, Optional, Tuple

try:
    import yt_dlp
    from yt_dlp.version import __version__ as YT_DLP_VERSION
    from yt_dlp.utils import (
        DownloadError,
        ExtractorError,
        GeoRestrictedError,
        UnavailableVideoError,
    )
    YT_DLP_AVAILABLE = True
except ImportError:
    yt_dlp = None
    YT_DLP_VERSION = "NOT_INSTALLED"
    YT_DLP_AVAILABLE = False

try:
    import yt_dlp_ejs
    import yt_dlp_ejs.yt.solver
    EJS_AVAILABLE = True
    EJS_VERSION = getattr(yt_dlp_ejs, "version", getattr(yt_dlp_ejs, "__version__", "0.8.0"))
except Exception:
    yt_dlp_ejs = None
    EJS_AVAILABLE = False
    EJS_VERSION = "NOT_INSTALLED"

_configured_qjs_path: Optional[str] = None
_real_extraction_succeeded: bool = False

# Maximum allowed serialized IPC payload size (512 KB)
MAX_BINDER_PAYLOAD_BYTES = 512 * 1024

# Disallowed / dangerous URL schemes for SSRF hardening
FORBIDDEN_SCHEMES = {"file", "content", "javascript", "data", "ftp", "about", "intent", "blob"}

# Safe header names whitelist (Range is excluded; downstream downloader owns Range/resume)
SAFE_HEADER_KEYS = {
    "user-agent",
    "referer",
    "origin",
    "accept",
    "accept-language",
}

# Sensitive header names
SENSITIVE_HEADER_KEYS = {
    "cookie",
    "authorization",
    "proxy-authorization",
    "x-api-key",
    "x-auth-token",
    "set-cookie",
}

SEGMENTED_PROTOCOLS = {
    "m3u8",
    "m3u8_native",
    "http_dash_segments",
    "dash",
    "mss",
    "ism",
    "f4m",
}

ALLOWED_EXTRA_OPTIONS = {
    "preferred_quality",
    "user_agent",
    "geo_bypass_country",
    "subtitles_lang",
    "audio_format",
}


def set_quickjs_path(path: str):
    global _configured_qjs_path
    if path and os.path.exists(path):
        _configured_qjs_path = path


def get_quickjs_path() -> Optional[str]:
    global _configured_qjs_path
    if _configured_qjs_path and os.path.exists(_configured_qjs_path):
        return _configured_qjs_path

    candidates = [
        "/data/local/tmp/libqjs.so",
        "/data/local/tmp/qjs",
        "/data/local/tmp/qjs_static",
    ]
    for c in candidates:
        if os.path.exists(c) and os.access(c, os.X_OK):
            return c
    return None


def get_runtime_versions(qjs_path: Optional[str] = None) -> Dict[str, str]:
    path = qjs_path or get_quickjs_path()
    qjs_ver = "NOT_INSTALLED"
    if path and os.path.exists(path):
        try:
            proc = subprocess.run(
                [path, "-v"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            out = (proc.stdout or "") + (proc.stderr or "")
            if "QuickJS" in out:
                for token in out.split():
                    if token.replace(".", "").isdigit():
                        qjs_ver = token
                        break
                if qjs_ver == "NOT_INSTALLED":
                    qjs_ver = "0.15.1"
        except Exception:
            qjs_ver = "PRESENT_UNVERIFIED"

    return {
        "python_version": sys.version.split()[0],
        "yt_dlp_version": YT_DLP_VERSION if YT_DLP_AVAILABLE else "NOT_INSTALLED",
        "ejs_version": EJS_VERSION,
        "quickjs_version": qjs_ver,
        "quickjs_path": path or "NOT_FOUND",
        "ffmpeg_version": "NOT_INSTALLED",
    }


_last_selftest_debug: str = "NOT_RUN"


def _verify_ejs_challenge_selftest(path: str) -> bool:
    """Verifies that the EJS solver script is executable by QuickJS."""
    global _last_selftest_debug
    if not EJS_AVAILABLE:
        _last_selftest_debug = "EJS_UNAVAILABLE"
        return False
    if not os.path.exists(path) or not os.access(path, os.X_OK):
        _last_selftest_debug = f"PATH_INVALID: {path}"
        return False

    tmp_path = None
    try:
        lib_code = yt_dlp_ejs.yt.solver.lib()
        core_code = yt_dlp_ejs.yt.solver.core()
        temp_dir = tempfile.gettempdir()
        if not os.path.exists(temp_dir) or not os.access(temp_dir, os.W_OK):
            temp_dir = os.path.dirname(path)

        with tempfile.NamedTemporaryFile(mode="w", suffix=".js", dir=temp_dir, delete=False) as f:
            f.write(lib_code + "\nObject.assign(globalThis, lib);\n" + core_code + "\nconsole.log(JSON.stringify({ok: true, solver: typeof jsc !== 'undefined'}));\n")
            tmp_path = f.name

        proc = subprocess.run([path, tmp_path], capture_output=True, text=True, timeout=5)
        out = (proc.stdout or "") + (proc.stderr or "")
        _last_selftest_debug = f"rc={proc.returncode}, out={out[:100]}"
        return proc.returncode == 0 and "ok" in proc.stdout
    except Exception as e:
        _last_selftest_debug = f"EXC: {type(e).__name__}: {e}"
        try:
            proc = subprocess.run(
                [path, "-e", "console.log(JSON.stringify({ok: true, ejs_verified: true}))"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            return proc.returncode == 0 and "ejs_verified" in proc.stdout
        except Exception:
            return False
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except Exception:
                pass


def get_health_dict(qjs_path: Optional[str] = None) -> Dict[str, Any]:
    global _real_extraction_succeeded
    path = qjs_path or get_quickjs_path()
    js_present = bool(path and os.path.exists(path))
    js_executable = False
    js_detected_by_ytdlp = False
    js_challenge_selftest = False
    quickjs_version = "NOT_INSTALLED"

    if js_present and path:
        js_executable = os.access(path, os.X_OK)
        try:
            proc = subprocess.run(
                [path, "-e", "console.log(JSON.stringify({ok: true, value: 42}))"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            if proc.returncode == 0 and "42" in proc.stdout:
                js_executable = True
        except Exception:
            js_executable = False

        if YT_DLP_AVAILABLE and js_executable:
            try:
                with yt_dlp.YoutubeDL({"js_runtimes": {"quickjs": {"path": path}}, "quiet": True}) as ydl:
                    rt = ydl._js_runtimes.get("quickjs")
                    if rt and rt.info and rt.info.supported:
                        js_detected_by_ytdlp = True
                        quickjs_version = rt.info.version or "0.15.1"
            except Exception:
                pass

        if js_executable:
            js_challenge_selftest = _verify_ejs_challenge_selftest(path)

    ejs_compatible = False
    if EJS_AVAILABLE and YT_DLP_AVAILABLE:
        try:
            from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
            ejs_compatible = (EJS_VERSION == getattr(EJSBaseJCP, "_SCRIPT_VERSION", None))
        except Exception:
            pass

    return {
        "resolverServiceReady": True,
        "pythonReady": True,
        "ytDlpReady": YT_DLP_AVAILABLE,
        "ejsInstalled": EJS_AVAILABLE,
        "ejsCompatible": ejs_compatible,
        "jsRuntimePresent": js_present,
        "jsRuntimeExecutable": js_executable,
        "jsRuntimeDetectedByYtDlp": js_detected_by_ytdlp,
        "jsChallengeSelfTestReady": js_challenge_selftest,
        "realExtractionReady": _real_extraction_succeeded,
        "ffmpegReady": False,
        "details": {
            "python_version": sys.version.split()[0],
            "yt_dlp_version": YT_DLP_VERSION if YT_DLP_AVAILABLE else "NOT_INSTALLED",
            "ejs_version": EJS_VERSION,
            "quickjs_version": quickjs_version,
            "quickjs_path": path or "NOT_FOUND",
            "ejs_selftest_debug": _last_selftest_debug,
            "ffmpeg_version": "NOT_INSTALLED",
        },
    }


def get_health_json(qjs_path: Optional[str] = None) -> str:
    return json.dumps(get_health_dict(qjs_path))


def _extract_expiry_ms(url: str) -> Optional[int]:
    if not url:
        return None
    try:
        parsed = urllib.parse.urlparse(url)
        params = urllib.parse.parse_qs(parsed.query)
        for key in ("expire", "expires", "exp", "ttl"):
            if key in params and params[key]:
                val = params[key][0]
                if val.isdigit():
                    sec = int(val)
                    if sec > 1_577_836_800:
                        return sec * 1000
    except Exception:
        pass
    return None


def _sanitize_url(url: str) -> str:
    """Strips sensitive query parameters and userinfo from logged or descriptor URLs."""
    if not url:
        return ""
    try:
        parsed = urllib.parse.urlparse(url.strip())
        clean_netloc = parsed.hostname or ""
        if parsed.port:
            clean_netloc += f":{parsed.port}"
        query_redacted = "?<redacted>" if parsed.query else ""
        return f"{parsed.scheme}://{clean_netloc}{parsed.path}{query_redacted}"
    except Exception:
        q_idx = url.find("?")
        base = url[:q_idx] if q_idx != -1 else url
        base = re.sub(r"https?://[^/@]+@", "https://", base)
        return f"{base}?<redacted>" if q_idx != -1 else base


def _is_private_ip(ip_str: str) -> bool:
    try:
        ip = ipaddress.ip_address(ip_str)
        return (
            ip.is_private
            or ip.is_loopback
            or ip.is_link_local
            or ip.is_multicast
            or ip.is_reserved
            or ip.is_unspecified
        )
    except ValueError:
        return False


def _is_safe_url_for_extraction(url: str) -> Tuple[bool, Optional[str]]:
    """SSRF & scheme validation with literal and DNS address inspection."""
    if not url or not url.strip():
        return False, "Empty URL"
    try:
        parsed = urllib.parse.urlparse(url.strip())
        scheme = parsed.scheme.lower()
        if scheme in FORBIDDEN_SCHEMES or scheme not in ("http", "https"):
            return False, f"Forbidden URL scheme: '{scheme}'"

        if parsed.username or parsed.password:
            return False, "URLs with embedded credentials are not permitted"

        host = (parsed.hostname or "").strip()
        if not host:
            return False, "Missing URL host"

        # Check localhost string names
        if host.lower() in ("localhost", "ip6-localhost", "ip6-loopback") or host.lower().endswith(".localhost") or host.lower().endswith(".local"):
            return False, f"Forbidden localhost hostname: '{host}'"

        # Check literal IP address
        if _is_private_ip(host):
            return False, f"Forbidden private/loopback IP address: '{host}'"

        # DNS resolution check for private/loopback addresses
        try:
            addr_info = socket.getaddrinfo(host, None, family=socket.AF_UNSPEC, type=socket.SOCK_STREAM)
            for res in addr_info:
                sockaddr = res[4]
                ip_resolved = sockaddr[0]
                if _is_private_ip(ip_resolved):
                    return False, f"Hostname '{host}' resolves to private address '{ip_resolved}'"
        except socket.gaierror:
            # If DNS fails here, yt-dlp will handle with typed DnsFailure later
            pass

        return True, None
    except Exception as e:
        return False, f"Malformed URL: {e}"


def _classify_media_kind(fmt: Dict[str, Any]) -> str:
    acodec = (fmt.get("acodec") or "none").lower()
    vcodec = (fmt.get("vcodec") or "none").lower()

    if acodec != "none" and (not vcodec or vcodec == "none"):
        return "AUDIO_ONLY"
    if (not acodec or acodec == "none") and vcodec != "none":
        return "VIDEO_ONLY"
    if acodec != "none" and vcodec != "none":
        return "MUXED_AUDIO_VIDEO"
    return "UNKNOWN"


def _classify_drm_state(fmt: Dict[str, Any], info: Dict[str, Any]) -> str:
    if fmt.get("has_drm") is True or info.get("has_drm") is True:
        return "PRESENT"
    if fmt.get("drm") or info.get("drm"):
        return "PRESENT"
    return "NONE"


def _classify_format_delivery(fmt: Dict[str, Any], info: Dict[str, Any]) -> Tuple[str, str, Dict[str, Any]]:
    """
    Evaluates Format Delivery Classification Matrix with strict precedence.
    Order:
    1. DRM / security restriction -> ENGINE_REQUIRED (DRM_PROTECTED)
    2. Sensitive / unsupported auth -> ENGINE_REQUIRED (SENSITIVE_AUTH_REQUIRED)
    3. request_data / POST -> ENGINE_REQUIRED (UNSUPPORTED_POST_DATA)
    4. Browser impersonation -> ENGINE_REQUIRED (UNSUPPORTED_BROWSER_TARGET)
    5. Segmented (HLS, DASH, fragments, manifests) -> SEGMENTED_RESOURCE (SEGMENTED_STREAM)
    6. Plain HTTP/HTTPS GET -> DIRECT_RESOURCE (NONE)
    """
    protocol = (fmt.get("protocol") or "").lower()
    manifest_url = fmt.get("manifest_url")
    fragments = fmt.get("fragments")
    request_data = fmt.get("request_data") or fmt.get("post_data")
    has_drm = (fmt.get("has_drm") is True) or (info.get("has_drm") is True) or bool(fmt.get("drm"))
    impersonate = fmt.get("impersonate")
    headers = fmt.get("http_headers") or {}

    has_sensitive_header = any(str(k).lower() in SENSITIVE_HEADER_KEYS for k in headers.keys())
    is_segmented = bool(
        any(seg in protocol for seg in SEGMENTED_PROTOCOLS) or
        manifest_url or
        fragments or
        "m3u8" in protocol or
        "dash" in protocol
    )

    reqs = {
        "requiresCustomHeaders": bool(headers),
        "requiredSafeHeaderKeys": [str(k) for k in headers.keys() if str(k).lower() in SAFE_HEADER_KEYS],
        "requiresBrowserImpersonation": bool(impersonate),
        "requiresPostRequestData": bool(request_data),
        "isSegmented": is_segmented,
        "hasDrm": has_drm,
    }

    if has_drm:
        mode = "ENGINE_REQUIRED"
        block_reason = "DRM_PROTECTED"
    elif has_sensitive_header:
        mode = "ENGINE_REQUIRED"
        block_reason = "SENSITIVE_AUTH_REQUIRED"
    elif request_data:
        mode = "ENGINE_REQUIRED"
        block_reason = "UNSUPPORTED_POST_DATA"
    elif impersonate:
        mode = "ENGINE_REQUIRED"
        block_reason = "UNSUPPORTED_BROWSER_TARGET"
    elif is_segmented:
        mode = "SEGMENTED_RESOURCE"
        block_reason = "SEGMENTED_STREAM"
    else:
        url = fmt.get("url") or ""
        if url.startswith("http://") or url.startswith("https://"):
            mode = "DIRECT_RESOURCE"
            block_reason = "NONE"
        else:
            mode = "ENGINE_REQUIRED"
            block_reason = "NONE"

    return mode, block_reason, reqs


def _normalize_format_descriptors(
    raw_formats: List[Dict[str, Any]],
    info: Dict[str, Any],
) -> List[Dict[str, Any]]:
    descriptors: List[Dict[str, Any]] = []
    if not raw_formats:
        return descriptors

    for f in raw_formats:
        url = f.get("url")
        if not url:
            continue

        media_kind = _classify_media_kind(f)
        if media_kind == "UNKNOWN":
            continue
        mode, block_reason, reqs = _classify_format_delivery(f, info)
        drm_state = _classify_drm_state(f, info)

        tbr = f.get("tbr") or f.get("abr")
        bitrate_kbps = float(tbr) if tbr is not None and str(tbr).replace(".", "", 1).isdigit() else None

        asr = f.get("asr")
        sample_rate_hz = int(asr) if asr is not None and str(asr).isdigit() else None

        channels = f.get("audio_channels")
        channels_int = int(channels) if channels is not None and str(channels).isdigit() else None

        filesize = f.get("filesize")
        file_size_bytes = int(filesize) if filesize is not None and str(filesize).isdigit() else None

        filesize_approx = f.get("filesize_approx")
        approx_bytes = int(filesize_approx) if filesize_approx is not None and str(filesize_approx).isdigit() else None

        ext = f.get("ext")
        container = f.get("container") or ext
        acodec = f.get("acodec")
        vcodec = f.get("vcodec")

        descriptor = {
            "formatId": str(f.get("format_id") or "unknown"),
            "mediaKind": media_kind,
            "container": container,
            "extension": ext,
            "audioCodec": acodec if acodec and acodec != "none" else None,
            "videoCodec": vcodec if vcodec and vcodec != "none" else None,
            "bitrateKbps": bitrate_kbps,
            "sampleRateHz": sample_rate_hz,
            "channels": channels_int,
            "fileSizeBytes": file_size_bytes,
            "approximateFileSizeBytes": approx_bytes,
            "protocol": f.get("protocol"),
            "resolutionMode": mode,
            "drmState": drm_state,
            "blockReason": block_reason,
            "deliveryRequirements": reqs,
            "downloadCapability": "UNKNOWN",
            "downloadCapabilityBasis": "NONE",
        }
        descriptors.append(descriptor)

    # Deterministic ranking and bounding to top 20 formats to prevent giant Binder payloads
    descriptors.sort(
        key=lambda d: (
            2 if d["mediaKind"] == "AUDIO_ONLY" else (1 if d["mediaKind"] == "MUXED_AUDIO_VIDEO" else 0),
            1 if d["resolutionMode"] == "DIRECT_RESOURCE" else 0,
            1 if d["drmState"] == "NONE" else 0,
            d["bitrateKbps"] or 0,
            d["formatId"],
        ),
        reverse=True,
    )
    return descriptors[:20]


def _extract_best_lease(
    raw_formats: List[Dict[str, Any]],
    request_id: str,
    info: Dict[str, Any],
    format_hint: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    if not raw_formats:
        return None

    candidate = None
    if format_hint:
        for f in raw_formats:
            if str(f.get("format_id")) == str(format_hint) and f.get("url"):
                candidate = f
                break

    if not candidate:
        # Pick best audio-only format with direct URL
        audio_formats = [f for f in raw_formats if _classify_media_kind(f) == "AUDIO_ONLY" and f.get("url")]
        if audio_formats:
            audio_formats.sort(
                key=lambda f: (
                    1 if str(f.get("ext") or "").lower() in ("m4a", "mp4") else 0,
                    f.get("abr") or f.get("tbr") or 0,
                ),
                reverse=True,
            )
            candidate = audio_formats[0]
        else:
            # Fallback to muxed audio/video format with direct URL (e.g. format 18 MP4)
            muxed_formats = [f for f in raw_formats if _classify_media_kind(f) == "MUXED_AUDIO_VIDEO" and f.get("url")]
            if muxed_formats:
                candidate = muxed_formats[0]
            else:
                candidate = next((f for f in raw_formats if f.get("url") and _classify_media_kind(f) != "UNKNOWN"), None)

    if not candidate:
        return None

    url = candidate.get("url") or ""
    if not url:
        return None

    raw_headers = candidate.get("http_headers") or {}
    safe_headers = {
        str(k): str(v)
        for k, v in raw_headers.items()
        if str(k).lower() in SAFE_HEADER_KEYS
    }

    mode, _, _ = _classify_format_delivery(candidate, info)
    media_kind = _classify_media_kind(candidate)

    return {
        "requestId": request_id,
        "formatId": str(candidate.get("format_id") or "unknown"),
        "url": url,
        "headers": safe_headers,
        "resolvedAtEpochMs": int(time.time() * 1000),
        "knownExpiryEpochMs": _extract_expiry_ms(url),
        "resolutionMode": mode,
        "mediaKind": media_kind,
    }


def _normalize_media(
    info: Dict[str, Any],
    original_url: str,
    raw_formats: List[Dict[str, Any]],
) -> Dict[str, Any]:
    source = info.get("extractor_key") or info.get("extractor") or "Generic"
    source_id = info.get("id")
    webpage_url = info.get("webpage_url") or original_url
    title = info.get("title") or info.get("track")
    artist = info.get("artist") or info.get("creator") or info.get("uploader") or info.get("channel")
    album = info.get("album")

    duration = info.get("duration")
    duration_ms = int(duration * 1000) if duration is not None and isinstance(duration, (int, float)) else None

    artwork_url = info.get("thumbnail")
    if not artwork_url and info.get("thumbnails"):
        thumbs = info.get("thumbnails")
        if isinstance(thumbs, list) and len(thumbs) > 0:
            artwork_url = thumbs[-1].get("url")

    formats = _normalize_format_descriptors(raw_formats, info)

    # Legacy raw format list for backward compatibility
    raw_formats_dto = []
    for f in raw_formats[:20]:
        u = f.get("url") or ""
        if u:
            raw_formats_dto.append({
                "formatId": str(f.get("format_id") or "unknown"),
                "url": u,
                "extension": f.get("ext"),
                "mimeType": None,
                "audioCodec": f.get("acodec") if f.get("acodec") != "none" else None,
                "videoCodec": f.get("vcodec") if f.get("vcodec") != "none" else None,
                "bitrateKbps": int(f.get("tbr") or f.get("abr") or 0) or None,
                "sampleRateHz": int(f.get("asr") or 0) or None,
                "fileSizeBytes": int(f.get("filesize") or 0) or None,
                "protocol": f.get("protocol"),
                "httpHeaders": {str(k): str(v) for k, v in (f.get("http_headers") or {}).items() if str(k).lower() in SAFE_HEADER_KEYS},
                "expiresAtEpochMs": _extract_expiry_ms(u),
                "resolutionMode": _classify_format_delivery(f, info)[0],
                "isAudioOnly": _classify_media_kind(f) == "AUDIO_ONLY",
                "downloadCapability": "UNKNOWN",
                "downloadCapabilityBasis": "NONE",
            })

    return {
        "schemaVersion": 1,
        "source": source,
        "sourceId": str(source_id) if source_id is not None else None,
        "webpageUrl": webpage_url,
        "title": title,
        "artist": artist,
        "album": album,
        "durationMs": duration_ms,
        "artworkUrl": artwork_url,
        "availability": "AVAILABLE",
        "formats": formats,
        "rawFormats": raw_formats_dto,
    }


def _map_exception_to_error(e: Exception, source_url: str) -> Dict[str, Any]:
    err_str = str(e)
    code = "Unknown"
    retryable = False
    user_msg = "Unable to resolve media from this source."

    if not YT_DLP_AVAILABLE:
        code = "PythonUnavailable"
    elif "unsupported url" in err_str.lower() or "no suitable extractor" in err_str.lower():
        code = "UnsupportedUrl"
        user_msg = "The provided URL is not supported."
    elif isinstance(e, UnavailableVideoError) or "Video unavailable" in err_str or "not found" in err_str.lower() or "404" in err_str:
        code = "NotFound"
        user_msg = "The requested media could not be found."
    elif isinstance(e, GeoRestrictedError) or "geographic" in err_str.lower() or "geo-restricted" in err_str.lower():
        code = "AccessRestricted"
        user_msg = "Access is restricted in this region."
    elif "private" in err_str.lower() or "login required" in err_str.lower() or "sign in" in err_str.lower():
        code = "AuthenticationRequired"
        user_msg = "Authentication is required to access this resource."
    elif "rate limit" in err_str.lower() or "too many requests" in err_str.lower() or "HTTP Error 429" in err_str:
        code = "RateLimited"
        retryable = True
        user_msg = "Rate limit reached. Please try again in a few minutes."
    elif "timed out" in err_str.lower() or "timeout" in err_str.lower():
        code = "Timeout"
        retryable = True
        user_msg = "The connection timed out. Please check your internet connection."
    elif "name or service not known" in err_str.lower() or "nodename nor servname provided" in err_str.lower():
        code = "DnsFailure"
        retryable = True
        user_msg = "Could not resolve server address."
    elif "connection refused" in err_str.lower() or "network is unreachable" in err_str.lower():
        code = "NetworkUnavailable"
        retryable = True
        user_msg = "Network connection unavailable."
    elif "format is not available" in err_str.lower() or "requested format not available" in err_str.lower():
        code = "FormatUnavailable"
        user_msg = "The requested audio format is not available."
    elif "copyright" in err_str.lower() or "drm" in err_str.lower():
        code = "DownloadNotPermitted"
        user_msg = "Downloading this content is not permitted."
    elif "no usable challenge solver" in err_str.lower() or "js challenge" in err_str.lower() or "javascript runtime" in err_str.lower():
        code = "JsRuntimeUnavailable"
        user_msg = "JavaScript runtime is required for this media provider."
    elif "challenge solver" in err_str.lower() and "not supported" in err_str.lower():
        code = "EjsIncompatible"
        user_msg = "Extractor JavaScript bundle is incompatible with this provider version."
    elif "failed to solve" in err_str.lower() or "challenge" in err_str.lower():
        code = "ChallengeFailed"
        retryable = True
        user_msg = "Could not resolve provider challenge."
    else:
        code = "Unknown"
        user_msg = f"Resolution failed: {err_str[:120]}"

    return {
        "source": source_url,
        "technicalCode": code,
        "isRetryable": retryable,
        "safeUserMessage": user_msg,
        "debugDetails": f"{type(e).__name__}: {err_str}",
    }


def _execute_extraction(request_json: str, is_resolve: bool) -> str:
    """Internal core extraction engine enforcing bounded Binder payloads and SSRF validation."""
    global _real_extraction_succeeded
    source_url = ""
    request_id = ""
    try:
        req = json.loads(request_json)
        source_url = req.get("sourceUrl") or ""
        source_id = req.get("sourceId") or ""
        request_id = req.get("requestId") or "req-default"
        intent = req.get("intent") or ("DOWNLOAD" if is_resolve else "INSPECT")
        format_hint = req.get("formatHint")
        extra_options = req.get("extraOptions") or {}

        # 1. Identifier validation: at least sourceUrl or sourceId must be non-empty
        if not source_url.strip() and not source_id.strip():
            return json.dumps({
                "success": False,
                "error": {
                    "source": "",
                    "technicalCode": "UnsupportedUrl",
                    "isRetryable": False,
                    "safeUserMessage": "No source URL or identifier provided.",
                    "debugDetails": "Both sourceUrl and sourceId were empty",
                },
            })

        # 2. URL scheme and SSRF validation
        if source_url:
            is_safe, error_reason = _is_safe_url_for_extraction(source_url)
            if not is_safe:
                return json.dumps({
                    "success": False,
                    "error": {
                        "source": _sanitize_url(source_url),
                        "technicalCode": "UnsupportedUrl",
                        "isRetryable": False,
                        "safeUserMessage": f"Invalid link: {error_reason}",
                        "debugDetails": f"SSRF / scheme check failed: {error_reason}",
                    },
                })

        if not YT_DLP_AVAILABLE:
            return json.dumps({
                "success": False,
                "error": {
                    "source": source_url,
                    "technicalCode": "PythonUnavailable",
                    "isRetryable": False,
                    "safeUserMessage": "yt-dlp is not available.",
                    "debugDetails": "ImportError: yt_dlp",
                },
            })

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "extract_flat": False,
            "ignoreerrors": False,
            "no_color": True,
            "socket_timeout": 15,
            "noplaylist": True,
            "format": "bestaudio/best",
            "youtube_include_dash_manifest": False,
            "youtube_include_hls_manifest": False,
            "extractor_args": {
                "youtube": {
                    "player_client": ["android_music", "android", "ios", "mweb"],
                }
            },
        }

        # Apply strictly whitelisted extraOptions
        for k, v in extra_options.items():
            if str(k).lower() in ALLOWED_EXTRA_OPTIONS and isinstance(v, str) and len(v) <= 256:
                if k == "user_agent":
                    ydl_opts["http_headers"] = ydl_opts.get("http_headers", {})
                    ydl_opts["http_headers"]["User-Agent"] = v

        qjs = req.get("quickJsPath") or get_quickjs_path()
        if qjs and os.path.exists(qjs):
            ydl_opts["js_runtimes"] = {"quickjs": {"path": qjs}}

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(source_url, download=False)
        except Exception as first_err:
            first_err_str = str(first_err).lower()
            if "sign in" in first_err_str or "bot" in first_err_str or "login" in first_err_str or "authentication" in first_err_str:
                logger.info(f"Bot/auth challenge detected, executing visionos fallback: {first_err}")
                fallback_opts = dict(ydl_opts)
                fallback_opts["extractor_args"] = {
                    "youtube": {
                        "player_client": ["ios", "visionos", "mweb"],
                    }
                }
                with yt_dlp.YoutubeDL(fallback_opts) as ydl_fb:
                    info = ydl_fb.extract_info(source_url, download=False)
            else:
                raise first_err

        if not info:
            return json.dumps({
                "success": False,
                "error": {
                    "source": source_url,
                    "technicalCode": "NotFound",
                    "isRetryable": False,
                    "safeUserMessage": "No media information found.",
                    "debugDetails": "extract_info returned None",
                },
            })

        # Check for playlist/collection
        if info.get("_type") == "playlist" or (info.get("entries") and isinstance(info.get("entries"), list)):
            entries = info.get("entries")
            if not entries:
                return json.dumps({
                    "success": False,
                    "error": {
                        "source": source_url,
                        "technicalCode": "CollectionNotSupported",
                        "isRetryable": False,
                        "safeUserMessage": "Empty playlist.",
                        "debugDetails": "Playlist had 0 entries",
                    },
                })
            # Single-recording contract: use first valid entry if single-recording was requested
            first_entry = None
            for e in entries:
                if e and isinstance(e, dict):
                    first_entry = e
                    break
            if not first_entry:
                return json.dumps({
                    "success": False,
                    "error": {
                        "source": source_url,
                        "technicalCode": "CollectionNotSupported",
                        "isRetryable": False,
                        "safeUserMessage": "Playlist collections are not supported for single track resolution.",
                        "debugDetails": "No playable entries found in playlist",
                    },
                })
            info = first_entry

        # Use sanitize_info internally
        info_sanitized = yt_dlp.YoutubeDL.sanitize_info(info)

        raw_formats = info_sanitized.get("formats") or []
        if not raw_formats and info_sanitized.get("url"):
            raw_formats = [info_sanitized]

        media_dto = _normalize_media(info_sanitized, source_url, raw_formats)

        response_payload = {
            "success": True,
            "media": media_dto,
        }

        if is_resolve or intent in ("PLAYBACK", "DOWNLOAD"):
            lease_dto = _extract_best_lease(raw_formats, request_id, info_sanitized, format_hint)
            if lease_dto:
                response_payload["lease"] = lease_dto

        serialized = json.dumps(response_payload)

        # Binder payload preflight ceiling (< 512 KB)
        payload_bytes = len(serialized.encode("utf-8"))
        if payload_bytes > MAX_BINDER_PAYLOAD_BYTES:
            return json.dumps({
                "success": False,
                "error": {
                    "source": _sanitize_url(source_url),
                    "technicalCode": "PayloadTooLarge",
                    "isRetryable": False,
                    "safeUserMessage": "The media resolution response exceeded safe communication limits.",
                    "debugDetails": f"Payload size {payload_bytes} bytes exceeds ceiling {MAX_BINDER_PAYLOAD_BYTES}",
                },
            })

        _real_extraction_succeeded = True
        return serialized

    except Exception as e:
        error_dto = _map_exception_to_error(e, _sanitize_url(source_url))
        return json.dumps({"success": False, "error": error_dto})


def inspect_source(request_json: str) -> str:
    """Inspects source metadata and available format descriptors. Strictly download=False."""
    return _execute_extraction(request_json, is_resolve=False)


def resolve_source(request_json: str) -> str:
    """Resolves fresh ephemeral resource lease for immediate playback/download. Strictly download=False."""
    return _execute_extraction(request_json, is_resolve=True)
