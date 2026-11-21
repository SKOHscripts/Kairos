# Licence : AGPLv3 ou ultérieure, attribution, documentation en CC BY-SA 4.0

_Rôle : dire sous quelles conditions Kairos se réutilise, et ce qu'un dérivé doit garder. Fichiers couverts : `LICENSE`,
`NOTICE`, `LICENSES/` (`CC-BY-SA-4.0.txt`, `MIT.txt`), `README.md` et `README.fr.md` (§ License / Licence, badge),
`kmp/ui/.../screens/AboutScreen.kt` et la chaîne `about_license` (six langues), `kmp/desktopApp/build.gradle.kts`
(`copyright`, `licenseFile`), `packaging/aur/PKGBUILD` (`license`), `fdroid/com.skohscripts.kairos.yml` (`License`),
`fdroid/README.md`, `fastlane/metadata/android/*/full_description.txt`, pieds de page de `site/`._

État : décidée et implémentée le 2026-11-21 (choix du propriétaire), à partir de la `4.4.0` (la `4.3.0`, taguée sur `d58b86ba`
le 2026-11-20, est sortie sous MIT).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos était sous licence MIT : chacun pouvait reprendre le code, le modifier et l'enfermer dans un logiciel fermé, à la
seule condition de garder la mention de copyright dans les fichiers, jamais à l'écran. Le propriétaire veut que Kairos
reste libre et qu'on le cite : un dérivé doit rester libre, y compris proposé en ligne, et dire d'où il vient.

### Comportement attendu (utilisateur)

- **Code** : GNU Affero General Public License, version 3 **ou toute version ultérieure** (`AGPL-3.0-or-later`). Un logiciel
  qui reprend le code de Kairos et qui est distribué, ou proposé à des utilisateurs à travers un réseau, publie ses sources
  sous la même licence.
- **Attribution** (condition additionnelle de la section 7(b) de l'AGPLv3) : un dérivé qui affiche des mentions légales
  (écran « À propos » ou équivalent) y garde : « Based on Kairos, by Corentin Michel — https://github.com/SKOHscripts/Kairos ».
  Kairos lui-même affiche sa mention d'auteur dans « À propos » : « Kairos, par Corentin Michel
  (github.com/SKOHscripts/Kairos). Logiciel libre sous licence GNU AGPL v3 ou ultérieure. ».
- **Documentation** : Creative Commons Attribution-Partage dans les mêmes conditions 4.0 (`CC-BY-SA-4.0`), avec la mention
  « Kairos documentation, by Corentin Michel — https://github.com/SKOHscripts/Kairos » : `docs/`, `README.md`,
  `README.fr.md`, textes et images des fiches (`fastlane/metadata/`), textes et images de la page (`site/`), captures.
  Les extraits de code cités dans la documentation sont aussi disponibles sous l'AGPL.
- **Avant le changement** : le code publié jusqu'au commit `d58b86ba` (2026-11-20, version `4.3.0` comprise) était sous
  licence MIT et le reste ; les contributions faites sous MIT gardent leur mention (`LICENSES/MIT.txt`).
- **Tiers** : Roboto reste sous OFL (`kmp/licenses/Roboto-OFL.txt`), chaque dépendance sous sa propre licence.
- Partout où Kairos dit sa licence (README, « À propos », fiches des magasins, page, recette F-Droid, paquet AUR, installeurs),
  il dit « AGPL v3 ou ultérieure ».

### Critères de succès

- GitHub reconnaît la licence du dépôt (`LICENSE` est le texte officiel de la FSF, sans ajout) ; le badge du README la montre.
- Aucune mention de « licence MIT » pour Kairos lui-même ne reste dans le dépôt, hors historique (`plan-v3-kotlin.md`,
  `distribution.md` § SignPath) et hors ce qui dit l'ancienne licence (`LICENSES/MIT.txt`, `NOTICE`, dernière ligne de la
  section Licence des README) ; les dépendances sous MIT gardent leur mention.
- « À propos » affiche la mention d'auteur et la licence dans les six langues (`StringsParityTest`).
- La recette F-Droid déclare `AGPL-3.0-or-later` (identifiant SPDX).

### Hors périmètre

- **En-tête de licence dans chaque fichier source** : non. La FSF le recommande sans l'exiger ; `LICENSE`, `NOTICE` et le
  README à la racine suffisent, et des centaines d'en-têtes alourdiraient chaque fichier.
- Protéger les idées (méthode WSJF, ordonnancement) : aucune licence ne le fait ; ce n'est pas l'objet.
- Une clause « non commerciale » : écartée, elle rendrait Kairos non libre (refusé par F-Droid et l'OSI).
- Un accord de contribution (CLA) : pas demandé.

## 2. Solution technique

- `LICENSE` : le texte officiel `https://www.gnu.org/licenses/agpl-3.0.txt`, inchangé (SHA-256
  `0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0`), pour que la détection de GitHub (licensee) le
  reconnaisse.
- `NOTICE` (anglais, comme `LICENSE`) : copyright, licence du code et choix « ou ultérieure », **condition additionnelle 7(b)**
  avec la mention exacte, licence de la documentation et sa mention, ancienne licence MIT et ses contributions, tiers.
  L'AGPL (section 7) admet des conditions additionnelles « écrites séparément » : elles vivent ici plutôt que dans `LICENSE`.
- `LICENSES/CC-BY-SA-4.0.txt` : le texte officiel `https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt`.
  `LICENSES/MIT.txt` : l'ancien `LICENSE`, inchangé.
- README (anglais et français) : badge « License: AGPL-3.0-or-later » (même image `github/license`, qui lit la licence
  détectée) ; section « License » / « Licence » qui résume code, attribution, documentation et ancienne licence.
- « À propos » (`AboutScreen`) : chaîne `about_license` mise à jour dans les six langues (mention d'auteur, lien court,
  licence, données locales) ; le bouton « Code source » reste le lien cliquable.
- Bureau : `copyright = "© 2026 Corentin Michel, AGPL-3.0-or-later"` ; `licenseFile` reste `LICENSE` (texte de la licence
  montré par les installeurs et copié en `share/doc/copyright` du `.deb`).
- AUR : `license=('AGPL-3.0-or-later')` (identifiant SPDX, que pacman accepte).
- F-Droid : `License: AGPL-3.0-or-later` ; `fdroid/README.md`.
- Fiches Fastlane (six langues) et pieds de page de `site/` (huit pages) : « licence GNU AGPL v3 ou ultérieure ».

### Décisions et pièges tracés

- **AGPL plutôt que GPL** (choix du propriétaire, 2026-11-21) : Kairos a une version web ; la GPL laisse proposer une version
  modifiée en ligne sans en publier les sources, l'AGPL non.
- **« Ou ultérieure »** : compatibilité avec une future version de la licence ; le propriétaire, principal titulaire des
  droits, peut de toute façon changer la licence des versions futures.
- **Attribution par la section 7(b)** : c'est la seule voie, dans l'AGPL, pour exiger une mention d'auteur visible dans
  l'interface d'un dérivé ; la MIT n'exige qu'une mention dans les fichiers.
- **Documentation en CC BY-SA 4.0** : l'AGPL est écrite pour du code ; la CC BY-SA 4.0 en est l'équivalent pour des textes et
  des images (citation obligatoire, partage dans les mêmes conditions). Écartées : tout en AGPL (citation mal adaptée à un
  texte recopié), CC BY 4.0 (un texte repris pourrait être fermé).
- **`LICENSE` sans ajout** : une condition additionnelle ajoutée en tête ou en fin du texte de la FSF empêcherait GitHub de
  reconnaître la licence (badge « other »).
- **Contributions sous MIT** : le code publié sous MIT, y compris celui d'autres contributeurs, peut être intégré dans une
  œuvre sous AGPL à condition de garder sa mention MIT, d'où `LICENSES/MIT.txt` et la ligne de `NOTICE`. Le contributeur
  extérieur (PR #242) est prévenu dans la pull request du changement.
