"""Dossier du paquet MSIX du Microsoft Store (docs/spec/distribution.md § Microsoft Store, #215).

Copie l'image jpackage de Windows (`createDistributable` : `Kairos.exe`, `app/`, `runtime/`) dans <dossier>, y écrit le
manifeste `packaging/msix/AppxManifest.xml` rempli (version de `kmp/gradle.properties`) et les logos
`packaging/msix/Assets/`, et pose le marqueur d'installation gérée `app/kairos-managed` : la copie du Store ne cherche pas
de mise à jour (`SystemPackage`). La CI indexe ensuite les logos (`makepri`) et emballe le dossier (`makeappx pack`),
outils du Windows SDK. Sans dépendance :

    python3 kmp/tools/make_msix.py <image> <dossier>
"""
from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGING = ROOT / "packaging" / "msix"
GRADLE_PROPERTIES = ROOT / "kmp" / "gradle.properties"
# Marqueurs à côté des JAR (SystemPackage.MARKER, PortableCopy.MARKER) : sous Windows, dans `app/`.
MANAGED = "kairos-managed"
PORTABLE = "kairos-portable"


def msix_version(version_name: str) -> str:
    """La version de l'installeur (AppVersion.packageVersion : X.Y.(Z*100 + s), s = numéro de beta, 99 pour une finale)
    suivie du quatrième nombre, que le Store réserve et veut à 0 : 4.6.0 → 4.6.99.0, 4.6.0-beta.2 → 4.6.2.0."""
    m = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?", version_name)
    if not m:
        raise ValueError(f"kairos.versionName illisible : {version_name}")
    major, minor, patch, beta = m.groups()
    return f"{major}.{minor}.{int(patch) * 100 + (int(beta) if beta else 99)}.0"


def version_name(properties: Path = GRADLE_PROPERTIES) -> str:
    found = re.search(r"^kairos\.versionName=(.+)$", properties.read_text(encoding="utf-8"), re.M)
    if not found:
        raise ValueError(f"kairos.versionName absent de {properties}")
    return found[1].strip()


def build(image: Path, out: Path, version: str) -> None:
    if not (image / "Kairos.exe").is_file() or not (image / "app").is_dir():
        raise ValueError(f"{image} n'est pas une image jpackage de Windows (Kairos.exe, app/)")
    # Le marqueur portable ferait proposer un raccourci à la copie du Store (raccourci-portable.md) : la CI construit le
    # paquet avant de le poser ; une image qui le porte déjà est refusée plutôt que nettoyée en silence.
    if (image / "app" / PORTABLE).exists():
        raise ValueError(f"{image} porte le marqueur portable {PORTABLE}")
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(image, out)
    manifest = (PACKAGING / "AppxManifest.xml").read_text(encoding="utf-8")
    (out / "AppxManifest.xml").write_text(manifest.replace("@VERSION@", msix_version(version)), encoding="utf-8")
    shutil.copytree(PACKAGING / "Assets", out / "Assets")
    (out / "app" / MANAGED).write_text("", encoding="utf-8")


def main(argv: list[str]) -> None:
    if len(argv) != 3:
        sys.exit(__doc__)
    version = version_name()
    build(Path(argv[1]), Path(argv[2]), version)
    print(f"Dossier MSIX {msix_version(version)} ({version}) : {argv[2]}")


if __name__ == "__main__":
    main(sys.argv)
