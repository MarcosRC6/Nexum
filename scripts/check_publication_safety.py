#!/usr/bin/env python3
"""Read-only pre-publication check of tracked files and the complete Git history.

No credentials are printed. Exit 1 on any suspected exposure; a clean result is
not proof that binary metadata and every possible secret format are safe.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]

def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True).stdout

def text(data):
    return data.decode("utf-8", errors="replace")

def forbidden_name(path):
    p = path.replace("\\", "/").lower()
    leaf = p.rsplit("/", 1)[-1]
    suffixes = (".p12", ".pfx", ".jks", ".keystore", ".pem", ".key",
                ".sqlite", ".sqlite3", ".db", ".zip", ".apk", ".aab",
                ".hprof", ".jfr")
    if leaf.endswith(suffixes) or leaf == ".env" or leaf.startswith(".env."):
        return True
    if (leaf.startswith("service-account") and leaf.endswith(".json")):
        return True
    if leaf in ("signing.properties", "keystore.properties", "local.properties"):
        return True
    return False

PATTERNS = (
    ("private key header", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----")),
    ("GitHub/cloud credential", re.compile(
        r"(?:gh[pousr]_[A-Za-z0-9_]{25,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|sk_live_[A-Za-z0-9]{16,})")),
    ("embedded credential literal", re.compile(
        r"""(?i)\b(?:password|passwd|client_secret|secret_key|api_key|access_token)\s*[:=]\s*["'][^"'\s]{8,}["']""")),
    ("URL with embedded password", re.compile(r"https?://[^\s/@]+:[^\s/@]+@", re.I)),
    ("personal Windows profile path", re.compile(r"[A-Z]:\\Users\\[^\\\s\"']{2,}", re.I)),
)
SKIP_TEXT_SUFFIX = (".png", ".jpg", ".jpeg", ".webp", ".jar", ".gif", ".ico")

def pattern_hits(body):
    return [label for label, regex in PATTERNS if regex.search(body)]

def main():
    failures = []
    tracked = [text(item) for item in git("ls-files", "-z").split(b"\0") if item]
    scanned = 0
    for path in tracked:
        if forbidden_name(path):
            failures.append(("current forbidden filename", path))
        if path.lower().endswith(SKIP_TEXT_SUFFIX):
            continue
        p = ROOT / path
        if not p.is_file() or p.stat().st_size > 2_000_000:
            failures.append(("unexpected missing/oversize text file", path))
            continue
        scanned += 1
        for kind in pattern_hits(p.read_text(encoding="utf-8", errors="replace")):
            failures.append(("current " + kind, path))
    # Scan all reachable commits and branch/tag refs, not just HEAD.
    names = text(git("log", "--all", "--full-history", "--format=", "--name-only"))
    for name in sorted(set(names.splitlines())):
        if name.strip() and forbidden_name(name):
            failures.append(("history forbidden filename", name.strip()))
    # Inspect historical additions; deleted secrets are still exposed in public history.
    diffs = text(git("log", "--all", "--full-history", "--no-ext-diff",
                     "--format=COMMIT:%H", "-p", "--no-color", "--binary"))
    additions = "\n".join(line[1:] for line in diffs.splitlines()
                          if line.startswith("+") and not line.startswith("+++"))
    for kind in pattern_hits(additions):
        failures.append(("history " + kind, "committed additions (details withheld)"))
    print("NEXUM publication preflight: tracked=%d text_scanned=%d" % (len(tracked), scanned))
    if failures:
        for kind, location in sorted(set(failures)):
            print("BLOCK:", kind, "in", location)
        print("FAIL: remain private until findings are resolved.")
        return 1
    print("PASS: no matching sensitive filenames or textual credentials in Git history.")
    print("LIMIT: binary metadata, GitHub Actions logs and unknown token formats need separate review.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
