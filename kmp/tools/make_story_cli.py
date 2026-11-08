#!/usr/bin/env python3
"""Reconstitution de la toute première version de Kairos : un outil en terminal, avant tout git.

Écrit `site/story/cli-fr.svg` et `site/story/cli-en.svg` (page « histoire », docs/spec/publication.md).
La version d'origine n'a laissé aucune trace : ce n'est **pas** une capture, c'est une reconstitution d'après les
souvenirs du propriétaire (deux commandes, `kairos --capture="…" --prio=N --fibo=N` et `kairos --list`, des tâches triées
par score décroissant, ni durée ni échéance). Le score est celui de Kairos sans l'échéance : 4^(2 − priorité) / points.
Usage : python3 kmp/tools/make_story_cli.py
"""
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

TASKS = {  # (titre fr, titre en, priorité, points de Fibonacci), dans l'ordre de capture
    "fr": [("réparer le script de sauvegarde", 0, 3), ("répondre au mail du fournisseur", 1, 1),
           ("préparer la démo de jeudi", 1, 5), ("mettre à jour la doc", 2, 1), ("ranger les notes de la semaine", 2, 3)],
    "en": [("fix the backup script", 0, 3), ("answer the supplier’s email", 1, 1),
           ("prepare Thursday’s demo", 1, 5), ("update the docs", 2, 1), ("sort this week’s notes", 2, 3)],
}
CAPTURED = {"fr": ("traiter la fiche #655", 1, 8), "en": ("process ticket #655", 1, 8)}
TEXT = {
    "fr": dict(done="Tâche capturée", head=("score", "prio", "pts", "tâche"), pts="pts"),
    "en": dict(done="Task captured", head=("score", "prio", "pts", "task"), pts="pts"),
}


def score(prio, fibo):
    return 4 ** (2 - prio) / fibo


def lines(lang):
    t = TEXT[lang]
    title, prio, fibo = CAPTURED[lang]
    tasks = sorted(TASKS[lang] + [(title, prio, fibo)], key=lambda x: -score(x[1], x[2]))
    out = [("cmd", f'$ kairos --capture="{title}" --prio="{prio}" --fibo="{fibo}"'),
           ("ok", f"{t['done']} : {title}" if lang == "fr" else f"{t['done']}: {title}"), ("", ""),
           ("cmd", "$ kairos --list"),
           ("dim", f"{t['head'][0]:>6}  {t['head'][1]:<4}  {t['head'][2]:>3}  {t['head'][3]}")]
    for name, p, f in tasks:
        out.append(("", f"{score(p, f):>6.2f}  P{p:<3}  {f:>3}  {name}"))
    return out


def svg(lang):
    rows = lines(lang)
    cw, lh, pad = 9.0, 24, 20
    width = int(max(len(r[1]) for r in rows) * cw + 2 * pad + 8)
    height = len(rows) * lh + 2 * pad + 8
    colors = {"cmd": "#E8EAF2", "ok": "#8FD6A0", "dim": "#8E93A6", "": "#C9CDDB"}
    body = "".join(
        f'<text x="{pad}" y="{pad + 16 + i * lh}" fill="{colors[k]}" xml:space="preserve">{escape(s)}</text>'
        for i, (k, s) in enumerate(rows)
    )
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
            f'font-family="DejaVu Sans Mono, Menlo, Consolas, monospace" font-size="15">'
            f'<rect width="{width}" height="{height}" rx="12" fill="#1B1D26"/>{body}</svg>\n')


if __name__ == "__main__":
    for lang in ("fr", "en"):
        (ROOT / "site/story" / f"cli-{lang}.svg").write_text(svg(lang), encoding="utf-8")
        print("\n".join(s for _, s in lines(lang)), "\n")
