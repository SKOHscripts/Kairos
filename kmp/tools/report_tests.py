"""Rapport des tests d'un lancement de CI (docs/spec/distribution.md § CI et release).

Lit les rapports JUnit que Gradle écrit (`<module>/build/test-results/<tâche>/TEST-*.xml`) et ajoute au résumé du job
(`$GITHUB_STEP_SUMMARY`, sinon la sortie standard) un tableau par module et par tâche (tests, échecs, ignorés, durée) puis
la liste des tests en échec. Lancé après les tests, même en échec (`if: always()`) : il ne fait jamais échouer le job.

Sans dépendance :

    python3 kmp/tools/report_tests.py [racine, par défaut kmp] [--title "Tests JVM"]
"""
from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAX_FAILURES = 30


@dataclass
class Row:
    module: str
    task: str
    tests: int = 0
    failures: int = 0
    skipped: int = 0
    seconds: float = 0.0
    failed: list[str] = field(default_factory=list)


def collect(root: Path) -> list[Row]:
    """Une ligne par (module, tâche), dans l'ordre des chemins."""
    rows: dict[tuple[str, str], Row] = {}
    for xml in sorted(root.glob("**/build/test-results/*/TEST-*.xml")):
        parts = xml.relative_to(root).parts
        module = "/".join(parts[:parts.index("build")]) or "."
        task = xml.parent.name
        row = rows.setdefault((module, task), Row(module, task))
        try:
            suite = ET.parse(xml).getroot()
        except ET.ParseError:
            row.failed.append(f"{xml.name} : rapport illisible")
            row.failures += 1
            continue
        row.tests += int(suite.get("tests", 0))
        row.skipped += int(suite.get("skipped", 0))
        row.failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        row.seconds += float(suite.get("time", 0) or 0)
        for case in suite.iter("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                message = (problem.get("message") or problem.text or "").strip().splitlines()
                row.failed.append(f"`{case.get('classname')}.{case.get('name')}` : {message[0][:200] if message else ''}")
    return list(rows.values())


def markdown(rows: list[Row], title: str = "Tests") -> str:
    if not rows:
        return f"### {title}\n\nAucun rapport de test trouvé.\n"
    total = Row("**Total**", "", sum(r.tests for r in rows), sum(r.failures for r in rows), sum(r.skipped for r in rows),
                sum(r.seconds for r in rows))
    icon = "✅" if total.failures == 0 else "❌"
    lines = [f"### {icon} {title}", "", "| Module | Tâche | Tests | Échecs | Ignorés | Durée |", "|---|---|---:|---:|---:|---:|"]
    for r in rows + [total]:
        lines.append(f"| {r.module} | {r.task} | {r.tests} | {r.failures} | {r.skipped} | {r.seconds:.0f} s |")
    failed = [f for r in rows for f in r.failed]
    if failed:
        lines += ["", f"**En échec ({len(failed)})**", ""] + [f"- {f}" for f in failed[:MAX_FAILURES]]
        if len(failed) > MAX_FAILURES:
            lines.append(f"- … et {len(failed) - MAX_FAILURES} autres")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    title = "Tests"
    if "--title" in argv:
        i = argv.index("--title")
        title = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    root = Path(argv[0]) if argv else ROOT / "kmp"
    text = markdown(collect(root), title)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write(text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
