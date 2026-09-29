#!/usr/bin/env bash
# Vérification F-Droid de Kairos 3 (docs/spec-v3/publication.md § F-Droid).
#
# À lancer DANS l'image officielle registry.gitlab.com/fdroid/fdroidserver:buildserver
# (celle des serveurs de construction de F-Droid), le dépôt monté en lecture :
#
#   docker run --rm --network host -v "$PWD":/repo:ro \
#     registry.gitlab.com/fdroid/fdroidserver:buildserver \
#     bash /repo/kmp/tools/fdroid_check.sh [--binaries]
#
# 1. `fdroid lint` sur la recette publiée (fdroid/com.skohscripts.kairos.yml) ;
# 2. `fdroid build` d'une copie de la recette pointée sur le commit courant et la
#    version de kmp/gradle.properties, identifiant définitif forcé
#    (-Pkairos.preview=false) : l'APK non signé est écrit dans $OUT ;
# 3. avec --binaries (tag d'une version finale) : la recette garde `Binaries`,
#    et `fdroid build` compare lui-même l'APK reconstruit à l'APK publié sur
#    GitHub (signature recopiée par apksigcopier) : c'est la preuve de
#    reproductibilité qu'exige F-Droid pour publier l'APK signé par nous.
set -euo pipefail

REPO=${REPO:-/repo}
WORK=${WORK:-/tmp/fdroid-check}
OUT=${OUT:-$WORK/out}
APP=com.skohscripts.kairos
FDROIDSERVER_REF=${FDROIDSERVER_REF:-master}
BINARIES=false
[ "${1:-}" = "--binaries" ] && BINARIES=true

rm -rf "$WORK" && mkdir -p "$WORK/fdroiddata/metadata" "$OUT"
git clone -q --depth 1 --branch "$FDROIDSERVER_REF" https://gitlab.com/fdroid/fdroidserver.git "$WORK/fdroidserver"
export PATH="$WORK/fdroidserver:$PATH"

# Dépôt source : une copie git locale du commit courant (le montage est en lecture
# seule), sur une branche : en CI, HEAD est détaché (commit de fusion d'une PR), et
# `fdroid build` ne retrouverait pas un commit qu'aucune branche ne porte.
git config --global --add safe.directory '*'
COMMIT=$(git -C "$REPO" rev-parse HEAD)
git init -q "$WORK/source"
git -C "$WORK/source" fetch -q "$REPO" "HEAD:refs/heads/fdroid-check"
git -C "$WORK/source" checkout -q fdroid-check

version_name=$(sed -n 's/^kairos.versionName=//p' "$REPO/kmp/gradle.properties")
version_code=$(sed -n 's/^kairos.versionCode=//p' "$REPO/kmp/gradle.properties")
echo "Kairos $version_name ($version_code), commit $COMMIT"

cd "$WORK/fdroiddata"
# Catégories officielles : `fdroid lint` lit config/ de fdroiddata (liste et icônes).
# Clone partiel : seulement ce dossier, le dépôt entier pèse plusieurs gigaoctets.
git clone -q --depth 1 --filter=blob:none --sparse https://gitlab.com/fdroid/fdroiddata.git "$WORK/upstream"
git -C "$WORK/upstream" sparse-checkout set config
cp -r "$WORK/upstream/config" config
cat > config.yml <<YML
sdk_path: ${ANDROID_HOME:-/opt/android-sdk}
YML
chmod 600 config.yml

# 1. Lint de la recette telle qu'elle sera proposée à fdroiddata.
cp "$REPO/fdroid/$APP.yml" "metadata/$APP.yml"
fdroid lint "$APP"

# 2. Recette de vérification : même contenu, construite au commit courant.
python3 - "$REPO/fdroid/$APP.yml" "metadata/$APP.yml" "$WORK/source" "$COMMIT" "$version_name" "$version_code" "$BINARIES" <<'PY'
import sys
from ruamel.yaml import YAML

src, dst, repo, commit, name, code, binaries = sys.argv[1:]
yaml = YAML()
recipe = yaml.load(open(src, encoding="utf-8"))
build = recipe["Builds"][-1]
build["versionName"] = name
build["versionCode"] = int(code)
build["commit"] = commit
# Une bêta porte l'identifiant « .preview » ; la recette construit le définitif.
build["gradleprops"] = ["kairos.preview=false"]
recipe["Builds"] = [build]
recipe["Repo"] = repo
recipe["CurrentVersion"] = name
recipe["CurrentVersionCode"] = int(code)
if binaries != "true":
    # Pas d'APK publié à comparer (bêta, ou pas encore de release).
    recipe.pop("Binaries", None)
yaml.dump(recipe, open(dst, "w", encoding="utf-8"))
PY

# fdroiddata est un dépôt git : `fdroid build` y date la construction
# (SOURCE_DATE_EPOCH) d'après l'historique de la recette.
git init -q . && git add -A && git -c user.name=ci -c user.email=ci@localhost commit -q -m "Kairos $version_name"

# `fdroid build` sort avec 0 même quand la construction échoue : on juge sur l'APK produit.
fdroid build --verbose --no-tarball --latest "$APP:$version_code" || true
apk=$(ls unsigned/"${APP}_${version_code}".apk 2>/dev/null || true)
if [ -z "$apk" ]; then
  echo "ÉCHEC : fdroid build n'a produit aucun APK (voir le journal ci-dessus)." >&2
  exit 1
fi
cp "$apk" "$OUT/"
sha256sum "$OUT"/*.apk
echo "Vérification F-Droid réussie."
