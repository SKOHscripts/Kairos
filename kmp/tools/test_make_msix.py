"""Tests du dossier MSIX (make_msix.py, docs/spec/distribution.md § Microsoft Store) :
python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import pathlib
import tempfile
import unittest
import xml.etree.ElementTree as ET

import make_msix as mm

NS = {
    "m": "http://schemas.microsoft.com/appx/manifest/foundation/windows10",
    "uap": "http://schemas.microsoft.com/appx/manifest/uap/windows10",
    "rescap": "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities",
    "desktop6": "http://schemas.microsoft.com/appx/manifest/desktop/windows10/6",
}


class MakeMsixTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.tmp.name)
        self.image = root / "Kairos"
        (self.image / "app").mkdir(parents=True)
        (self.image / "runtime" / "bin").mkdir(parents=True)
        (self.image / "Kairos.exe").write_bytes(b"MZ")
        (self.image / "app" / "kairos.jar").write_bytes(b"PK")
        self.out = root / "layout"

    def tearDown(self):
        self.tmp.cleanup()

    def test_the_version_follows_the_installer_and_ends_with_zero(self):
        # Même règle que AppVersion.packageVersion (AppVersionTest.desktopPackageVersionsGrowToo), plus le « .0 » du Store.
        self.assertEqual("4.6.99.0", mm.msix_version("4.6.0"))
        self.assertEqual("4.6.2.0", mm.msix_version("4.6.0-beta.2"))
        self.assertEqual("4.6.199.0", mm.msix_version("4.6.1"))
        self.assertLess(tuple(map(int, mm.msix_version("4.6.0-beta.9").split("."))),
                        tuple(map(int, mm.msix_version("4.6.0").split("."))))
        with self.assertRaises(ValueError):
            mm.msix_version("4.6")
        self.assertTrue(mm.msix_version(mm.version_name()).endswith(".0"))

    def test_the_layout_holds_the_image_the_manifest_the_logos_and_the_managed_marker(self):
        mm.build(self.image, self.out, "4.6.0")
        self.assertTrue((self.out / "Kairos.exe").is_file())
        self.assertTrue((self.out / "app" / "kairos.jar").is_file())
        self.assertTrue((self.out / "app" / mm.MANAGED).is_file())
        self.assertFalse((self.out / "app" / mm.PORTABLE).exists())
        # L'image d'origine (celle du zip portable) n'est pas touchée.
        self.assertFalse((self.image / "app" / mm.MANAGED).exists())
        package = ET.parse(self.out / "AppxManifest.xml").getroot()
        identity = package.find("m:Identity", NS)
        self.assertEqual("SKOHscripts.KairosPlanner", identity.get("Name"))
        self.assertEqual("CN=EF0DE618-1AD9-4656-8417-18B58EB3603F", identity.get("Publisher"))
        self.assertEqual("4.6.99.0", identity.get("Version"))
        self.assertEqual("Kairos Planner", package.findtext("m:Properties/m:DisplayName", namespaces=NS))
        self.assertEqual("disabled",
                         package.findtext("m:Properties/desktop6:FileSystemWriteVirtualization", namespaces=NS))
        app = package.find("m:Applications/m:Application", NS)
        self.assertEqual("Kairos.exe", app.get("Executable"))
        self.assertEqual("Kairos Planner", app.find("uap:VisualElements", NS).get("DisplayName"))
        capabilities = {c.get("Name") for c in package.findall("m:Capabilities/rescap:Capability", NS)}
        self.assertEqual({"runFullTrust", "unvirtualizedResources"}, capabilities)
        languages = [r.get("Language") for r in package.findall("m:Resources/m:Resource", NS)]
        self.assertEqual(["en-US", "fr-FR", "es-ES", "de-DE", "ru-RU", "it-IT"], languages)
        self.assertNotIn("@", (self.out / "AppxManifest.xml").read_text(encoding="utf-8"))

    def test_every_logo_of_the_manifest_exists_at_every_scale(self):
        mm.build(self.image, self.out, "4.6.0")
        package = ET.parse(self.out / "AppxManifest.xml").getroot()
        visual = package.find("m:Applications/m:Application/uap:VisualElements", NS)
        logos = [package.findtext("m:Properties/m:Logo", namespaces=NS),
                 visual.get("Square150x150Logo"), visual.get("Square44x44Logo")]
        for logo in logos:
            base = logo.replace("\\", "/").removesuffix(".png")
            for scale in (100, 200, 400):
                self.assertTrue((self.out / f"{base}.scale-{scale}.png").is_file(), f"{base} à l'échelle {scale}")
        for size in (16, 24, 32, 48, 256):
            self.assertTrue((self.out / f"Assets/Square44x44Logo.targetsize-{size}_altform-unplated.png").is_file())

    def test_an_image_with_the_portable_marker_or_no_launcher_is_refused(self):
        (self.image / "app" / mm.PORTABLE).write_text("")
        with self.assertRaises(ValueError):
            mm.build(self.image, self.out, "4.6.0")
        (self.image / "app" / mm.PORTABLE).unlink()
        (self.image / "Kairos.exe").unlink()
        with self.assertRaises(ValueError):
            mm.build(self.image, self.out, "4.6.0")


if __name__ == "__main__":
    unittest.main()
