#!/usr/bin/env python3
"""Fetch and validate one STORYFORGE .storygame bundle for an isolated APK build."""
import json
import os
import pathlib
import re
import sys
import struct
import urllib.request
import zipfile

url = os.environ["STORYGAME_URL"].strip()
title = os.environ["GAME_TITLE"].strip()
if not url.startswith("https://") or len(url) > 2048:
    raise SystemExit("storygame_url must be an HTTPS URL")
if not title or len(title) > 40:
    raise SystemExit("game_title must contain 1-40 characters")

request = urllib.request.Request(url, headers={"User-Agent": "STORYFORGE-APK-Builder/1.0"})
with urllib.request.urlopen(request, timeout=45) as response:
    if response.status != 200:
        raise SystemExit("Unable to download the story package")
    data = response.read(100 * 1024 * 1024 + 1)
if len(data) > 100 * 1024 * 1024:
    raise SystemExit("Story package exceeds 100 MB")

tmp = pathlib.Path("storygame-upload.zip")
tmp.write_bytes(data)
with zipfile.ZipFile(tmp) as archive:
    infos = archive.infolist()
    if len(infos) > 500:
        raise SystemExit("Too many entries in story package")
    names = set()
    total = 0
    for item in infos:
        name = item.filename
        parts = pathlib.PurePosixPath(name).parts
        if name.startswith("/") or "\\\\" in name or any(p in ("", ".", "..") for p in parts):
            raise SystemExit("Unsafe path in story package")
        if name in names:
            raise SystemExit("Duplicate path in story package")
        names.add(name)
        total += item.file_size
        if item.file_size > 20 * 1024 * 1024 or total > 100 * 1024 * 1024:
            raise SystemExit("Story package exceeds safety limits")
    required = {"game-manifest.json", "storyforge-project.json"}
    if not required.issubset(names):
        raise SystemExit("This is not a valid STORYFORGE .storygame package")
    manifest = json.loads(archive.read("game-manifest.json"))
    projects = json.loads(archive.read("storyforge-project.json"))
    if manifest.get("format") != "storyforge-game" or manifest.get("formatVersion") != 1:
        raise SystemExit("Unsupported STORYFORGE package version")
    if not isinstance(projects, list) or len(projects) != 1:
        raise SystemExit("A standalone APK must contain exactly one story")
    projects[0]["title"] = title
    manifest["title"] = title
    manifest["branding"] = manifest.get("branding") or {}
    manifest["branding"]["displayName"] = title
    manifest["branding"]["introTitle"] = title

    out = pathlib.Path("player/src/main/assets/default.storygame")
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as result:
        result.writestr("game-manifest.json", json.dumps(manifest, ensure_ascii=False, separators=(",", ":")))
        result.writestr("storyforge-project.json", json.dumps(projects, ensure_ascii=False, separators=(",", ":")))
        for item in infos:
            if item.is_dir() or item.filename in required:
                continue
            if not (item.filename.startswith("assets/storyforge_assets/") or item.filename.startswith("storyforge_assets/")):
                raise SystemExit("Unsupported file in story package")
            result.writestr(item.filename, archive.read(item.filename))
icon_url = os.environ.get("ICON_URL", "").strip()
if icon_url:
    if not icon_url.startswith("https://") or len(icon_url) > 2048:
        raise SystemExit("icon_url must be an HTTPS URL")
    icon_request = urllib.request.Request(icon_url, headers={"User-Agent": "STORYFORGE-APK-Builder/1.0"})
    with urllib.request.urlopen(icon_request, timeout=30) as response:
        icon = response.read(3 * 1024 * 1024 + 1)
    if len(icon) > 3 * 1024 * 1024 or not icon.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
        raise SystemExit("App icon must be a PNG file smaller than 3 MB")
    width, height = struct.unpack(">II", icon[16:24])
    if width < 96 or height < 96 or width > 4096 or height > 4096:
        raise SystemExit("App icon dimensions must be between 96 and 4096 pixels")
    icon_path = pathlib.Path("player/src/main/res/drawable-nodpi/game_icon.png")
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    icon_path.write_bytes(icon)

tmp.unlink(missing_ok=True)
print("Validated and embedded standalone story package.")
