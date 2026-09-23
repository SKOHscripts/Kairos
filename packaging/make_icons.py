"""Génère `templates/_icons.html` (macro `icon()`) à partir des SVG Material
Symbols (style Outlined, graisse 400) du paquet npm `@material-symbols/svg-400`
(Apache 2.0). Les tracés sont recopiés dans le gabarit : l'app n'a ni police
d'icônes ni dépendance de build, et reste utilisable hors ligne.

Pour ajouter une icône : l'ajouter à `MAP` (nom Kairos -> nom Material
Symbols, voir https://fonts.google.com/icons), puis :

    npm install --prefix /tmp/ms @material-symbols/svg-400
    python packaging/make_icons.py /tmp/ms/node_modules/@material-symbols/svg-400/outlined

Voir docs/DESIGN_SYSTEM.md § Icônes.
"""
import pathlib
import re
import sys

SRC = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
OUT = pathlib.Path(__file__).resolve().parent.parent / "templates" / "_icons.html"
# nom Kairos -> nom Material Symbols. Les noms historiques restent valides :
# aucun appel {{ icon('...') }} existant n'a à changer.
MAP = {
    "refresh": "refresh", "repeat": "repeat", "lock": "lock", "warning": "warning",
    "error": "error", "check": "check", "check_circle": "check_circle", "plus": "add",
    "link": "link", "folder": "folder", "tag": "sell", "download": "download",
    "close": "close", "clipboard": "content_paste", "pencil": "edit", "save": "save",
    "trash": "delete", "file_text": "description", "grid": "grid_view", "share": "share",
    "blocked": "block", "comment": "comment", "arrow_left": "arrow_back",
    "arrow_up_right": "arrow_outward", "trending_up": "trending_up",
    "chevron_down": "keyboard_arrow_down", "chevron_right": "chevron_right",
    "chevron_left": "chevron_left", "export_up": "upload", "import_down": "download",
    "dot": "fiber_manual_record", "dot_empty": "radio_button_unchecked",
    "clock": "schedule", "calendar": "calendar_month", "layers": "layers",
    "dashboard": "dashboard", "skip_forward": "redo", "search": "search",
    "home": "home", "gear": "settings", "notes": "sticky_note_2",
    "today": "today", "date_range": "date_range", "bar_chart": "bar_chart",
    "play": "play_arrow", "stop": "stop", "logout": "logout",
}
def path_of(name, fill=False):
    """Tracé `d` du SVG Material Symbols (variante pleine si elle existe)."""
    f = SRC / (f"{name}-fill.svg" if fill else f"{name}.svg")
    if not f.exists():
        f = SRC / f"{name}.svg"
    return re.search(r'<path d="([^"]+)"', f.read_text()).group(1)

if SRC is None or not SRC.is_dir():
    sys.exit("usage : python packaging/make_icons.py <dossier outlined de @material-symbols/svg-400>")

out = []
out.append('''{#- ---- _icons.html ----
   Icônes Material Symbols (Outlined, graisse 400) inline, en SVG monochrome
   (`fill="currentColor"`) : aucune police d'icônes ni requête réseau, l'app
   restant utilisable hors ligne (exécutable, APK). Tracés copiés du paquet npm
   `@material-symbols/svg-400` 0.47.5 (Apache 2.0, Google) ; seule `gitlab`
   (marque, absente de Material Symbols) est dessinée à la main. Voir
   docs/DESIGN_SYSTEM.md § Icônes.
   Usage :
     {% from "_icons.html" import icon %}
     {{ icon('lock') }}                     - aria-hidden par défaut
     {{ icon('warning', 'Avertissement') }} - avec aria-label
     {{ icon('today', fill=true) }}         - variante pleine (destination active)
   Les noms sont ceux de Kairos (historiques), traduits vers Material Symbols
   ici même : un nom inconnu rend un SVG vide plutôt qu'une icône arbitraire.
   Chaque SVG porte `ico ico-<nom>` : la feuille de style cible une icône
   précise (ex. seuls les chevrons pivotent à l'ouverture d'un <details>).
   Fichier GÉNÉRÉ par packaging/make_icons.py : l'éditer là, pas ici.
-#}

{%- macro icon(name, title='', fill=false) -%}
{%- if name == 'gitlab' -%}
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" width="1em" height="1em"
     fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"
     class="ico ico-gitlab"
     {%- if title %} role="img" aria-label="{{ title }}"{% else %} aria-hidden="true"{% endif -%}>
  <path d="M8 14 2.5 9.7 4 3.2l2 4.2h4l2-4.2 1.5 6.5z"/>
</svg>
{%- else -%}
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" width="1em" height="1em"
     fill="currentColor" class="ico ico-{{ name }}"
     {%- if title %} role="img" aria-label="{{ title }}"{% else %} aria-hidden="true"{% endif -%}>''')
first = True
for kname, mname in MAP.items():
    kw = "if" if first else "elif"
    first = False
    p, pf = path_of(mname), path_of(mname, True)
    if p == pf:
        out.append(f"  {{%- {kw} name == '{kname}' -%}}<path d=\"{p}\"/>")
    else:
        out.append(f"  {{%- {kw} name == '{kname}' -%}}{{% if fill %}}<path d=\"{pf}\"/>{{% else %}}<path d=\"{p}\"/>{{% endif %}}")
out.append("  {%- endif -%}\n</svg>\n{%- endif -%}\n{%- endmacro -%}\n")
pathlib.Path("/home/user/Kairos/templates/_icons.html").write_text("\n".join(out))
