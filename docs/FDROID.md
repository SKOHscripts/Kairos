# Publication sur F-Droid — mode opératoire

Ce document décrit comment publier l'APK Android de Kairos sur
[F-Droid](https://f-droid.org). Il complète
[`ANDROID_PACKAGING.md`](ANDROID_PACKAGING.md) (qui décrit *comment l'APK est
construit*) : ici, il s'agit de *comment le faire accepter et distribuer par
F-Droid*.

## Verdict avant de commencer

**Faisable, mais avec un point de blocage technique à lever en premier**,
avant d'investir du temps dans le reste de la procédure : le pip de Chaquopy
(`android/app/build.gradle`, bloc `chaquopy { pip { ... } }`) installe des
paquets Python contenant du code natif (`cryptography`, `cffi`, `greenlet`,
`markupsafe`) sous forme de **wheels précompilées pour Android**. Ces wheels
ne viennent pas de PyPI (qui n'a que des wheels Linux/Windows/macOS pour ces
paquets) mais du **dépôt de wheels propre à Chaquopy**
(`chaquo.com/pypi-13.1`, voir `docs/ANDROID_PACKAGING.md`) : un mirroir tiers,
non listé parmi les sources de binaires précompilés explicitement autorisées
par la [politique d'inclusion F-Droid](https://f-droid.org/docs/Inclusion_Policy/)
(Debian, Maven Central/Google/JitPack, PyPI officiel, quelques exceptions
nommées). F-Droid exige que tout code binaire embarqué dans l'APK soit
recompilé par leur propre serveur de build à partir des sources, pas
téléchargé déjà compilé depuis un mirroir tiers.

Ce point n'est **pas documenté noir sur blanc** comme un refus automatique —
la politique évolue et des exceptions existent au cas par cas. La bonne
pratique est donc de le soumettre explicitement dans le ticket RFP (étape 1
ci-dessous) **avant** de préparer toute la métadonnée, pour ne pas découvrir
un refus après plusieurs heures de travail. Deux issues possibles :

- F-Droid accepte (précédent existant pour d'autres apps Chaquopy) → suite de
  la procédure normale.
- F-Droid refuse → il faudra soit trouver un recueil de wheels Android
  acceptable pour `cryptography`/`cffi`/`greenlet`/`markupsafe` compilé par
  leurs soins, soit changer de chaîne d'empaquetage (BeeWare/Briefcase avec
  compilation source complète, ou retrait des dépendances natives — pas
  trivial : `greenlet` vient de `sqlalchemy`, `markupsafe` de `jinja2`, tous
  deux difficiles à retirer). Ce serait un chantier séparé, à ne pas engager
  avant d'avoir la réponse de F-Droid.

Les exécutables desktop (PyInstaller, Windows/Linux) ne sont **pas concernés**
par F-Droid, qui ne distribue que des APK Android. Le reste de ce document ne
traite que de `android/`.

## Prérequis déjà en place

- **Licence** : MIT (`LICENSE`), licence libre reconnue OSI — conforme.
- **Dépôt public** : `github.com/SKOHscripts/Kairos` — conforme.
- **Pas de traqueur/service propriétaire imposé** : ni Firebase, ni Google
  Play Services, ni SDK publicitaire. Le scanner automatique F-Droid
  (`fdroid scanner`) ne devrait rien remonter sur ces points.
- **Permissions justifiées et minimales** : `INTERNET` (loopback uniquement,
  commenté dans `AndroidManifest.xml`) et `POST_NOTIFICATIONS` (demandée à
  l'usage, jamais au démarrage) — pas de permission superflue à justifier en
  revue.
- **Versionnage** : tags `vX.Y.Z` déjà utilisés par
  `.github/workflows/release.yml` (`on: push: tags: ["v*"]`), et
  `versionCode`/`versionName` déjà dérivés du tag dans
  `android/app/build.gradle` (`kairosVersionCode`/`kairosVersionName`) — F-Droid
  a besoin exactement de ce couple (tag git + versionCode strictement
  croissant), déjà garanti par le commentaire du fichier.
- **Build reproductible sans intervention manuelle** : `./gradlew
  assembleRelease` fonctionne seul (SDK Android + JDK 17 + `python3.13`
  requis), sans étape interactive — condition de base pour que le serveur de
  build F-Droid puisse le rejouer.

## Point de vigilance secondaire : signature APK

F-Droid **compile et signe lui-même** l'APK avec sa propre clé de dépôt — le
`KAIROS_KEYSTORE_*` fourni à la CI GitHub (voir `release.yml` et le README, §
Empaquetage) n'entre pas en jeu pour la version F-Droid. Conséquence
concrète à documenter pour les utilisateurs : **l'APK des releases GitHub et
l'APK F-Droid sont deux identités de signature différentes**. Un utilisateur
qui a déjà installé la version GitHub ne pourra pas « mettre à jour » vers la
version F-Droid par-dessus (Android refuse un remplacement avec une signature
différente) — il doit désinstaller puis réinstaller pour changer de canal, et
choisir un seul canal ensuite. À mentionner dans le README une fois la
publication actée.

## Point de vigilance mineur : anti-feature possible

L'intégration TimeTree (`timetree-exporter`, optionnelle, désactivée par
défaut, configurée par l'utilisateur depuis **Réglages**) pointe vers un
service cloud propriétaire. F-Droid pourrait étiqueter l'app avec
l'anti-feature **`NonFreeNet`** (« facilite l'usage d'un service réseau non
libre ») — ce n'est pas un motif de refus, juste un badge informatif affiché
sur la fiche de l'app, courant pour ce genre d'intégration optionnelle.
Rien à changer côté code ; à assumer dans la description soumise (voir
gabarit plus bas).

## Étapes

### 1. Poser la question de blocage (RFP)

Ouvrir un ticket **RFP (Request For Packaging)** sur
[`gitlab.com/fdroid/rfp`](https://gitlab.com/fdroid/rfp/-/issues), avant tout
autre travail. Contenu à inclure :

- Lien du dépôt, licence (MIT), description courte de l'app.
- Description explicite de la chaîne de build : Gradle + plugin Chaquopy
  (open source, MIT, publié sur Maven Central depuis la 12.0.1 — Kairos utilise
  la 17.0.0, voir `android/build.gradle`) qui embarque un interpréteur CPython
  et installe les dépendances Python via `pip` pendant le build Gradle
  (`android/app/build.gradle`, `chaquopy.pip.install`, source :
  `packaging/android-requirements.txt`).
- Le point précis à trancher : certaines de ces dépendances
  (`cryptography`, `cffi`, `greenlet`, `markupsafe`) n'ont pas de wheel PyPI
  pour Android et sont donc résolues par pip depuis le dépôt de wheels
  Android propre à Chaquopy (`chaquo.com/pypi-13.1`) plutôt que depuis PyPI
  officiel. Demander explicitement si c'est acceptable ou rédhibitoire, et si
  une alternative existe côté F-Droid (mirroir de confiance, recompilation
  locale par leur serveur, etc.).

Attendre une réponse avant de continuer — c'est ce ticket qui détermine si le
reste a un sens.

### 2. Préparer la métadonnée « upstream » dans le dépôt Kairos

F-Droid préfère désormais que chaque projet héberge sa propre métadonnée au
format *fastlane*, dans son propre dépôt (plutôt que tout écrire directement
dans `fdroiddata`). Créer dans Kairos :

```
fastlane/metadata/android/fr-FR/
├── title.txt                     # "Kairos" (≤ 50 caractères)
├── short_description.txt         # ≤ 80 caractères
├── full_description.txt          # description longue, HTML limité autorisé
├── changelogs/
│   └── <versionCode>.txt         # ex. 10000.txt pour la v1.0.0 — ≤ 500 caractères
└── images/
    ├── icon.png                  # 512×512
    ├── featureGraphic.png        # 1024×500 (optionnelle mais recommandée)
    └── phoneScreenshots/
        ├── 1.png
        └── ...
```

Ajouter un dossier `en-US/` équivalent (F-Droid affiche l'anglais par défaut
si `fr-FR` seul manque de traduction sur certains champs). Le `versionCode`
du nom de fichier changelog doit correspondre exactement à celui calculé par
`kairosVersionCode` dans `android/app/build.gradle` pour le tag concerné
(ex. `v1.0.0` → `10000`).

Contenu suggéré (à ajuster) :

- `short_description.txt` : « Dashboard de tâches local, priorisation WSJF et
  planning automatique, sans compte ni cloud. »
- `full_description.txt` : reprendre le paragraphe « En bref » du `README.md`,
  en précisant que l'intégration TimeTree est optionnelle et désactivée par
  défaut (cf. anti-feature `NonFreeNet` ci-dessus).

Committer ces fichiers sur `main` (ou la branche par défaut) : ils doivent
être présents au tag qui sera proposé au build.

### 3. Installer et tester avec les outils F-Droid en local

```bash
pipx install fdroidserver   # ou: pip install --user fdroidserver
mkdir -p ~/fdroid-test/metadata
cd ~/fdroid-test
fdroid init
```

Créer `metadata/com.skohscripts.kairos.yml` (brouillon local, avant
soumission — voir gabarit à l'étape 4) puis :

```bash
fdroid readmeta          # valide la syntaxe YAML
fdroid lint com.skohscripts.kairos     # règles de style/complétude F-Droid
fdroid build -v -l com.skohscripts.kairos   # build réel en environnement isolé (nécessite Docker/systemd-nspawn, voir doc F-Droid « Installing the Server and Repo Tools »)
```

`fdroid build` reproduit l'environnement du serveur officiel (téléchargement
des SDK/NDK, isolation réseau partielle) : c'est le test le plus proche de la
réalité, et l'endroit où le problème de wheels Chaquopy (étape 1) se
manifesterait concrètement si la réponse RFP était incertaine.

### 4. Écrire le build recipe

Gabarit adapté à la structure de Kairos (`android/` en sous-dossier, code
Python à la racine copié par la tâche Gradle `stageKairosPython`) :

```yaml
Categories:
  - Productivity
License: MIT
SourceCode: https://github.com/SKOHscripts/Kairos
IssueTracker: https://github.com/SKOHscripts/Kairos/issues
Changelog: https://github.com/SKOHscripts/Kairos/releases

AutoName: Kairos
Summary: Dashboard de tâches local, priorisation WSJF
Description: |
  Dashboard personnel de tâches, mono-utilisateur, sans compte ni cloud.
  Priorisation automatique (méthode WSJF) et planning autour de l'agenda.
  .
  L'intégration optionnelle avec TimeTree (désactivée par défaut) dépend
  d'un service cloud propriétaire tiers.

RepoType: git
Repo: https://github.com/SKOHscripts/Kairos

Builds:
  - versionName: 1.0.0
    versionCode: 10000
    commit: v1.0.0
    subdir: android
    gradle:
      - yes
    # Le plugin Chaquopy a besoin de python3.13 sur la machine de build
    # (voir .github/workflows/release.yml, job build-android) — préciser au
    # besoin via `sudo`/`init` selon ce que documente F-Droid pour les
    # prérequis non-Gradle (Python) d'un plugin de ce type. À valider avec
    # les mainteneurs F-Droid pendant la revue (cf. étape 1).

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 10000
```

Points spécifiques à Kairos à garder en tête en écrivant ce fichier :

- `subdir: android` : `settings.gradle`/`app/build.gradle` vivent dans ce
  sous-dossier, mais **le code Python source qu'ils copient reste à la
  racine du dépôt** (`app/`, `templates/`, `static/`) — F-Droid clone tout le
  dépôt donc ceci fonctionne sans configuration supplémentaire (contrairement
  à un `srclib` qui isolerait `android/` seul). `stageKairosPython` remonte
  explicitement à `rootProject.projectDir.parentFile` pour aller chercher ces
  fichiers.
- Le `commit:` doit être un tag existant (`vX.Y.Z`) — déjà garanti par le
  process de release actuel.
- `ndk` : `abiFilters "arm64-v8a"` déjà fixé côté `build.gradle` pour la
  release — pas besoin de champ NDK séparé sauf si F-Droid exige une version
  d'NDK précise que Chaquopy n'aurait pas déjà réglée.

### 5. Soumettre à `fdroiddata`

1. Fork de [`gitlab.com/fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata).
2. Ajouter `metadata/com.skohscripts.kairos.yml` (contenu de l'étape 4).
3. Merge request, en référençant le ticket RFP ouvert à l'étape 1.
4. Le scanner automatique (licences, blobs binaires connus, permissions)
   tourne d'abord ; puis revue humaine par un mainteneur F-Droid, qui peut
   redemander des ajustements sur le recipe.
5. Une fois la MR mergée : le serveur de build F-Droid construit l'APK,
   généralement sous 24 à 48h avant apparition dans le dépôt principal (`F-Droid`
   classique) si le build passe du premier coup.

### 6. Cycle de mise à jour après acceptation

Rien à changer dans le processus existant : chaque nouveau tag `vX.Y.Z` publié
(déjà le déclencheur de `release.yml`) est détecté automatiquement par F-Droid
grâce à `UpdateCheckMode: Tags`, qui reconstruit et republie sans intervention
— à condition d'ajouter à chaque release le fichier changelog fastlane
correspondant (`fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt`,
étape 2) avant ou au moment du tag.

## Références

- [Politique d'inclusion F-Droid](https://f-droid.org/docs/Inclusion_Policy/)
- [Guide de soumission rapide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Référence des métadonnées de build](https://f-droid.org/docs/Build_Metadata_Reference/)
- [File d'attente RFP](https://gitlab.com/fdroid/rfp/-/issues)
- [Licence Chaquopy (libre depuis la 12.0.1)](https://chaquo.com/chaquopy/license/)
- `docs/ANDROID_PACKAGING.md` (ce dépôt) : détail de la chaîne de build Android
  concernée par ce document.
