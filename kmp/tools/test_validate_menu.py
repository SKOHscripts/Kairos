"""Tests du contrôle du menu du site (validate_menu.py) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import pathlib
import shutil
import tempfile
import unittest

import validate_menu as vm


class ValidateMenuTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        for page in vm.PAGES:
            (self.root / page).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(vm.ROOT / page, self.root / page)

    def tearDown(self):
        self.tmp.cleanup()

    def test_the_versioned_pages_pass(self):
        self.assertEqual([], vm.check(self.root))

    def test_a_wrong_target_or_a_missing_form_is_caught(self):
        page = self.root / "de/index.html"
        page.write_text(page.read_text(encoding="utf-8").replace('href="../code/"', 'href="../karte/"', 1), encoding="utf-8")
        story = self.root / "histoire/index.html"
        text = story.read_text(encoding="utf-8")
        story.write_text(text[:text.index('<details class="menu-narrow">')] + text[text.index("</details>") + 10:], encoding="utf-8")
        errors = vm.check(self.root)
        self.assertTrue(any(e.startswith("de/index.html : menu") for e in errors), errors)
        self.assertTrue(any(e.startswith("histoire/index.html : 1 menu") for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
