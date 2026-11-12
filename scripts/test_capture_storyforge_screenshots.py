#!/usr/bin/env python3
"""Regression tests for Android UI label matching in screenshot capture."""
import importlib.util
import pathlib
import unittest
import xml.etree.ElementTree as ET

SCRIPT = pathlib.Path(__file__).with_name("capture_storyforge_screenshots.py").resolve()
SPEC = importlib.util.spec_from_file_location("storyforge_capture", SCRIPT)
CAPTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAPTURE)


class UiLabelMatchingTests(unittest.TestCase):
    def test_starter_scene_text_is_checked_after_scrolling_to_its_field(self):
        source = SCRIPT.read_text(encoding="utf-8")
        scroll = source.index('scroll_to_text("نص المشهد")')
        require = source.index('require_visible("ثلاث طرقات بطيئة")')
        self.assertLess(scroll, require)

    def test_exact_field_label_matches(self):
        self.assertTrue(CAPTURE.matches_ui_label("المتحدث", "المتحدث"))

    def test_label_inside_helper_sentence_does_not_match(self):
        helper = "كل سطر مرتبط بهذا المشهد. اكتب اسم المتحدث ونص كلامه، وسيظهر بالترتيب في المعاينة."
        self.assertFalse(CAPTURE.matches_ui_label(helper, "المتحدث"))

    def test_whitespace_is_normalized_without_allowing_substrings(self):
        self.assertTrue(CAPTURE.matches_ui_label("  نص   الحوار ", "نص الحوار"))
        self.assertFalse(CAPTURE.matches_ui_label("اكتب نص الحوار هنا", "نص الحوار"))

    def test_descriptive_compose_heading_can_be_matched_by_explicit_prefix(self):
        heading = "تخطيطات بيئية جاهزة — تستبدل العناصر الحالية للمشهد"
        self.assertTrue(CAPTURE.matches_ui_label(heading, "تخطيطات بيئية جاهزة", contains=True))
        self.assertFalse(CAPTURE.matches_ui_label(heading, "تخطيطات بيئية جاهزة"))

    def test_library_capture_occurs_after_sample_project_creation(self):
        source = SCRIPT.read_text(encoding="utf-8")
        create = source.index('require_visible("The Ashen Bell")')
        library_capture = source.index('capture("00-library-project.png")')
        self.assertLess(create, library_capture)
        self.assertNotIn("00-library-empty.png", source)

    def test_environment_scroll_uses_the_real_descriptive_heading(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn('scroll_to_text("تخطيطات بيئية جاهزة", contains=True)', source)

    def test_pixel_launcher_anr_finds_only_its_close_app_button(self):
        root = ET.fromstring(
            '<hierarchy>'
            '<node text="Pixel Launcher isn\'t responding" />'
            '<node text="Close app" clickable="true" bounds="[70,898][1010,1024]" />'
            '<node text="Wait" clickable="true" bounds="[70,1024][1010,1150]" />'
            '</hierarchy>'
        )
        self.assertEqual((540, 961), CAPTURE.pixel_launcher_anr_close_point(root))

    def test_non_launcher_anr_is_not_closed_by_launcher_recovery(self):
        root = ET.fromstring(
            '<hierarchy>'
            '<node text="STORYFORGE isn\'t responding" />'
            '<node text="Close app" clickable="true" bounds="[70,898][1010,1024]" />'
            '</hierarchy>'
        )
        self.assertIsNone(CAPTURE.pixel_launcher_anr_close_point(root))

    def test_screen_text_reads_recover_launcher_anr_without_duplicate_dumps(self):
        source = SCRIPT.read_text(encoding="utf-8")
        visible_body = source[source.index("def visible_text"):source.index("def matches_ui_label")]
        require_body = source[source.index("def require_visible"):source.index("def tap_bottom_tab")]
        self.assertIn("dismiss_pixel_launcher_anr(root)", visible_body)
        self.assertIn("visible_text()", require_body)
        self.assertNotIn("dismiss_pixel_launcher_anr()", require_body)



if __name__ == "__main__":
    unittest.main()
