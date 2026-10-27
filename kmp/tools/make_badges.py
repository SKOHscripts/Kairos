"""Génère les boutons de téléchargement : `site/badges/<langue>/<id>.svg` (docs/spec/publication.md
§ Boutons de téléchargement).

Badges aux couleurs du thème « miel » (primaire plein, texte blanc, pilule de 56 px). Le texte est converti en **tracés**
avec Roboto du dépôt (rendu identique partout, rien d'embarqué) ; les icônes sont des Material Symbols (Outlined 400,
Apache 2.0, comme `make_icons.py`), génériques : jamais un logo de marque.

Dépendances de développement seulement (la CI ne relance pas ce script) :

    python -m venv /tmp/badges && /tmp/badges/bin/pip install fonttools
    npm install --prefix /tmp/ms @material-symbols/svg-400@0.47.5
    /tmp/badges/bin/python kmp/tools/make_badges.py /tmp/ms/node_modules/@material-symbols/svg-400/outlined
"""
import pathlib
import re
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONTS = ROOT / "kmp/ui/src/commonMain/composeResources/font"
OUT = ROOT / "site/badges"

PRIMARY = "#7F5610"  # primaire du thème « miel » (clair), lisible sur fond clair et sombre
ON_PRIMARY = "#FFFFFF"
HEIGHT = 56
LANGUAGES = ("en", "fr", "es", "de", "ru", "it")

# id -> icône Material Symbols (générique, jamais un logo de marque)
ICONS = {
    "fdroid": "android",
    "obtainium": "update",
    "apk": "download",
    "windows": "desktop_windows",
    "linux": "terminal",
    "macos": "laptop_mac",
    "web": "language",
}

# id -> (petite ligne par langue, grande ligne par langue) ; les noms propres ne se traduisent pas.
GET_IT = {"en": "Get it on", "fr": "Disponible sur", "es": "Disponible en", "de": "Jetzt bei", "ru": "Скачать в", "it": "Disponibile su"}
DOWNLOAD_FOR = {"en": "Download for", "fr": "Télécharger pour", "es": "Descargar para", "de": "Herunterladen für", "ru": "Скачать для", "it": "Scarica per"}
APK = {"en": "Download the APK", "fr": "Télécharger l’APK", "es": "Descargar el APK", "de": "APK herunterladen", "ru": "Скачать APK", "it": "Scarica l’APK"}
WEB_SMALL = {"en": "Use it in the", "fr": "Utiliser dans le", "es": "Úsalo en el", "de": "Nutzen im", "ru": "Открыть в", "it": "Usa nel"}
WEB_BIG = {"en": "Browser", "fr": "Navigateur", "es": "Navegador", "de": "Browser", "ru": "браузере", "it": "Browser"}

TEXTS = {
    "fdroid": (GET_IT, {lang: "F-Droid" for lang in LANGUAGES}),
    "obtainium": (GET_IT, {lang: "Obtainium" for lang in LANGUAGES}),
    "apk": (APK, {lang: "GitHub" for lang in LANGUAGES}),
    "windows": (DOWNLOAD_FOR, {lang: "Windows" for lang in LANGUAGES}),
    "linux": (DOWNLOAD_FOR, {lang: "Linux" for lang in LANGUAGES}),
    "macos": (DOWNLOAD_FOR, {lang: "macOS" for lang in LANGUAGES}),
    "web": (WEB_SMALL, WEB_BIG),
}


class Face:
    """Une police Roboto : largeur et tracé d'un texte, en unités du corps demandé."""

    def __init__(self, name: str):
        self.font = TTFont(FONTS / name)
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.upm = self.font["head"].unitsPerEm
        self.hmtx = self.font["hmtx"]

    def glyph(self, char: str) -> str:
        name = self.cmap.get(ord(char))
        if name is None:
            sys.exit(f"glyphe absent de Roboto : {char!r} (U+{ord(char):04X})")
        return name

    def width(self, text: str, size: float) -> float:
        return sum(self.hmtx[self.glyph(c)][0] for c in text) * size / self.upm

    def path(self, text: str, size: float, x: float, baseline: float) -> str:
        scale = size / self.upm
        pen = SVGPathPen(self.glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        cursor = 0.0
        for char in text:
            name = self.glyph(char)
            # y vers le bas : le tracé de police est y vers le haut
            self.glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x + cursor * scale, baseline)))
            cursor += self.hmtx[name][0]
        return pen.getCommands()


def icon_path(src: pathlib.Path, name: str) -> str:
    return re.search(r'<path d="([^"]+)"', (src / f"{name}.svg").read_text()).group(1)


def badge(regular: Face, medium: Face, icon: str, small: str, big: str, title: str) -> str:
    text_x = 56
    width = round(text_x + max(regular.width(small, 12), medium.width(big, 22)) + 24)
    small_path = regular.path(small, 12, text_x, 23)
    big_path = medium.path(big, 22, text_x, 46)
    # Les symboles sont dessinés dans une boîte 960 × 960 dont l'origine est en bas à gauche (y négatif) : mise à 28 px.
    scale = 28 / 960
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{HEIGHT}" viewBox="0 0 {width} {HEIGHT}" role="img">'
        f"<title>{title}</title>"
        f'<rect width="{width}" height="{HEIGHT}" rx="{HEIGHT // 2}" fill="{PRIMARY}"/>'
        f'<g fill="{ON_PRIMARY}">'
        f'<path transform="translate(16 14) scale({scale:.5f}) translate(0 960)" d="{icon}"/>'
        f'<path d="{small_path}"/><path d="{big_path}"/></g></svg>\n'
    )


def main() -> None:
    if len(sys.argv) < 2 or not pathlib.Path(sys.argv[1]).is_dir():
        sys.exit("usage : python kmp/tools/make_badges.py <dossier outlined de @material-symbols/svg-400>")
    src = pathlib.Path(sys.argv[1])
    regular, medium = Face("roboto_regular.ttf"), Face("roboto_medium.ttf")
    count = 0
    for lang in LANGUAGES:
        folder = OUT / lang
        folder.mkdir(parents=True, exist_ok=True)
        for ident, (small, big) in TEXTS.items():
            title = f"{small[lang]} ({big[lang]})" if ident == "apk" else f"{small[lang]} {big[lang]}"
            svg = badge(regular, medium, icon_path(src, ICONS[ident]), small[lang], big[lang], title)
            (folder / f"{ident}.svg").write_text(svg, encoding="utf-8")
            count += 1
    print(f"Écrit : {count} badges dans {OUT}")


if __name__ == "__main__":
    main()
