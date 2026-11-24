"""Génère la page « Habitudes » du site (docs/spec/habitudes.md § README, site, magasins) dans les six langues.

Source unique : les affirmations, leurs niveaux et leurs références de
`core/.../habit/HabitScience.kt`, et les textes des `strings.xml` de l'interface
(mêmes phrases que l'écran « Sources scientifiques »). Rien n'est recopié : la page
suit l'application. Bibliothèque standard seulement.

    python3 kmp/tools/make_habits_page.py <dossier du site assemblé>

écrit `habitudes/index.html` (français) et `<langue>/habits/index.html` (en, es,
de, ru, it). `--check` vérifie seulement que tout se lit (aucune affirmation sans
texte, aucune référence inconnue).
"""
import html
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

KMP = pathlib.Path(__file__).resolve().parent.parent
SCIENCE = KMP / "core/src/commonMain/kotlin/com/skohscripts/kairos/core/habit/HabitScience.kt"
RES = KMP / "ui/src/commonMain/composeResources"
LANGS = {"fr": "values-fr", "en": "values", "es": "values-es", "de": "values-de", "ru": "values-ru", "it": "values-it"}
# Emplacement de chaque page, relatif à la racine du site ; le français est à la racine, comme la page de téléchargement.
PATHS = {"fr": "habitudes", "en": "en/habits", "es": "es/habits", "de": "de/habits", "ru": "ru/habits", "it": "it/habits"}
LEVEL_KEY = {"SOLID": "habit_level_solid", "MODERATE": "habit_level_moderate", "WEAK": "habit_level_weak"}


def science():
    text = SCIENCE.read_text(encoding="utf-8")
    refs = {m[0]: (m[1], m[2]) for m in re.findall(r'Reference\(\s*"(R\d+)",\s*"((?:[^"\\]|\\.)*)",\s*"([^"]+)",?\s*\)', text, re.S)}
    claims = [(m[0], m[1], re.findall(r'"(R\d+)"', m[2])) for m in re.findall(r'Claim\("(H\d+)", EvidenceLevel\.(\w+), listOf\(([^)]*)\)\)', text)]
    return refs, claims


def strings(lang):
    root = ET.parse(RES / LANGS[lang] / "strings.xml").getroot()
    return {e.get("name"): "".join(e.itertext()) for e in root.iter("string")}


def fmt(template, *args):
    for i, a in enumerate(args, 1):
        template = template.replace(f"%{i}$s", a)
    return template


def page(lang, refs, claims, t):
    depth = PATHS[lang].count("/") + 1
    up = "../" * depth
    alternates = "\n".join(
        f'<link rel="alternate" hreflang="{l}" href="{up}{PATHS[l]}/">' for l in PATHS if l != lang
    )
    langs = " · ".join(
        f'<a href="{up}{PATHS[l]}/" hreflang="{l}" lang="{l}">{html.escape(strings(l)["language_" + l])}</a>' for l in PATHS if l != lang
    )
    items = []
    for cid, level, ids in claims:
        cites = "".join(
            f'<li>{html.escape(refs[r][0])} <a href="https://doi.org/{html.escape(refs[r][1])}">doi:{html.escape(refs[r][1])}</a></li>'
            for r in ids
        )
        items.append(
            f'  <h3>{html.escape(t["habit_claim_" + cid.lower()])}</h3>\n'
            f'  <p class="sources">{html.escape(fmt(t["habit_level"], t[LEVEL_KEY[level]]))}</p>\n'
            f'  <ul class="plain">{cites}</ul>'
        )
    title = t["settings_section_habits"]
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Kairos · {html.escape(title)}</title>
<meta name="description" content="{html.escape(t["habit_sources_intro"])}">
<link rel="icon" href="{up}favicon.png">
{alternates}
<link rel="stylesheet" href="{up}style.css">
</head>
<body>
<header>
  <img src="{up}icon.png" alt="">
  <span class="name">Kairos</span>
  <nav class="langs">{langs}</nav>
</header>
<main class="story">
  <section class="hero">
    <h1>{html.escape(title)}</h1>
  </section>
  <p>{html.escape(t["settings_section_habits_body"])}</p>
  <h2>{html.escape(t["habit_sources_title"])}</h2>
  <p>{html.escape(t["habit_sources_intro"])}</p>
{chr(10).join(items)}
  <p>{html.escape(t["habit_sources_not_claimed"])}</p>
  <p>{html.escape(t["settings_habits_limits"])}</p>
  <p>{html.escape(t["habit_sources_translation"])}</p>
  <p class="cta"><a class="button filled" href="{up if lang == "fr" else up + lang + "/"}">Kairos</a></p>
</main>
</body>
</html>
"""


def main(argv):
    refs, claims = science()
    if not claims:
        sys.exit("aucune affirmation lue dans HabitScience.kt")
    for _, _, ids in claims:
        for r in ids:
            if r not in refs:
                sys.exit(f"référence inconnue : {r}")
    pages = {}
    for lang in PATHS:
        t = strings(lang)
        missing = [c for c, _, _ in claims if "habit_claim_" + c.lower() not in t]
        if missing:
            sys.exit(f"{lang} : textes manquants {missing}")
        pages[lang] = page(lang, refs, claims, t)
    if argv[:1] == ["--check"]:
        print(f"{len(claims)} affirmations, {len(refs)} références, {len(pages)} langues")
        return
    out = pathlib.Path(argv[0])
    for lang, text in pages.items():
        target = out / PATHS[lang] / "index.html"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        print(f"Écrit : {target}")


if __name__ == "__main__":
    main(sys.argv[1:])
