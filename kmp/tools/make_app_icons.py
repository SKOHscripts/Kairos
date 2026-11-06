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
- site/og/<langue>.png (1280×640, aperçu des liens de la page et de GitHub, fond clair, docs/spec/publication.md
  § Aperçu des liens)

    pip install Pillow
    python kmp/tools/make_app_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

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


# Image d'aperçu des liens (Open Graph) et de GitHub, 1280×640 (docs/spec/publication.md § Aperçu des liens) : le logo, le nom,
# l'accroche de l'application (`app_tagline`) et une ligne « gratuit, open source, plateformes », dans la langue de la page.
PREVIEW_W, PREVIEW_H = 1280, 640
PREVIEW_MARGIN = 80  # GitHub et les réseaux rognent les bords : le contenu reste dans cette marge
PREVIEW_PLATFORMS = "Android, Windows, Linux, macOS, Web"
PREVIEW_TEXTS = {  # langue : (accroche = `app_tagline`, ligne du bas sans les plateformes)
    "fr": ("Le bon moment pour chaque tâche", "Gratuit · Open source"),
    "en": ("The right time for every task", "Free · Open-source"),
    "es": ("El momento justo para cada tarea", "Gratis · Código abierto"),
    "de": ("Der richtige Moment für jede Aufgabe", "Kostenlos · Open Source"),
    "ru": ("Подходящее время для каждой задачи", "Бесплатно · Открытый код"),
    "it": ("Il momento giusto per ogni attività", "Gratuito · Open source"),
}
# Fond clair, comme l'application : rôles du thème clair (surface, texte, texte secondaire, primaire de `KairosLightColors`).
PREVIEW_COLORS = ("#FAF8FF", "#1A1B21", "#44464F", "#485D92")
FONTS = ROOT / "ui" / "src" / "commonMain" / "composeResources" / "font"


def social_preview(master: Image.Image, lang: str) -> Image.Image:
    """Le bandeau de [lang] : logo à gauche, textes à droite, l'ensemble centré et réduit s'il dépasse les marges."""
    tagline, line = PREVIEW_TEXTS[lang]
    line = f"{line} · {PREVIEW_PLATFORMS}"
    bg, text, muted, accent = PREVIEW_COLORS
    k = 2  # rendu à 2x puis réduit : bords nets
    w, h = PREVIEW_W * k, PREVIEW_H * k

    def layout(f: float):
        name = ImageFont.truetype(str(FONTS / "roboto_regular.ttf"), round(124 * f * k))
        tag = ImageFont.truetype(str(FONTS / "roboto_regular.ttf"), round(44 * f * k))
        small = ImageFont.truetype(str(FONTS / "roboto_medium.ttf"), round(30 * f * k))
        probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
        boxes = [probe.textbbox((0, 0), t, font=fo) for t, fo in (("Kairos", name), (tagline, tag), (line, small))]
        logo = round(280 * f * k)
        gap = round(56 * f * k)
        width = logo + gap + max(b[2] for b in boxes)
        return name, tag, small, boxes, logo, gap, width

    f = 1.0
    while layout(f)[6] > w - 2 * PREVIEW_MARGIN * k:
        f -= 0.02
    name, tag, small, boxes, logo_size, gap, width = layout(f)
    img = Image.new("RGB", (w, h), bg)
    draw = ImageDraw.Draw(img)
    # Le cadran occupe 37/40 du dessin d'origine : on le met à la taille voulue pour le cadran lui-même.
    logo = master.resize((round(logo_size * 40 / 37),) * 2, Image.LANCZOS)
    x0 = (w - width) // 2
    img.paste(logo, (x0 - (logo.width - logo_size) // 2, (h - logo.height) // 2), logo)
    spacing = [round(18 * f * k), round(30 * f * k)]
    heights = [b[3] - b[1] for b in boxes]
    y = (h - sum(heights) - sum(spacing)) // 2
    x = x0 + logo_size + gap
    for (t, fo, colour), b, ht, sp in zip(
        (("Kairos", name, text), (tagline, tag, muted), (line, small, accent)), boxes, heights, spacing + [0]
    ):
        draw.text((x, y - b[1]), t, font=fo, fill=colour)
        y += ht + sp
    return img.resize((PREVIEW_W, PREVIEW_H), Image.LANCZOS)


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
    og = site / "og"
    og.mkdir(parents=True, exist_ok=True)
    for lang in PREVIEW_TEXTS:
        social_preview(master, lang).save(og / f"{lang}.png", optimize=True)
    print("Icônes et aperçus écrits :", icons, web, repo / "fastlane", site)


if __name__ == "__main__":
    main()
