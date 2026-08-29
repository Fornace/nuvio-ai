#!/usr/bin/env python3
"""Validate one AI media research/build artifact and repository policy."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


def split_csv(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def fail(message: str, failures: list[str]) -> None:
    failures.append(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--artifact", required=True)
    parser.add_argument("--required-headings", default="")
    parser.add_argument("--required-text", default="")
    parser.add_argument("--build-command", default="")
    parser.add_argument("--scan-paths", default="app/src,docs/ai-media-research")
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    artifact = Path(args.artifact)
    if not artifact.is_absolute():
        artifact = repo / artifact
    failures: list[str] = []

    if not artifact.is_file() or artifact.stat().st_size < 100:
        fail(f"artifact missing or too small: {artifact}", failures)
    else:
        text = artifact.read_text(encoding="utf-8", errors="replace")
        for heading in split_csv(args.required_headings):
            if not re.search(rf"(?im)^#+\s+.*{re.escape(heading)}", text):
                fail(f"required heading absent: {heading}", failures)
        for snippet in split_csv(args.required_text):
            if snippet not in text:
                fail(f"required text absent: {snippet}", failures)
        if any(ord(character) in {0x2013, 0x2014} for character in text):
            fail("artifact contains prohibited long dash characters", failures)

    for rel in split_csv(args.scan_paths):
        root = repo / rel
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in {".kt", ".java", ".py", ".ts", ".js", ".md", ".json"}:
                continue
            if path.suffix.lower() in {".kt", ".java", ".py", ".ts", ".js"}:
                lines = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
                if lines > 400:
                    # Existing oversized files are permitted only when unchanged.
                    changed = subprocess.run(
                        ["git", "status", "--porcelain", "--", str(path.relative_to(repo))],
                        cwd=repo,
                        capture_output=True,
                        text=True,
                    ).stdout.strip()
                    if changed:
                        fail(f"modified source exceeds 400 lines: {path.relative_to(repo)} ({lines})", failures)

    secret_patterns = [
        re.compile(r"\bsk-[A-Za-z0-9_-]{16,}\b"),
        re.compile(r"(?i)\b(?:api[_-]?key|authorization)\s*[:=]\s*['\"]?[A-Za-z0-9._-]{16,}"),
        re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._-]{16,}"),
    ]
    for rel in split_csv(args.scan_paths):
        root = repo / rel
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.stat().st_size > 2_000_000:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            for pattern in secret_patterns:
                if pattern.search(text):
                    fail(f"possible plaintext credential in {path.relative_to(repo)}", failures)
                    break

    for receipt in (repo / "docs/ai-media-research/benchmarks").glob("*.json"):
        try:
            json.loads(receipt.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid benchmark JSON {receipt.relative_to(repo)}: {exc}", failures)

    if args.build_command:
        print(f"running: {args.build_command}")
        proc = subprocess.run(args.build_command, cwd=repo, shell=True, capture_output=True, text=True, timeout=3600)
        print(proc.stdout[-4000:])
        print(proc.stderr[-2000:])
        if proc.returncode:
            fail(f"build command failed with exit {proc.returncode}", failures)

    if failures:
        print("FAIL:")
        for message in failures:
            print(f" - {message}")
        return 1
    print("PASS: artifact structure, style, source size, secret scan, receipts and configured build check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
