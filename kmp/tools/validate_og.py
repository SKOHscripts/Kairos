"""Vérifie l'aperçu des liens de la page de téléchargement (docs/spec/publication.md § Aperçu des liens).

Échoue (code 1) si une page (`site/index.html`, `site/<langue>/index.html`) :

- n'a pas ses balises Open Graph (`og:title`, `og:description`, `og:url`, `og:locale`, `og:image` et ses dimensions,
  `og:image:alt`) et `twitter:card` ;
- a un `og:title` différent de son `<title>`, un `og:description` différent de sa description, une adresse ou une image
  qui ne sont pas les siennes (`og/<langue>.png`) ;
- ou si l'image `site/og/<langue>.png` manque ou n'est pas un PNG de 1280×640.

Sans dépendance :

    python kmp/tools/validate_og.py
"""
from __future__ import annotations

import re
import struct
import sys
from html import unescape
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = "https://skohscripts.github.io/Kairos/"
LOCALES = {"fr": "fr_FR", "en": "en_US", "es": "es_ES", "de": "de_DE", "ru": "ru_RU", "it": "it_IT"}
SIZE = (1280, 640)


def meta(text: str, attr: str, key: str) -> str | None:
    m = re.search(rf'<meta {attr}="{re.escape(key)}" content="([^"]*)">', text)
    return unescape(m.group(1)) if m else None


def png_size(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()[:24]
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def main() -> None:
    errors: list[str] = []
    for lang, locale in LOCALES.items():
        name = "site/index.html" if lang == "fr" else f"site/{lang}/index.html"
        text = (ROOT / name).read_text(encoding="utf-8")
        title = unescape(re.search(r"<title>([^<]*)</title>", text).group(1))
        expected = {
            ("property", "og:type"): "website",
            ("property", "og:site_name"): "Kairos",
            ("property", "og:title"): title,
            ("property", "og:description"): meta(text, "name", "description"),
            ("property", "og:url"): BASE + ("" if lang == "fr" else f"{lang}/"),
            ("property", "og:locale"): locale,
            ("property", "og:image"): f"{BASE}og/{lang}.png",
            ("property", "og:image:width"): str(SIZE[0]),
            ("property", "og:image:height"): str(SIZE[1]),
            ("property", "og:image:alt"): title,
            ("name", "twitter:card"): "summary_large_image",
        }
        for (attr, key), value in expected.items():
            found = meta(text, attr, key)
            if found != value:
                errors.append(f"{name} : {key} vaut {found!r}, attendu {value!r}")
        image = ROOT / "site/og" / f"{lang}.png"
        if not image.exists():
            errors.append(f"image absente : {image.relative_to(ROOT)} (kmp/tools/make_app_icons.py)")
        elif png_size(image) != SIZE:
            errors.append(f"{image.relative_to(ROOT)} : PNG de {SIZE[0]}×{SIZE[1]} attendu, trouvé {png_size(image)}")
    if errors:
        print("\n".join(f"ERREUR : {e}" for e in errors))
        sys.exit(1)
    print(f"Aperçu des liens valide ({len(LOCALES)} pages, images {SIZE[0]}×{SIZE[1]}).")


if __name__ == "__main__":
    main()
