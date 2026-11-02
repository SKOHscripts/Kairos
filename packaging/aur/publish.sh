#!/usr/bin/env bash
# Pousse PKGBUILD et .SRCINFO de <sortie> (build.sh) dans le dépôt AUR kairos-bin
# (docs/spec/distribution.md § Paquet Arch Linux). Clé privée dans AUR_SSH_PRIVATE_KEY.
set -euo pipefail

out=$(realpath "${1:?sortie}")
: "${AUR_SSH_PRIVATE_KEY:?secret AUR_SSH_PRIVATE_KEY absent}"
# Clé d'hôte Ed25519 de l'AUR, publiée sur https://aur.archlinux.org/ : la clé privée ne parle qu'à elle.
expected='SHA256:RFzBCUItH9LZS0cKB5UE6ceAYhBD5C8GeOBip8Z11+4'

ssh_dir=$(mktemp -d)
trap 'rm -rf "$ssh_dir"' EXIT
printf '%s\n' "$AUR_SSH_PRIVATE_KEY" > "$ssh_dir/key"
chmod 600 "$ssh_dir/key"
ssh-keyscan -t ed25519 aur.archlinux.org 2> /dev/null > "$ssh_dir/known_hosts" || true
if [ ! -s "$ssh_dir/known_hosts" ]; then
  echo "clé d'hôte de l'AUR introuvable (aur.archlinux.org:22 injoignable ?)" >&2
  exit 1
fi
actual=$(ssh-keygen -lf "$ssh_dir/known_hosts" | awk '{print $2}')
if [ "$actual" != "$expected" ]; then
  echo "clé d'hôte de l'AUR inattendue : $actual (attendue $expected)" >&2
  exit 1
fi
export GIT_SSH_COMMAND="ssh -i $ssh_dir/key -o UserKnownHostsFile=$ssh_dir/known_hosts -o StrictHostKeyChecking=yes -o IdentitiesOnly=yes"

repo="$ssh_dir/kairos-bin"
git clone -q ssh://aur@aur.archlinux.org/kairos-bin.git "$repo"
cp "$out/PKGBUILD" "$out/.SRCINFO" "$repo/"
cd "$repo"
# Premier envoi : dépôt vide, l'AUR crée la base de paquet à la poussée sur master.
git checkout -q -B master
git add PKGBUILD .SRCINFO
if git diff --cached --quiet; then
  echo "Rien à pousser : l'AUR a déjà ce contenu."
  exit 0
fi
version=$(sed -n 's/^\tpkgver = //p' .SRCINFO)
git -c user.name="Kairos release" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit -q -m "Kairos $version"
git push -q origin master
echo "kairos-bin $version publié sur l'AUR."
