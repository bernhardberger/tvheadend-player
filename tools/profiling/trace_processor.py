#!/usr/bin/env python3
"""Pinned Perfetto trace_processor_shell for the profiling queries.

Fetches the official prebuilt once into the user cache and checks its SHA-256 against the
manifest of the pinned release, so every query runs on the same version as the recorded
baselines. Without arguments it prints the binary's path; with arguments it runs it:

    python3 tools/profiling/trace_processor.py
    python3 tools/profiling/trace_processor.py run-1.perfetto-trace -Q 'SELECT 1'
"""
import hashlib
import os
from pathlib import Path
import platform
import sys
import tempfile
import urllib.request

VERSION = "v58.2"
# From the release's python/perfetto/prebuilts/manifests/trace_processor_shell.py.
PREBUILTS = {
    ("Linux", "x86_64"): ("linux-amd64", "58042408e6cc861fb1a731c26bb082dc222285561eaa4e12a48a8b2b90dca7b9"),
    ("Linux", "aarch64"): ("linux-arm64", "0e6e0c5452c505c8d46fe472fd196a0d17d963460727e2ce2013b02aa1309555"),
    ("Darwin", "x86_64"): ("mac-amd64", "3927a2767eadd140db3ff4fe0dfbf1bde35c1f56501149cd367f5cee898bef27"),
    ("Darwin", "arm64"): ("mac-arm64", "d29864d1ba3b36855527bb1b0ca3aa7f703cdce338b9680bb922c5c151b358fa"),
}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for block in iter(lambda: file.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def ensure():
    """The pinned binary's path, downloading and verifying it on first use."""
    key = (platform.system(), platform.machine())
    if key not in PREBUILTS:
        raise SystemExit(f"No pinned trace_processor_shell for {key[0]} {key[1]}")
    arch, expected = PREBUILTS[key]
    cache = Path(os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache") / "tvheadend-player" / "perfetto"
    binary = cache / f"trace_processor_shell-{VERSION}-{arch}"
    if binary.exists() and sha256(binary) == expected:
        return binary
    cache.mkdir(parents=True, exist_ok=True)
    url = f"https://commondatastorage.googleapis.com/perfetto-luci-artifacts/{VERSION}/{arch}/trace_processor_shell"
    print(f"Downloading trace_processor_shell {VERSION} ({arch})", file=sys.stderr)
    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as partial:
        with urllib.request.urlopen(url, timeout=120) as response:
            for block in iter(lambda: response.read(1 << 20), b""):
                partial.write(block)
    actual = sha256(partial.name)
    if actual != expected:
        os.unlink(partial.name)
        raise SystemExit(f"trace_processor_shell checksum mismatch: expected {expected}, got {actual}")
    os.chmod(partial.name, 0o755)
    os.replace(partial.name, binary)
    return binary


def main():
    binary = ensure()
    if len(sys.argv) == 1:
        print(binary)
        return
    os.execv(binary, [str(binary), *sys.argv[1:]])


if __name__ == "__main__":
    main()
