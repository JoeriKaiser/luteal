#!/usr/bin/env python3
"""Validates Fastlane / F-Droid store metadata and release tag consistency."""

from pathlib import Path
import re
import sys

MAX_CHANGELOG_BYTES = 500
MAX_TITLE_CHARS = 50
MAX_SHORT_DESC_CHARS = 80
MAX_FULL_DESC_CHARS = 4000

REQUIRED_CHANGELOG_LOCALES = ("en-US", "fr-FR", "es-ES")
ALL_METADATA_LOCALES = ("en-US", "fr-FR", "es-ES")


def parse_build_gradle(path: Path) -> tuple[int, str]:
    text = path.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        print("Error: could not parse versionCode / versionName from app/build.gradle.kts", file=sys.stderr)
        sys.exit(1)
    return int(code_match.group(1)), name_match.group(1)


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    version_code, version_name = parse_build_gradle(root / "app" / "build.gradle.kts")
    fastlane_dir = root / "fastlane" / "metadata" / "android"

    errors = 0

    # Optional tag verification when invoked from release workflow: check-fastlane-metadata.py --tag v1.10.0
    if len(sys.argv) >= 3 and sys.argv[1] == "--tag":
        raw_tag = sys.argv[2].removeprefix("refs/tags/")
        expected_tag = f"v{version_name}"
        if raw_tag != expected_tag:
            print(
                f"Error: release tag '{raw_tag}' does not match app/build.gradle.kts versionName '{expected_tag}'.",
                file=sys.stderr,
            )
            errors += 1

    for locale in ALL_METADATA_LOCALES:
        locale_dir = fastlane_dir / locale
        if not locale_dir.is_dir():
            print(f"Error: missing fastlane locale directory: {locale_dir}", file=sys.stderr)
            errors += 1
            continue

        title_path = locale_dir / "title.txt"
        short_path = locale_dir / "short_description.txt"
        full_path = locale_dir / "full_description.txt"

        for p, max_chars, label in (
            (title_path, MAX_TITLE_CHARS, "title.txt"),
            (short_path, MAX_SHORT_DESC_CHARS, "short_description.txt"),
            (full_path, MAX_FULL_DESC_CHARS, "full_description.txt"),
        ):
            if not p.is_file():
                print(f"Error: missing {locale}/{label}", file=sys.stderr)
                errors += 1
                continue
            content = p.read_text(encoding="utf-8").strip()
            if not content:
                print(f"Error: {locale}/{label} is empty", file=sys.stderr)
                errors += 1
            if len(content) > max_chars:
                print(
                    f"Error: {locale}/{label} is {len(content)} chars (max {max_chars})",
                    file=sys.stderr,
                )
                errors += 1
            if "\\'" in content or '\\"' in content:
                print(
                    f"Error: {locale}/{label} contains literal backslash-escaped quotes",
                    file=sys.stderr,
                )
                errors += 1

        if short_path.is_file():
            short_text = short_path.read_text(encoding="utf-8").strip()
            if short_text.endswith("."):
                print(
                    f"Error: {locale}/short_description.txt must not end with a trailing period",
                    file=sys.stderr,
                )
                errors += 1

    for locale in REQUIRED_CHANGELOG_LOCALES:
        changelog_path = fastlane_dir / locale / "changelogs" / f"{version_code}.txt"
        if not changelog_path.is_file():
            print(
                f"Error: missing changelog for current versionCode={version_code}: {changelog_path.relative_to(root)}",
                file=sys.stderr,
            )
            errors += 1
            continue

        raw_bytes = changelog_path.read_bytes()
        text = raw_bytes.decode("utf-8").strip()
        if not text:
            print(f"Error: {changelog_path.relative_to(root)} is empty", file=sys.stderr)
            errors += 1
        if len(raw_bytes) > MAX_CHANGELOG_BYTES:
            print(
                f"Error: {changelog_path.relative_to(root)} is {len(raw_bytes)} bytes (F-Droid max is {MAX_CHANGELOG_BYTES} bytes)",
                file=sys.stderr,
            )
            errors += 1
        if "\\'" in text or '\\"' in text:
            print(
                f"Error: {changelog_path.relative_to(root)} contains literal backslash-escaped quotes",
                file=sys.stderr,
            )
            errors += 1

    if errors == 0:
        print(
            f"OK: Fastlane metadata and changelogs/{version_code}.txt (v{version_name}) valid across {', '.join(ALL_METADATA_LOCALES)}."
        )
        return 0

    return 1


if __name__ == "__main__":
    sys.exit(main())
