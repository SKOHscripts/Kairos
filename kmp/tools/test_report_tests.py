"""Tests du rapport des tests (report_tests.py) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import pathlib
import tempfile
import unittest

import report_tests as rt


def suite(path: pathlib.Path, tests: int, failures: int = 0, skipped: int = 0, cases: str = "") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f'<testsuite name="S" tests="{tests}" skipped="{skipped}" failures="{failures}" errors="0" time="1.5">'
                    f"{cases}</testsuite>", encoding="utf-8")


class ReportTestsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_rows_by_module_and_task_with_failures_listed(self):
        suite(self.root / "core/build/test-results/jvmTest/TEST-a.xml", 3)
        suite(self.root / "core/build/test-results/jvmTest/TEST-b.xml", 2, skipped=1)
        failure = '<testcase classname="x.SyncTest" name="fast[jvm]"><failure message="trop lent&#10;détail">pile</failure></testcase>'
        suite(self.root / "ui/build/test-results/jvmTest/TEST-c.xml", 4, failures=1, cases=failure)
        rows = rt.collect(self.root)
        self.assertEqual([("core", "jvmTest", 5, 0, 1), ("ui", "jvmTest", 4, 1, 0)],
                         [(r.module, r.task, r.tests, r.failures, r.skipped) for r in rows])
        text = rt.markdown(rows, "Tests JVM")
        self.assertIn("### ❌ Tests JVM", text)
        self.assertIn("| **Total** |  | 9 | 1 | 1 |", text)
        self.assertIn("- `x.SyncTest.fast[jvm]` : trop lent", text)

    def test_all_green_and_nothing_found(self):
        suite(self.root / "core/build/test-results/wasmJsBrowserTest/TEST-a.xml", 7)
        self.assertIn("### ✅ Tests", rt.markdown(rt.collect(self.root)))
        self.assertIn("Aucun rapport", rt.markdown(rt.collect(self.root / "vide")))

    def test_an_unreadable_report_counts_as_a_failure(self):
        bad = self.root / "data/build/test-results/jvmTest/TEST-x.xml"
        bad.parent.mkdir(parents=True)
        bad.write_text("<testsuite", encoding="utf-8")
        rows = rt.collect(self.root)
        self.assertEqual(1, rows[0].failures)
        self.assertIn("illisible", rows[0].failed[0])


if __name__ == "__main__":
    unittest.main()
