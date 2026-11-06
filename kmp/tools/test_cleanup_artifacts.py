"""Tests de la règle de nettoyage des artefacts (cleanup_artifacts.plan) : python3 -m unittest discover -s kmp/tools -p 'test_*.py'."""
import unittest

from cleanup_artifacts import Artifact, Run, plan


def run(id, workflow, status="completed", day=1):
    return Run(id, workflow, status, f"2026-11-{day:02d}T10:00:00Z")


class PlanTest(unittest.TestCase):
    def test_keeps_the_two_newest_completed_runs_of_each_workflow(self):
        runs = {1: run(1, 10, day=1), 2: run(2, 10, day=2), 3: run(3, 10, day=3), 4: run(4, 20, day=1)}
        artifacts = [Artifact(100 + i, "kmp-ci-screens", i) for i in (1, 2, 3)] + [Artifact(200, "fdroid-unsigned-apk", 4)]
        self.assertEqual([101], [a.id for a in plan(artifacts, runs, keep=2)])

    def test_never_touches_a_run_in_progress(self):
        # Une release en cours (la plus ancienne ou non) garde ses artefacts, et ne compte pas parmi les runs gardés.
        runs = {1: run(1, 10, status="in_progress", day=1), 2: run(2, 10, day=2), 3: run(3, 10, day=3), 4: run(4, 10, day=4)}
        artifacts = [Artifact(100 + i, f"kairos3-{i}", i) for i in (1, 2, 3, 4)]
        self.assertEqual([102], [a.id for a in plan(artifacts, runs, keep=2)])

    def test_artifacts_of_a_vanished_run_go(self):
        self.assertEqual([1], [a.id for a in plan([Artifact(1, "x", 9)], {9: None}, keep=2)])
        self.assertEqual([2], [a.id for a in plan([Artifact(2, "x", None)], {}, keep=2)])

    def test_several_artifacts_of_one_run_count_as_one_run(self):
        runs = {1: run(1, 10, day=1), 2: run(2, 10, day=2)}
        artifacts = [Artifact(11, "a", 1), Artifact(12, "b", 1), Artifact(21, "a", 2)]
        self.assertEqual([11, 12], sorted(a.id for a in plan(artifacts, runs, keep=1)))


if __name__ == "__main__":
    unittest.main()
