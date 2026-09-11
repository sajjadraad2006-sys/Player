"""
Build script to curate a minimal, isolated, self-contained Windows CPython 3.12 + yt-dlp runtime.
Zero system Python requirement for end users.
"""

import os
import sys
import shutil
import zipfile
import hashlib
import json

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def create_runtime():
    python_root = os.path.dirname(sys.executable)
    print(f"Using host Python from: {python_root}")
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    staging_dir = os.path.join(project_root, "build", "resolver-runtime-staging")
    output_res_dir = os.path.join(project_root, "src", "main", "resources", "resolver")
    os.makedirs(staging_dir, exist_ok=True)
    os.makedirs(output_res_dir, exist_ok=True)
    
    # 1. Core Executables and DLLs
    core_files = [
        "python.exe",
        "python3.dll",
        "python312.dll",
        "vcruntime140.dll",
        "vcruntime140_1.dll",
    ]
    for cf in core_files:
        src = os.path.join(python_root, cf)
        if os.path.exists(src):
            shutil.copy2(src, staging_dir)
            print(f"Copied core binary: {cf}")
        else:
            print(f"Warning: {cf} not found in {python_root}")
            
    # 2. C-Extension DLLs and OpenSSL libraries
    dlls_dir = os.path.join(staging_dir, "DLLs")
    os.makedirs(dlls_dir, exist_ok=True)
    src_dlls = os.path.join(python_root, "DLLs")
    target_pyds = [
        "_socket.pyd",
        "_ssl.pyd",
        "_hashlib.pyd",
        "select.pyd",
        "unicodedata.pyd",
        "_asyncio.pyd",
        "_ctypes.pyd",
        "_queue.pyd",
        "_overlapped.pyd",
        "_bz2.pyd",
        "_lzma.pyd",
        "libcrypto-3.dll",
        "libssl-3.dll",
        "libffi-8.dll",
    ]
    for pyd in target_pyds:
        src = os.path.join(src_dlls, pyd)
        if os.path.exists(src):
            shutil.copy2(src, dlls_dir)
            # Also copy crypto DLLs to staging root for reliable loader resolution
            if pyd.endswith(".dll"):
                shutil.copy2(src, staging_dir)
            print(f"Copied DLL: {pyd}")

    # 3. Standard Library Zip
    lib_dir = os.path.join(python_root, "Lib")
    zip_path = os.path.join(staging_dir, "python312.zip")
    print("Packaging standard library into python312.zip...")
    
    # Excluded directories to keep runtime minimal
    excluded_dirs = {
        "test", "tests", "idlelib", "tkinter", "tcl8", "tcl8.6",
        "turtle", "turtledemo", "distutils", "ensurepip", "venv",
        "pydoc_data", "site-packages", "__pycache__"
    }
    
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(lib_dir):
            dirs[:] = [d for d in dirs if d.lower() not in excluded_dirs]
            rel_dir = os.path.relpath(root, lib_dir)
            if rel_dir.startswith("site-packages") or rel_dir.startswith("test"):
                continue
            for file in files:
                if file.endswith((".py", ".pyc")):
                    full_path = os.path.join(root, file)
                    arcname = os.path.relpath(full_path, lib_dir)
                    zf.write(full_path, arcname)
    print(f"Created python312.zip ({os.path.getsize(zip_path) // 1024} KB)")

    # 4. yt-dlp Package
    packages_dir = os.path.join(staging_dir, "packages")
    os.makedirs(packages_dir, exist_ok=True)
    
    yt_dlp_src = os.path.join(python_root, "Lib", "site-packages", "yt_dlp")
    yt_dlp_dst = os.path.join(packages_dir, "yt_dlp")
    if os.path.exists(yt_dlp_dst):
        shutil.rmtree(yt_dlp_dst)
    shutil.copytree(
        yt_dlp_src,
        yt_dlp_dst,
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "__pyinstaller")
    )
    print("Copied yt_dlp package to packages/")

    # 5. Isolation Config (python312._pth)
    pth_content = "python312.zip\n.\nDLLs\npackages\n"
    pth_file = os.path.join(staging_dir, "python312._pth")
    with open(pth_file, "w", encoding="utf-8") as f:
        f.write(pth_content)
    print("Created isolated python312._pth")

    # 6. Test isolated runtime execution
    isolated_python = os.path.join(staging_dir, "python.exe")
    import subprocess
    test_proc = subprocess.run(
        [isolated_python, "-c", "import sys, json, urllib.request, yt_dlp; print('RESOLVER_TEST_OK', yt_dlp.version.__version__)"],
        capture_output=True,
        text=True,
        cwd=staging_dir,
    )
    print(f"Isolated runtime test: {test_proc.stdout.strip()} (returncode={test_proc.returncode})")
    if test_proc.returncode != 0:
        print(f"Test error: {test_proc.stderr}")
        raise RuntimeError("Isolated runtime verification failed!")

    # 7. Generate Manifest
    manifest = {
        "runtimeVersion": "3.12.10",
        "ytDlpVersion": "2026.7.4",
        "protocolVersion": 1,
        "files": {},
    }
    for root, _, files in os.walk(staging_dir):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, staging_dir).replace("\\", "/")
            manifest["files"][rel] = sha256_file(full)
            
    manifest_path = os.path.join(staging_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print("Generated manifest.json")

    # 8. Create compressed bundle zip for desktopApp resources
    zip_bundle = os.path.join(output_res_dir, "resolver-runtime-win-x64.zip")
    with zipfile.ZipFile(zip_bundle, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(staging_dir):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, staging_dir)
                zf.write(full, rel)
    print(f"Bundle successfully created: {zip_bundle} ({os.path.getsize(zip_bundle) // 1024} KB)")

if __name__ == "__main__":
    create_runtime()
