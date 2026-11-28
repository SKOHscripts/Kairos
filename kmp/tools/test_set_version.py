"""Tests de set_version.py et du lien avec Release Please : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import json
import re
import unittest

import set_version as sv


class SetVersionTest(unittest.TestCase):
    def test_codes_follow_the_formula(self):
        self.assertEqual(4060002, sv.version_code("4.6.0-beta.2"))
        self.assertEqual(4060099, sv.version_code("4.6.0"))
        self.assertEqual(40100, sv.version_code("4.1.0"))

    def test_release_as_is_rewritten_and_the_rest_kept(self):
        config = '{\n  "release-type": "simple",\n  "packages": {\n    ".": {\n      "release-as": "4.6.0"\n    }\n  }\n}\n'
        out = sv.set_release_as(config, "4.7.0-beta.1")
        self.assertEqual("4.7.0-beta.1", json.loads(out)["packages"]["."]["release-as"])
        self.assertEqual("simple", json.loads(out)["release-type"])
        self.assertTrue(out.endswith("}\n"))

    def test_release_please_proposes_the_version_of_main(self):
        # docs/spec/distribution.md § Release Please : la PR de release propose la version de kmp/gradle.properties.
        props = (sv.ROOT / "kmp" / "gradle.properties").read_text(encoding="utf-8")
        name = re.search(r"^kairos\.versionName=(.+)$", props, re.M).group(1).strip()
        config = json.loads(sv.RELEASE_PLEASE.read_text(encoding="utf-8"))
        self.assertEqual(name, config["packages"]["."]["release-as"],
                         "release-please-config.json en retard : python3 kmp/tools/set_version.py " + name)
        self.assertTrue(config["skip-github-release"], "le tag et la release restent à « Kairos tag »")
        self.assertFalse(config["include-component-in-tag"])


if __name__ == "__main__":
    unittest.main()
