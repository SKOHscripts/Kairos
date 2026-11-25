"""Identité de l'app, figée (docs/spec/distribution.md § Identité de l'app).

Les noms et identifiants qui relient une installation à la suivante (nom de l'app, paquet Linux, upgrade code Windows,
bundle macOS, applicationId Android, classe de fenêtre, dossier des données, recette F-Droid, anciens noms des
raccourcis, identité du paquet du Microsoft Store) ne changent pas sans migration : en 4.2.0, le passage de « Kairos Preview » à « Kairos » a laissé des
entrées de menu qu'une nouvelle copie ne savait pas remplacer. Ce contrôle échoue (code 1) sur tout écart et renvoie à la
spec, où se trouve la marche à suivre ; une valeur ne se change ici qu'avec sa migration.

Sans dépendance :

    python3 kmp/tools/validate_identity.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = "kmp/desktopApp"
KOTLIN = f"{DESKTOP}/src/jvmMain/kotlin/com/skohscripts/kairos/desktop"

# (fichier, motif dont le groupe 1 est la valeur, valeur attendue, ce qu'elle désigne)
IDENTITY: list[tuple[str, str, str, str]] = [
    (f"{DESKTOP}/build.gradle.kts", r'^val appName = "([^"]*)"', "Kairos", "nom de l'app (bureau)"),
    (f"{KOTLIN}/Main.kt", r'val appName = "([^"]*)"', "Kairos", "nom de l'app (lanceur, raccourcis)"),
    (f"{DESKTOP}/build.gradle.kts", r'packageName = "([^"]*)"', "kairos", "paquet Linux (.deb)"),
    (f"{DESKTOP}/build.gradle.kts", r'upgradeUuid = "([^"]*)"', "5b0e2d7c-8a41-4f6b-b3c9-1e7d6a2f4c08", "upgrade code Windows (.msi)"),
    (f"{DESKTOP}/build.gradle.kts", r'bundleID = "([^"]*)"', "com.skohscripts.kairos", "bundle macOS"),
    ("kmp/androidApp/build.gradle.kts", r'applicationId = "([^"]*)"', "com.skohscripts.kairos", "applicationId Android"),
    ("kmp/androidApp/build.gradle.kts", r'resValue\("string", "app_name", "([^"]*)"\)', "Kairos", "nom Android"),
    (f"{KOTLIN}/WindowClass.kt", r'const val NAME = "([^"]*)"', "kairos", "classe de fenêtre X11"),
    (f"{KOTLIN}/DataDirectory.kt", r'"\$home/\.local/share", "([^"]*)"\)', "Kairos", "racine des données (Linux)"),
    (f"{KOTLIN}/DataDirectory.kt", r'File\(root, "([^"]*)"\)', "v3", "sous-dossier des données"),
    (f"{KOTLIN}/PortableShortcuts.kt", r'val LEGACY_APP_NAMES = listOf\(([^)]*)\)', '"Kairos Preview"', "anciens noms (raccourcis)"),
    # Identité attribuée par Partner Center au paquet du Microsoft Store (§ Microsoft Store).
    ("packaging/msix/AppxManifest.xml", r'^\s*Name="([^"]*)"', "SKOHscripts.KairosPlanner", "identité MSIX (Store)"),
    ("packaging/msix/AppxManifest.xml", r'^\s*Publisher="([^"]*)"', "CN=EF0DE618-1AD9-4656-8417-18B58EB3603F", "éditeur MSIX (Store)"),
]
FILES = ["fdroid/com.skohscripts.kairos.yml"]


def check(root: Path = ROOT) -> list[str]:
    errors = []
    for name, pattern, expected, what in IDENTITY:
        path = root / name
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        found = re.search(pattern, text, re.M)
        value = found[1] if found else None
        if value != expected:
            errors.append(f"{what} : {value!r} au lieu de {expected!r} ({name})")
    errors += [f"fichier absent : {name}" for name in FILES if not (root / name).is_file()]
    return errors


def main() -> None:
    errors = check()
    if errors:
        print("\n".join(f"ERREUR : {e}" for e in errors))
        print("Identité de l'app changée : prévoir la migration (docs/spec/distribution.md § Identité de l'app), "
              "puis mettre à jour IDENTITY dans kmp/tools/validate_identity.py.")
        sys.exit(1)
    print(f"Identité de l'app inchangée ({len(IDENTITY)} valeurs, {len(FILES)} fichier).")


if __name__ == "__main__":
    main()
