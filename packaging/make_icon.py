"""Génère `packaging/kairos.ico` (icône de l'exécutable Windows) et les icônes
PNG pour la web app (favicons, apple-touch-icon, manifest) à partir du logo
de l'application — le même dessin que `static/favicon.svg` et le bandeau
d'accueil : cadran miel clair cerclé, secteur miel (l'aiguille qui balaie
12h→2h), point sombre au centre. Couleurs : tons de la palette de la graine
MD3 `#C28417` (docs/DESIGN_SYSTEM.md § Logo).

Redessiné directement avec Pillow plutôt que rasterisé depuis le SVG : évite une
dépendance de conversion (cairosvg/rsvg), reste reproductible, et le logo est
assez simple pour être fidèle. Lancer après toute évolution du logo :

    python packaging/make_icon.py

L'icône `.ico` générée est commitée (asset binaire) et référencée par
`packaging/kairos.spec` (paramètre `icon=` de `EXE`, embarqué dans le .exe
Windows ; ignoré sans erreur pour le binaire Linux, qui ne porte pas d'icône).

Les PNG générés (`static/icon-192.png`, `static/icon-512.png`,
`static/apple-touch-icon.png`) sont liés depuis `templates/base.html` et
embarqués dans toutes les distributions (dev, PyInstaller, Android APK).

`packaging/splash.png` (fenêtre de démarrage de l'exécutable de bureau, voir
`Splash` dans `packaging/kairos.spec`) est régénéré à chaque lancement : le nom
« Kairos » y est dessiné en Roboto, la police de la charte, lue directement
dans les WOFF2 embarqués par l'app (`static/fonts/`, que Pillow ouvre via
FreeType) — aucune police à installer sur le poste de build.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# Tons du logo (palette de la graine miel #C28417, voir static/favicon.svg et
# docs/DESIGN_SYSTEM.md § Logo) : cadran ton 95, anneau ton 85, secteur =
# graine (ton 60), axe = neutre ton 15.
_DIAL = "#FFEEDC"
_RING = "#FFCC85"
_WEDGE = "#C28417"
_DARK = "#2B251C"

# On dessine à grande échelle (suréchantillonnage ×64 du viewBox 40) puis on
# réduit : anti-aliasing propre sans dépendre du rendu vectoriel.
_VIEWBOX = 40
_SCALE = 64
_SIZE = _VIEWBOX * _SCALE  # 2560 px
_ICON_SIZES = [256, 128, 64, 48, 32, 16]


def _s(value: float) -> float:
    """Coordonnée du viewBox (0-40) → pixel de la grande image."""
    return value * _SCALE


def render_master() -> Image.Image:
    img = Image.new("RGBA", (_SIZE, _SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    center = _s(20)
    r_disc = _s(18.5)
    ring_width = _s(1.6)

    # Disque clair + cercle de contour (le contour = un anneau dessiné par-dessus).
    disc_box = [center - r_disc, center - r_disc, center + r_disc, center + r_disc]
    draw.ellipse(disc_box, fill=_DIAL)
    draw.ellipse(disc_box, outline=_RING, width=round(ring_width))

    # Secteur miel : de 12h (270°) à ~2h, dans le sens horaire — reproduit le
    # `path` du SVG (M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z). Rayon 16 (viewBox).
    r_wedge = _s(16)
    wedge_box = [center - r_wedge, center - r_wedge, center + r_wedge, center + r_wedge]
    # Angles Pillow : mesurés depuis 3h, sens horaire (y vers le bas). 12h = 270°,
    # le point (35.76, 17.22) tombe à ~350° (dx=15.76, dy=-2.78 → atan2 ≈ -10°).
    draw.pieslice(wedge_box, start=270, end=350, fill=_WEDGE)

    # Pastille sombre au centre (l'axe de l'aiguille).
    r_dot = _s(2.6)
    draw.ellipse(
        [center - r_dot, center - r_dot, center + r_dot, center + r_dot], fill=_DARK
    )
    return img


def write_png_icons(master: Image.Image) -> None:
    """Génère les icônes PNG pour la web app à partir de l'image maître.

    Redimensionne l'image maître (2560×2560) en trois tailles standard :
    - 192×192 pour `icon-192.png` (web manifest)
    - 512×512 pour `icon-512.png` (web manifest, splash screens)
    - 180×180 pour `apple-touch-icon.png` (iOS home screen)
    """
    static_dir = Path(__file__).resolve().parent.parent / "static"

    png_configs = [
        (192, "icon-192.png"),
        (512, "icon-512.png"),
        (180, "apple-touch-icon.png"),
    ]

    for size, filename in png_configs:
        resized = master.resize((size, size), Image.LANCZOS)
        out_path = static_dir / filename
        resized.save(out_path, format="PNG")
        print(f"Icône PNG écrite : {out_path}")


# Fenêtre de démarrage de bureau : rôles MD3 de docs/DESIGN_SYSTEM.md
# (surface, contour pour se détacher d'un bureau clair, texte principal et
# secondaire). Composition alignée à gauche, parce que le texte d'état ajouté
# par Tk à l'exécution est ancré en bas à gauche (`text_pos` du spec) : logo,
# nom et état partagent la même marge gauche.
SPLASH_SIZE = (420, 200)
SPLASH_MARGIN = 32
SPLASH_STATUS_BASELINE = 164  # repris par `text_pos` dans packaging/kairos.spec
_SURFACE = "#FFF8F4"          # --md-surface
_BORDER_STRONG = "#D3C4B4"     # --md-outline-variant
_TEXT = "#201B13"              # --md-on-surface
_TEXT_2 = "#4F4539"            # --md-on-surface-variant (repris par kairos.spec)
_FONTS = Path(__file__).resolve().parent.parent / "static" / "fonts"
SPLASH_FONT_TITLE = _FONTS / "roboto-latin-500-normal.woff2"
SPLASH_FONT_TEXT = _FONTS / "roboto-latin-400-normal.woff2"


def render_splash(master: Image.Image, title_font: Path = SPLASH_FONT_TITLE,
                  text_font: Path = SPLASH_FONT_TEXT) -> Image.Image:
    """Fond, logo, nom et sous-titre de la fenêtre de démarrage (sans le texte
    d'état, dessiné par Tk). Dessiné ×4 puis réduit, comme le logo."""
    k = 4
    width, height = SPLASH_SIZE
    img = Image.new("RGB", (width * k, height * k), _SURFACE)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, width * k - 1, height * k - 1], outline=_BORDER_STRONG, width=k)

    logo = 64
    top = 40
    mark = master.resize((logo * k, logo * k), Image.LANCZOS)
    img.paste(mark, (SPLASH_MARGIN * k, top * k), mark)  # le canal alpha sert de masque

    text_x = (SPLASH_MARGIN + logo + 18) * k
    title = ImageFont.truetype(str(title_font), 30 * k)
    subtitle = ImageFont.truetype(str(text_font), 13 * k)
    draw.text((text_x, (top + 30) * k), "Kairos", font=title, fill=_TEXT, anchor="ls")
    draw.text((text_x, (top + 52) * k), "le bon moment, la bonne tâche", font=subtitle,
              fill=_TEXT_2, anchor="ls")
    return img.resize(SPLASH_SIZE, Image.LANCZOS)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.parse_args()

    master = render_master()
    out_path = Path(__file__).resolve().parent / "kairos.ico"
    frames = [
        master.resize((size, size), Image.LANCZOS) for size in _ICON_SIZES
    ]
    # `save` sur le plus grand cadre, les autres tailles fournies via `sizes` /
    # `append_images` pour un .ico multi-résolutions (barre des tâches, bureau...).
    frames[0].save(
        out_path,
        format="ICO",
        sizes=[(s, s) for s in _ICON_SIZES],
        append_images=frames[1:],
    )
    print(f"Icône écrite : {out_path}")
    write_png_icons(master)
    splash_path = Path(__file__).resolve().parent / "splash.png"
    render_splash(master).save(splash_path, format="PNG")
    print(f"Fenêtre de démarrage écrite : {splash_path}")


if __name__ == "__main__":
    main()
