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
    local_xml = Path("/tmp/storyforge-window.xml")
    for attempt in range(4):
        try:
            local_xml.unlink(missing_ok=True)
            dump = subprocess.run(
                ["adb", "shell", "uiautomator", "dump", "/sdcard/storyforge-window.xml"],
                capture_output=True, text=True, timeout=15
            )
            if dump.returncode == 0:
                pull = subprocess.run(
                    ["adb", "pull", "/sdcard/storyforge-window.xml", str(local_xml)],
                    capture_output=True, text=True, timeout=15
                )
                if pull.returncode == 0 and local_xml.is_file():
                    root = ET.parse(local_xml).getroot()
                    if root.tag == "hierarchy":
                        return root
        except (ET.ParseError, OSError, subprocess.TimeoutExpired):
            pass
        # uiautomator occasionally reports a transient idle-state failure while
        # Compose is drawing; retry instead of treating a visible screen as empty.
        time.sleep(0.25 * (attempt + 1))
    return None


def visible_text() -> str:
    root = hierarchy()
    if root is None:
        return ""
    return " ".join(
        (node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")).strip()
        for node in root.iter("node")
    )


def has_exact_text(label: str) -> bool:
    """Match a dialog heading without confusing it with a longer button label."""
    root = hierarchy()
    return root is not None and any(
        node.attrib.get("text", "").strip() == label
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


def tap_edit_text_for_label(label: str) -> None:
    """Focus the EditText associated with a visible label or section heading."""
    root = hierarchy()
    if root is None:
        raise RuntimeError(f"Cannot inspect UI before editing field: {label}")

    def rect(node):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        return tuple(map(int, match.groups())) if match else None

    label_nodes = []
    edit_nodes = []
    direct = []
    for node in root.iter("node"):
        value = (node.attrib.get("text", "") + " " + node.attrib.get("content-desc", "")).strip()
        bounds = rect(node)
        if not bounds:
            continue
        if label in value:
            label_nodes.append((node, bounds))
            if "EditText" in node.attrib.get("class", ""):
                direct.append(bounds)
        if "EditText" in node.attrib.get("class", ""):
            edit_nodes.append((node, bounds))

    if direct:
        x1, y1, x2, y2 = max(direct, key=lambda r: (r[3] - r[1]) * (r[2] - r[0]))
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.6)
        return

    if not label_nodes:
        raise RuntimeError(f"Field label is not visible: {label}")

    overlapping = []
    for _, (lx1, ly1, lx2, ly2) in label_nodes:
        for _, (x1, y1, x2, y2) in edit_nodes:
            ix1, iy1 = max(lx1, x1), max(ly1, y1)
            ix2, iy2 = min(lx2, x2), min(ly2, y2)
            area = max(0, ix2 - ix1) * max(0, iy2 - iy1)
            if area:
                overlapping.append((area, (x1, y1, x2, y2)))
    if overlapping:
        x1, y1, x2, y2 = max(overlapping, key=lambda item: item[0])[1]
    else:
        label_bottom = min(bounds[3] for _, bounds in label_nodes)
        below = [
            bounds for _, bounds in edit_nodes
            if bounds[1] >= label_bottom - 8 and bounds[1] - label_bottom <= 560
        ]
        if not below:
            raise RuntimeError(f"No editable field was found for label: {label}")
        x1, y1, x2, y2 = min(below, key=lambda bounds: bounds[1])

    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.6)

def require_tap(label: str, contains: bool = False) -> None:
    if not tap_text(label, contains):
        raise RuntimeError(f"Required UI control was not found: {label}")


def type_ascii(value: str) -> None:
    # Android's shell input accepts spaces as %s; use ASCII-only fixture content.
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.4)


def require_visible(label: str, timeout_seconds: float = 12.0) -> None:
    deadline = time.time() + timeout_seconds
    current = ""
    while time.time() < deadline:
        current = visible_text()
        if label in current:
            return
        time.sleep(0.4)
    raise RuntimeError(f"Expected UI text is not visible: {label!r}. Current UI: {current[:1200]}")


def tap_bottom_tab(label: str, index: int) -> None:
    if not tap_text(label):
        width, height, density = dimensions()
        x = round(width * (index + 0.5) / 5)
        y = height - round(64 * density)
        adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)


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


def scroll_to_top(max_swipes: int = 18) -> None:
    # Compose keeps the parent scroll offset when switching bottom tabs.
    # Repeated downward swipes make each captured destination start at its header.
    for _ in range(max_swipes):
        adb("shell", "input", "swipe", "540", "500", "540", "1570", "350")
        time.sleep(0.18)


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

    # Turn the blank starter scene into a genuinely playable sample before
    # reviewing the editor, preview, Academy, and Build Center.
    scroll_to_text("نص المشهد")
    # The starter scene already has sample text, so its placeholder is absent.
    # Locate the editable field by its section heading and append a short setting note.
    tap_edit_text_for_label("نص المشهد")
    adb("shell", "input", "keyevent", "123")  # Move cursor to the end of the existing scene text.
    type_ascii(" At dusk, the last bell rings beneath the abandoned village. Cold wind moves through the broken trees. Someone is waiting beyond the gate.")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    require_visible("At dusk")

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

    # Capture the environmental set dressing, dialogue, branching choices, and asset library.
    scroll_to_text("تخطيطات بيئية جاهزة")
    # Use a layout name unique to the environment presets; "غابة ميتة" is also
    # an atmosphere preset earlier in the editor and can stop scrolling too soon.
    scroll_to_text("خرابة مهجورة")
    require_tap("خرابة مهجورة", contains=True)
    time.sleep(0.8)
    captures.append(capture("03-studio-environment-layout.png"))

    scroll_to_text("حوارات المشهد")
    require_tap("＋ سطر حوار", contains=True)
    time.sleep(0.6)
    scroll_to_text("المتحدث")
    tap_edit_text_for_label("المتحدث")
    type_ascii("Mara")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.6)
    scroll_to_text("نص الحوار")
    tap_edit_text_for_label("نص الحوار")
    type_ascii("If the bell rings again, forget my name. Do not follow the sound.")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    scroll_to_text("نص الحوار")
    captures.append(capture("04-studio-dialogue.png"))

    # Populate two actual branches and capture the choice controls, not only
    # their section heading (which previously left this screenshot identical
    # to the dialogue screen).
    scroll_to_text("الاختيار 1")
    tap_edit_text_for_label("الاختيار 1")
    type_ascii("Follow the bell")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.6)
    scroll_to_text("الاختيار 2")
    tap_edit_text_for_label("الاختيار 2")
    type_ascii("Hide in the chapel")
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    scroll_to_text("الاختيار 1")
    captures.append(capture("05-studio-choices.png"))

    scroll_to_text("مكتبة الأصول")
    captures.append(capture("06-studio-assets.png"))

    # Reset the shared Compose scroll position before each destination capture.
    scroll_to_top()
    tap_bottom_tab("المكتبة", 0)
    scroll_to_top()
    require_visible("The Ashen Bell")
    # The library's primary action is "إنشاء مشروع جديد"; substring matching
    # incorrectly treats that normal button as an open dialog. Only the exact
    # dialog heading should block this capture.
    if has_exact_text("مشروع جديد"):
        raise RuntimeError("Unexpected project dialog while viewing the library.")
    captures.append(capture("07-library-project.png"))

    tap_bottom_tab("المعاينة", 2)
    scroll_to_top()
    preview_ui = visible_text()
    if "The Ashen Bell" not in preview_ui:
        raise RuntimeError(f"Preview is missing the active sample story: {preview_ui[:1200]}")
    if not any(token in preview_ui for token in ("معاينة تفاعلية", "الطرقات على الباب", "مشهد")):
        raise RuntimeError(f"Story preview did not open: {preview_ui[:1200]}")
    require_visible("ابدأ التجربة السينمائية الغامرة")
    time.sleep(1)
    captures.append(capture("08-story-preview.png"))

    # Open the full-screen story player and record a short clip so moving layers
    # (fog, rain, spores, fireflies) can be reviewed rather than inferred from PNGs.
    require_tap("ابدأ التجربة السينمائية الغامرة", contains=True)
    time.sleep(1)
    # The cinematic player first presents the story's own title screen. Enter
    # the adventure explicitly before checking the scene and its live choices.
    require_visible("The Ashen Bell")
    require_tap("ابدأ المغامرة", contains=True)
    require_visible("مشهد جديد", timeout_seconds=12)
    require_visible("Follow the bell", timeout_seconds=12)
    time.sleep(0.8)
    player_ui = visible_text()
    if "مشهد جديد" not in player_ui or "Follow the bell" not in player_ui:
        raise RuntimeError(f"Immersive scene or its sample choice did not open: {player_ui[:1200]}")
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
    scroll_to_top()
    academy_ui = visible_text()
    if not any(token in academy_ui for token in ("أكاديمية", "التعلّم", "تعلم")):
        raise RuntimeError(f"Learning center did not open: {academy_ui[:1200]}")
    if "The Ashen Bell" not in academy_ui:
        raise RuntimeError(f"Academy is missing the active sample project context: {academy_ui[:1200]}")
    captures.append(capture("10-learning-academy.png"))

    tap_bottom_tab("بناء APK", 4)
    scroll_to_top()
    build_ui = visible_text()
    if not any(token in build_ui for token in ("مركز بناء الألعاب", "بناء APK", "بناء الألعاب")):
        raise RuntimeError(f"Build center did not open: {build_ui[:1200]}")
    if "The Ashen Bell" not in build_ui:
        raise RuntimeError(f"Build Center is missing the active sample project: {build_ui[:1200]}")
    captures.append(capture("11-build-center.png"))

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
