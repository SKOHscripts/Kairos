#!/usr/bin/env python3
"""Pose la version de Kairos (docs/spec/distribution.md § Cycle des versions).

    python3 kmp/tools/set_version.py 4.1.0-beta.2

Écrit kairos.versionName et kairos.versionCode dans kmp/gradle.properties, le code suivant la formule d'AppVersion :
X*10000 + Y*100 + Z, −500 + N pour une beta, −1000 + N pour une alpha. Refuse ce que le cycle n'autorise pas : une
préversion d'une version qui ne finit pas par .0, un numéro de préversion hors de 1 à 99.
"""
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
    base = major * 10000 + minor * 100 + patch
    if stage is None:
        return base
    n = int(number)
    if patch != 0:
        sys.exit("une préversion annonce une version X.Y.0")
    if not 1 <= n <= 99:
        sys.exit("numéro de préversion de 1 à 99")
    return base - (500 if stage == "beta" else 1000) + n


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
    print(f"{name} ({code})")


if __name__ == "__main__":
    main()
