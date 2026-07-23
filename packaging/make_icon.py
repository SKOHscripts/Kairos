"""Génère `packaging/kairos.ico` (icône de l'exécutable Windows) et les icônes
PNG pour la web app (favicons, apple-touch-icon, manifest) à partir du logo
de l'application — le même dessin que `static/favicon.svg` et le bandeau
d'accueil : cadran crème cerclé, secteur orange (l'aiguille qui balaie 12h→2h),
point sombre au centre.

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
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# Jetons de la charte (voir static/style.css / static/favicon.svg).
_CREAM = "#FBEEDF"
_RING = "#E7C4A2"
_ORANGE = "#D9713C"
_DARK = "#2B241E"

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

    # Disque crème + cercle de contour (le contour = un anneau dessiné par-dessus).
    disc_box = [center - r_disc, center - r_disc, center + r_disc, center + r_disc]
    draw.ellipse(disc_box, fill=_CREAM)
    draw.ellipse(disc_box, outline=_RING, width=round(ring_width))

    # Secteur orange : de 12h (270°) à ~2h, dans le sens horaire — reproduit le
    # `path` du SVG (M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z). Rayon 16 (viewBox).
    r_wedge = _s(16)
    wedge_box = [center - r_wedge, center - r_wedge, center + r_wedge, center + r_wedge]
    # Angles Pillow : mesurés depuis 3h, sens horaire (y vers le bas). 12h = 270°,
    # le point (35.76, 17.22) tombe à ~350° (dx=15.76, dy=-2.78 → atan2 ≈ -10°).
    draw.pieslice(wedge_box, start=270, end=350, fill=_ORANGE)

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


def main() -> None:
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


if __name__ == "__main__":
    main()
