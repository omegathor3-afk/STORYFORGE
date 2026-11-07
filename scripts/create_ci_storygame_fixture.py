#!/usr/bin/env python3
"""Create a tiny deterministic .storygame fixture for Android CI only."""
import json
import zipfile
from pathlib import Path

manifest = {
    "format": "storyforge-game",
    "formatVersion": 1,
    "projectId": "storyforge-ci-fixture",
    "title": "CI Story Game",
    "branding": {"displayName": "CI Story Game", "introTitle": "CI Story Game"}
}
project = {
    "id": "storyforge-ci-fixture",
    "title": "CI Story Game",
    "description": "A tiny story used to verify standalone APK packaging.",
    "variables": [],
    "assets": [],
    "scenes": [{
        "id": "ci-start",
        "title": "The First Door",
        "text": "A door opens in the dark. This fixture confirms that a story ships inside its own APK.",
        "choices": [],
        "choiceTargets": [],
        "choiceConditions": [],
        "choiceEffects": [],
        "dialogues": [],
        "choiceTimerSeconds": 0,
        "timeoutChoiceIndex": 0
    }]
}
output = Path(".ci-fixture.storygame")
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("game-manifest.json", json.dumps(manifest, ensure_ascii=False))
    archive.writestr("storyforge-project.json", json.dumps([project], ensure_ascii=False))
print(f"Created {output}")
