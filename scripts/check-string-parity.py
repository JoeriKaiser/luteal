#!/usr/bin/env python3
"""Validates string resource parity and format specifier compatibility between shipped locales."""

from pathlib import Path
import re
import sys

SPEC_RE = re.compile(r"%(\d+)\$[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")
STRING_RE = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)
PLURALS_RE = re.compile(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', re.DOTALL)


def parse_resources(path: Path) -> tuple[set[str], dict[str, list[str]]]:
    if not path.is_file():
        print(f"Error: resource file not found: {path}", file=sys.stderr)
        sys.exit(1)

    text = path.read_text(encoding="utf-8")
    names = set()
    signatures = {}

    for match in STRING_RE.finditer(text):
        name = match.group(1)
        body = match.group(2)
        names.add(name)
        sigs = sorted([f"%{m.group(1)}${m.group(2)}" for m in SPEC_RE.finditer(body)])
        if sigs:
            signatures[name] = sigs

    for match in PLURALS_RE.finditer(text):
        name = match.group(1)
        names.add(name)

    return names, signatures


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    en_file = root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    fr_file = root / "app" / "src" / "main" / "res" / "values-fr" / "strings.xml"

    en_names, en_sigs = parse_resources(en_file)
    fr_names, fr_sigs = parse_resources(fr_file)

    missing_in_fr = sorted(en_names - fr_names)
    missing_in_en = sorted(fr_names - en_names)

    sig_mismatches = []
    for name, expected in en_sigs.items():
        actual = fr_sigs.get(name, [])
        if expected != actual:
            sig_mismatches.append(f"  {name}: values/ has {expected}, but values-fr/ has {actual}")

    for name, actual in fr_sigs.items():
        if name not in en_sigs:
            sig_mismatches.append(f"  {name}: values-fr/ has unexpected format specifiers {actual}")

    errors = 0
    if missing_in_fr:
        errors += len(missing_in_fr)
        print(f"Missing in values-fr/strings.xml ({len(missing_in_fr)}):", file=sys.stderr)
        for name in missing_in_fr:
            print(f"  {name}", file=sys.stderr)

    if missing_in_en:
        errors += len(missing_in_en)
        print(f"Missing in values/strings.xml ({len(missing_in_en)}):", file=sys.stderr)
        for name in missing_in_en:
            print(f"  {name}", file=sys.stderr)

    if sig_mismatches:
        errors += len(sig_mismatches)
        print(f"Format specifier mismatches ({len(sig_mismatches)}):", file=sys.stderr)
        for mismatch in sig_mismatches:
            print(mismatch, file=sys.stderr)

    if errors == 0:
        print(f"OK: {len(en_names)} string resources in full parity across values/ and values-fr/.")
        return 0

    return 1


if __name__ == "__main__":
    sys.exit(main())
