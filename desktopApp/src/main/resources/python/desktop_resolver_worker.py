"""
Desktop Resolver Worker process for Lyrune.
Communicates via hardened NDJSON protocol on stdin/stdout.
Processes inspect, resolve, health, cancel, and shutdown commands.
Guarantees strict stdout/stderr separation: third-party logs never corrupt protocol frames.
"""

import sys
import os
import json
import traceback

PROTOCOL_VERSION = 1

# Duplicate raw stdout for pure protocol transmission, then redirect sys.stdout to sys.stderr
# so any library logging (yt-dlp, urllib, print) strictly routes to stderr.
_raw_stdout_fd = os.dup(sys.stdout.fileno())
_protocol_out = os.fdopen(_raw_stdout_fd, "w", encoding="utf-8")
sys.stdout = sys.stderr

# Ensure current directory is in sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

try:
    import lrr_extractor
    LRR_AVAILABLE = True
except Exception as e:
    lrr_extractor = None
    LRR_AVAILABLE = False
    sys.stderr.write(f"[DesktopResolverWorker] Warning: lrr_extractor could not be imported: {e}\n")
    sys.stderr.flush()

def send_frame(obj):
    try:
        obj["protocol"] = PROTOCOL_VERSION
        payload = json.dumps(obj)
        _protocol_out.write(payload + "\n")
        _protocol_out.flush()
    except Exception as e:
        sys.stderr.write(f"[DesktopResolverWorker] Frame write error: {e}\n")
        sys.stderr.flush()

def handle_health():
    if LRR_AVAILABLE and hasattr(lrr_extractor, "get_health_dict"):
        health = lrr_extractor.get_health_dict()
    else:
        health = {
            "resolverServiceReady": True,
            "pythonReady": True,
            "ytDlpReady": False,
            "ejsInstalled": False,
            "ejsCompatible": False,
            "jsRuntimePresent": False,
            "jsRuntimeExecutable": False,
            "jsRuntimeDetectedByYtDlp": False,
            "jsChallengeSelfTestReady": False,
            "realExtractionReady": False,
            "ffmpegReady": False,
            "details": {
                "process_name": "desktop_resolver_worker",
                "pid": str(os.getpid()),
                "process_role": "DESKTOP_RESOLVER_WORKER",
                "python_version": sys.version,
            }
        }
    send_frame({"type": "health", "data": health})

def handle_inspect(req_id, req_data):
    if not LRR_AVAILABLE:
        send_frame({
            "type": "error",
            "id": req_id,
            "error": {
                "source": req_data.get("sourceUrl", ""),
                "technicalCode": "PythonUnavailable",
                "isRetryable": False,
                "safeUserMessage": "Resolver Python extraction module is not available.",
                "debugDetails": "lrr_extractor could not be imported"
            }
        })
        return

    try:
        res_str = lrr_extractor.inspect_source(json.dumps(req_data))
        parsed = json.loads(res_str)
        if parsed.get("success"):
            send_frame({"type": "result", "id": req_id, "data": parsed})
        else:
            send_frame({"type": "error", "id": req_id, "error": parsed.get("error", {})})
    except Exception as e:
        send_frame({
            "type": "error",
            "id": req_id,
            "error": {
                "source": req_data.get("sourceUrl", ""),
                "technicalCode": "Unknown",
                "isRetryable": False,
                "safeUserMessage": f"Inspection error: {e}",
                "debugDetails": traceback.format_exc()
            }
        })

def handle_resolve(req_id, req_data):
    if not LRR_AVAILABLE:
        send_frame({
            "type": "error",
            "id": req_id,
            "error": {
                "source": req_data.get("sourceUrl", ""),
                "technicalCode": "PythonUnavailable",
                "isRetryable": False,
                "safeUserMessage": "Resolver Python extraction module is not available.",
                "debugDetails": "lrr_extractor could not be imported"
            }
        })
        return

    try:
        res_str = lrr_extractor.resolve_source(json.dumps(req_data))
        parsed = json.loads(res_str)
        if parsed.get("success"):
            send_frame({"type": "result", "id": req_id, "data": parsed})
        else:
            send_frame({"type": "error", "id": req_id, "error": parsed.get("error", {})})
    except Exception as e:
        send_frame({
            "type": "error",
            "id": req_id,
            "error": {
                "source": req_data.get("sourceUrl", ""),
                "technicalCode": "Unknown",
                "isRetryable": False,
                "safeUserMessage": f"Resolution error: {e}",
                "debugDetails": traceback.format_exc()
            }
        })

def handle_cancel(req_id):
    sys.stderr.write(f"[DesktopResolverWorker] Request cancelled: {req_id}\n")
    sys.stderr.flush()
    send_frame({"type": "cancel_ack", "id": req_id})

def main():
    # Initial protocol handshake with PID
    send_frame({"type": "ready", "pid": os.getpid()})

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception as e:
            sys.stderr.write(f"[DesktopResolverWorker] Invalid JSON on stdin: {e}\n")
            sys.stderr.flush()
            continue

        msg_type = msg.get("type")
        req_id = msg.get("id")
        req_data = msg.get("request", {})

        if msg_type == "health":
            handle_health()
        elif msg_type == "inspect":
            handle_inspect(req_id, req_data)
        elif msg_type == "resolve":
            handle_resolve(req_id, req_data)
        elif msg_type == "cancel":
            handle_cancel(req_id)
        elif msg_type == "shutdown":
            send_frame({"type": "shutdown_ack"})
            sys.exit(0)
        else:
            sys.stderr.write(f"[DesktopResolverWorker] Unknown message type: {msg_type}\n")
            sys.stderr.flush()

if __name__ == "__main__":
    main()
