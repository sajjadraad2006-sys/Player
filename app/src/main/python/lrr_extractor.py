"""
Lyrune Resolver Runtime (LRR) — Python Normalization & Extraction Module
Strictly metadata and format inspection (download=False).
"""

import json
import sys
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


def get_runtime_versions() -> Dict[str, str]:
    return {
        "python_version": sys.version.split()[0],
        "yt_dlp_version": YT_DLP_VERSION if YT_DLP_AVAILABLE else "NOT_INSTALLED",
        "ejs_version": "NOT_INSTALLED",
        "quickjs_version": "NOT_INSTALLED",
        "ffmpeg_version": "NOT_INSTALLED",
    }


def get_health_json() -> str:
    versions = get_runtime_versions()
    health = {
        "resolverServiceReady": True,
        "pythonReady": True,
        "ytDlpReady": YT_DLP_AVAILABLE,
        "ejsReady": False,
        "jsRuntimeReady": False,
        "ffmpegReady": False,
        "details": versions,
    }
    return json.dumps(health)


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
                    # If seconds since epoch (> 2020), convert to ms
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
            elif ext == "ogg" or ext == "oga":
                mime_type = "audio/ogg"
            elif ext == "opus":
                mime_type = "audio/opus"
            elif ext == "flac":
                mime_type = "audio/flac"
            elif ext == "wav":
                mime_type = "audio/wav"

        headers = f.get("http_headers") or {}
        # Ensure string mapping for headers without cookies/secrets
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
    
    # Do not invent fake artists
    artist = info.get("artist") or info.get("creator") or info.get("uploader") or info.get("channel")
    album = info.get("album")

    duration = info.get("duration")
    duration_ms = int(duration * 1000) if duration is not None and isinstance(duration, (int, float)) else None

    # Artwork
    artwork_url = info.get("thumbnail")
    if not artwork_url and info.get("thumbnails"):
        thumbs = info.get("thumbnails")
        if isinstance(thumbs, list) and len(thumbs) > 0:
            artwork_url = thumbs[-1].get("url")

    raw_formats = info.get("formats") or []
    if not raw_formats and info.get("url"):
        # Single direct format fallback
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
    elif isinstance(e, UnavailableVideoError) or "Video unavailable" in err_str or "not found" in err_str.lower():
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
    # For Phase 2 PoC, resolution delegates to extract_info(download=False)
    # to obtain fresh signed URLs directly at resolve time.
    return inspect_source(request_json)
