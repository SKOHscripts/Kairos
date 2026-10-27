"""Vérifie les boutons de téléchargement (docs/spec/publication.md § Boutons de téléchargement).

Échoue (code 1) si :

- un des 42 badges `site/badges/<langue>/<id>.svg` (6 langues × 7 boutons) manque ou n'est pas un SVG ;
- une page (`site/index.html`, `site/<langue>/index.html`) ne porte pas une rangée des sept boutons dans sa langue, ou un README
  (`README.md`, `README.fr.md`) pas deux (en tête, puis dans « Install »), ou référence un badge qui n'existe pas ;
- « bientôt sur F-Droid » (ou son équivalent) reste dans une page ou un README.

Sans dépendance :

    python kmp/tools/validate_badges.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LANGUAGES = ("en", "fr", "es", "de", "ru", "it")
IDS = ("fdroid", "obtainium", "apk", "windows", "linux", "macos", "web")
PAGES = {"fr": "site/index.html", **{lang: f"site/{lang}/index.html" for lang in LANGUAGES if lang != "fr"}}
READMES = {"en": "README.md", "fr": "README.fr.md"}
SOON = ("coming soon to f-droid", "f-droid coming soon", "bientôt sur f-droid", "próximamente en f-droid",
        "bald auf f-droid", "скоро в f-droid", "presto su f-droid")


def main() -> None:
    errors: list[str] = []
    for lang in LANGUAGES:
        for ident in IDS:
            path = ROOT / "site/badges" / lang / f"{ident}.svg"
            if not path.exists():
                errors.append(f"badge absent : {path.relative_to(ROOT)}")
            elif not path.read_text(encoding="utf-8").startswith("<svg"):
                errors.append(f"{path.relative_to(ROOT)} n'est pas un SVG")
    for lang, name in {**{k: v for k, v in PAGES.items()}, **{f"readme-{k}": v for k, v in READMES.items()}}.items():
        text = (ROOT / name).read_text(encoding="utf-8")
        code = lang.removeprefix("readme-")
        used = re.findall(r"badges/([a-z]{2})/([a-z]+)\.svg", text)
        # Une rangée sur une page ; deux dans un README (en tête, puis dans « Install »).
        rows = 2 if lang.startswith("readme-") else 1
        if sorted(i for _, i in used) != sorted(IDS * rows):
            errors.append(f"{name} : {rows} rangée(s) des sept boutons attendue(s) ({', '.join(IDS)}), trouvés : {[i for _, i in used]}")
        for found_lang, ident in used:
            if found_lang != code:
                errors.append(f"{name} : badge dans la mauvaise langue ({found_lang}/{ident})")
            if not (ROOT / "site/badges" / found_lang / f"{ident}.svg").exists():
                errors.append(f"{name} : badge inexistant {found_lang}/{ident}")
        lowered = text.lower()
        for phrase in SOON:
            if phrase in lowered:
                errors.append(f"{name} : « {phrase} » doit disparaître (F-Droid est publié)")
    if errors:
        print("\n".join(f"ERREUR : {e}" for e in errors))
        sys.exit(1)
    print(f"Boutons de téléchargement valides ({len(LANGUAGES)} langues × {len(IDS)} boutons, pages et README).")


if __name__ == "__main__":
    main()
