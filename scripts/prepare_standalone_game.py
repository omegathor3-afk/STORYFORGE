#!/usr/bin/env python3
"""Fetch and validate one STORYFORGE .storygame bundle for an isolated APK build."""
import json
import os
import pathlib
import re
import sys
import struct
import hashlib
import stat
import urllib.parse
import urllib.request
import zipfile

url = os.environ.get("STORYGAME_URL", "").strip()
local_package = os.environ.get("STORYGAME_LOCAL_PATH", "").strip()
title = os.environ.get("GAME_TITLE", "").strip()
application_id = os.environ.get("APPLICATION_ID", "").strip()
version_name = os.environ.get("GAME_VERSION_NAME", "1.0.0").strip()
version_code = os.environ.get("GAME_VERSION_CODE", "1").strip()
if local_package:
    local_path = pathlib.Path(local_package).resolve()
    if not local_path.is_file():
        raise SystemExit("STORYGAME_LOCAL_PATH must point to a local story package")
    with local_path.open("rb") as source:
        data = source.read(100 * 1024 * 1024 + 1)
else:
    parsed_url = urllib.parse.urlsplit(url)
    if parsed_url.scheme != "https" or not parsed_url.hostname or parsed_url.username or parsed_url.password or len(url) > 2048:
        raise SystemExit("storygame_url must be a valid HTTPS URL without embedded credentials")
    request = urllib.request.Request(url, headers={"User-Agent": "STORYFORGE-APK-Builder/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        if response.status != 200 or urllib.parse.urlsplit(response.geturl()).scheme != "https":
            raise SystemExit("Unable to download the story package securely")
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
        if item.flag_bits & 0x1:
            raise SystemExit("Encrypted files are not supported in story packages")
        if stat.S_ISLNK(item.external_attr >> 16):
            raise SystemExit("Symbolic links are not allowed in story packages")
        parts = pathlib.PurePosixPath(name).parts
        if name.startswith("/") or chr(92) in name or any(p in ("", ".", "..") for p in parts):
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
    if not isinstance(manifest, dict) or not isinstance(projects, list) or len(projects) != 1 or not isinstance(projects[0], dict):
        raise SystemExit("The story package contains invalid project data")
    if manifest.get("format") != "storyforge-game" or manifest.get("formatVersion") != 1:
        raise SystemExit("Unsupported STORYFORGE package version")
    if not isinstance(projects, list) or len(projects) != 1:
        raise SystemExit("A standalone APK must contain exactly one story")
    if not title:
        title = str(manifest.get("title", "")).strip()
    if not title or len(title) > 40:
        raise SystemExit("Game title must contain 1-40 characters")
    if not application_id:
        project_key = str(projects[0].get("id") or manifest.get("projectId") or title)
        suffix = hashlib.sha256(project_key.encode("utf-8")).hexdigest()[:12]
        application_id = "com.storyforge.games.g" + suffix
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*){1,5}", application_id):
        raise SystemExit("Invalid Android application ID")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version_name):
        raise SystemExit("Version name must use MAJOR.MINOR.PATCH")
    if not version_code.isdigit() or int(version_code) < 1 or int(version_code) > 2100000000:
        raise SystemExit("Version code must be between 1 and 2100000000")
    if application_id == "com.storyforge.player":
        raise SystemExit("Standalone games must use a unique Android application ID")
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
    parsed_icon_url = urllib.parse.urlsplit(icon_url)
    if parsed_icon_url.scheme != "https" or not parsed_icon_url.hostname or parsed_icon_url.username or parsed_icon_url.password or len(icon_url) > 2048:
        raise SystemExit("icon_url must be a valid HTTPS URL without embedded credentials")
    icon_request = urllib.request.Request(icon_url, headers={"User-Agent": "STORYFORGE-APK-Builder/1.0"})
    with urllib.request.urlopen(icon_request, timeout=30) as response:
        if response.status != 200 or urllib.parse.urlsplit(response.geturl()).scheme != "https":
            raise SystemExit("Unable to download the app icon securely")
        icon = response.read(3 * 1024 * 1024 + 1)
    if len(icon) < 24 or len(icon) > 3 * 1024 * 1024 or not icon.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])):
        raise SystemExit("App icon must be a PNG file smaller than 3 MB")
    width, height = struct.unpack(">II", icon[16:24])
    if width < 96 or height < 96 or width > 4096 or height > 4096:
        raise SystemExit("App icon dimensions must be between 96 and 4096 pixels")
    if width != height:
        raise SystemExit("App icon must be square")
    icon_path = pathlib.Path("player/src/main/res/drawable-nodpi/game_icon.png")
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    icon_path.write_bytes(icon)
    pathlib.Path("player/src/main/res/drawable/game_icon.xml").unlink(missing_ok=True)

with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env:
    env.write("GAME_TITLE=" + title.replace(chr(10), " ").replace(chr(13), " ") + chr(10))
    env.write("APPLICATION_ID=" + application_id + chr(10))
    env.write("GAME_VERSION_NAME=" + version_name + chr(10))
    env.write("GAME_VERSION_CODE=" + version_code + chr(10))

tmp.unlink(missing_ok=True)
print("Validated and embedded standalone story package.")
