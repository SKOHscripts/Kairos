"""Génère les icônes d'application de Kairos 3 à partir du logo (cadran solaire).

Même dessin que `docs/DESIGN_SYSTEM.md` § Logo et `ui/.../icons/KairosLogo.kt`
(cadran ton 95, anneau ton 85, secteur = le miel #C28417, axe ton 15), **inchangé** : il garde ses couleurs
miel, sur un fond uni océan profond (`BACKGROUND`, ton 20 de la graine océan, docs/spec/apparence.md § Icônes),
redessiné avec Pillow (pas de dépendance de conversion SVG). Sorties, commitées :

- desktopApp/icons/kairos.png  (1024 px, Linux / fenêtre)
- desktopApp/icons/kairos.ico  (Windows, 16 à 256 px)
- desktopApp/icons/kairos.icns (macOS)
- webApp/src/wasmJsMain/resources/favicon.png, icon-192.png, icon-512.png
- fastlane/metadata/android/{fr-FR,en-US}/images/icon.png (512 px, fiches F-Droid)
  et site/icon.png, site/favicon.png (page de téléchargement)

    pip install Pillow
    python kmp/tools/make_app_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

DIAL, RING, WEDGE, DARK = "#FFEEDC", "#FFCC85", "#C28417", "#2B251C"
# Fond de l'icône : ton 20 de la palette primaire de la graine océan (#2F6FED), `ic_launcher_background` côté Android.
BACKGROUND = "#1C2F59"
# Part du côté de l'icône occupée par le diamètre du cadran.
LOGO_RATIO = 0.64
CORNER = 0.225  # rayon des coins arrondis (part du côté) des icônes de bureau et de la page
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


def tile(master: Image.Image, size: int, rounded: bool) -> Image.Image:
    """Le logo sur le fond uni : un carré plein (les magasins et le web arrondissent eux-mêmes) ou à coins arrondis."""
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if rounded:
        draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=round(big * CORNER), fill=BACKGROUND)
    else:
        draw.rectangle([0, 0, big - 1, big - 1], fill=BACKGROUND)
    k = round(big * LOGO_RATIO)
    # Le cadran occupe 37/40 du dessin d'origine : le ratio est celui du cadran, pas du canevas.
    logo = master.resize((round(k * 40 / 37), round(k * 40 / 37)), Image.LANCZOS)
    img.alpha_composite(logo, ((big - logo.width) // 2, (big - logo.height) // 2))
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    master = render_master()
    icons = ROOT / "desktopApp" / "icons"
    icons.mkdir(parents=True, exist_ok=True)
    big = tile(master, 1024, rounded=True)
    big.save(icons / "kairos.png")
    big.save(icons / "kairos.ico", sizes=[(n, n) for n in (256, 128, 64, 48, 32, 16)])
    big.save(icons / "kairos.icns")
    web = ROOT / "webApp" / "src" / "wasmJsMain" / "resources"
    web.mkdir(parents=True, exist_ok=True)
    # Favicon à coins arrondis ; icônes d'application installée en carré plein (le système les masque).
    tile(master, 64, rounded=True).save(web / "favicon.png")
    tile(master, 192, rounded=False).save(web / "icon-192.png")
    tile(master, 512, rounded=False).save(web / "icon-512.png")
    repo = ROOT.parent
    for locale in ("fr-FR", "en-US"):
        images = repo / "fastlane" / "metadata" / "android" / locale / "images"
        images.mkdir(parents=True, exist_ok=True)
        tile(master, 512, rounded=False).save(images / "icon.png")
    site = repo / "site"
    site.mkdir(parents=True, exist_ok=True)
    tile(master, 192, rounded=True).save(site / "icon.png")
    tile(master, 64, rounded=True).save(site / "favicon.png")
    print("Icônes écrites :", icons, web, repo / "fastlane", site)


if __name__ == "__main__":
    main()
