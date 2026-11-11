#!/usr/bin/env python3
"""Copy XCTest screenshot attachments out of an .xcresult with readable names.

    ./export-xcresult-screenshots.py island.xcresult island-shots
"""
import json
import pathlib
import re
import shutil
import subprocess
import sys

IMAGE_SUFFIXES = (".png", ".jpg", ".jpeg")
UUID_SUFFIX = re.compile(r"_\d+_[0-9A-Fa-f-]{36}(\.(png|jpe?g))?$")
UUID_ONLY = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
NAME_KEYS = (
    "suggestedHumanReadableName",
    "suggested_human_readable_name",
    "suggestedName",
    "suggested_name",
    "name",
    "title",
)
FILE_KEYS = (
    "exportedFileName",
    "exported_file_name",
    "filename",
    "fileName",
    "file_name",
    "path",
)


def readable_label(raw: str) -> str:
    """Turn an XCTest suggested name into a stable, lowercase slug."""
    label = UUID_SUFFIX.sub("", pathlib.Path(raw).stem)
    return re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-")


def attachment_label(entry: dict) -> str | None:
    for key in NAME_KEYS:
        value = entry.get(key)
        if isinstance(value, str) and value.strip():
            label = readable_label(value)
            if label and not UUID_ONLY.match(label):
                return label
    return None


def attachment_file(entry: dict, raw: pathlib.Path) -> pathlib.Path | None:
    for key in FILE_KEYS:
        value = entry.get(key)
        if isinstance(value, str):
            candidate = raw / pathlib.Path(value).name
            if candidate.exists():
                return candidate
    # Unknown schema: accept any value that names an exported image.
    for value in entry.values():
        if isinstance(value, str) and value.lower().endswith(IMAGE_SUFFIXES):
            candidate = raw / pathlib.Path(value).name
            if candidate.exists():
                return candidate
    return None


def walk_dicts(node):
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from walk_dicts(value)
    elif isinstance(node, list):
        for value in node:
            yield from walk_dicts(value)


def parse_export_output(text: str) -> dict[str, str]:
    """Map exported file names to the suggested names printed by xcresulttool."""
    names: dict[str, str] = {}
    pattern = re.compile(r'File:\s*(\S+?\.(?:png|jpe?g)),\s*suggested name:\s*"([^"]+)"', re.I)
    for exported, suggested in pattern.findall(text):
        names[exported] = suggested
    return names


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    result = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2])
    if not result.exists():
        print(f"No result bundle at {result}", file=sys.stderr)
        return 1

    out.mkdir(parents=True, exist_ok=True)
    raw = out / "_raw"
    if raw.exists():
        shutil.rmtree(raw)

    exported = subprocess.run(
        [
            "xcrun",
            "xcresulttool",
            "export",
            "attachments",
            "--path",
            str(result),
            "--output-path",
            str(raw),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    if exported.stdout:
        print(exported.stdout, end="")
    printed_names = parse_export_output((exported.stdout or "") + "\n" + (exported.stderr or ""))

    copies: dict[pathlib.Path, str] = {}
    manifest = raw / "manifest.json"
    if manifest.exists():
        data = json.loads(manifest.read_text())
        for entry in walk_dicts(data):
            source = attachment_file(entry, raw)
            if source is None:
                continue
            label = attachment_label(entry)
            if label:
                copies.setdefault(source, label)

    # Anything the manifest missed can still be named from the tool's output.
    for source in sorted(raw.glob("*.png")):
        if source in copies:
            continue
        suggested = printed_names.get(source.name)
        copies[source] = readable_label(suggested) if suggested else ""

    copied = 0
    for source, label in copies.items():
        destination = out / f"{label or source.stem}.png"
        shutil.copyfile(source, destination)
        print(destination.name)
        copied += 1

    shutil.rmtree(raw, ignore_errors=True)
    print(f"Exported {copied} screenshots to {out}")
    return 0 if copied else 1


if __name__ == "__main__":
    raise SystemExit(main())
