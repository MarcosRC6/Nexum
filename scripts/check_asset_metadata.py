#!/usr/bin/env python3
"""Check tracked image bytes for sensitive identifying metadata.

Read-only, no external dependencies. Reports path and class of finding,
never extracted values. This is a privacy preflight, not an asset-license audit.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
EXTS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
PATTERNS = {
    "personal e-mail address": re.compile(
        rb"[a-zA-Z0-9_.+-]+@(?:gmail|hotmail|outlook|yahoo|protonmail|icloud)\.[a-zA-Z]{2,}", re.I),
    "Windows user profile": re.compile(rb"[a-zA-Z]:\\Users\\[^\\\s]{2,}", re.I),
    "macOS user profile": re.compile(rb"/Users/[a-zA-Z0-9_.-]+/", re.I),
    "GitHub access token": re.compile(rb"(?:gh[pousr]_[a-zA-Z0-9_]{25,}|github_pat_[a-zA-Z0-9_]{30,})"),
    "private key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GPS location field": re.compile(rb"(?:GPSLatitude|GPSLongitude|GPSCoordinates)", re.I),
}

def png_metadata(data: bytes):
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return []
    values = []
    off = 8
    while off + 12 <= len(data):
        size = int.from_bytes(data[off:off + 4], "big")
        kind = data[off + 4:off + 8]
        start, end = off + 8, off + 8 + size
        if end + 4 > len(data):
            break
        raw = data[start:end]
        if kind in (b"tEXt", b"iTXt", b"eXIf"):
            values.append(raw[:1_000_000])
        if kind == b"zTXt":
            sep = raw.find(b"\0")
            if sep >= 0 and raw[sep + 1:sep + 2] == b"\0":
                try:
                    dec = zlib.decompressobj()
                    expanded = dec.decompress(raw[sep + 2:], 1_000_000)
                    values.append(expanded)
                except zlib.error:
                    pass
        off = end + 4
        if kind == b"IEND":
            break
    return values

def main():
    files = [x.decode("utf-8", "replace") for x in
             subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0") if x]
    count = 0
    problems = []
    for rel in files:
        if pathlib.Path(rel).suffix.lower() not in EXTS:
            continue
        count += 1
        data = (ROOT / rel).read_bytes()
        samples = [data, *png_metadata(data)]
        for label, expression in PATTERNS.items():
            if any(expression.search(part) for part in samples):
                problems.append((rel, label))
    print("NEXUM asset metadata preflight: %d tracked images examined" % count)
    for path, label in problems:
        print("BLOCK:", path, "-", label, "(value withheld)")
    if problems:
        print("FAIL: review private metadata before making repository public")
        return 1
    print("PASS: no targeted private metadata patterns found in tracked images.")
    print("LIMIT: content ownership and rights, invisible steganography and non-text EXIF GPS require separate review.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
