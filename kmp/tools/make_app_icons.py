"""Génère les icônes d'application de Kairos 3 à partir du logo (cadran solaire).

Même dessin que `docs/DESIGN_SYSTEM.md` § Logo et `ui/.../icons/KairosLogo.kt`
(cadran ton 95, anneau ton 85, secteur = graine #C28417, axe ton 15), redessiné
avec Pillow (pas de dépendance de conversion SVG). Sorties, commitées :

- desktopApp/icons/kairos.png  (1024 px, Linux / fenêtre)
- desktopApp/icons/kairos.ico  (Windows, 16 à 256 px)
- desktopApp/icons/kairos.icns (macOS)
- webApp/src/wasmJsMain/resources/favicon.png, icon-192.png, icon-512.png
- fastlane/metadata/android/{fr-FR,en-US}/images/icon.png (512 px, fiches F-Droid
  et IzzyOnDroid) et site/icon.png, site/favicon.png (page de téléchargement)

    pip install Pillow
    python kmp/tools/make_app_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

DIAL, RING, WEDGE, DARK = "#FFEEDC", "#FFCC85", "#C28417", "#2B251C"
VIEWBOX, SCALE = 40, 64
SIZE = VIEWBOX * SCALE
ROOT = Path(__file__).resolve().parent.parent


def s(v: float) -> float:
    return v * SCALE


def render_master() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    c = s(20)
    r = s(18.5)
    box = [c - r, c - r, c + r, c + r]
    draw.ellipse(box, fill=DIAL)
    draw.ellipse(box, outline=RING, width=round(s(1.6)))
    rw = s(16)
    # Secteur de 12h (270°) à ~2h (350°) : le point (35.76, 17.22) du SVG.
    draw.pieslice([c - rw, c - rw, c + rw, c + rw], start=270, end=350, fill=WEDGE)
    rd = s(2.6)
    draw.ellipse([c - rd, c - rd, c + rd, c + rd], fill=DARK)
    return img


def main() -> None:
    master = render_master()
    icons = ROOT / "desktopApp" / "icons"
    icons.mkdir(parents=True, exist_ok=True)
    big = master.resize((1024, 1024), Image.LANCZOS)
    big.save(icons / "kairos.png")
    big.save(icons / "kairos.ico", sizes=[(n, n) for n in (256, 128, 64, 48, 32, 16)])
    big.save(icons / "kairos.icns")
    web = ROOT / "webApp" / "src" / "wasmJsMain" / "resources"
    web.mkdir(parents=True, exist_ok=True)
    for size, name in ((64, "favicon.png"), (192, "icon-192.png"), (512, "icon-512.png")):
        master.resize((size, size), Image.LANCZOS).save(web / name)
    repo = ROOT.parent
    for locale in ("fr-FR", "en-US"):
        images = repo / "fastlane" / "metadata" / "android" / locale / "images"
        images.mkdir(parents=True, exist_ok=True)
        master.resize((512, 512), Image.LANCZOS).save(images / "icon.png")
    site = repo / "site"
    site.mkdir(parents=True, exist_ok=True)
    master.resize((192, 192), Image.LANCZOS).save(site / "icon.png")
    master.resize((64, 64), Image.LANCZOS).save(site / "favicon.png")
    print("Icônes écrites :", icons, web, repo / "fastlane", site)


if __name__ == "__main__":
    main()
