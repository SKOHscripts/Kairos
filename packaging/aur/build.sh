#!/usr/bin/env bash
# Construit, installe et teste le paquet AUR kairos-bin dans un conteneur archlinux:base-devel (lancé en root), puis
# écrit PKGBUILD et .SRCINFO dans <sortie> (docs/spec/distribution.md § Paquet Arch Linux).
#   docker run --rm -v "$PWD:/w" -w /w archlinux:base-devel bash packaging/aur/build.sh 4.0.0 Kairos-linux-x64.deb aur-out
set -euo pipefail

version=${1:?version}
deb=$(realpath "${2:?.deb}")
out=${3:?sortie}
here=$(cd "$(dirname "$0")" && pwd)

case "$version" in
  *-*) echo "kairos-bin ne publie que les versions stables (reçu $version)" >&2; exit 1 ;;
esac

pacman -Syu --noconfirm --needed namcap > /dev/null

work=$(mktemp -d)
sha=$(sha256sum "$deb" | cut -d' ' -f1)
sed -e "s/@PKGVER@/$version/" -e "s/@SHA256@/$sha/" "$here/PKGBUILD" > "$work/PKGBUILD"
# Sous le nom de la source : makepkg le prend au lieu de le télécharger, et en vérifie la somme.
cp "$deb" "$work/Kairos-$version-linux-x64.deb"
id -u builder > /dev/null 2>&1 || useradd -m builder
chown -R builder: "$work"

# makepkg refuse root ; les dépendances sont installées ensuite, depuis les dépôts, par pacman -U.
su builder -c "cd '$work' && makepkg -f --nodeps --noconfirm"
pkg=$(ls "$work"/kairos-bin-"$version"-*-x86_64.pkg.tar.zst)
pacman -U --noconfirm "$pkg"

# Avis seulement : namcap signale toujours « ELF hors chemin valide » (/opt) et « java-runtime non déclaré » (runtime
# embarqué), attendus pour cette image.
echo "== namcap (avis)"
namcap "$work/PKGBUILD" || true
namcap "$pkg" || true

echo "== Fichiers installés"
for f in /usr/bin/kairos /opt/kairos/bin/Kairos /opt/kairos/lib/app/kairos-managed \
  /usr/share/applications/kairos.desktop /usr/share/pixmaps/kairos.png /usr/share/licenses/kairos-bin/LICENSE; do
  [ -e "$f" ] || { echo "manquant : $f" >&2; exit 1; }
done
# Aucune bibliothèque partagée de l'image ne doit manquer (dépendances incomplètes).
# libjvm.so et les bibliothèques du runtime se trouvent par son propre dossier, comme le lanceur les charge.
runtime=/opt/kairos/lib/runtime/lib
missing=$(find /opt/kairos -name '*.so*' -type f -exec env LD_LIBRARY_PATH="$runtime:$runtime/server" ldd {} \; 2>/dev/null \
  | grep 'not found' | sort -u || true)
if [ -n "$missing" ]; then echo "bibliothèques manquantes :" >&2; echo "$missing" >&2; exit 1; fi

echo "== Auto-test par /usr/bin/kairos"
data=$(mktemp -d); screens=$(mktemp -d); chown builder: "$data" "$screens"
su builder -c "KAIROS_DATA_DIR='$data' /usr/bin/kairos --self-test='$screens'"
ls "$screens" | head -3

mkdir -p "$out"
cp "$work/PKGBUILD" "$out/PKGBUILD"
su builder -c "cd '$work' && makepkg --printsrcinfo" > "$out/.SRCINFO"
echo "== $out"
cat "$out/.SRCINFO"
