"""Génère `ui/.../theme/KairosColors.kt` : le schéma de couleurs MD3 de Kairos.

Tous les rôles viennent de l'algorithme officiel (material-color-utilities,
portage Python `materialyoucolor`, schéma *Tonal spot*, spec 2021, contraste
standard) depuis UNE graine, le miel `#C28417` du logo. Pour changer de
couleur : changer `SEED` et regénérer, jamais retoucher un rôle isolé
(CLAUDE.md, docs/spec/navigation-theme.md § Thème).

    pip install materialyoucolor==3.0.4
    python kmp/tools/make_theme.py
"""
import pathlib

from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors as M
from materialyoucolor.hct import Hct
from materialyoucolor.scheme.scheme_tonal_spot import SchemeTonalSpot

SEED = 0xFFC28417
OUT = (pathlib.Path(__file__).resolve().parent.parent
       / "ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/theme/KairosColors.kt")
ROLES = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
    "surfaceTint", "inverseSurface", "inverseOnSurface", "error", "onError",
    "errorContainer", "onErrorContainer", "outline", "outlineVariant", "scrim",
    "surfaceBright", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    "surfaceContainerLow", "surfaceContainerLowest", "surfaceDim",
]

scheme = SchemeTonalSpot(Hct.from_int(SEED), False, 0.0, spec_version="2021")
lines = [
    "// GÉNÉRÉ par kmp/tools/make_theme.py (graine #%06X, Tonal spot, spec 2021) : ne pas éditer."
    % (SEED & 0xFFFFFF),
    "package com.skohscripts.kairos.ui.theme",
    "",
    "import androidx.compose.material3.lightColorScheme",
    "import androidx.compose.ui.graphics.Color",
    "",
    "internal val KairosLightColors = lightColorScheme(",
]
for role in ROLES:
    argb = getattr(M, role).get_argb(scheme) & 0xFFFFFFFF
    lines.append(f"    {role} = Color(0x{argb:08X}),")
lines += [")", ""]
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"Écrit : {OUT}")
