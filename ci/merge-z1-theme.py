#!/usr/bin/env python3
"""Merge the user-provided Z1Music Green theme into the legacy Android resources ZIP."""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: merge-z1-theme.py ROCKBOX_ZIP THEME_ZIP")
rockbox_zip = Path(sys.argv[1]).resolve()
theme_zip = Path(sys.argv[2]).resolve()
if not rockbox_zip.is_file() or not theme_zip.is_file():
    raise SystemExit("Rockbox ZIP or theme ZIP does not exist")

wanted = (
    ".rockbox/themes/Z1Music_Green.cfg",
    ".rockbox/wps/Z1Music_Green.wps",
    ".rockbox/wps/Z1Music_Green.sbs",
    ".rockbox/fonts/50-2-lmyy.fnt",
    ".rockbox/fonts/80-2-lmyy.fnt",
    ".rockbox/icons/XperiaZ1icons.bmp",
    ".rockbox/icons/XperiaZ1V.bmp",
)
with zipfile.ZipFile(theme_zip) as source:
    all_names = source.namelist()
    required = list(wanted)
    required += [n for n in all_names if n.startswith(".rockbox/wps/Z1Music_Green/") and not n.endswith("/")]
    missing = [n for n in wanted if n not in all_names]
    if missing:
        raise SystemExit("Theme archive is missing required files: " + ", ".join(missing))
    with tempfile.TemporaryDirectory(prefix="z1-theme-") as td:
        root = Path(td)
        for name in required:
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(name))
        # This backdrop is referenced by the theme cfg but is supplied as the WPS background.
        backdrop = root / ".rockbox/backdrops/Z1bg_green.bmp"
        backdrop.parent.mkdir(parents=True, exist_ok=True)
        backdrop.write_bytes((root / ".rockbox/wps/Z1Music_Green/bg_green.bmp").read_bytes())

        existing = set()
        with zipfile.ZipFile(rockbox_zip) as rb:
            existing = set(rb.namelist())
        overlay = [str(p.relative_to(root)) for p in (root / ".rockbox").rglob("*") if p.is_file()]
        collisions = sorted(existing.intersection(overlay))
        if collisions:
            # Delete exact previous names before re-adding so timestamps don't affect replacement.
            subprocess.run(["zip", "-q", "-d", str(rockbox_zip), *collisions], check=True)
        subprocess.run(["zip", "-q", "-r", str(rockbox_zip), ".rockbox"], cwd=root, check=True)

with zipfile.ZipFile(rockbox_zip) as result:
    names = set(result.namelist())
    for name in wanted:
        if name not in names:
            raise SystemExit("Merged Rockbox ZIP is missing " + name)
    if ".rockbox/backdrops/Z1bg_green.bmp" not in names:
        raise SystemExit("Merged Rockbox ZIP is missing the theme backdrop")
print(f"Merged Z1Music_Green theme ({len(overlay)} files) into {rockbox_zip}")
