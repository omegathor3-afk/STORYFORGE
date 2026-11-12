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


def scroll_to_top() -> None:
    # The app keeps one scroll state while bottom tabs change. Reset it so
    # controls near the top are visible before accessibility-based taps.
    for _ in range(12):
        adb("shell", "input", "swipe", "540", "420", "540", "1550", "350")
        time.sleep(0.18)


def tap_bottom_tab(label: str, index: int) -> None:
    if not tap_text(label):
        width, height, density = dimensions()
        x = round(width * (index + 0.5) / 6)
        y = height - round(64 * density)
        adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1)
    scroll_to_top()
    time.sleep(1)


def section_is_visible(label: str) -> bool:
    root = hierarchy()
    if root is None:
        return False
    _, height, _ = dimensions()
    for node in root.iter("node"):
        value = (node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")).strip()
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if label in value and bounds:
            _, y1, _, y2 = map(int, bounds.groups())
            # Ignore Compose semantics nodes that exist in the tree but are outside
            # the actual viewport or hidden beneath the bottom navigation.
            if y2 > 150 and y1 < height - 235 and y2 - y1 < 220:
                return True
    return False


def scroll_to_text(label: str, max_swipes: int = 18) -> None:
    for _ in range(max_swipes):
        if section_is_visible(label):
            return
        adb("shell", "input", "swipe", "540", "1570", "540", "560", "450")
        time.sleep(0.45)
    raise RuntimeError(f"Could not bring section {label!r} into the visible viewport; current UI: {visible_text()[:1000]}")


def scroll_back_to_text(label: str, max_swipes: int = 18) -> None:
    """Scroll toward earlier content after selecting a lower effect chip."""
    for _ in range(max_swipes):
        if section_is_visible(label):
            return
        # Reverse the normal scroll gesture: move the page back toward the
        # live preview instead of continuing farther down the effect list.
        adb("shell", "input", "swipe", "540", "560", "540", "1250", "450")
        time.sleep(0.45)
    raise RuntimeError(f"Could not scroll back to section {label!r}; current UI: {visible_text()[:1000]}")


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

    # Google Messages can become unresponsive in headless emulator sessions and
    # raise a system ANR dialog over the app, blocking UI automation. It is not
    # required by STORYFORGE, so disable it for this isolated screenshot run.
    adb("shell", "pm", "disable-user", "--user", "0", "com.google.android.apps.messaging", check=False)

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
    time.sleep(1.5)
    # Recover if Android surfaces a transient system ANR dialog above the app.
    for _ in range(3):
        system_ui = visible_text()
        if "isn't responding" not in system_ui and "لا يستجيب" not in system_ui:
            break
        if not (tap_text("Wait") or tap_text("انتظار")):
            raise RuntimeError(f"System ANR dialog blocked project creation: {system_ui[:1000]}")
        time.sleep(2)
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
    require_tap("غابة ميتة", contains=True)
    time.sleep(1)
    # The live preview now sits above the long chip list, so it is visible
    # in the same viewport as the selected preset and can be inspected directly.
    captures.append(capture("02-studio-atmosphere-live-preview.png"))

    # Add rain on top of the dead-forest preset so the live preview and player
    # evidence prove that layered effects can coexist in one scene.
    scroll_to_text("🌧 مطر")
    require_tap("🌧 مطر", contains=True)
    time.sleep(1)
    # Return to the live preview itself, not the top of the editor page:
    # the preview's active-layer count is below the project header.
    scroll_back_to_text("المشهد الآن")
    active_preview_text = visible_text()
    if "10 طبقة جوية نشطة" not in active_preview_text:
        raise RuntimeError(
            "Rain chip did not add a tenth active atmosphere layer. "
            f"Visible preview text: {active_preview_text[:1200]}"
        )
    captures.append(capture("02b-studio-rain-live-preview.png"))

    # Capture the environmental set dressing, dialogue, branching choices, and asset library.
    scroll_to_text("تخطيطات بيئية جاهزة")
    # The layout preset is below its section heading; bring the actual button
    # into the viewport before tapping it (the atmosphere preset is higher up).
    scroll_to_text("غابة ميتة")
    require_tap("غابة ميتة", contains=True)
    time.sleep(0.8)
    captures.append(capture("03-studio-environment-layout.png"))

    scroll_to_text("حوارات المشهد")
    if tap_text("＋ سطر حوار"):
        time.sleep(0.8)
    captures.append(capture("04-studio-dialogue.png"))

    scroll_to_text("اختيارات اللاعب")
    captures.append(capture("05-studio-choices.png"))

    scroll_to_text("مكتبة الأصول")
    captures.append(capture("06-studio-assets.png"))

    # Return to the top of the long Studio screen before using bottom navigation.
    for _ in range(16):
        adb("shell", "input", "swipe", "540", "500", "540", "1570", "350")
    time.sleep(0.8)
    tap_bottom_tab("المكتبة", 0)
    require_visible("مكتبة القصص")
    require_visible("The Ashen Bell")
    # The library legitimately contains a "＋ إنشاء مشروع جديد" button;
    # do not mistake that permanent action for the new-project dialog.
    captures.append(capture("07-library-project.png"))

    tap_bottom_tab("المعاينة", 2)
    preview_ui = visible_text()
    if not any(token in preview_ui for token in ("معاينة تفاعلية", "الطرقات على الباب", "مشهد")):
        raise RuntimeError(f"Story preview did not open: {preview_ui[:1200]}")
    time.sleep(1)
    captures.append(capture("08-story-preview.png"))

    # Reset the shared scroll container: the preview launch button is above
    # the scene card and may be outside the viewport after Studio navigation.
    scroll_to_top()
    require_tap("ابدأ التجربة السينمائية الغامرة", contains=True)
    time.sleep(1)
    # The immersive player has a short intro gate; enter the actual playable scene
    # before capturing or recording so the video contains moving atmosphere layers.
    require_tap("ابدأ المغامرة", contains=True)
    time.sleep(1)
    player_ui = visible_text()
    if not any(token in player_ui for token in ("المشهد", "متابعة", "انتهت الحكاية", "الطرقات على الباب")):
        raise RuntimeError(f"Immersive story player did not open: {player_ui[:1200]}")
    captures.append(capture("09-immersive-atmosphere-player.png"))
    adb("shell", "settings", "put", "global", "window_animation_scale", "1.0")
    adb("shell", "settings", "put", "global", "transition_animation_scale", "1.0")
    adb("shell", "settings", "put", "global", "animator_duration_scale", "1.0")
    video_remote = "/sdcard/storyforge-immersive-review.mp4"
    recorder = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "9", video_remote],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(9.8)
    recorder.wait(timeout=5)
    video_path = OUT / "09-immersive-atmosphere-review.mp4"
    subprocess.run(["adb", "pull", video_remote, str(video_path)], check=True, capture_output=True)
    if not video_path.exists() or video_path.stat().st_size < 12000:
        raise RuntimeError("Immersive player recording is missing or unexpectedly small.")
    print(f"Captured {video_path} ({video_path.stat().st_size} bytes)")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)

    tap_bottom_tab("التعلّم", 3)
    academy_ui = visible_text()
    if not any(token in academy_ui for token in ("أكاديمية", "التعلّم", "تعلم")):
        raise RuntimeError(f"Learning center did not open: {academy_ui[:1200]}")
    captures.append(capture("10-learning-academy.png"))

    tap_bottom_tab("بناء APK", 4)
    build_ui = visible_text()
    if not any(token in build_ui for token in ("مركز بناء الألعاب", "بناء APK", "بناء الألعاب")):
        raise RuntimeError(f"Build center did not open: {build_ui[:1200]}")
    captures.append(capture("11-build-center.png"))

    tap_bottom_tab("المجتمع", 5)
    community_ui = visible_text()
    if not any(token in community_ui for token in ("مجتمع STORYFORGE", "حالة الاتصال")):
        raise RuntimeError(f"Community hub did not open: {community_ui[:1200]}")
    captures.append(capture("12-community-hub.png"))

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
        "video_review": {
            "file": "09-immersive-atmosphere-review.mp4",
            "bytes": video_path.stat().st_size,
            "sha256": hashlib.sha256(video_path.read_bytes()).hexdigest(),
            "duration_seconds": 9,
        },
    }
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    hashes = [item["sha256"] for item in captures]
    if len(set(hashes)) != len(hashes):
        raise RuntimeError("At least two screenshot targets produced identical images; navigation must be fixed.")
    print(f"Captured and verified {len(captures)} distinct real app screenshots plus a 9-second player recording.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        save_diagnostics()
        raise
