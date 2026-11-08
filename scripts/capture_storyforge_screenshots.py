#!/usr/bin/env python3
"""Capture meaningful, repeatable STORYFORGE screens using a real Android emulator."""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("screenshots")
DIAG = OUT / "diagnostics"
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
    subprocess.run(
        ["adb", "pull", "/sdcard/storyforge-window.xml", "/tmp/storyforge-window.xml"],
        capture_output=True, text=True
    )
    try:
        return ET.parse("/tmp/storyforge-window.xml").getroot()
    except (ET.ParseError, OSError):
        return None


def visible_text() -> str:
    root = hierarchy()
    if root is None:
        return ""
    return " ".join(
        (node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")).strip()
        for node in root.iter("node")
    )


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
    time.sleep(0.6)
    return True


def require_tap(label: str, contains: bool = False) -> None:
    if not tap_text(label, contains):
        raise RuntimeError(f"Required UI control was not found: {label}")


def type_ascii(value: str) -> None:
    # Android's shell input accepts spaces as %s; use ASCII-only fixture content.
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.4)


def require_visible(label: str) -> None:
    text = visible_text()
    if label not in text:
        raise RuntimeError(f"Expected UI text is not visible: {label!r}. Current UI: {text[:1200]}")


def tap_bottom_tab(label: str, index: int) -> None:
    if not tap_text(label):
        width, height, density = dimensions()
        x = round(width * (index + 0.5) / 5)
        y = height - round(64 * density)
        adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)


def capture(filename: str) -> dict:
    remote = f"/sdcard/{filename}"
    adb("shell", "screencap", "-p", remote)
    local = OUT / filename
    subprocess.run(["adb", "pull", remote, str(local)], check=True, capture_output=True)
    data = local.read_bytes()
    if len(data) < 4096 or not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Screenshot is missing or invalid: {local}")
    print(f"Captured {local} ({len(data)} bytes)")
    return {
        "file": filename,
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def save_diagnostics() -> None:
    DIAG.mkdir(parents=True, exist_ok=True)
    try:
        adb("shell", "uiautomator", "dump", "/sdcard/storyforge-window.xml", check=False)
        subprocess.run(
            ["adb", "pull", "/sdcard/storyforge-window.xml", str(DIAG / "window.xml")],
            capture_output=True, text=True
        )
    except Exception:
        pass
    try:
        (DIAG / "logcat.txt").write_text(
            adb("logcat", "-d", "-t", "5000", check=False), encoding="utf-8"
        )
    except Exception:
        pass
    try:
        adb("shell", "screencap", "-p", "/sdcard/storyforge-failure.png", check=False)
        subprocess.run(
            ["adb", "pull", "/sdcard/storyforge-failure.png", str(DIAG / "failure-screen.png")],
            capture_output=True, text=True
        )
    except Exception:
        pass


def main() -> None:
    adb("wait-for-device")
    deadline = time.time() + 180
    while time.time() < deadline:
        if adb("shell", "getprop", "sys.boot_completed") == "1":
            break
        time.sleep(2)
    else:
        raise RuntimeError("Android emulator did not finish booting")

    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    # A clean app data set makes the screenshots deterministic on every CI run.
    adb("shell", "pm", "clear", "com.storyforge.app")
    adb("shell", "am", "force-stop", "com.storyforge.app")
    adb("shell", "monkey", "-p", "com.storyforge.app", "-c",
        "android.intent.category.LAUNCHER", "1")
    time.sleep(8)

    captures = []
    require_visible("مكتبة القصص")
    captures.append(capture("00-library-empty.png"))

    # Create a real sample project through the same UI a new user sees.
    require_tap("إنشاء مشروع جديد", contains=True)
    require_visible("مشروع جديد")
    require_tap("اسم القصة", contains=True)
    type_ascii("The Ashen Bell")
    require_tap("وصف مختصر", contains=True)
    type_ascii("A haunted village where every bell toll erases one memory.")
    # Hide the software keyboard before tapping the dialog confirmation button.
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    require_tap("إنشاء")
    time.sleep(2)
    current_ui = visible_text()
    if "مشروع جديد" in current_ui:
        raise RuntimeError(f"Project dialog is still open after confirmation: {current_ui[:1000]}")
    require_visible("The Ashen Bell")
    require_visible("فحص القصة")

    # The create action selects the new project and opens Studio (page 1).
    captures.append(capture("01-studio-project.png"))

    # Configure a real scene atmosphere, then capture the selected effects in the editor.
    for _ in range(12):
        if "تركيبات أجواء جاهزة" in visible_text():
            break
        adb("shell", "input", "swipe", "540", "1550", "540", "620", "500")
        time.sleep(0.35)
    require_visible("تركيبات أجواء جاهزة")
    require_tap("غابة ميتة")
    time.sleep(1)
    captures.append(capture("02-studio-atmosphere-effects.png"))

    # Return to the top of the long Studio screen before using bottom navigation.
    for _ in range(12):
        adb("shell", "input", "swipe", "540", "500", "540", "1550", "350")
    time.sleep(0.8)
    tap_bottom_tab("المكتبة", 0)
    require_visible("The Ashen Bell")
    if "مشروع جديد" in visible_text():
        raise RuntimeError("Unexpected project dialog while viewing the library.")
    captures.append(capture("03-library-project.png"))

    tap_bottom_tab("المعاينة", 2)
    require_visible("The Ashen Bell")
    time.sleep(1)
    captures.append(capture("04-story-preview.png"))

    tap_bottom_tab("التعلّم", 3)
    academy_ui = visible_text()
    if not any(token in academy_ui for token in ("أكاديمية", "التعلّم", "تعلم")):
        raise RuntimeError(f"Learning center did not open: {academy_ui[:1200]}")
    captures.append(capture("05-learning-academy.png"))

    tap_bottom_tab("بناء APK", 4)
    build_ui = visible_text()
    if not any(token in build_ui for token in ("مركز بناء الألعاب", "بناء APK", "بناء الألعاب")):
        raise RuntimeError(f"Build center did not open: {build_ui[:1200]}")
    captures.append(capture("06-build-center.png"))

    manifest = {
        "application": "STORYFORGE",
        "capture_method": "Android emulator + adb screencap",
        "emulator": {
            "size": adb("shell", "wm", "size"),
            "density": adb("shell", "wm", "density"),
            "android_release": adb("shell", "getprop", "ro.build.version.release"),
            "sdk": adb("shell", "getprop", "ro.build.version.sdk"),
        },
        "fixture_project": "The Ashen Bell",
        "screenshots": captures,
    }
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Captured and verified {len(captures)} meaningful real app screenshots.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        save_diagnostics()
        raise
