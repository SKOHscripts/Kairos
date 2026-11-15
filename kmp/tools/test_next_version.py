"""Tests de la version attendue (next_version.py) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import datetime
import unittest

import next_version as nv

TODAY = datetime.date(2026, 12, 1)


def catalog(*cards: tuple[str, str]) -> str:
    return "\n".join(f'FeatureCard("{i}", GuideChapter.X, {{ KairosIcons.Help }}, a, b, GuideTarget.Demo, since = "{s}"),'
                     for i, s in cards)


OLD = catalog(("day", "3.0.0"), ("gtd", "4.1.0"))


def evaluate(current="4.2.0", catalog_now=OLD, fmt_then=3, fmt_now=3, subjects=("fix: a",), waiting=None, missing=()):
    return nv.evaluate((4, 1, 0), current, catalog_now, fmt_then, fmt_now, list(subjects), waiting, TODAY, list(missing))


class NextVersionTest(unittest.TestCase):
    def test_the_last_final_is_the_highest_tag_without_suffix(self):
        tags = ["v3.14.0", "v4.1.0-beta.9", "v4.1.0", "v4.10.0-beta.1", "v4.9.0", "v2.5.1", "foo"]
        self.assertEqual((4, 9, 0), nv.last_final(tags))
        self.assertIsNone(nv.last_final(["v4.2.0-beta.1"]))

    def test_cards_are_read_from_the_catalog_source(self):
        text = catalog(("tips", "4.2.0")) + '\n        FeatureCard(\n            "multi",\n            x,\n            since = "4.3.0"),'
        self.assertEqual({"tips": (4, 2, 0), "multi": (4, 3, 0)}, nv.cards(text))

    def test_levels(self):
        self.assertEqual(nv.MAJOR, nv.level(["a"], 3, 4, 2))
        self.assertEqual(nv.MINOR, nv.level(["a"], 3, 3, 2))
        self.assertEqual(nv.PATCH, nv.level([], 3, None, 1))
        self.assertEqual(nv.NONE, nv.level([], 3, 3, 0))
        self.assertEqual((5, 0, 0), nv.bump((4, 1, 3), nv.MAJOR))
        self.assertEqual((4, 2, 0), nv.bump((4, 1, 3), nv.MINOR))
        self.assertEqual((4, 1, 4), nv.bump((4, 1, 3), nv.PATCH))

    def test_a_new_card_needs_the_next_minor_beta_or_final(self):
        now = catalog(("gtd", "4.1.0"), ("tips", "4.2.0"))
        self.assertEqual([], evaluate("4.2.0-beta.1", now).errors)
        self.assertEqual([], evaluate("4.2.0", now).errors)
        report = evaluate("4.1.1", now)
        self.assertEqual((4, 2, 0), report.expected)
        self.assertIn("set_version.py 4.2.0", report.errors[0])

    def test_main_left_on_the_published_final_fails(self):
        report = evaluate("4.1.0")
        self.assertIn("finale publiée 4.1.0", report.errors[0])
        self.assertEqual([], evaluate("4.1.0", subjects=()).errors)

    def test_a_format_change_needs_a_major(self):
        self.assertIn("5.0.0", evaluate("4.2.0", fmt_now=4).errors[0])
        self.assertEqual([], evaluate("5.0.0-beta.1", fmt_now=4).errors)

    def test_only_fixes_under_a_minor_suggests_a_patch(self):
        report = evaluate("4.2.0")
        self.assertEqual([], report.errors)
        self.assertIn("4.1.1", report.notices[0])

    def test_feat_without_a_card_warns(self):
        self.assertTrue(evaluate(subjects=("feat(ui): x",)).warnings)
        self.assertFalse(evaluate(subjects=("fix(ui): feature flag",)).warnings)

    def test_features_waiting_a_week_ask_for_a_release(self):
        now = catalog(("tips", "4.2.0"))
        self.assertFalse(evaluate(catalog_now=now, waiting=TODAY - datetime.timedelta(days=6)).warnings)
        warning = evaluate(catalog_now=now, waiting=TODAY - datetime.timedelta(days=7)).warnings
        self.assertIn("publier 4.2.0", warning[0])

    def test_what_is_left_to_publish_a_final(self):
        report = evaluate(missing=("en-US", "fr-FR"))
        self.assertIn("en-US, fr-FR", report.lines[-1])
        self.assertIn("Kairos tag", evaluate().lines[-1])
        self.assertNotIn("Pour publier", evaluate("4.2.0-beta.1").lines[-1])

    def test_the_versioned_repository_passes(self):
        report = nv.evaluate((4, 1, 0), nv.property_value("versionName"),
                             (nv.ROOT / nv.CATALOG).read_text(encoding="utf-8"), 3,
                             nv.sync_format((nv.ROOT / nv.CODEC).read_text(encoding="utf-8")), ["fix: x"], None, TODAY, [])
        self.assertEqual([], report.errors)
        self.assertIn("tips", " ".join(report.lines))


if __name__ == "__main__":
    unittest.main()
