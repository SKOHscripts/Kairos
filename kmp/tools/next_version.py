"""Version que `main` doit porter (docs/spec/distribution.md § Version attendue).

    python3 kmp/tools/next_version.py           # résumé, code 0
    python3 kmp/tools/next_version.py --check   # code 1 si la version de main est trop basse (CI : job version de « Kairos checks »)

Lit la dernière finale (plus haut tag vX.Y.Z), ce qui a été fusionné depuis (cartes du catalogue, format du fichier,
commits) et la version déclarée dans kmp/gradle.properties. Sans dépendance ; a besoin des tags (fetch-depth: 0 en CI).
Sous GitHub Actions, écrit les annotations (::error::, ::warning::, ::notice::) et le résumé du job.
"""
from __future__ import annotations

import datetime
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES = "kmp/gradle.properties"
CATALOG = "kmp/ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/guide/FeatureCatalog.kt"
CODEC = "kmp/data/src/commonMain/kotlin/com/skohscripts/kairos/data/ExportCodec.kt"
LOCALES = ["en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "ru-RU"]
# Repère d'une semaine (celui des betas) : des fonctionnalités qui attendent plus longtemps méritent une finale.
WAITING_DAYS = 7

FINAL_TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-(?:alpha|beta)\.\d+)?$")
CARD = re.compile(r'FeatureCard\(\s*"([\w-]+)".*?since\s*=\s*"(\d+\.\d+\.\d+)"', re.S)
SYNC_FORMAT = re.compile(r"SYNC_FORMAT_VERSION\s*=\s*(\d+)")

Version = tuple[int, int, int]

MAJOR, MINOR, PATCH, NONE = "majeure", "mineure", "correctif", "aucun"


def parse(name: str) -> Version:
    """X.Y.Z d'une version, suffixe de préversion ignoré."""
    m = VERSION.match(name)
    if not m:
        raise ValueError(f"version illisible : {name}")
    return int(m[1]), int(m[2]), int(m[3])


def show(v: Version) -> str:
    return ".".join(map(str, v))


def last_final(tags: list[str]) -> Version | None:
    """Plus haute finale parmi les tags, en ordre numérique (v4.10.0 après v4.9.0)."""
    finals = [tuple(int(g) for g in m.groups()) for m in map(FINAL_TAG.match, tags) if m]
    return max(finals) if finals else None


def cards(catalog: str) -> dict[str, Version]:
    """Cartes du catalogue → version qui les annonce."""
    return {card_id: parse(since) for card_id, since in CARD.findall(catalog)}


def sync_format(codec: str | None) -> int | None:
    m = SYNC_FORMAT.search(codec or "")
    return int(m[1]) if m else None


def level(new_cards: list[str], format_then: int | None, format_now: int | None, commits: int) -> str:
    """Niveau de ce qui est fusionné depuis la dernière finale (§ Quel numéro donner à une version)."""
    if format_then is not None and format_now is not None and format_now != format_then:
        return MAJOR
    if new_cards:
        return MINOR
    return PATCH if commits else NONE


def bump(final: Version, lvl: str) -> Version:
    x, y, z = final
    return {MAJOR: (x + 1, 0, 0), MINOR: (x, y + 1, 0), PATCH: (x, y, z + 1), NONE: final}[lvl]


@dataclass
class Report:
    final: Version | None
    current: str
    expected: Version | None = None
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    notices: list[str] = field(default_factory=list)
    lines: list[str] = field(default_factory=list)


def evaluate(final: Version | None, current: str, catalog_now: str, format_then: int | None, format_now: int | None,
             subjects: list[str], waiting_since: datetime.date | None, today: datetime.date,
             missing_notes: list[str]) -> Report:
    """Le verdict, sans git ni fichiers : testable (test_next_version.py)."""
    report = Report(final, current)
    if final is None:
        report.notices.append("aucune version finale taguée : rien à contrôler")
        return report
    new_cards = sorted(card for card, since in cards(catalog_now).items() if since > final)
    lvl = level(new_cards, format_then, format_now, len(subjects))
    expected = bump(final, lvl)
    report.expected = expected
    base = parse(current)
    command = f"python3 kmp/tools/set_version.py {show(expected)}"
    report.lines += [
        f"Dernière finale : {show(final)} ; depuis : {len(subjects)} commit(s), niveau **{lvl}**.",
        f"Version de main : {current} ; attendue : {show(expected)} au moins.",
    ]
    if new_cards:
        report.lines.append("Fonctionnalités en attente (cartes) : " + ", ".join(f"`{c}`" for c in new_cards) + ".")
    if lvl == MAJOR:
        report.lines.append(f"Format du fichier : {format_then} → {format_now} (version majeure).")
    if base < expected:
        if base == final:
            why = f"main porte encore la finale publiée {show(final)}"
        else:
            why = f"ce qui est fusionné depuis {show(final)} demande une version {lvl}"
        report.errors.append(f"{why} : lancer `{command}` dans cette PR.")
    elif lvl == PATCH and base > expected:
        report.notices.append(f"seuls des correctifs depuis {show(final)} : un correctif {show(expected)} est possible "
                              f"(`{command}`, notes de version, « Kairos tag », puis retour à {show(base)}).")
    if not new_cards and lvl != MAJOR and any(re.match(r"^feat(\(|!|:)", s) for s in subjects):
        report.warnings.append("des commits `feat` sans carte nouvelle du catalogue : si un comportement visible change, "
                               "la version est une mineure (docs/spec/distribution.md § Quel numéro).")
    if new_cards and waiting_since is not None:
        days = (today - waiting_since).days
        if days >= WAITING_DAYS:
            report.warnings.append(f"{len(new_cards)} fonctionnalité(s) attendent une finale depuis {days} jours : "
                                   f"il est temps de publier {show(max(base, expected))}.")
    if "-" not in current and base > final:
        if missing_notes:
            report.lines.append(f"Pour publier {current} : notes de version à écrire ({', '.join(missing_notes)}), "
                                "puis « Kairos tag ».")
        else:
            report.lines.append(f"Pour publier {current} : notes de version prêtes, lancer « Kairos tag ».")
    return report


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def git_show(tag: str, path: str) -> str | None:
    try:
        return git("show", f"{tag}:{path}")
    except subprocess.CalledProcessError:
        return None


def property_value(key: str) -> str:
    for line in (ROOT / PROPERTIES).read_text(encoding="utf-8").splitlines():
        if line.startswith(f"kairos.{key}="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"kairos.{key} absent de {PROPERTIES}")


def collect(today: datetime.date) -> Report:
    final = last_final(git("tag", "-l", "v*").split())
    current = property_value("versionName")
    code = property_value("versionCode")
    missing = [loc for loc in LOCALES
               if not (ROOT / f"fastlane/metadata/android/{loc}/changelogs/{code}.txt").is_file()]
    catalog_now = (ROOT / CATALOG).read_text(encoding="utf-8")
    format_now = sync_format((ROOT / CODEC).read_text(encoding="utf-8"))
    if final is None:
        return evaluate(None, current, catalog_now, None, format_now, [], None, today, missing)
    tag = f"v{show(final)}"
    subjects = [s for s in git("log", "--format=%s", f"{tag}..HEAD").splitlines() if s]
    touched = git("log", "--reverse", "--format=%cs", f"{tag}..HEAD", "--", CATALOG).split()
    waiting = datetime.date.fromisoformat(touched[0]) if touched else None
    return evaluate(final, current, catalog_now, sync_format(git_show(tag, CODEC)), format_now, subjects, waiting,
                    today, missing)


def main(argv: list[str]) -> int:
    report = collect(datetime.date.today())
    actions = os.environ.get("GITHUB_ACTIONS") == "true"
    for kind, messages in (("error", report.errors), ("warning", report.warnings), ("notice", report.notices)):
        for message in messages:
            print(f"::{kind}::{message}" if actions else f"{kind} : {message}")
    summary = ["### Version attendue", *report.lines,
               *(f"- ❌ {m}" for m in report.errors), *(f"- ⚠️ {m}" for m in report.warnings),
               *(f"- {m}" for m in report.notices)]
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as out:
            out.write("\n".join(summary) + "\n")
    print("\n".join(summary))
    return 1 if "--check" in argv and report.errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
