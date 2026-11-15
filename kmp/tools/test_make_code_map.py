"""Tests de la carte du code (docs/spec/carte-du-code.md) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import pathlib
import tempfile
import unittest

import make_code_map as cm

PKG = "com.skohscripts.kairos"


class CodeMapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        self.write("kmp/core/src/commonMain/kotlin/com/skohscripts/kairos/core/model/Model.kt", f"""package {PKG}.core.model

data class Task(val id: Long)
private fun hidden() = Unit
internal object Helpers
""")
        self.write("kmp/core/src/commonMain/kotlin/com/skohscripts/kairos/core/stats/Stats.kt", f"""package {PKG}.core.stats

import {PKG}.core.model.Task
import {PKG}.core.stats.Other
import kotlinx.datetime.LocalDate

object Stats {{
    fun inner() = Unit
}}
fun Task.total(): Int = 0
""")
        # Les tests ne comptent pas.
        self.write("kmp/core/src/commonTest/kotlin/com/skohscripts/kairos/core/stats/StatsTest.kt", f"package {PKG}.core.stats\n\nclass StatsTest\n")
        self.write("kmp/ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/stats/StatsScreen.kt", f"""package {PKG}.ui.stats

import {PKG}.core.stats.Stats
import {PKG}.core.model.Task

@Composable
fun StatsScreen() = Unit
""")
        self.write("docs/spec/statistiques.md", "# Statistiques\n\nFichiers : `core/stats/Stats.kt`, `kmp/ui/.../ui/stats/StatsScreen.kt`.\n")
        self.write("docs/spec/modele.md", "# Modèle\n\nLe modèle vit dans `Model.kt` ; voir aussi `Absent.kt`.\n")

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding="utf-8")

    def packages(self):
        return {p.name: p for p in cm.build(self.root)[0]}

    def test_reads_packages_declarations_and_imports(self):
        packages = self.packages()
        self.assertEqual(["core.model", "core.stats", "ui.stats"], list(packages))
        model = packages["core.model"].files[0]
        self.assertEqual(["data class Task", "object Helpers"], model.decls)  # la fonction privée n'y est pas
        stats = packages["core.stats"].files[0]
        self.assertEqual(["object Stats", "fun total"], stats.decls)  # « inner » n'est pas de premier niveau
        self.assertEqual(10, stats.lines)
        self.assertEqual(["commonMain"], packages["core.stats"].source_sets)
        self.assertEqual(1, len(packages["core.stats"].files))  # StatsTest.kt est un test

    def test_package_edges_skip_self_and_external(self):
        packages = self.packages()
        self.assertEqual(["core.model"], packages["core.stats"].deps)  # ni lui-même, ni kotlinx
        self.assertEqual(["core.model", "core.stats"], packages["ui.stats"].deps)
        self.assertEqual([], packages["core.model"].deps)

    def test_links_files_to_specs(self):
        packages = self.packages()
        self.assertEqual(["statistiques.md"], packages["core.stats"].files[0].specs)
        self.assertEqual(["statistiques.md"], packages["ui.stats"].files[0].specs)  # chemin avec « ... »
        self.assertEqual(["modele.md"], packages["core.model"].files[0].specs)  # nom seul
        self.assertEqual("Statistiques", cm.read_specs(self.root)["statistiques.md"][0])

    def test_index_filter(self):
        packages = cm.build(self.root)[0]
        text = cm.index(packages, "ui/stats")
        self.assertIn("## ui.stats", text)
        self.assertNotIn("## core.model", text)
        self.assertIn("Dépend de : core.model, core.stats", text)
        whole = cm.index(packages)
        self.assertIn("(3 paquets, 3 fichiers)", whole)
        self.assertIn("· specs statistiques.md", whole)

    def test_unlinked_files_are_flagged(self):
        self.write("kmp/core/src/commonMain/kotlin/com/skohscripts/kairos/core/model/Lonely.kt", f"package {PKG}.core.model\n\nobject Lonely\n")
        self.assertIn("Lonely.kt` (3) object Lonely · sans spec", cm.index(cm.build(self.root)[0]))

    def test_output_is_deterministic(self):
        packages, specs = cm.build(self.root)
        first = cm.page(cm.data(packages, specs, None))
        packages, specs = cm.build(self.root)
        self.assertEqual(first, cm.page(cm.data(packages, specs, None)))
        self.assertIn("Commit 0123456789ab.", cm.page(cm.data(packages, specs, "0123456789abcdef")))

    def test_page_is_self_contained(self):
        packages, specs = cm.build(self.root)
        page = cm.page(cm.data(packages, specs, None))
        # Aucune ressource tierce : seuls des liens (a href) vers GitHub, et les polices du site lui-même.
        for marker in ("<script src", "<link rel=\"stylesheet\"", "@import", "url(\"http", "url(http"):
            self.assertNotIn(marker, page)
        self.assertIn('data-id="ui:ui.stats"', page)
        self.assertIn('data-from="ui:ui.stats" data-to="core:core.model"', page)
        self.assertIn("https://github.com/SKOHscripts/Kairos/blob/main/docs/spec/statistiques.md", page)

    def test_page_carries_the_site_menu(self):
        packages, specs = cm.build(self.root)
        page = cm.page(cm.data(packages, specs, None), web="app/")
        # Les six entrées du menu du site (publication.md § Menu du site), deux fois (rangée et bouton), « Code map » courant.
        for label, href in [("Download", "../en/"), ("Story", "../en/story/"), ("Web version", "../app/"), ("Code map", "./")]:
            self.assertEqual(2, page.count(f'href="{href}"'), label)
        self.assertEqual(2, page.count('href="./" aria-current="page">Code map<'))
        self.assertIn("../fonts/roboto_regular.ttf", page)


if __name__ == "__main__":
    unittest.main()
