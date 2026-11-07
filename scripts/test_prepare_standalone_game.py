#!/usr/bin/env python3
"""Security and behavior tests for standalone STORYFORGE package preparation."""
import io, json, os, pathlib, runpy, tempfile, unittest, zipfile
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).with_name("prepare_standalone_game.py").resolve()

class FakeResponse(io.BytesIO):
    status = 200
    def __enter__(self): return self
    def __exit__(self, *args): self.close()
    def geturl(self): return "https://files.example.test/story.storygame"

def make_package(extra=None, manifest=None, projects=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("game-manifest.json", json.dumps(manifest or {
            "format": "storyforge-game", "formatVersion": 1,
            "projectId": "project-demo", "title": "Demo Story"
        }))
        archive.writestr("storyforge-project.json", json.dumps(projects if projects is not None else [
            {"id": "project-demo", "title": "Demo Story", "scenes": []}
        ]))
        for name, payload in (extra or {}).items(): archive.writestr(name, payload)
    return buffer.getvalue()

class PrepareStandaloneGameTests(unittest.TestCase):
    def run_script(self, package, extra_env=None):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            env_file = root / "github-env.txt"
            env = {
                "STORYGAME_URL": "https://files.example.test/story.storygame",
                "GAME_TITLE": "", "APPLICATION_ID": "",
                "GAME_VERSION_NAME": "1.0.0", "GAME_VERSION_CODE": "1",
                "ICON_URL": "", "GITHUB_ENV": str(env_file)
            }
            env.update(extra_env or {})
            old_cwd = pathlib.Path.cwd()
            try:
                os.chdir(root)
                with patch.dict(os.environ, env, clear=True), patch(
                    "urllib.request.urlopen", return_value=FakeResponse(package)
                ):
                    runpy.run_path(str(SCRIPT), run_name="__main__")
            finally:
                os.chdir(old_cwd)
            embedded = root / "player/src/main/assets/default.storygame"
            embedded_data = embedded.read_bytes() if embedded.exists() else None
            return embedded_data, env_file.read_text(encoding="utf-8")

    def test_valid_package_embeds_and_generates_unique_identity(self):
        package = make_package({"assets/storyforge_assets/cover.png": b"image"})
        embedded_data, env_text = self.run_script(package)
        self.assertIsNotNone(embedded_data)
        self.assertIn("APPLICATION_ID=com.storyforge.games.", env_text)
        self.assertIn("GAME_TITLE=Demo Story", env_text)
        with zipfile.ZipFile(io.BytesIO(embedded_data)) as archive:
            self.assertEqual(json.loads(archive.read("game-manifest.json"))["title"], "Demo Story")
            self.assertEqual(json.loads(archive.read("storyforge-project.json"))[0]["title"], "Demo Story")
            self.assertEqual(archive.read("assets/storyforge_assets/cover.png"), b"image")

    def test_custom_application_identity_and_version_are_preserved(self):
        _, env_text = self.run_script(make_package(), {
            "GAME_TITLE": "Night Watch", "APPLICATION_ID": "org.example.nightwatch",
            "GAME_VERSION_NAME": "2.4.1", "GAME_VERSION_CODE": "7"
        })
        self.assertIn("APPLICATION_ID=org.example.nightwatch", env_text)
        self.assertIn("GAME_VERSION_NAME=2.4.1", env_text)
        self.assertIn("GAME_VERSION_CODE=7", env_text)

    def test_path_traversal_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package({"assets/storyforge_assets/../../outside.txt": b"no"}))

    def test_non_https_url_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package(), {"STORYGAME_URL": "http://files.example.test/game.zip"})

    def test_invalid_application_id_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package(), {"APPLICATION_ID": "not a package"})

    def test_invalid_version_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package(), {"GAME_VERSION_NAME": "latest"})

    def test_multiple_projects_are_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package(projects=[{"id": "a"}, {"id": "b"}]))

    def test_invalid_manifest_is_rejected(self):
        with self.assertRaises(SystemExit):
            self.run_script(make_package(manifest={"format": "other", "formatVersion": 1}))

if __name__ == "__main__": unittest.main()
