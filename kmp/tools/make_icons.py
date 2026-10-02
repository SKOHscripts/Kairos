"""Génère `ui/.../icons/KairosIcons.kt` : les icônes Material Symbols de Kairos.

Tracés du paquet npm `@material-symbols/svg-400` (style Outlined, graisse 400,
Apache 2.0) recopiés en `ImageVector` : ni police d'icônes, ni requête réseau,
l'app reste utilisable hors ligne (docs/spec/navigation-theme.md § Icônes).

Pour ajouter une icône : l'ajouter à `ICONS` (nom Kotlin -> nom Material
Symbols, voir https://fonts.google.com/icons), puis :

    npm install --prefix /tmp/ms @material-symbols/svg-400@0.47.5
    python kmp/tools/make_icons.py /tmp/ms/node_modules/@material-symbols/svg-400/outlined
"""
import pathlib
import re
import sys

OUT = (pathlib.Path(__file__).resolve().parent.parent
       / "ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/icons/KairosIcons.kt")
# Nom Kotlin -> (nom Material Symbols, variante pleine générée aussi ?)
# La variante pleine sert à la destination active de la navigation (convention MD3).
ICONS = {
    "Notes": ("sticky_note_2", True),
    "Today": ("today", True),
    "DateRange": ("date_range", True),
    "BarChart": ("bar_chart", True),
    "Settings": ("settings", True),
    "Info": ("info", True),
    "ArrowBack": ("arrow_back", False),
    "ChevronRight": ("chevron_right", False),
    "OpenInNew": ("open_in_new", False),
    "CheckCircle": ("check_circle", True),
    "RadioUnchecked": ("radio_button_unchecked", False),
    "Edit": ("edit", False),
    "Delete": ("delete", False),
    "Add": ("add", False),
    "Upload": ("upload", False),
    "Download": ("download", False),
    "InstallDesktop": ("install_desktop", False),
    "ExpandMore": ("keyboard_arrow_down", False),
    "ExpandLess": ("keyboard_arrow_up", False),
    "Inbox": ("inbox", False),
    "Warning": ("warning", False),
    "Redo": ("redo", False),
    "Search": ("search", False),
    "Schedule": ("schedule", False),
    "Block": ("block", False),
    "Repeat": ("repeat", False),
    "PushPin": ("keep", False),
    "TrendingUp": ("trending_up", False),
    "Description": ("description", False),
    "Layers": ("layers", False),
    "PlayArrow": ("play_arrow", False),
    "Stop": ("stop", False),
    "Close": ("close", False),
    "NotificationsActive": ("notifications_active", False),
    "ChevronLeft": ("chevron_left", False),
    "Check": ("check", False),
    "Groups": ("groups", True),
    "Person": ("person", False),
    "ViewKanban": ("view_kanban", True),
    "Stacks": ("stacks", True),
    "Monitoring": ("monitoring", True),
    "PersonAdd": ("person_add", False),
    "EventBusy": ("event_busy", False),
    "Archive": ("archive", False),
    "Unarchive": ("unarchive", False),
    "SwapHoriz": ("swap_horiz", False),
    "MoreVert": ("more_vert", False),
    "HourglassEmpty": ("hourglass_empty", False),
    "Balance": ("balance", False),
    # Prévisions (jalon E5) : tirage, scénario, comparaison, recalcul, meilleur de la ligne, copie.
    "Casino": ("casino", False),
    "Science": ("science", False),
    "CompareArrows": ("compare_arrows", False),
    "Refresh": ("refresh", False),
    "Star": ("star", True),
    "ContentCopy": ("content_copy", False),
}


def path_of(src: pathlib.Path, name: str, fill: bool) -> str:
    f = src / (f"{name}-fill.svg" if fill else f"{name}.svg")
    if not f.exists():
        f = src / f"{name}.svg"
    return re.search(r'<path d="([^"]+)"', f.read_text()).group(1)


def main() -> None:
    if len(sys.argv) < 2 or not pathlib.Path(sys.argv[1]).is_dir():
        sys.exit("usage : python kmp/tools/make_icons.py <dossier outlined de @material-symbols/svg-400>")
    src = pathlib.Path(sys.argv[1])
    out = [
        "// GÉNÉRÉ par kmp/tools/make_icons.py depuis @material-symbols/svg-400 0.47.5",
        "// (Apache 2.0, Google) : ne pas éditer, ajouter l'icône dans le script.",
        "package com.skohscripts.kairos.ui.icons",
        "",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "",
        "/** Icônes Material Symbols (Outlined 400) ; `*Filled` = variante pleine. */",
        "object KairosIcons {",
    ]
    for kotlin_name, (ms_name, with_fill) in ICONS.items():
        out.append(f'    val {kotlin_name}: ImageVector by lazy {{ symbol("{kotlin_name}", "{path_of(src, ms_name, False)}") }}')
        if with_fill:
            out.append(
                f'    val {kotlin_name}Filled: ImageVector by lazy '
                f'{{ symbol("{kotlin_name}Filled", "{path_of(src, ms_name, True)}") }}'
            )
    out += ["}", ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"Écrit : {OUT} ({len(ICONS)} icônes)")


if __name__ == "__main__":
    main()
