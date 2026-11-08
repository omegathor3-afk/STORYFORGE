#!/usr/bin/env python3
"""Capture repeatable, real screenshots of STORYFORGE's five main sections on an Android emulator."""
from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("screenshots")
OUT.mkdir(exist_ok=True)


def adb(*args: str, check: bool = True) -> str:
    result = subprocess.run(["adb", *args], text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def dimensions() -> tuple[int, int, float]:
    size = adb("shell", "wm", "size")
    density = adb("shell", "wm", "density")
    match = re.search(r"(\d+)x(\d+)", size)
    dm = re.search(r"(\d+)", density)
    if not match or not dm:
        raise RuntimeError(f"Cannot read emulator dimensions: {size!r} {density!r}")
    return int(match.group(1)), int(match.group(2)), int(dm.group(1)) / 160.0


def hierarchy() -> ET.Element | None:
    adb("shell", "uiautomator", "dump", "/sdcard/storyforge-window.xml", check=False)
    subprocess.run(["adb", "pull", "/sdcard/storyforge-window.xml", "/tmp/storyforge-window.xml"],
                   capture_output=True, text=True)
    try:
        return ET.parse("/tmp/storyforge-window.xml").getroot()
    except (ET.ParseError, OSError):
        return None


def tap_text(label: str, contains: bool = False) -> bool:
    root = hierarchy()
    if root is None:
        return False
    matches = []
    for node in root.iter("node"):
        value = (node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")).strip()
        ok = label in value if contains else value == label
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if ok and bounds:
            x1, y1, x2, y2 = map(int, bounds.groups())
            matches.append((y2, (x1 + x2) // 2, (y1 + y2) // 2))
    if not matches:
        return False
    _, x, y = max(matches)
    adb("shell", "input", "tap", str(x), str(y))
    return True


def tap_bottom_tab(label: str, index: int) -> None:
    if not tap_text(label):
        width, height, density = dimensions()
        x = round(width * (index + 0.5) / 5)
        y = height - round(64 * density)
        adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)


def capture(filename: str) -> None:
    remote = f"/sdcard/{filename}"
    adb("shell", "screencap", "-p", remote)
    local = OUT / filename
    subprocess.run(["adb", "pull", remote, str(local)], check=True, capture_output=True)
    data = local.read_bytes()
    if len(data) < 4096 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Screenshot is missing or invalid: {local}")
    print(f"Captured {local} ({len(data)} bytes)")


adb("wait-for-device")
deadline = time.time() + 180
while time.time() < deadline:
    if adb("shell", "getprop", "sys.boot_completed") == "1":
        break
    time.sleep(2)
else:
    raise RuntimeError("Android emulator did not finish booting")

adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "am", "force-stop", "com.storyforge.app")
adb("shell", "monkey", "-p", "com.storyforge.app", "-c",
    "android.intent.category.LAUNCHER", "1")
time.sleep(8)

# Keep the opening/brand screen as evidence, then dismiss it for the app sections.
capture("00-brand-intro.png")
for _ in range(3):
    if tap_text("ابدأ", contains=True) or tap_text("متابعة", contains=True) or tap_text("دخول", contains=True):
        time.sleep(2)
    else:
        width, height, density = dimensions()
        adb("shell", "input", "tap", str(width // 2), str(height - round(90 * density)))
        time.sleep(2)
    root = hierarchy()
    visible = " ".join(n.attrib.get("text", "") for n in root.iter("node")) if root is not None else ""
    if not any(word in visible for word in ("ابدأ", "متابعة", "دخول")):
        break

capture("01-library.png")
for index, (label, filename) in enumerate([
    ("الاستوديو", "02-studio.png"),
    ("المعاينة", "03-preview.png"),
    ("التعلّم", "04-learning.png"),
    ("بناء APK", "05-build-apk.png"),
], start=1):
    tap_bottom_tab(label, index)
    capture(filename)

print("All six real emulator screenshots captured and validated.")
