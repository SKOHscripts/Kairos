"""Vérifie les métadonnées Fastlane de Kairos 3 (docs/spec/publication.md).

F-Droid et IzzyOnDroid lisent `fastlane/metadata/android/<langue>/` dans le
dépôt, au commit du tag : une fiche incomplète ou trop longue se voit trop tard.
Ce script échoue (code 1) si, pour fr-FR et en-US :

- title.txt (50), short_description.txt (80), full_description.txt (4000)
  manquent, sont vides ou trop longs ;
- le journal de la version courante (`changelogs/<versionCode>.txt`, 500 au plus)
  manque — versionCode lu dans kmp/gradle.properties ;
- images/icon.png n'est pas un PNG de 512 × 512 ;
- images/phoneScreenshots/ ne contient pas au moins deux PNG, numérotés 1, 2…

Sans dépendance :

    python kmp/tools/validate_fastlane.py
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
META = ROOT / "fastlane" / "metadata" / "android"
LOCALES = ("fr-FR", "en-US")
LIMITS = {"title.txt": 50, "short_description.txt": 80, "full_description.txt": 4000}


def version_code() -> str:
    for line in (ROOT / "kmp" / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("kairos.versionCode="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("ERREUR : kairos.versionCode absent de kmp/gradle.properties")


def png_size(path: Path) -> tuple[int, int]:
    header = path.read_bytes()[:24]
    if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise SystemExit(f"ERREUR : {path} n'est pas un PNG")
    return struct.unpack(">II", header[16:24])


def main() -> None:
    code = version_code()
    errors: list[str] = []
    for locale in LOCALES:
        folder = META / locale
        for name, limit in LIMITS.items():
            path = folder / name
            text = path.read_text(encoding="utf-8").strip() if path.exists() else ""
            if not text:
                errors.append(f"{path} manquant ou vide")
            elif len(text) > limit:
                errors.append(f"{path} : {len(text)} caractères (au plus {limit})")
        changelog = folder / "changelogs" / f"{code}.txt"
        if not changelog.exists():
            errors.append(f"journal absent pour versionCode {code} : {changelog}")
        elif len(changelog.read_text(encoding="utf-8").strip()) > 500:
            errors.append(f"{changelog} dépasse 500 caractères")
        icon = folder / "images" / "icon.png"
        if not icon.exists() or png_size(icon) != (512, 512):
            errors.append(f"{icon} absent ou pas en 512 × 512")
        shots = sorted((folder / "images" / "phoneScreenshots").glob("*.png"))
        if len(shots) < 2:
            errors.append(f"{locale} : moins de deux captures de téléphone")
        for i, shot in enumerate(sorted(shots, key=lambda p: int(p.stem) if p.stem.isdigit() else 0), 1):
            if shot.stem != str(i):
                errors.append(f"{shot} : captures à numéroter 1, 2, 3…")
                break
            width, height = png_size(shot)
            if min(width, height) < 320 or max(width, height) > 3840:
                errors.append(f"{shot} : {width} × {height} hors des limites (320 à 3840 px)")
    if errors:
        print("\n".join(f"ERREUR : {e}" for e in errors))
        sys.exit(1)
    print(f"Métadonnées Fastlane valides ({', '.join(LOCALES)}, versionCode {code}).")


if __name__ == "__main__":
    main()
