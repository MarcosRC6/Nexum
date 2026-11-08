#!/usr/bin/env python3
"""Reject accidental inclusion of private credentials in tracked NEXUM files.

This is a defense in depth check, not a guarantee that every secret is detected.
Print paths and rule identifiers only: never write detected secret values to CI logs.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PRIVATE_FILE_NAMES = {
    ".env", ".npmrc", ".pypirc", "local.properties",
    "google-services.json", "service-account.json",
    "credentials.json", "secrets.json", "id_rsa", "id_ed25519",
}
PRIVATE_SUFFIXES = {
    ".p12", ".pfx", ".jks", ".keystore", ".pem", ".key",
    ".asc", ".gpg", ".kdbx", ".sqlite", ".sqlite3", ".db",
    ".bak", ".7z", ".rar", ".zip", ".apk",
}
ALLOWED_BINARY_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".jar"}
PATTERNS = (
    ("PEM_PRIVATE_KEY", re.compile(r"-----BEGIN (?:RSA |DSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    ("GITHUB_TOKEN", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{25,}|github_pat_[A-Za-z0-9_]{30,})\b")),
    ("AWS_ACCESS_KEY_ID", re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("GOOGLE_API_KEY", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("SLACK_TOKEN", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{15,}\b")),
    ("STRIPE_LIVE_KEY", re.compile(r"\b(?:sk_live|rk_live)_[A-Za-z0-9]{16,}\b")),
    ("URL_WITH_PASSWORD", re.compile(r"https?://[A-Za-z0-9._-]+:[^\s@/]+@", re.I)),
    (
        "HARDCODED_CREDENTIAL",
        re.compile(
            r"""(?i)\b(?:password|passwd|client_secret|api[_-]?key|
            access[_-]?token|auth[_-]?token|keystore[_-]?password)
            \b\s*[:=]\s*["'][^"'$\s]{8,}["']""",
            re.X,
        ),
    ),
)

def tracked_paths() -> list[pathlib.Path]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [pathlib.Path(item.decode("utf-8", errors="replace"))
            for item in raw.split(b"\0") if item]

def examine(rel: pathlib.Path) -> list[tuple[str, int]]:
    name = rel.name.lower()
    parts = {piece.lower() for piece in rel.parts}
    if (
        name in PRIVATE_FILE_NAMES
        or name.startswith(".env.")
        or rel.suffix.lower() in PRIVATE_SUFFIXES
        or ".secrets" in parts
    ):
        return [("PRIVATE_FILE_TRACKED", 0)]
    path = ROOT / rel
    if not path.is_file():
        return [("TRACKED_FILE_MISSING", 0)]
    if rel.suffix.lower() in ALLOWED_BINARY_SUFFIXES:
        return []
    data = path.read_bytes()
    if b"\0" in data[:4096]:
        return [("UNEXPECTED_BINARY_FILE", 0)]
    try:
        body = data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return [("UNEXPECTED_BINARY_FILE", 0)]
    findings = []
    for code, expression in PATTERNS:
        match = expression.search(body)
        if match:
            findings.append((code, body.count("\n", 0, match.start()) + 1))
    return findings

def main() -> int:
    failed = 0
    paths = tracked_paths()
    for rel in paths:
        for code, line in examine(rel):
            failed += 1
            print(f"SECURITY_BLOCK {rel.as_posix()}:{line} {code}")
    if failed:
        print(f"Publication preflight FAILED: {failed} issue(s). No secret values printed.")
        return 1
    print(f"Publication preflight PASS: {len(paths)} tracked files inspected.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
