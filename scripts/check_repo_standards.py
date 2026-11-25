#!/usr/bin/env python3
"""Repository standards and architectural contracts checker for Argus."""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Sequence

ALLOWED_EXTENSIONS = {
    ".md",
    ".kt",
    ".ino",
    ".cpp",
    ".h",
    ".xml",
    ".yml",
    ".yaml",
    ".ps1",
    ".py",
}

SKIP_DIRS = {
    ".git",
    "build",
    ".gradle",
    ".idea",
    "__pycache__",
}

FORBIDDEN_TYPOGRAPHY = {
    "\u0451": "U+0451 (Cyrillic small io)",
    "\u0401": "U+0401 (Cyrillic capital IO)",
    "\u2014": "U+2014 (em dash)",
    "\u2013": "U+2013 (en dash)",
    "\u00ab": "U+00AB (left guillemet)",
    "\u00bb": "U+00BB (right guillemet)",
    "\u201c": "U+201C (left double curly quote)",
    "\u201d": "U+201D (right double curly quote)",
    "\u2018": "U+2018 (left single curly quote)",
    "\u2019": "U+2019 (right single curly quote)",
}

BT_WRITE_PATTERN = re.compile(
    r"\b(?:outputStream|getOutputStream)\b|\.\s*(?:write|send)\s*\("
)
BT_STOP_LISTENING_PATTERN = re.compile(
    r"\bbtSerial\s*\.\s*stopListening\s*\(\s*\)\s*;"
)
DOMAIN_ANDROID_IMPORT_PATTERN = re.compile(
    r"^\s*import\s+android(?:x)?\."
)


def strip_comments_and_strings(
    text: str,
    allow_nested_block: bool,
    allow_raw_strings: bool,
    erase_strings: bool,
) -> str:
    """Strip comments (and optionally string/char literals) while preserving newlines."""
    out: list[str] = []
    n = len(text)
    i = 0
    state = "CODE"
    block_depth = 0

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        nxt2 = text[i + 2] if i + 2 < n else ""

        if state == "CODE":
            if ch == "/" and nxt == "/":
                out.append("  ")
                i += 2
                state = "LINE_COMMENT"
            elif ch == "/" and nxt == "*":
                out.append("  ")
                i += 2
                state = "BLOCK_COMMENT"
                block_depth = 1
            elif allow_raw_strings and ch == '"' and nxt == '"' and nxt2 == '"':
                out.append("   " if erase_strings else '"""')
                i += 3
                state = "RAW_STRING"
            elif ch == '"':
                out.append(" " if erase_strings else '"')
                i += 1
                state = "STRING"
            elif ch == "'":
                out.append(" " if erase_strings else "'")
                i += 1
                state = "CHAR"
            else:
                out.append(ch)
                i += 1
        elif state == "LINE_COMMENT":
            if ch == "\n":
                out.append("\n")
                state = "CODE"
            else:
                out.append(" ")
            i += 1
        elif state == "BLOCK_COMMENT":
            if allow_nested_block and ch == "/" and nxt == "*":
                out.append("  ")
                block_depth += 1
                i += 2
            elif ch == "*" and nxt == "/":
                out.append("  ")
                block_depth -= 1
                i += 2
                if block_depth <= 0:
                    state = "CODE"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
        elif state == "RAW_STRING":
            if ch == '"' and nxt == '"' and nxt2 == '"':
                out.append("   " if erase_strings else '"""')
                i += 3
                state = "CODE"
            else:
                if erase_strings:
                    out.append("\n" if ch == "\n" else " ")
                else:
                    out.append(ch)
                i += 1
        elif state == "STRING":
            if ch == "\\" and i + 1 < n:
                if erase_strings:
                    out.append(" ")
                    out.append("\n" if nxt == "\n" else " ")
                else:
                    out.append(ch)
                    out.append(nxt)
                i += 2
            elif ch == '"':
                out.append(" " if erase_strings else '"')
                i += 1
                state = "CODE"
            elif ch == "\n":
                out.append("\n")
                i += 1
                state = "CODE"
            else:
                out.append(" " if erase_strings else ch)
                i += 1
        elif state == "CHAR":
            if ch == "\\" and i + 1 < n:
                if erase_strings:
                    out.append(" ")
                    out.append("\n" if nxt == "\n" else " ")
                else:
                    out.append(ch)
                    out.append(nxt)
                i += 2
            elif ch == "'":
                out.append(" " if erase_strings else "'")
                i += 1
                state = "CODE"
            elif ch == "\n":
                out.append("\n")
                i += 1
                state = "CODE"
            else:
                out.append(" " if erase_strings else ch)
                i += 1

    return "".join(out)


def collect_target_files(root: Path) -> list[Path]:
    collected: list[Path] = []
    for current_root, dirs, files in os.walk(root):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
        for fname in sorted(files):
            fpath = Path(current_root) / fname
            if fpath.suffix.lower() in ALLOWED_EXTENSIONS:
                collected.append(fpath)
    collected.sort(key=lambda p: p.relative_to(root).as_posix())
    return collected


def run_checks(root: Path) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    files = collect_target_files(root)
    decoded_files: dict[str, str] = {}

    kotlin_bt_files = 0
    domain_files = 0

    for fpath in files:
        rel = fpath.relative_to(root).as_posix()
        raw = fpath.read_bytes()

        # Check 1: strict UTF-8 and 0 U+FFFD
        try:
            text = raw.decode("utf-8", errors="strict")
        except UnicodeDecodeError as exc:
            line_no = raw[: exc.start].count(b"\n") + 1
            errors.append(f"{rel}:{line_no}: invalid UTF-8 byte sequence ({exc.reason})")
            continue

        decoded_files[rel] = text
        lines = text.splitlines()

        for line_no, line in enumerate(lines, start=1):
            if "\ufffd" in line:
                errors.append(f"{rel}:{line_no}: replacement character U+FFFD found")

            # Check 2: Typography (Rule 0)
            seen_on_line: set[str] = set()
            for ch in line:
                if ch in FORBIDDEN_TYPOGRAPHY and ch not in seen_on_line:
                    seen_on_line.add(ch)
                    errors.append(
                        f"{rel}:{line_no}: forbidden typography character {FORBIDDEN_TYPOGRAPHY[ch]}"
                    )

        # Kotlin-specific architectural checks
        if fpath.suffix.lower() == ".kt":
            stripped_kt = strip_comments_and_strings(
                text,
                allow_nested_block=True,
                allow_raw_strings=True,
                erase_strings=False,
            )
            stripped_lines = stripped_kt.splitlines()

            # Check 3a: Unidirectional Bluetooth on Android side
            if rel.startswith("android/app/src/"):
                kotlin_bt_files += 1
                for line_no, code_line in enumerate(stripped_lines, start=1):
                    match = BT_WRITE_PATTERN.search(code_line)
                    if match:
                        errors.append(
                            f"{rel}:{line_no}: forbidden Bluetooth write/output reference '{match.group(0).strip()}'"
                        )

            # Check 4: Clean Architecture domain purity
            if rel.startswith("android/app/src/main/java/com/project/argus/domain/"):
                domain_files += 1
                for line_no, code_line in enumerate(stripped_lines, start=1):
                    if DOMAIN_ANDROID_IMPORT_PATTERN.match(code_line):
                        errors.append(
                            f"{rel}:{line_no}: forbidden Android framework import in domain layer ({code_line.strip()})"
                        )

    # Check 3b: Firmware must keep btSerial.stopListening(); active outside comments
    firmware_rel = "firmware/argus_cane/argus_cane.ino"
    firmware_path = root / "firmware" / "argus_cane" / "argus_cane.ino"
    if not firmware_path.is_file():
        errors.append(
            f"{firmware_rel}:1: missing required firmware file for unidirectional Bluetooth check"
        )
    elif firmware_rel in decoded_files:
        stripped_ino = strip_comments_and_strings(
            decoded_files[firmware_rel],
            allow_nested_block=False,
            allow_raw_strings=False,
            erase_strings=True,
        )
        if not BT_STOP_LISTENING_PATTERN.search(stripped_ino):
            errors.append(
                f"{firmware_rel}:1: missing active 'btSerial.stopListening();' call outside comments"
            )

    stats = {
        "scanned_files": len(files),
        "kotlin_files": kotlin_bt_files,
        "domain_files": domain_files,
    }
    return errors, stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify Argus repository UTF-8, typography, Bluetooth, and Clean Architecture contracts."
    )
    default_root = Path(__file__).resolve().parent.parent
    parser.add_argument(
        "--root",
        type=Path,
        default=default_root,
        help="Repository root directory to inspect (defaults to argus/).",
    )
    args = parser.parse_args(argv)
    root = args.root.resolve()

    if not root.is_dir():
        print(f"{root}:1: root directory does not exist", file=sys.stderr)
        return 1

    errors, stats = run_checks(root)
    if errors:
        for err in errors:
            print(err)
        print(f"FAILED: {len(errors)} violation(s) detected across {stats['scanned_files']} file(s).")
        return 1

    print(f"Argus repository standards verified ({stats['scanned_files']} files scanned):")
    print("  [PASS] Check 1 - UTF-8 decoding & zero U+FFFD")
    print("  [PASS] Check 2 - Typography Rule 0 (ASCII hyphen, plain 'e', straight quotes)")
    print(
        f"  [PASS] Check 3 - Unidirectional Bluetooth ({stats['kotlin_files']} Kotlin files read-only, firmware stopListening active)"
    )
    print(
        f"  [PASS] Check 4 - Clean Architecture domain purity ({stats['domain_files']} domain files free of android/androidx imports)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
