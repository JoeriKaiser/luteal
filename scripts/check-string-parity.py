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


TRANSLATED_LOCALES = ("fr", "es")


def check_locale(en_names: set[str], en_sigs: dict[str, list[str]], locale: str, path: Path) -> int:
    folder = f"values-{locale}/"
    names, sigs = parse_resources(path)

    missing = sorted(en_names - names)
    extra = sorted(names - en_names)

    sig_mismatches = []
    for name, expected in en_sigs.items():
        actual = sigs.get(name, [])
        if expected != actual:
            sig_mismatches.append(f"  {name}: values/ has {expected}, but {folder} has {actual}")

    for name, actual in sigs.items():
        if name not in en_sigs:
            sig_mismatches.append(f"  {name}: {folder} has unexpected format specifiers {actual}")

    errors = 0
    if missing:
        errors += len(missing)
        print(f"Missing in {folder}strings.xml ({len(missing)}):", file=sys.stderr)
        for name in missing:
            print(f"  {name}", file=sys.stderr)

    if extra:
        errors += len(extra)
        print(f"Missing in values/strings.xml, present in {folder} ({len(extra)}):", file=sys.stderr)
        for name in extra:
            print(f"  {name}", file=sys.stderr)

    if sig_mismatches:
        errors += len(sig_mismatches)
        print(f"Format specifier mismatches in {folder} ({len(sig_mismatches)}):", file=sys.stderr)
        for mismatch in sig_mismatches:
            print(mismatch, file=sys.stderr)

    return errors


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    res = root / "app" / "src" / "main" / "res"
    en_names, en_sigs = parse_resources(res / "values" / "strings.xml")

    errors = 0
    for locale in TRANSLATED_LOCALES:
        errors += check_locale(en_names, en_sigs, locale, res / f"values-{locale}" / "strings.xml")

    if errors == 0:
        locales = ", ".join(f"values-{locale}/" for locale in TRANSLATED_LOCALES)
        print(f"OK: {len(en_names)} string resources in full parity across values/ and {locales}.")
        return 0

    return 1


if __name__ == "__main__":
    sys.exit(main())
