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
    subprocess.run(
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
    )

    copied = 0
    manifest = raw / "manifest.json"
    if manifest.exists():
        for attachment in json.loads(manifest.read_text()).get("attachments", []):
            exported = attachment.get("exportedFileName")
            if not exported:
                continue
            source = raw / exported
            if not source.exists():
                continue
            label = attachment.get("suggestedHumanReadableName") or source.stem
            slug = re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-")
            destination = out / f"{slug or source.stem}.png"
            shutil.copyfile(source, destination)
            print(destination.name)
            copied += 1

    if copied == 0:
        # Fall back to every image in the export when there is no manifest.
        for source in sorted(raw.glob("*.png")):
            shutil.copyfile(source, out / source.name)
            print(source.name)
            copied += 1

    shutil.rmtree(raw, ignore_errors=True)
    print(f"Exported {copied} screenshots to {out}")
    return 0 if copied else 1


if __name__ == "__main__":
    raise SystemExit(main())
