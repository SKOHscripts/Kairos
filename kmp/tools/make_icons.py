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
# Icônes des widgets Android (docs/spec/widgets-android.md) : `RemoteViews` n'affiche pas d'`ImageVector`, il lui faut des
# ressources `drawable` ; mêmes tracés, en blanc (teintés par le widget avec `setColorFilter`).
DRAWABLES = (pathlib.Path(__file__).resolve().parent.parent / "androidApp/src/main/res/drawable")
WIDGET_ICONS = {
    "ic_widget_check": "check",
    "ic_widget_play": "play_arrow",
    "ic_widget_stop": "stop",
    "ic_widget_add": "add",
}
# Nom Kotlin -> (nom Material Symbols, variante pleine générée aussi ?)
# La variante pleine sert à la destination active de la navigation (convention MD3).
ICONS = {
    "Home": ("home", True),
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
    # Aide, accueil et visite guidée : le « ? » de la barre du haut.
    "Help": ("help", False),
    # Soutien : le cœur de la barre du haut et de « À propos » (docs/spec/soutien.md).
    "Favorite": ("favorite", False),
    # Réglages rangés par catégories (docs/spec/reglages.md) : icônes des catégories et des cartes.
    "Tune": ("tune", False),
    "Sort": ("sort", False),
    "Timer": ("timer", False),
    "Database": ("database", False),
    "Translate": ("translate", False),
    "Palette": ("palette", False),
    "Update": ("update", False),
    "Work": ("work", False),
    "CalendarViewWeek": ("calendar_view_week", False),
    "Workspaces": ("workspaces", False),
    "Label": ("label", False),
    "LightMode": ("light_mode", False),
    "History": ("history", False),
    "Shield": ("shield", False),
    "Leaderboard": ("leaderboard", False),
    "Bedtime": ("bedtime", False),
    "SettingsBackupRestore": ("settings_backup_restore", False),
    # Guide : l'étiquette « Ici » de la section qu'une carte fait voir (docs/spec/visite-guidee.md, issue #95).
    "Spot": ("my_location", False),
    "TableView": ("table_view", False),
    "Image": ("image", False),
    "Flag": ("flag", False),
    "CalendarMonth": ("calendar_month", False),
    "Alarm": ("alarm", False),
    "ShowChart": ("show_chart", False),
    "EditNote": ("edit_note", False),
    "DarkMode": ("dark_mode", False),
    # Calendriers importés (3.10.0, issue #136) : créneau importé, journée entière.
    "Event": ("event", False),
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
    for res_name, ms_name in WIDGET_ICONS.items():
        # Boîte 960 × 960 d'origine en bas à gauche (y négatif) : décalée de 960 vers le bas.
        (DRAWABLES / f"{res_name}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- GÉNÉRÉ par kmp/tools/make_icons.py depuis @material-symbols/svg-400 0.47.5 (Apache 2.0, Google) :\n"
            "     ne pas éditer, ajouter l'icône dans le script. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n'
            '    android:height="24dp"\n'
            '    android:viewportWidth="960"\n'
            '    android:viewportHeight="960">\n'
            '    <group android:translateY="960">\n'
            f'        <path android:fillColor="#FFFFFFFF" android:pathData="{path_of(src, ms_name, False)}" />\n'
            "    </group>\n"
            "</vector>\n",
            encoding="utf-8",
        )
    print(f"Écrit : {len(WIDGET_ICONS)} icônes de widget dans {DRAWABLES}")


if __name__ == "__main__":
    main()
