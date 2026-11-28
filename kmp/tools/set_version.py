#!/usr/bin/env python3
"""Pose la version de Kairos (docs/spec/distribution.md § Cycle des versions).

    python3 kmp/tools/set_version.py 4.1.0-beta.2

Écrit kairos.versionName et kairos.versionCode dans kmp/gradle.properties, le code suivant la formule d'AppVersion
(docs/spec/distribution.md § Versionnage) : à partir de la 4.2.0, X*1000000 + Y*10000 + Z*100 + s (s = numéro de beta,
99 pour une finale), une seule app pour la beta et la stable ; avant, X*10000 + Y*100 + Z, −500 + N pour une beta, −1000
+ N pour une alpha. Refuse ce que le cycle n'autorise pas : une préversion d'une version qui ne finit pas par .0, un
numéro de beta hors de 1 à 98, une alpha à partir de la 4.2.0.

Écrit aussi la même version dans `release-please-config.json` (`release-as`, docs/spec/distribution.md § Release Please) :
la PR de release propose ainsi la version que `main` porte, beta comprise.
"""
import json
import pathlib
import re
import sys

PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$")


def version_code(name: str) -> int:
    m = PATTERN.match(name)
    if not m:
        sys.exit(f"version illisible : {name} (attendu X.Y.Z ou X.Y.Z-beta.N)")
    major, minor, patch, stage, number = m.groups()
    major, minor, patch = int(major), int(minor), int(patch)
    if minor > 99 or patch > 99:
        sys.exit("Y et Z vont de 0 à 99")
    single_app = major * 100 + minor >= 402
    if stage is not None:
        n = int(number)
        if patch != 0:
            sys.exit("une préversion annonce une version X.Y.0")
        if not 1 <= n <= 98:
            sys.exit("numéro de préversion de 1 à 98 (99 marque une finale)")
        if single_app and stage == "alpha":
            sys.exit("plus d'alpha à partir de la 4.2.0 : une beta")
    if single_app:
        return major * 1_000_000 + minor * 10_000 + patch * 100 + (99 if stage is None else int(number))
    base = major * 10000 + minor * 100 + patch
    if stage is None:
        return base
    return base - (500 if stage == "beta" else 1000) + int(number)


ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
RELEASE_PLEASE = ROOT / "release-please-config.json"


def set_release_as(config: str, name: str) -> str:
    """Le texte de `release-please-config.json` avec `release-as` = [name] ; le reste du fichier est gardé octet pour octet."""
    json.loads(config)  # refuse un fichier illisible avant de le réécrire
    out, count = re.subn(r'("release-as"\s*:\s*)"[^"]*"', lambda m: f'{m.group(1)}"{name}"', config, count=1)
    if count != 1:
        sys.exit("release-please-config.json sans release-as (docs/spec/distribution.md § Release Please)")
    return out


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    name = sys.argv[1].removeprefix("v")
    code = version_code(name)
    props = pathlib.Path(__file__).resolve().parent.parent / "gradle.properties"
    text = props.read_text(encoding="utf-8")
    text = re.sub(r"^kairos\.versionName=.*$", f"kairos.versionName={name}", text, flags=re.M)
    text = re.sub(r"^kairos\.versionCode=.*$", f"kairos.versionCode={code}", text, flags=re.M)
    props.write_text(text, encoding="utf-8")
    if RELEASE_PLEASE.exists():
        RELEASE_PLEASE.write_text(set_release_as(RELEASE_PLEASE.read_text(encoding="utf-8"), name), encoding="utf-8")
    print(f"{name} ({code})")


if __name__ == "__main__":
    main()
