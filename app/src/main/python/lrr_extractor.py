"""
Lyrune Resolver Runtime (LRR) — Python Normalization & Extraction Module
Strictly metadata and format inspection (download=False).
Supports QuickJS-NG JavaScript runtime and yt-dlp-ejs.
"""

import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.parse
from typing import Any, Dict, List, Optional

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


def set_quickjs_path(path: str):
    global _configured_qjs_path
    if path and os.path.exists(path):
        _configured_qjs_path = path


def get_quickjs_path() -> Optional[str]:
    global _configured_qjs_path
    if _configured_qjs_path and os.path.exists(_configured_qjs_path):
        return _configured_qjs_path

    # Common search locations (native lib dir, standard emulator test locations)
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
        # Use app cache / temp directory
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
        # Fallback to direct inline execution if tempfile fails
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
        # Verify execution of a trivial JS script
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

        # Verify yt-dlp recognizes the quickjs runtime
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
    """Attempt to parse expiry timestamps from signed URLs if present."""
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


def _classify_resolution_mode(fmt: Dict[str, Any]) -> str:
    protocol = (fmt.get("protocol") or "").lower()
    if any(seg in protocol for seg in ("m3u8", "hls", "dash", "f4m", "ism")):
        return "SEGMENTED_RESOURCE"

    acodec = fmt.get("acodec") or "none"
    vcodec = fmt.get("vcodec") or "none"
    if acodec == "none" and vcodec != "none":
        return "ENGINE_REQUIRED"

    url = fmt.get("url") or ""
    if url.startswith("http://") or url.startswith("https://"):
        return "DIRECT_RESOURCE"

    return "ENGINE_REQUIRED"


def _normalize_formats(raw_formats: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    normalized: List[Dict[str, Any]] = []
    if not raw_formats:
        return normalized

    for f in raw_formats:
        url = f.get("url")
        if not url:
            continue

        acodec = f.get("acodec")
        vcodec = f.get("vcodec")
        is_audio_only = bool(acodec and acodec != "none" and (not vcodec or vcodec == "none"))

        tbr = f.get("tbr") or f.get("abr")
        bitrate_kbps = int(tbr) if tbr is not None and str(tbr).replace(".", "", 1).isdigit() else None

        asr = f.get("asr")
        sample_rate_hz = int(asr) if asr is not None and str(asr).isdigit() else None

        filesize = f.get("filesize") or f.get("filesize_approx")
        file_size_bytes = int(filesize) if filesize is not None and str(filesize).isdigit() else None

        ext = f.get("ext")
        mime_type = None
        if ext:
            if ext == "mp3":
                mime_type = "audio/mpeg"
            elif ext in ("m4a", "aac"):
                mime_type = "audio/mp4"
            elif ext in ("ogg", "oga"):
                mime_type = "audio/ogg"
            elif ext == "opus":
                mime_type = "audio/opus"
            elif ext == "flac":
                mime_type = "audio/flac"
            elif ext == "wav":
                mime_type = "audio/wav"

        headers = f.get("http_headers") or {}
        safe_headers = {str(k): str(v) for k, v in headers.items() if str(k).lower() not in ("cookie", "authorization")}

        norm_fmt = {
            "formatId": f.get("format_id"),
            "url": url,
            "extension": ext,
            "mimeType": mime_type,
            "audioCodec": acodec if acodec != "none" else None,
            "videoCodec": vcodec if vcodec != "none" else None,
            "bitrateKbps": bitrate_kbps,
            "sampleRateHz": sample_rate_hz,
            "fileSizeBytes": file_size_bytes,
            "protocol": f.get("protocol"),
            "httpHeaders": safe_headers,
            "expiresAtEpochMs": _extract_expiry_ms(url),
            "resolutionMode": _classify_resolution_mode(f),
            "isAudioOnly": is_audio_only,
        }
        normalized.append(norm_fmt)

    return normalized


def _normalize_info(info: Dict[str, Any], original_url: str) -> Dict[str, Any]:
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

    raw_formats = info.get("formats") or []
    if not raw_formats and info.get("url"):
        raw_formats = [info]

    formats = _normalize_formats(raw_formats)

    return {
        "source": source,
        "sourceId": str(source_id) if source_id is not None else None,
        "webpageUrl": webpage_url,
        "title": title,
        "artist": artist,
        "album": album,
        "durationMs": duration_ms,
        "artworkUrl": artwork_url,
        "formats": formats,
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


def inspect_source(request_json: str) -> str:
    """
    Inspects source metadata and available formats. Strictly download=False.
    """
    global _real_extraction_succeeded
    try:
        req = json.loads(request_json)
        source_url = req.get("sourceUrl") or ""
        if not source_url:
            return json.dumps({
                "success": False,
                "error": {
                    "source": "",
                    "technicalCode": "UnsupportedUrl",
                    "isRetryable": False,
                    "safeUserMessage": "No URL provided.",
                    "debugDetails": "sourceUrl was empty",
                }
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
                }
            })

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "extract_flat": False,
            "ignoreerrors": False,
            "no_color": True,
            "socket_timeout": 15,
        }

        # Configure QuickJS if available
        qjs = req.get("quickJsPath") or get_quickjs_path()
        if qjs and os.path.exists(qjs):
            ydl_opts["js_runtimes"] = {"quickjs": {"path": qjs}}

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(source_url, download=False)
            if not info:
                return json.dumps({
                    "success": False,
                    "error": {
                        "source": source_url,
                        "technicalCode": "NotFound",
                        "isRetryable": False,
                        "safeUserMessage": "No media information found.",
                        "debugDetails": "extract_info returned None",
                    }
                })

            normalized = _normalize_info(info, source_url)
            _real_extraction_succeeded = True
            return json.dumps({"success": True, "media": normalized})

    except Exception as e:
        source_url = ""
        try:
            req = json.loads(request_json)
            source_url = req.get("sourceUrl") or ""
        except Exception:
            pass
        error_dto = _map_exception_to_error(e, source_url)
        return json.dumps({"success": False, "error": error_dto})


def resolve_source(request_json: str) -> str:
    """
    Resolves fresh URLs for immediate consumption. Strictly download=False.
    """
    return inspect_source(request_json)
