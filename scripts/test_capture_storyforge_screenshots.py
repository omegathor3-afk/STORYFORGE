#!/usr/bin/env python3
"""Regression tests for Android UI label matching in screenshot capture."""
import importlib.util
import pathlib
import unittest

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


if __name__ == "__main__":
    unittest.main()
