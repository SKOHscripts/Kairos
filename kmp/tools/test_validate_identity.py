"""Tests du contrôle de l'identité de l'app (validate_identity.py) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import pathlib
import shutil
import tempfile
import unittest

import validate_identity as vi


class ValidateIdentityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        for name in {n for n, *_ in vi.IDENTITY} | set(vi.FILES):
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(vi.ROOT / name, self.root / name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_the_versioned_repository_passes(self):
        self.assertEqual([], vi.check(self.root))

    def test_a_renamed_package_or_a_dropped_legacy_name_is_caught(self):
        gradle = self.root / vi.DESKTOP / "build.gradle.kts"
        gradle.write_text(gradle.read_text(encoding="utf-8").replace('packageName = "kairos"', 'packageName = "kairos-next"'), encoding="utf-8")
        shortcuts = self.root / vi.KOTLIN / "PortableShortcuts.kt"
        shortcuts.write_text(shortcuts.read_text(encoding="utf-8").replace('listOf("Kairos Preview")', "listOf()"), encoding="utf-8")
        errors = vi.check(self.root)
        self.assertEqual(2, len(errors))
        self.assertIn("paquet Linux", errors[0])
        self.assertIn("anciens noms", errors[1])


if __name__ == "__main__":
    unittest.main()
