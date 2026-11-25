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
    ".kts",
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


def _is_kotlin_ident_start(ch: str) -> bool:
    return ch == "_" or ch.isalpha()


def _is_kotlin_ident_part(ch: str) -> bool:
    return ch == "_" or ch.isalnum()


def strip_kotlin_comments_and_strings(text: str) -> str:
    """Erase Kotlin comments and string/char bodies while preserving newlines and $-interpolations."""
    out: list[str] = []
    n = len(text)
    i = 0
    stack: list[list[object]] = [["CODE", 0]]

    while i < n:
        mode = stack[-1][0]
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        nxt2 = text[i + 2] if i + 2 < n else ""

        if mode in ("CODE", "INTERP_CODE"):
            if ch == "/" and nxt == "/":
                out.append("  ")
                i += 2
                stack.append(["LINE_COMMENT", 0])
            elif ch == "/" and nxt == "*":
                out.append("  ")
                i += 2
                stack.append(["BLOCK_COMMENT", 1])
            elif ch == '"' and nxt == '"' and nxt2 == '"':
                out.append("   ")
                i += 3
                stack.append(["RAW_STRING", 0])
            elif ch == '"':
                out.append(" ")
                i += 1
                stack.append(["STRING", 0])
            elif ch == "'":
                out.append(" ")
                i += 1
                stack.append(["CHAR", 0])
            elif mode == "INTERP_CODE" and ch == "{":
                stack[-1][1] = int(stack[-1][1]) + 1
                out.append("{")
                i += 1
            elif mode == "INTERP_CODE" and ch == "}":
                depth = int(stack[-1][1]) - 1
                stack[-1][1] = depth
                if depth <= 0:
                    stack.pop()
                    out.append(" ")
                else:
                    out.append("}")
                i += 1
            else:
                out.append(ch)
                i += 1

        elif mode == "LINE_COMMENT":
            if ch == "\n":
                out.append("\n")
                stack.pop()
            else:
                out.append(" ")
            i += 1

        elif mode == "BLOCK_COMMENT":
            if ch == "/" and nxt == "*":
                out.append("  ")
                stack[-1][1] = int(stack[-1][1]) + 1
                i += 2
            elif ch == "*" and nxt == "/":
                out.append("  ")
                depth = int(stack[-1][1]) - 1
                stack[-1][1] = depth
                i += 2
                if depth <= 0:
                    stack.pop()
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1

        elif mode == "STRING":
            if ch == "\\" and i + 1 < n:
                out.append(" ")
                out.append("\n" if nxt == "\n" else " ")
                i += 2
            elif ch == '"':
                out.append(" ")
                i += 1
                stack.pop()
            elif ch == "\n":
                out.append("\n")
                i += 1
                stack.pop()
            elif ch == "$" and nxt == "{":
                out.append("  ")
                i += 2
                stack.append(["INTERP_CODE", 1])
            elif ch == "$" and _is_kotlin_ident_start(nxt):
                out.append(" ")
                i += 1
                while i < n and _is_kotlin_ident_part(text[i]):
                    out.append(text[i])
                    i += 1
            else:
                out.append(" ")
                i += 1

        elif mode == "RAW_STRING":
            if ch == '"' and nxt == '"' and nxt2 == '"':
                if i + 3 < n and text[i + 3] == '"':
                    out.append(" ")
                    i += 1
                else:
                    out.append("   ")
                    i += 3
                    stack.pop()
            elif ch == "$" and nxt == "{":
                out.append("  ")
                i += 2
                stack.append(["INTERP_CODE", 1])
            elif ch == "$" and _is_kotlin_ident_start(nxt):
                out.append(" ")
                i += 1
                while i < n and _is_kotlin_ident_part(text[i]):
                    out.append(text[i])
                    i += 1
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1

        elif mode == "CHAR":
            if ch == "\\" and i + 1 < n:
                out.append(" ")
                out.append("\n" if nxt == "\n" else " ")
                i += 2
            elif ch == "'":
                out.append(" ")
                i += 1
                stack.pop()
            elif ch == "\n":
                out.append("\n")
                i += 1
                stack.pop()
            else:
                out.append(" ")
                i += 1

    return "".join(out)


def strip_cpp_comments_and_strings(text: str) -> str:
    """Erase C/C++ comments and string/char literals while preserving newlines."""
    out: list[str] = []
    n = len(text)
    i = 0
    state = "CODE"

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if state == "CODE":
            if ch == "/" and nxt == "/":
                out.append("  ")
                i += 2
                state = "LINE_COMMENT"
            elif ch == "/" and nxt == "*":
                out.append("  ")
                i += 2
                state = "BLOCK_COMMENT"
            elif ch == '"':
                out.append(" ")
                i += 1
                state = "STRING"
            elif ch == "'":
                out.append(" ")
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
            if ch == "*" and nxt == "/":
                out.append("  ")
                i += 2
                state = "CODE"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
        elif state == "STRING":
            if ch == "\\" and i + 1 < n:
                out.append(" ")
                out.append("\n" if nxt == "\n" else " ")
                i += 2
            elif ch == '"':
                out.append(" ")
                i += 1
                state = "CODE"
            elif ch == "\n":
                out.append("\n")
                i += 1
                state = "CODE"
            else:
                out.append(" ")
                i += 1
        elif state == "CHAR":
            if ch == "\\" and i + 1 < n:
                out.append(" ")
                out.append("\n" if nxt == "\n" else " ")
                i += 2
            elif ch == "'":
                out.append(" ")
                i += 1
                state = "CODE"
            elif ch == "\n":
                out.append("\n")
                i += 1
                state = "CODE"
            else:
                out.append(" ")
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
            stripped_kt = strip_kotlin_comments_and_strings(text)

            # Check 3a: Unidirectional Bluetooth on Android side (supports multiline calls)
            if rel.startswith("android/app/src/"):
                kotlin_bt_files += 1
                for match in BT_WRITE_PATTERN.finditer(stripped_kt):
                    line_no = stripped_kt.count("\n", 0, match.start()) + 1
                    snippet = " ".join(match.group(0).split())
                    errors.append(
                        f"{rel}:{line_no}: forbidden Bluetooth write/output reference '{snippet}'"
                    )

            # Check 4: Clean Architecture domain purity
            if rel.startswith("android/app/src/main/java/com/project/argus/domain/"):
                domain_files += 1
                for line_no, code_line in enumerate(stripped_kt.splitlines(), start=1):
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
        stripped_ino = strip_cpp_comments_and_strings(decoded_files[firmware_rel])
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
