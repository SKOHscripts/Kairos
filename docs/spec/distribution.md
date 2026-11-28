# Distribution : versions, Android, bureau, web, CI

_Rôle : comment Kairos est versionné, construit, testé, empaqueté et
publié sur chaque cible. Fichiers couverts : `kmp/gradle.properties`
(version), `kmp/core/src/commonMain/.../AppVersion.kt`, `kmp/androidApp/`,
`kmp/desktopApp/` (dont `Main.kt`, `DataDirectory.kt`, `SingleInstance.kt`,
`CrashLog.kt`, `SelfTest.kt`, `icons/`), `kmp/webApp/`,
`kmp/tools/make_app_icons.py`, `packaging/aur/` (paquet Arch Linux), `packaging/msix/` et `kmp/tools/make_msix.py`
(Microsoft Store), `.github/workflows/kmp.yml`,
`.github/workflows/kmp-release.yml` et `.github/workflows/pages.yml` (sa
partie version web). Les services de fichiers et de sauvegarde de chaque plateforme
sont décrits par `export-import.md` ; F-Droid, fiches Fastlane et
page de téléchargement par `publication.md`._

État : **jalon M7** (`3.0.0`, bascule : version de référence du dépôt).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos doit s'installer, ou simplement se lancer, partout où ses
utilisateurs travaillent :

- sur Android, depuis F-Droid ou l'APK des releases ;
- sur un poste Windows, Linux ou macOS, par un installeur classique ;
- sur **Arch Linux** et ses dérivées, par le gestionnaire de paquets, qui le tient à jour (4.0.0) ;
- sur un **poste professionnel verrouillé**, sans droit d'installation : par un
  **zip portable** ou par la **version web** dans Edge ou Chrome.

Une préversion doit s'installer **à côté** de la version stable, sans la
remplacer ; une version stable s'installe par-dessus la précédente.

### Comportement attendu (utilisateur)

- **Chaque version** a un tag : une préversion (`vX.Y.Z-alpha.N`,
  `vX.Y.Z-beta.N`) est publiée en **préversion GitHub**, une version finale
  (`vX.Y.Z`) en release GitHub, avec tous les fichiers.
- **Cycle des versions** (choix du propriétaire du 2026-11-15, qui remplace le « beta puis finale » du 2026-11-05) :
  les **mineures sortent au fil de l'eau**, en finale, dès qu'une ou quelques fonctionnalités sont prêtes (`4.2.0`,
  `4.3.0`…) : un utilisateur de la stable n'attend pas qu'un lot entier soit fini. Une **beta** (`X.Y.0-beta.N`) n'est
  plus une étape obligée : elle se réserve à un chantier risqué, à faire essayer avant sa finale. `main` porte toujours
  la prochaine version : juste après une finale, la mineure suivante (`4.2.0` → `4.3.0`), ou sa beta si le chantier en
  cours en demande une, si bien que tout ce qui sort de `main` dit ce qu'il est. Le tag se pose depuis l'onglet Actions (« Kairos tag »), qui vérifie avant de poser
  et lance la release. Pas de branche de correctif : un correctif part de `main` avec la version suivante.
- **Quel numéro** : majeure si une version plus ancienne ne peut plus lire ce que la nouvelle écrit (format du fichier)
  ou si l'utilisateur perd quelque chose ; mineure dès qu'une fonctionnalité ou un comportement visible change ;
  correctif s'il n'y a que des corrections ; une beta seulement pour un chantier risqué, quand toutes ses
  fonctionnalités sont là, puis plus aucune avant la finale (§ Quel numéro donner à une version).
- **Version attendue, dite à chaque PR** (demande du propriétaire du 2026-11-15 : choisir le numéro et savoir quand
  publier s'oublient) : un contrôle calcule, d'après ce qui est fusionné depuis la dernière finale, la version que
  `main` doit porter et échoue si la PR la contredit, en donnant la commande à lancer. Il dit aussi quand publier : des
  fonctionnalités qui attendent une finale depuis une semaine ou plus sont signalées. Après le tag d'une finale, une PR
  passe `main` à la mineure suivante ; le propriétaire la fusionne, ou la change en beta pour un chantier risqué
  (§ Version attendue).
- **Une beta**, pour ses testeurs : depuis la 4.2.0, **la même app « Kairos »** que la stable (Android, bureau), qu'elle
  remplace en s'installant par-dessus (décision du 2026-11-15 ; avant, une app « Kairos Preview » à côté de la stable),
  et la préversion web sous `preview/` ; jamais sur F-Droid, l'AUR, ni dans la vérification des mises à jour d'une
  version stable, mais une beta de bureau installée apprend la beta suivante. Revenir à la stable précédente demande de
  désinstaller : le plus simple est d'attendre la finale, qui s'installe par-dessus la beta. Elle n'a pas de note de version dans les fiches (celles de la
  préversion GitHub, générées, suffisent) ; une finale a la sienne dans les six langues avant d'être taguée.
- **Android** : un APK, l'application « Kairos », beta comprise (même identifiant depuis la 4.2.0 : une beta remplace la
  stable). Aucune
  permission réseau jusqu'à la 4.1 (rouvert pour la 4.2.0 : un seul APK avec `INTERNET`, fonctions en ligne désactivées par défaut,
  `connexions-en-ligne.md`) ; deux permissions pour le chrono (notifier, reprendre
  après un redémarrage), voir `temps-reel-chrono.md`.
- **Bureau** : un installeur par OS (`.msi`, `.deb`, `.dmg`), qui n'exige pas
  de droits administrateur sous Windows, et un **zip portable** par OS : on
  dézippe, on lance, rien n'est installé.
- **Microsoft Store** (#215, décision du propriétaire du 2026-11-25) : Kairos s'y installe sous le nom réservé
  **« Kairos Planner »** (« Kairos » était pris), sans avertissement SmartScreen et sans droits administrateur. Le Store
  le met à jour : Kairos ne cherche alors pas lui-même de nouvelle version. Il lit et écrit **les mêmes données** que
  l'installeur `.msi` (même dossier), et une désinstallation ne les efface pas. Seules les versions finales vont au Store.
- **Arch Linux** (4.0.0, choix du propriétaire du 2026-11-02) : un paquet AUR **`kairos-bin`**
  (`yay -S kairos-bin`, ou tout assistant AUR), qui reprend le `.deb` de la release. Kairos apparaît dans le menu des
  applications et se lance par la commande `kairos`. Chaque version stable arrive par `yay -Syu` peu après son tag,
  sans geste du propriétaire. Installé ainsi, Kairos ne cherche pas lui-même de nouvelle version (ni bandeau ni carte
  « Mises à jour », `mises-a-jour.md`) : le gestionnaire de paquets s'en charge. Les données sont les mêmes que
  celles du `.deb` (`~/.local/share/Kairos/v3`) : passer de l'un à l'autre ne perd rien.
- L'application de bureau s'ouvre sur une fenêtre « Kairos » (ou « Kairos
  Preview »). La relancer alors qu'elle est ouverte ramène la fenêtre
  existante au premier plan, sans seconde instance.
- Un crash laisse une trace lisible dans un fichier, sans terminal.
- **Web** : la même interface dans le navigateur. Un navigateur trop ancien
  affiche un message clair plutôt qu'un écran figé.
- **Build de test avant une release** (demande du 2026-11-03) : depuis l'onglet Actions, on lance à la main un build d'**un
  commit précis** de `main` (par défaut le dernier), en choisissant les plateformes (Android, bureau, web), pour essayer la
  version avant de poser le tag. L'APK de test est signé avec la clé des releases : il s'installe **par-dessus** la
  version en place de son canal et garde ses données, comme le fera la release ; `main` portant la prochaine beta
  entre deux finales (§ Cycle des versions), c'est en général une beta, qui remplace la stable (même app depuis la 4.2.0). Rien n'est publié : ni release, ni AUR, ni signature
  Windows ; les fichiers sont téléchargeables dans les artefacts du lancement, qui portent le commit dans leur nom.

### Critères de succès

- Le tag et la version déclarée ne peuvent pas diverger (la release échoue, et « Kairos tag » refuse de le poser).
- Chaque `versionCode` Android est strictement supérieur au précédent, beta ou finale (une seule app depuis la 4.2.0 ;
  jusqu'à la 4.1, au précédent **de son canal** : les finales entre elles, les préversions entre elles, identifiant
  `.preview`).
- Une version finale ne se tague pas sans sa note de version dans les six langues (« Kairos tag »).
- Une PR qui ajoute une fonctionnalité (carte du catalogue) ou change le format du fichier ne passe pas tant que `main`
  ne porte pas la mineure ou la majeure qui convient (job `version` de « Kairos checks »).
- Chaque image de bureau empaquetée est **lancée en CI** sur son OS (auto-test)
  avant publication ; un auto-test en échec bloque la release.
- Un tag produit un APK **signé**, avec toujours la même clé (chaque version s'installe par-dessus la précédente).
- Les fichiers publiés ont des noms fixes (liens stables) et un `SHA256SUMS`.
- Le paquet AUR est **construit, installé avec ses dépendances et lancé** (auto-test) dans un conteneur Arch Linux
  avant d'être poussé ; un échec bloque sa publication, jamais la release GitHub. Il vérifie la somme SHA-256 du
  `.deb` publié.
- Un build de test ne construit que des commits de la branche depuis laquelle il est lancé, passe les mêmes tests et
  auto-tests qu'une release, et ne publie jamais rien.
- Chaque construction Windows produit le paquet MSIX du Store, dont `makeappx` valide le manifeste ; son identité est
  celle que Partner Center a attribuée (`validate_identity.py`), sa version suit celle de l'installeur, il porte le
  marqueur d'installation gérée et jamais celui des copies portables, et chaque logo cité par le manifeste existe
  (`test_make_msix.py`). Une installation du Store ne vérifie pas les mises à jour (`SystemPackageTest`).

### Hors périmètre (à ce jalon)

- Signature et notarisation macOS : différées (plan § 6.2), **décision du 2026-10-29** (issue #116) : pas de compte
  Apple Developer pour l'instant (§ Décisions). Gatekeeper affiche « développeur non identifié » au premier lancement ;
  le README et la page de téléchargement gardent la marche à suivre (Réglages Système → Confidentialité et sécurité →
  « Ouvrir quand même »).
- Signature Authenticode Windows : **préparée le 2026-10-29** (issue #117, § Signature Windows) par SignPath
  Foundation, active dès que le propriétaire a été accepté et a posé les secrets ; d'ici là, et pour les forks,
  l'installeur reste non signé et SmartScreen affiche un avertissement.
- Mise à jour intégrée : jamais sur Android ; sur le bureau, simple
  vérification (`mises-a-jour.md`).
- F-Droid, fiches et page de téléchargement : `publication.md`.
- Microsoft Store : soumission automatique depuis la CI (§ Microsoft Store, « Soumission automatique », différée) ; le
  `.msix` hors du Store (il n'est pas signé : rien ne l'installe ailleurs) ; ARM64 ; le badge « Get it from Microsoft »
  du README et de la page de téléchargement, posé quand la fiche est en ligne.
- Arch Linux : dépôts officiels (`extra`, il faudrait qu'un mainteneur de confiance d'Arch adopte le paquet), paquet
  compilé depuis les sources, préversions (`kairos-preview-bin`), ARM. Autres distributions (Flatpak, RPM) : non
  prévues.

## 2. Solution technique

### Versionnage

- **Source unique** : `kmp/gradle.properties`, `kairos.versionName` et
  `kairos.versionCode`. Jalon M0 : `3.0.0-alpha.1` (`29001`) ; jalon M1 :
  `3.0.0-alpha.2` (`29002`) ; jalon M2 : `3.0.0-alpha.3` (`29003`) ;
  jalon M3 : `3.0.0-alpha.4` (`29004`) ; jalon M4 : `3.0.0-alpha.5`
  (`29005`) ; jalon M5 : `3.0.0-alpha.6` (`29006`) ; jalon M6 (fonctions
  complètes, publication prête) : `3.0.0-beta.1` (`29501`) ; jalon M7
  (bascule) : `3.0.0` (`30000`).
  Versions suivantes : `3.1.0` (`30100`), `3.2.0` (`30200`), `3.3.0` (`30300`), puis `3.4.0` (`30400`, issue #64 :
  horaires par jour de la semaine, durées apprises, catalogue de fonctionnalités et première tâche guidée, ergonomie et
  réglages, retrait de l'import de l'ancienne version ; notes `30400.txt` en français et en anglais), puis `3.5.0` (`30500`, issue #64 : plusieurs plages par jour et contextes, carte Statistiques « Quand tu travailles chaque type » ; notes `30500.txt` en français et en anglais), puis `3.6.0` (`30600`, issue #64 : réglage « Meilleures heures », le planning préfère les heures habituelles ou préférées de chaque type ; notes `30600.txt` en français et en anglais), puis `3.7.0` (`30700` : thème sombre, issue #72 ; filtres et export des statistiques, #114 et #113 ; déplacer depuis la vue Semaine, #106 ; fériés des créneaux « jours ouvrés », #109 ; Réglages par catégories, PR #124 ; guide qui retient et montre l'endroit, #94 et #95 ; notes `30700.txt` en français et en anglais), puis `3.8.0` (`30800` : vue Mois, #107 ; créneaux mensuels, #108 ; une seule occurrence d'un créneau, #110 ; évolutions des statistiques, #112 ; corriger les estimations, #101 ; rappels, #103 ; première tâche d'équipe guidée, #97 ; notes `30800.txt` en français et en anglais), puis `3.9.0` (`30900` : noms des niveaux, #93 ; meilleures heures mesurées par l'efficacité, #105 ; accompagnement des récurrences et des Réglages, #98 ; jours et heures par membre, #104 ; notes dans les échanges, #120 ; propositions d'un membre, #121 ; dossier des données, diagnostic et proxy du bureau, #115 ; lien « Voir la dernière version » sur Android, #143 ; notes `30900.txt` en français et en anglais), puis `3.10.0` (`31000` : import d'un calendrier `.ics`, #136 ; fichier lié sur le bureau et Android, #137 ; calendriers du téléphone, #138 ; notes `31000.txt` en français et en anglais), puis `3.11.0` (`31100` : espagnol, allemand, russe et italien, #75 ; fiches du magasin et page web dans ces langues ; notes `31100.txt` dans les six langues), puis `3.12.0` (`31200` : widgets Android, #150 ; récapitulatif du jour, #151 ; couleur par défaut océan ; notes `31200.txt` dans les six langues), puis `3.13.0` (`31300` : liste complète des fonctionnalités, #153 ; avis des mois sautés, #111 ; démonstration interactive, #96 ; décision sur la signature macOS, #116, et signature Windows préparée, #117 ; notes `31300.txt` dans les six langues), puis `3.14.0` (`31400` : type choisi sur place dans « À traiter » et « À qualifier », durée posée une fois la tâche qualifiée, facette « Incomplètes », type dans l'essentiel de la fiche et dans la démonstration, barre de défilement du menu des types, #166 ; notes `31400.txt` dans les six langues), puis `4.0.0` (`40000` : synchronisation entre appareils par le fichier lié, avec fusion, #88, et durées apprises partagées, #100 ; estimations par machine learning sur l'appareil, #99 ; type requis pour sortir de « À traiter », PR #186 ; garde du modèle allégée, PR #187 ; paquet Arch Linux AUR ; corrections de la QA d’avant 4.0, PR #192 ; toujours sans permission réseau, les connexions en ligne étant reportées à la 4.1.0 ; notes `40000.txt` dans les six langues). Puis, avec le cycle des versions (2026-11-05), `main` passe en `4.1.0-beta.1` (`39601`) : sous-tâches et enchaînement (#201), sans note de version (préversion) ; puis `4.1.0-beta.2` (`39602`) : file de sous-tâches enchaînées (#209, exception au gel décidée le 2026-11-05) ; puis `4.1.0-beta.3` à `4.1.0-beta.9` (`39603` à `39609`, sans notes de version) : « Donner un avis » (#211), aperçu des liens de la page de téléchargement par langue (#217), notifications du chrono à l'heure (#219) et garde-fou du chrono (#220), corrections (#193 à #197), page « L'histoire de Kairos », taille du texte (#229), cloches du pomodoro (#234), méthode GTD expliquée (#235), prévisions par tâche et chronos à vérifier (#232), Bilan de la semaine et du mois, fiche par groupes et « Ce que Kairos apprend » (#233), ce qui traîne ; puis `4.1.0` (`40100` : tout ce qui précède depuis la 4.0.0 ; la méthode GTD et les cloches, prévues pour la 4.2.0 et fusionnées pendant les betas, y sont livrées ; toujours sans permission réseau, les connexions en ligne, #139 et #144, étant reportées à la 4.2.0 le 2026-11-15 ; notes `40100.txt` dans les six langues). Puis `main` passe en
  `4.2.0-beta.1`, d'abord avec l'ancienne formule (`39701`), puis, avant son tag, avec la nouvelle (`4020001`) : à partir
  de la 4.2.0, une seule app pour la beta et la stable (§ Décisions) ; tag `v4.2.0-beta.1` posé le 2026-11-15. Puis `4.2.0`
  (`4020099` : conseils au bon moment (#221), connexions en ligne O1, lien ICS sur Android et le bureau (#139, PR #242 de
  @pyroMain404), une seule app « Kairos » pour la beta et la stable, remplacement des anciennes entrées « Kairos Preview » des
  copies portables, identité de l'app figée, Android Lint et tableau des tests en CI ; spec de la mise à jour automatique du
  bureau (#249, non implémentée) ; notes `4020099.txt` dans les six langues), première finale du cycle « mineures au fil de
  l'eau » ; `main` passe ensuite en `4.3.0` par la PR qu'ouvre « Kairos tag ». Puis `4.3.0` (`4030099` : style « Cadran »,
  inspiré d'iOS, au choix à côté de Material, `style-cadran.md`, PR #256, qui porte aussi le passage en `4.3.0` exigé par la
  vérification « version » ; notes `4030099.txt` dans les six langues). Avant la note, `main` a porté un moment `4.3.1`
  (`4030199`), posé à la main par erreur et ramené à `4.3.0` : aucune `4.3.0` n'était publiée.
- Formats acceptés (`AppVersion.parse`) : `X.Y.Z`, `X.Y.Z-alpha.N`,
  `X.Y.Z-beta.N` (préfixe `v` toléré ; `Y`, `Z` ≤ 99 ; `N` de 1 à 499).
- `versionCode` (`AppVersion.versionCode`), deux formules :
  - **à partir de la 4.2.0** (décision du 2026-11-15, § Décisions « Une seule app pour la beta et la stable ») :
    `X*1 000 000 + Y*10 000 + Z*100 + s`, avec `s` = `N` pour une beta (1 à 98) et 99 pour une finale. Ainsi
    4.2.0-beta.1 = 4 020 001 < 4.2.0-beta.2 = 4 020 002 < 4.2.0 = 4 020 099 < 4.2.1 = 4 020 199 < 4.3.0-beta.1 =
    4 030 001, et tous au-dessus de 4.1.0 = 40 100 : une beta s'installe par-dessus la stable précédente, sa finale
    par-dessus elle. Une alpha n'a plus de code (le cycle n'en publie plus) ;
  - **jusqu'à la 4.1.x** (codes publiés, inchangés) : `X*10000 + Y*100 + Z` ; alpha : − 1000 + N ; beta : − 500 + N.
    Ainsi 3.0.0-alpha.1 = 29001 < 3.0.0-beta.1 = 29501 < 3.0.0 = 30000.
  Mêmes règles dans `kmp/tools/set_version.py` (refuse une beta hors de 1 à 98 et une alpha à partir de la 4.2.0) et dans
  « Kairos tag ». `AppVersionTest.fromFourTwoBetasAndFinalsShareOneGrowingLine` vérifie la ligne croissante.
- Version des paquets de bureau (`AppVersion.packageVersion`, recalculée de la même façon par
  `desktopApp/build.gradle.kts`) : `X.Y.(Z*100 + s)` à partir de la 4.2.0, `X.Y.Z` (finale) ou `1.0.<versionCode>`
  (préversion) avant (`AppVersionTest.desktopPackageVersionsGrowToo`, § Bureau).
  `AppVersionTest.buildVersionIsConsistent` échoue si `gradle.properties` ne
  suit pas la formule.
- `KairosBuild` (généré par `core`) expose la version au code commun.
- Le tag de release doit valoir `v` + `kairos.versionName` (job `version` de
  `kmp-release.yml`).
- F-Droid lit la version dans `kmp/gradle.properties` par `UpdateCheckData`
  (`publication.md`) ; chaque versionCode publié a sa note de version
  Fastlane dans les deux langues.

### Cycle des versions (mineures au fil de l'eau, 2026-11-15)

- **Étapes** : une mineure `X.Y.0` sort en finale, sans beta, dès qu'une ou quelques fonctionnalités sont fusionnées
  avec leurs specs, leurs tests et leurs textes dans les six langues. Une ou plusieurs betas `X.Y.0-beta.N` (fonctions
  complètes, à essayer) la précèdent seulement pour un chantier risqué (§ Quel numéro donner à une version). Du
  2026-11-05 au 2026-11-15, chaque mineure passait par au moins une beta (la 4.1.0 en a eu neuf) ; la 4.2.0 garde la
  sienne (`4.2.0-beta.1`, posée avant la décision, chantier réseau), puis sa finale. Les alphas restent lisibles (`AppVersion`) mais ne servent plus. Une préversion n'existe que d'une version en `.0`, avec
  `N` de 1 à 98 (`AppVersion.MAX_PRERELEASE` ; 99 avant la 4.2.0) : 99 marque une finale dans la formule de la 4.2.0,
  et une beta de correctif (`4.2.1-beta.1`) passerait sous la finale qu'elle précède. Vérifié sur la version
  déclarée (`AppVersionTest.buildVersionIsConsistent`, `prereleaseCodesGrowFromOneMinorToTheNext`), par
  `kmp/tools/set_version.py` et par « Kairos tag ». Un correctif est une version finale (`4.1.1`), sans beta.
- **Canaux** (depuis la 4.2.0) : une seule app. Une beta a un `versionCode` au-dessus de la finale précédente et sous la
  sienne (`4.1.0` = 40 100 < `4.2.0-beta.1` = 4 020 001 < `4.2.0` = 4 020 099) : elle s'installe par-dessus la stable,
  et sa finale par-dessus elle. Jusqu'à la 4.1, une beta avait un code sous la finale précédente et s'installait sous
  l'identifiant `.preview` (Android) ou comme produit « Kairos Preview » (bureau) ; ces anciennes installations restent
  des apps à part, que leurs testeurs désinstallent.
- **`main` en avance** : après le tag d'une finale, le commit suivant de `main` pose la mineure suivante
  (`python3 kmp/tools/set_version.py 4.3.0`, qui écrit `kairos.versionName` et `kairos.versionCode` selon la formule),
  ou sa première beta (`set_version.py 4.3.0-beta.1` ; refuse une préversion hors `.0` ou hors 1-98) si le prochain
  chantier est risqué. Une nouvelle beta : la même commande avec `beta.N+1`, après le tag de la précédente. La finale :
  `set_version.py X.Y.0` si `main` n'y est pas déjà, notes de version dans les six langues, historique ci-dessus et
  feuille de route, puis le tag. Conséquence : un build de test (§ Build de test) de `main` porte le numéro de la
  prochaine version, beta ou finale à venir, et remplace la stable (même app, depuis la 4.2.0). Entre deux finales sans
  beta, il a donc le numéro et le `versionCode` de la finale qui vient sans en être une : accepté, un build de test est
  un fichier d'essai gardé 14 jours, jamais publié (2026-11-15).
- **Notes de version** : une finale a `fastlane/metadata/android/<langue>/changelogs/<versionCode>.txt` dans les six
  langues, 500 caractères au plus, exigées **au tag** (« Kairos tag ») ; une beta n'en a pas (ni F-Droid ni fiches ;
  notes générées de la préversion GitHub). La CI ne les exige plus pour la version déclarée, puisque `main` porte la
  finale à venir pendant tout son développement (2026-11-15) : `ReleaseNotesTest` et `validate_fastlane.py` vérifient
  qu'une note commencée existe dans les six langues et la limite de 500 caractères ; une finale déclarée sans note est
  un rappel (`validate_fastlane.py` l'affiche, le contrôle de la version attendue la liste), pas une erreur.
- **« Kairos tag »** (`.github/workflows/kmp-tag.yml`, lancement manuel sur `main`, champ `version` à recopier) : refuse
  une version qui n'est pas celle de `kmp/gradle.properties`, hors du cycle, un `versionCode` hors formule, un tag qui
  existe déjà, une finale sans ses six notes, et un commit dont la CI (`kmp.yml`) est rouge ou en cours ; une CI absente (commit qui ne touche que la documentation, hors
  des chemins de `kmp.yml`) passe, la release refaisant les tests (`gh run list` rend alors une liste vide, lue `// empty`
  depuis le 2026-11-15 : lue `.[0]`, elle donnait « null null », pris pour une CI en cours). Refuse aussi une table des fériés
  périmée face à la dernière python-holidays, après avoir ouvert la PR `holidays/<version>` qui la met à jour (`jours-feries.md`
  § Recalcul à chaque version). Puis tag annoté
  `vX.Y.Z…` sur le dernier commit de `main`, et `gh workflow run kmp-release.yml --ref <tag>`, parce qu'un tag poussé
  avec le jeton du workflow ne déclenche aucun autre workflow. Permissions `contents: write`, `actions: write`.
- **Après une release lancée ainsi** : le job `follow-up` de `kmp-release.yml` lance `pages.yml` (page de
  téléchargement) et, pour une finale, `fdroid-check.yml` avec `after_release` (comparaison à l'APK signé publié), la
  fin d'une release lancée par le jeton ne déclenchant pas de façon sûre leurs `workflow_run`. Un tag posé à la main
  garde le chemin d'avant (`push` du tag, puis `workflow_run`).
- **Tags acceptés par la release** : `v[3-9].*` et `v[1-9][0-9].*` (toute version à partir de la 3 ; les `v2.*` sont
  ceux de l'ancienne version) : une 5.0.0 n'aura rien à changer.
- **Données des testeurs** : depuis la 4.2.0, une beta reprend les données de la stable qu'elle remplace (même app). Une
  beta qui change le format du fichier (`formatVersion`) ou le schéma de la base le dit dans les notes de sa préversion :
  une stable plus ancienne lirait ce fichier comme `TOO_NEW` et cesserait de le fusionner. Jusqu'à la 4.1, la « Kairos
  Preview » Android avait ses propres données (identifiant distinct) ; elles y restent.

### Quel numéro donner à une version (règles du 2026-11-05)

On choisit le numéro d'après ce que la version **contient depuis la dernière finale**, pas d'après sa taille :

- **Majeure** (`X.0.0`) : une version plus ancienne ne peut plus lire ce que celle-ci écrit, ou l'utilisateur perd
  quelque chose sur quoi il comptait.
  - Nouveau format du fichier d'export ou de synchronisation (`ExportCodec.FORMAT_VERSION`, `SYNC_FORMAT_VERSION`) :
    l'ancienne version le lit `TOO_NEW` et cesse de le fusionner. Garde-fou : `FormatVersionTest` lie chaque majeure au
    format qu'elle écrit.
  - Retrait d'une fonctionnalité ou d'un réglage, ou changement qui oblige l'utilisateur à refaire quelque chose.
  - Une migration de base **additive** n'en est pas une : la base n'est jamais relue par une version plus ancienne,
    seul le fichier d'export l'est.
- **Mineure** (`X.Y.0`) : au moins une fonctionnalité nouvelle ou un comportement visible qui change, c'est-à-dire
  une carte du catalogue `since = "X.Y.0"`, une puce du README ou une spec dont le « Besoin métier » change. Publiée
  au fil de l'eau, sans beta, dès qu'une ou quelques fonctionnalités sont prêtes (2026-11-15 ; avant, toujours précédée
  d'au moins une beta). Garde-fou : `FeatureCatalogTest.aFeatureArrivesInAMinorOrMajorVersion` (une carte
  n'annonce jamais un `X.Y.Z` avec `Z > 0`).
- **Correctif** (`X.Y.Z`, `Z > 0`) : uniquement des corrections de défauts, sans beta. Ni carte, ni réglage, ni
  migration, ni nouveau texte d'interface (un texte corrigé, oui). Faute de branche de correctif, possible seulement
  quand `main` ne contient **que** des correctifs depuis la dernière finale : `main`, en `X.(Y+1).0` (ou sa beta) non
  tagué, passe en `X.Y.(Z+1)` (`set_version.py`), notes de version, tag, puis retour à la mineure. Dès qu'une
  fonctionnalité est fusionnée, un correctif part avec la prochaine mineure, qui peut sortir aussitôt (ou avec la
  prochaine beta d'un chantier risqué).
- **Beta** (`X.Y.0-beta.N`), **facultative** depuis le 2026-11-15 : pour un chantier qu'il faut faire essayer avant
  de le donner à tous (réseau, format du fichier ou schéma de la base, migration, refonte d'un écran central). Elle se
  décide en passant `main` à la version (`beta.1` plutôt que la finale) et se note dans la feuille de route
  (`docs/ROADMAP.md`). Ensuite :
  - `beta.1` quand **toutes** les fonctionnalités prévues de la version sont fusionnées, avec leurs specs, leurs
    tests et leurs textes dans les six langues ;
  - à partir de là, **gel des fonctionnalités** : seuls des correctifs entrent dans la version. Une fonctionnalité
    prête attend, en PR ouverte, que la finale soit taguée (pas de branche de version : ce qui est fusionné dans
    `main` part avec la finale) ; une **exception** se décide explicitement et se consigne dans la spec de la
    fonctionnalité (seule à ce jour : la file de sous-tâches, #209, en `4.1.0-beta.2`, `sous-taches.md`) ;
  - `beta.N+1` quand des correctifs ont été fusionnés et qu'il faut les faire essayer ;
  - la **finale** quand la dernière beta a tourné chez ses testeurs sans régression (repère : une semaine), notes de
    version écrites.

### Version attendue (`kmp/tools/next_version.py`, job `version` de « Kairos checks », 2026-11-15)

- **Dernière finale** : le plus haut tag `vX.Y.Z` sans suffixe, en ordre numérique. Sans tag, rien à contrôler.
- **Niveau** de ce qui est fusionné depuis cette finale (`git log vX.Y.Z..HEAD`) :
  - **majeure** si `ExportCodec.SYNC_FORMAT_VERSION` diffère de sa valeur au tag (règle § Quel numéro ;
    `FormatVersionTest` lie déjà chaque majeure à son format) ;
  - **mineure** si une carte du catalogue (`FeatureCatalog.kt`, `FeatureCard("id", …, since = "X.Y.0")`) annonce une
    version au-dessus de la dernière finale : une fonctionnalité est une carte (`CLAUDE.md`, étape 4) ;
  - **correctif** s'il y a au moins un commit ; aucun sinon.
- **Contrôle** : la version de `main` sans son suffixe `-beta.N` doit valoir au moins la dernière finale montée du
  niveau (majeure `(X+1).0.0`, mineure `X.(Y+1).0`, correctif `X.Y.(Z+1)`). Sinon échec, avec la commande
  `python3 kmp/tools/set_version.py …` à lancer dans la PR. Une version au-dessus n'est pas une erreur (`main` passe à la
  mineure suivante après chaque finale) : si seuls des correctifs attendent, le contrôle signale qu'un correctif
  `X.Y.(Z+1)` est possible (§ Quel numéro).
- **Avertissements**, sans échec : un commit `feat` sans carte nouvelle depuis la finale (un comportement visible qui
  change fait aussi une mineure, ce que le script ne sait pas voir) ; des cartes qui attendent depuis **7 jours** ou
  plus (date du premier commit depuis le tag qui touche `FeatureCatalog.kt` ; le repère d'une semaine des betas) :
  « il est temps de publier ».
- **Résumé** du job (`$GITHUB_STEP_SUMMARY`, sinon la sortie standard) : dernière finale, version de `main`, version
  attendue, cartes en attente, notes de version manquantes si `main` porte une finale, et ce qu'il reste à faire pour
  la publier (notes, puis « Kairos tag »).
- **Workflow** : job `version` de `.github/workflows/metadata.yml` (« Kairos checks »), sur chaque PR sans filtre de
  chemins (tout changement compte), `push` sur `main`, `workflow_dispatch` ; `fetch-depth: 0` pour les tags ;
  `contents: read`. Les fonctions pures du script sont testées par `kmp/tools/test_next_version.py` (job `metadata`).
- **Après le tag d'une finale** : « Kairos tag » ouvre la PR « chore(version): set X.(Y+1).0 » (branche
  `version/X.(Y+1).0`, `set_version.py`), puis lance `kmp.yml`, `metadata.yml` et `fdroid-check.yml` sur cette branche (une
  PR ouverte avec le jeton du workflow ne déclenche aucun workflow). Il faut le réglage du dépôt « Allow GitHub Actions
  to create and approve pull requests » (Settings → Actions → General) ; sans lui, l'étape échoue sans faire échouer le
  tag, déjà posé, et le résumé donne la commande. Après une beta, rien n'est ouvert : le résumé rappelle les deux
  suites (`beta.N+1` ou la finale).

### Identité de l'app (figée, 2026-11-17)

Les noms et identifiants qui relient une installation à la suivante ne changent pas sans migration (demande du
propriétaire du 2026-11-17, après le passage de « Kairos Preview » à « Kairos ») :

| Identifiant | Valeur | Ce qui casse s'il change |
|---|---|---|
| Nom de l'app (bureau, `appName` de `desktopApp/build.gradle.kts` et `Main.kt`) | `Kairos` | nom de l'image et du lanceur, icône `lib/Kairos.png`, raccourcis portables (`kairos.desktop`, `Kairos.lnk`) |
| Paquet Linux (`.deb`) | `kairos` | le nouveau paquet s'installe à côté de l'ancien |
| Upgrade code Windows (`.msi`) | `5b0e2d7c-8a41-4f6b-b3c9-1e7d6a2f4c08` | le `.msi` s'installe à côté au lieu de remplacer |
| Bundle macOS | `com.skohscripts.kairos` | deuxième application, réglages système à part |
| `applicationId` Android | `com.skohscripts.kairos` | deuxième app, données à part, F-Droid perd le suivi |
| Nom Android (`app_name`) | `Kairos` | nom affiché |
| Classe de fenêtre X11 (`WindowClass.NAME`) | `kairos` | icône de la barre des tâches (`StartupWMClass`) |
| Dossier des données (`DataDirectory`) | racine `Kairos`, sous-dossier `v3` | les données semblent perdues |
| Recette F-Droid | `fdroid/com.skohscripts.kairos.yml` | suivi F-Droid |
| Identité MSIX (`packaging/msix/AppxManifest.xml`, `Name`) | `SKOHscripts.KairosPlanner` | le Store refuse le paquet ; un autre nom serait une autre app |
| Éditeur MSIX (`Publisher`) | `CN=EF0DE618-1AD9-4656-8417-18B58EB3603F` | le Store refuse le paquet |
| Anciens noms (`PortableShortcuts.LEGACY_APP_NAMES`) | `Kairos Preview` | les entrées laissées par les betas jusqu'à la 4.1 restent dans les menus |

- **Contrôle** : `kmp/tools/validate_identity.py` (job `metadata` de « Kairos checks ») lit ces valeurs dans les fichiers
  et échoue sur tout écart, en renvoyant ici ; testé par `test_validate_identity.py`.
- **Pour en changer un** : prévoir la migration de ce qui casse (colonne de droite) dans le même changement : ancien nom
  ajouté à `LEGACY_APP_NAMES` (raccourcis portables), paquet, données ; le dire dans les notes de version ; puis
  mettre à jour la valeur dans `validate_identity.py` et ce tableau.

### Android (`kmp/androidApp`)

- `applicationId` `com.skohscripts.kairos` et nom `Kairos` (`resValue app_name`) pour toutes les versions, betas
  comprises, depuis la 4.2.0 (avant : suffixe `.preview` et nom `Kairos Preview` pour une préversion, que la propriété
  Gradle `kairos.preview` pouvait forcer ; propriété retirée, une recette qui la passe encore n'a aucun effet).
- `minSdk 26`, `targetSdk 36`, `compileSdk 37`, Java 21.
- `MainActivity` (`ComponentActivity`) : `enableEdgeToEdge()`, puis
  `setContent { KairosApp(Platform.ANDROID) { services.await() } }`. Les
  services (`AndroidServices.open`) sont ouverts **une fois par process**
  (`Deferred` du compagnon, portée `SupervisorJob` + `Dispatchers.Default`) :
  une rotation retrouve la même base. Les sélecteurs de fichiers passent par
  l'activité courante (sur le fil principal).
- Base : `AndroidSqliteDriver` sur `kairos.db` avec un schéma factice
  (`DeferredSchema` : version du schéma, création et migration vides), la
  création réelle étant faite par `KairosStore.open` (`modele-donnees.md`).
  Exemples dans la langue du système (`Locale.getDefault()`).
- Manifeste : **aucune permission réseau** (pas de `INTERNET`) jusqu'à la 4.0 ; **rouvert le 2026-10-26 par
  `connexions-en-ligne.md`** : la 4.2.0 déclare `INTERNET` (connexions en ligne, désactivées par défaut) et
  `ACCESS_NETWORK_STATE` (contrainte réseau de leur synchronisation en arrière-plan, permission normale), un seul APK, sans variante ni
  drapeau de construction ;
  `POST_NOTIFICATIONS` (demandée à l'opt-in des alertes) et
  `RECEIVE_BOOT_COMPLETED` pour le chrono (`temps-reel-chrono.md`), avec
  ses deux récepteurs (`ChronoReceiver` non exporté, `BootReceiver`) ; `SCHEDULE_EXACT_ALARM` (4.1.0, #219) : alarmes
  exactes des alertes du chrono quand l'utilisateur les accorde (`temps-reel-chrono.md` § Décisions) ;
  `READ_CALENDAR`, **facultative**, demandée seulement quand l'utilisateur
  active « Utiliser les calendriers du téléphone » (3.10.0,
  `connexions-externes.md`) : ce n'est pas une permission réseau ;
  les widgets de l'écran d'accueil (3.12.0, `widgets-android.md`), sans permission : `NowWidgetProvider` et
  `DayWidgetProvider` (exportés, `APPWIDGET_UPDATE`), `WidgetActionReceiver` (non exporté) et `DayWidgetService`
  (réservé au système par `BIND_REMOTEVIEWS`) ;
  la cible de partage (4.6.0, `partage-android.md`), sans permission : `ShareNoteActivity` (exportée, `SEND` sur `text/plain`,
  transparente, sans historique ni affinité ; `SEND` et `SEND_MULTIPLE` sur `image/*` pour les images des notes,
  `notes-images.md`) ; un `FileProvider` non exporté (`com.skohscripts.kairos.files`, `cache/camera/`) pour les photos des
  notes, sans permission `CAMERA` ;
  activité `singleTop` (la notification du chrono et les widgets la rouvrent) ;
  `enableOnBackInvokedCallback="true"` (geste retour prédictif) ;
  `windowSoftInputMode="adjustResize"`. Base et suivi du chrono ouverts une
  fois par processus (`KairosProcess`), quel que soit le point d'entrée
  (activité, alarme, redémarrage). `KairosApplication` (`android:name`) pose le
  rapport de plantage local avant tout composant : `demarrage.md` § Rapport de
  plantage (toujours sans réseau ni permission).
- Thème de fenêtre `Theme.Kairos` :
  `forceDarkAllowed=false` ; avant l'API 31, `windowBackground` =
  `kairos_launch_background` (fond `kairos_bg` = `#FAF8FF` + cadran vide
  `kairos_splash_dial` centré 288 dp) ; API 31+ (`values-v31`), écran de
  démarrage système avec ce même cadran, statique. Ce ne sont que les relais
  de la page de démarrage Compose (`demarrage.md`), qui anime l'aiguille.
  Système en sombre : `values-night/` et `values-night-v31/` (fond `kairos_bg` = `#121318`,
  icônes des barres claires, `apparence.md` § Thème sombre).
- Icône adaptative `mipmap-anydpi/ic_launcher.xml` (fond uni océan profond `#1C2F59` = `ic_launcher_background`, premier
  plan `ic_launcher_foreground` : le logo complet, cadran crème compris ; changé le 2026-10-27, le fond était le cadran
  `#FFEEDC` ; `apparence.md` § Icônes et supports fixes). Les fonds de démarrage (`#FAF8FF` clair, `#121318` sombre) sont les
  `surface` du thème océan par défaut, au lieu de `#FFF8F4` et `#18120B` du miel.
- Release : R8 (`isMinifyEnabled`, `isShrinkResources`) ; signature par les
  variables `KAIROS_KEYSTORE_FILE`, `KAIROS_KEYSTORE_PASSWORD`,
  `KAIROS_KEY_ALIAS`, `KAIROS_KEY_PASSWORD` si présentes, APK non signé
  sinon ; `dependenciesInfo` désactivé (bloc illisible par F-Droid, nuisible
  aux builds reproductibles).
- Taille : environ 1,3 Mo au jalon M0, 2 Mo à la `3.0.0-beta.1`.

### Bureau (`kmp/desktopApp`)

- `main(args)` :
  1. dossier de données (`DataDirectory.resolve`) ;
  2. journal de crash (`CrashLog.install`) ;
  3. `--self-test[=dossier]` : auto-test puis sortie ;
  4. instance unique (`SingleInstance.acquire`), sinon sortie immédiate ;
  5. copie portable (`PortableCopy.detect`) et classe de fenêtre X11
     (`WindowClass.apply`), voir `raccourci-portable.md` ; installation tenue par un gestionnaire de paquets
     (`SystemPackage.detect`, § Paquet Arch Linux) ;
  6. fenêtre 1200 × 800 dp (minimum 360 × 480 px), titre `Kairos` (beta comprise depuis la 4.2.0), icône = logo,
     contenu `KairosApp(Platform.DESKTOP) { DesktopServices.open(dataDir, …,
     portable) }`.
- **Services** (`DesktopServices.open`) : `JdbcSqliteDriver` sur
  `<données>/kairos.db` (ou en mémoire pour l'auto-test), exemples dans la
  langue de la JVM, dossier des données affiché dans la carte Données.
- **Dossier de données** : `--data-dir=` puis `KAIROS_DATA_DIR` s'ils sont
  donnés, puis le dossier choisi dans les Réglages (`bureau-avance.md`, 3.9.0),
  sinon la racine Kairos de `platformdirs` + `v3` :
  - Windows : `%LOCALAPPDATA%\Kairos\v3` ;
  - macOS : `~/Library/Application Support/Kairos/v3` ;
  - Linux : `$XDG_DATA_HOME/Kairos/v3`, par défaut `~/.local/share/Kairos/v3`.
- **Instance unique** : `FileChannel.tryLock` sur `instance.lock` (libéré par
  le système si le process meurt). Une seconde instance écrit le fichier
  `activate` et se ferme ; l'instance principale le surveille
  (`WatchService`, thread `kairos-activation`) et ramène sa fenêtre au premier
  plan (dé-minimise, `toFront`). Aucun port réseau.
- **Journal de crash** : toute exception non rattrapée est ajoutée à
  `crash.log` (date, fil, version, OS, Java, pile).
- **Auto-test** (`SelfTest`) : vérifie la version (`AppVersion` et
  `KairosBuild` cohérents), écrit puis efface un fichier dans le dossier de
  données, ouvre une base **en mémoire** avec les exemples (pilote JDBC
  réellement chargé), et rend hors écran (`ImageComposeScene`, 1000 px de
  haut, 10 images pour laisser charger polices et chaînes) l'application en
  1200 px et en 420 px de large, les Réglages et « À propos » en 900 px,
  puis la vue Jour entière en 1200 × 3000 px et 420 × 5000 px (agenda,
  sections, frise), et une seconde base en mémoire avec un chrono en marche
  (1200 × 1000 px). Avec `=dossier`, les rendus y sont écrits
  (`desktop-wide.png`, `desktop-narrow.png`, `desktop-settings.png`,
  `desktop-about.png`, `desktop-day-full.png`,
  `desktop-day-full-narrow.png`, `desktop-day-chrono.png`,
  `desktop-notes.png`, `desktop-week.png`, `desktop-week-narrow.png`,
  `desktop-stats.png`, puis les Réglages par catégories, `reglages.md` : `desktop-settings-narrow.png`,
  `desktop-settings-tracking-narrow.png`, `desktop-settings-advanced.png`), une base avec un calendrier importé (aperçu,
  vues Jour, Semaine et Mois, carte « Calendriers » : `desktop-calendar-*.png`, `connexions-externes.md`), puis, sur une base où la gestion d'équipe est activée,
  l'espace Équipe (`desktop-team.png`, `desktop-team-narrow.png` en 360 px,
  `desktop-team-settings.png`, et, depuis le jalon E2, l'écran Équipe, la
  fiche d'un membre et l'éditeur d'absence, chacun en 1200 et 360 px :
  `desktop-team-members[-narrow].png`, `desktop-team-member-sheet[-narrow].png`,
  `desktop-team-absence[-narrow].png`, `equipe.md` ; une démonstration interactive à l'étape de la priorité,
  `desktop-demo.png` et `desktop-demo-narrow.png` en 360 px, `visite-guidee.md` § Visite interactive ; le dialogue d'un
  conseil au plus long texte, `desktop-tip.png` en 900 px et `desktop-tip-narrow.png` en 360 px, `conseils.md`). L'auto-test
  n'affiche pas l'accueil du premier lancement ni de conseil par lui-même (base en mémoire, `AppServices.tips` faux), et
  ne vérifie pas les mises à jour (aucun réseau). Code de
  sortie 0 ou 1.
- **Empaquetage** (plugin Compose Desktop, jpackage, runtime Java réduit
  embarquant `java.instrument`, `java.management`, `java.net.http` (depuis
  M5, vérification des mises à jour), `java.sql`, `jdk.localedata` (noms de
  mois et de jours français du sélecteur de dates Material 3 : sans lui, le
  runtime réduit retombe sur l'anglais, constaté au test exploratoire du
  2026-10-11), `jdk.unsupported` en plus des modules détectés) :
  - formats `Msi`, `Deb`, `Dmg` ; `createDistributable` produit l'image
    portable ; la release y écrit le marqueur `kairos-portable` (Linux,
    Windows) juste avant d'en faire l'archive, après les installeurs
    (`raccourci-portable.md`) ;
  - option de la JVM : `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` (classe de fenêtre X11) ;
  - un seul produit `Kairos` (paquet Linux `kairos`, bundle macOS `com.skohscripts.kairos`, `upgradeUuid` Windows
    `5b0e2d7c-…`), betas comprises depuis la 4.2.0. Version des paquets (`packageVersion`) : `X.Y.(Z*100 + s)`, `s` comme
    pour le `versionCode` (N d'une beta, 99 d'une finale), car MSI, DMG et `.deb` n'acceptent que des numéros et doivent
    croître d'une beta à sa finale : `4.2.0-beta.1` → 4.2.1, `4.2.0` → 4.2.99, `4.2.1` → 4.2.199, toutes au-dessus de
    la stable `4.1.0` déjà installée (MSI : troisième nombre ≤ 65 535). L'application, elle, affiche `4.2.0`. Jusqu'à la
    4.1 : finale `X.Y.Z`, préversion produit distinct `Kairos Preview` (`kairos-preview`, `….preview`, autre
    `upgradeUuid`) en `1.0.<versionCode>` ;
  - Windows : installation **par utilisateur** (`perUserInstall`, sans droit
    administrateur), raccourci et menu ; `upgradeUuid` fixe par produit ;
  - icônes `desktopApp/icons/kairos.{ico,png,icns}` (`tools/make_app_icons.py`) ;
  - ProGuard désactivé.

### Signature Windows (SignPath Foundation, issue #117)

- **Quoi** : l'installeur `Kairos-windows-x64.msi` et le lanceur `Kairos.exe` (beta comprise depuis la 4.2.0 ;
  « Kairos Preview.exe » avant) qu'il contient, ainsi que le lanceur du zip portable, reçoivent une signature Authenticode horodatée.
  Le certificat est celui de **SignPath Foundation**, qui apparaît comme éditeur (offre gratuite des projets libres) ;
  aucune clé n'est dans le dépôt ni sur le runner.
- **Quand** : dans l'entrée `windows-x64` de la matrice `build` de `kmp-release.yml`, **sur un tag seulement** et seulement si
  `SIGNPATH_READY` est vrai (secret `SIGNPATH_API_TOKEN` et variable `SIGNPATH_ORGANIZATION_ID` présents). Sinon les
  trois étapes sont sautées et la construction reste celle d'avant, non signée (forks, contributeurs, lancement
  manuel (build de test), préversions sans configuration).
- **Comment** : après « Nommer les fichiers », `actions/upload-artifact` envoie le `.msi` et le zip portable comme
  artefact `unsigned-windows-x64` (préfixe distinct de `dist-` : la release ne le reprend pas) ;
  `signpath/github-action-submit-signing-request@v3` soumet la demande (projet `vars.SIGNPATH_PROJECT_SLUG`, `Kairos`
  par défaut ; politique `vars.SIGNPATH_SIGNING_POLICY_SLUG`, `release-signing` par défaut), attend l'**approbation
  manuelle** une heure au plus, et dépose les fichiers signés à la place des non signés dans `out`. Une étape PowerShell
  vérifie ensuite (`Get-AuthenticodeSignature`) que le `.msi` et le lanceur du zip portable ont une signature valide :
  sinon la release échoue. L'artefact `dist-windows-x64`, donc la release et `SHA256SUMS`, portent les fichiers
  signés. Le job a `actions: read` (l'action lit l'artefact par l'API des Actions).
- **Configuration d'artefact** : `.signpath/artifact-configuration.xml`, à recopier dans le projet SignPath (SignPath ne
  la lit pas dans le dépôt) : racine `<zip-file>` (l'artefact téléversé), signature profonde du `.msi` (lanceur
  `**/Kairos*.exe`, puis le `.msi` lui-même) et du lanceur du zip portable.
- **À faire par le propriétaire** (rien de cela ne peut se faire depuis le dépôt) :
  1. Déposer la candidature sur signpath.org (licence AGPL v3 ou ultérieure depuis le 2026-11-21, MIT avant, `licence.md` ; construction depuis ce dépôt public par GitHub Actions).
  2. Une fois accepté, publier la **politique de signature** exigée par SignPath Foundation, sous le titre « Code
     signing policy » dans le README et sur la page de téléchargement : « Free code signing provided by SignPath.io,
     certificate by SignPath Foundation », les rôles (auteurs, relecteurs, approbateurs : le propriétaire), et la
     déclaration de confidentialité (Kairos ne transmet rien sur le réseau sans demande de l'utilisateur,
     `connexions-en-ligne.md`). Elle n'est pas publiée avant l'acceptation : elle affirmerait une signature qui
     n'existe pas encore.
  3. Dans SignPath : organisation, projet `Kairos`, configuration d'artefact ci-dessus, politique `release-signing`
     (approbation manuelle), système de construction de confiance « GitHub.com » lié au projet, application GitHub
     SignPath installée sur le dépôt ; authentification à deux facteurs pour tous les membres (SignPath et GitHub).
  4. Dans le dépôt GitHub : secret `SIGNPATH_API_TOKEN`, variable `SIGNPATH_ORGANIZATION_ID` (et, si les noms
     diffèrent, `SIGNPATH_PROJECT_SLUG`, `SIGNPATH_SIGNING_POLICY_SLUG`).
  5. Au premier tag signé, approuver la demande dans SignPath, puis vérifier l'installeur publié ; alors seulement,
     retirer du README et de `site/` la consigne SmartScreen (« Informations complémentaires » → « Exécuter quand
     même »), dans une petite PR de suivi. La réputation SmartScreen d'un nouvel éditeur se construit aux
     téléchargements : l'avertissement peut persister quelque temps.

### Paquet Arch Linux (AUR, `packaging/aur/`, 4.0.0)

- **Quoi** : le paquet AUR `kairos-bin` (base de paquet du même nom), x86-64, versions stables seulement. Le dépôt
  garde le gabarit `packaging/aur/PKGBUILD` (`pkgver=@PKGVER@`, `sha256sums=('@SHA256@')`) et deux scripts ; l'AUR
  ne reçoit que le `PKGBUILD` rempli et son `.SRCINFO`.
- **`PKGBUILD`** :
  - source : `Kairos-<version>-linux-x64.deb::https://github.com/SKOHscripts/Kairos/releases/download/v<version>/Kairos-linux-x64.deb`,
    avec sa somme SHA-256 (le nom local porte la version : le cache d'un assistant AUR ne resert pas l'ancien) ;
  - `package()` : extrait `data.tar.*` du `.deb` (que `makepkg` a déjà ouvert) dans `$pkgdir`, donc l'image jpackage
    dans `/opt/kairos` ; y ajoute le marqueur `/opt/kairos/lib/app/kairos-managed` (§ Décisions) ; lien
    `/usr/bin/kairos` → `/opt/kairos/bin/Kairos` ; entrée de menu `/usr/share/applications/kairos.desktop`
    (`Exec=kairos`, `Icon=kairos`, `Categories=Office;ProjectManagement;`, `StartupWMClass=kairos`, la classe de
    fenêtre de `WindowClass`, commentaire anglais et `Comment[fr]`) ; icône `/usr/share/pixmaps/kairos.png` (le logo
    1024 px de l'image) ; licence `/usr/share/licenses/kairos-bin/LICENSE` (le fichier `copyright` du `.deb`, copie de `LICENSE` : l'AGPLv3, `licence.md`) ; `license=('AGPL-3.0-or-later')` ;
  - `depends` : les bibliothèques que l'image charge elle-même (runtime Java réduit, Skiko) : `alsa-lib`,
    `fontconfig`, `glibc`, `libglvnd` (libGL, chargée à l'exécution par Skiko), `libstdc++`, `libx11`, `libxext`,
    `libxi`, `libxrender`, `libxtst` ; freetype et zlib viennent du runtime, qui est embarqué : aucun JDK n'est
    demandé ; `provides`/`conflicts` : `kairos` ;
    `options=('!strip' '!debug')` (binaires du runtime jpackage laissés tels quels).
- **`build.sh <version> <.deb> <sortie>`** (lancé en root dans `archlinux:base-devel`) : met le système à jour,
  remplit le gabarit (version, somme du `.deb` local, posé sous le nom de la source pour que `makepkg` ne le
  retélécharge pas), construit le paquet sous un compte sans privilège, l'installe par `pacman -U` (ses dépendances
  viennent des dépôts : un nom faux échoue ici), vérifie qu'aucune bibliothèque de `/opt/kairos` ne manque (`ldd`,
  avec les dossiers du runtime dans `LD_LIBRARY_PATH` comme le lanceur : `libjvm.so` n'est pas dans le système),
  que le marqueur, l'entrée de menu, l'icône et la licence sont là, lance **l'auto-test par `/usr/bin/kairos`**
  (`KAIROS_DATA_DIR` temporaire), passe `namcap` (avis seulement : il signale toujours, à tort pour cette image,
  « ELF hors chemin valide » pour `/opt` et une dépendance `java-runtime` non déclarée), puis écrit `PKGBUILD` et `.SRCINFO`
  (`makepkg --printsrcinfo`) dans la sortie. Essayé le 2026-11-02 sur le `.deb` 3.14.0 publié : paquet installé
  avec ses dépendances, aucune bibliothèque manquante, auto-test réussi par `/usr/bin/kairos`.
- **`publish.sh <sortie>`** : clé privée `AUR_SSH_PRIVATE_KEY`, empreinte de la clé d'hôte Ed25519 de l'AUR
  **épinglée** (`SHA256:RFzBCUItH9LZS0cKB5UE6ceAYhBD5C8GeOBip8Z11+4`, publiée sur aur.archlinux.org) et comparée à
  celle que renvoie `ssh-keyscan` (sinon échec) ; clone `ssh://aur@aur.archlinux.org/kairos-bin.git` (vide au premier
  envoi : l'AUR crée la base de paquet à la première poussée sur `master`), y copie les deux fichiers, commit
  « Kairos <version> » (auteur `Kairos release`, adresse `noreply` de GitHub Actions) et pousse ; rien à pousser si
  le contenu est déjà là (relance du job).
- **Job `aur` de `kmp-release.yml`** : après `release` sur un tag (ou après `build` lors d'un build de test qui
  comprend le bureau), version stable seulement ; télécharge l'artefact `<préfixe>-linux-x64` (`dist-` sur un tag), lance `build.sh` dans le conteneur, téléverse
  l'artefact `aur-kairos-bin` (`PKGBUILD`, `.SRCINFO`, pour une poussée à la main), puis lance `publish.sh` **sur un
  tag seulement et seulement si** le secret `AUR_SSH_PRIVATE_KEY` existe (`AUR_READY`, comme `SIGNPATH_READY`) :
  forks et lancements manuels construisent et testent sans rien publier. Un échec du job n'annule pas la release
  GitHub, déjà publiée.
- **Application** : `SystemPackage.detect()` (`desktopApp`) cherche le marqueur `kairos-managed` à côté des JAR de
  l'image (même résolution que `PortableCopy` à partir de `jpackage.app-path`, `/proc/self/exe` du lanceur, donc
  `/opt/kairos/bin/Kairos` même lancé par le lien `/usr/bin/kairos`) ; `Main` le passe à `DesktopServices.open
  (managed = …)`, qui ne crée alors pas `DesktopUpdates` (`AppServices.updates` nul : ni vérification, ni bandeau, ni
  carte, `mises-a-jour.md`).
- **À faire par le propriétaire** (rien de cela ne peut se faire depuis le dépôt) :
  1. Créer un compte sur aur.archlinux.org.
  2. Créer une paire de clés dédiée (`ssh-keygen -t ed25519 -f aur -C kairos-aur`), poser la clé **publique** dans
     « My Account » → « SSH Public Key » de l'AUR.
  3. Dans le dépôt GitHub : secret `AUR_SSH_PRIVATE_KEY` = le contenu de la clé **privée**.
  4. Au premier tag stable (`v4.0.0`), vérifier le job `aur`, puis la page `https://aur.archlinux.org/packages/kairos-bin`.
     Sans secret, l'artefact `aur-kairos-bin` se pousse à la main (`git clone ssh://aur@aur.archlinux.org/kairos-bin.git`,
     copier les deux fichiers, commit, push).

### Microsoft Store (MSIX, `packaging/msix/`, `kmp/tools/make_msix.py`, #215)

État : spécifiée et implémentée le 2026-11-25 ; construite par la CI, **pas encore essayée sur un Windows réel** ni
soumise (à faire par le propriétaire, ci-dessous).

- **Format** : un paquet **MSIX** non signé, que le Store signe à la publication. Compose Desktop n'a pas de cible MSIX :
  le paquet reprend l'**image jpackage** de `createDistributable` (celle du zip portable : `Kairos.exe` à la racine,
  `app/`, `runtime/`), à laquelle s'ajoutent le manifeste, les logos et l'index des ressources.
- **Manifeste** (`packaging/msix/AppxManifest.xml`, gabarit, seul marqueur `@VERSION@`) :
  - identité attribuée par Partner Center (*Identité du produit*) : `Name="SKOHscripts.KairosPlanner"`,
    `Publisher="CN=EF0DE618-1AD9-4656-8417-18B58EB3603F"`, `PublisherDisplayName` `SKOHscripts`, x64 ; nom de famille
    `SKOHscripts.KairosPlanner_bfx3ns6pzhhgt`, ID Store `9MX45HFZ42TF` (pour la soumission, absents du manifeste) ;
  - `DisplayName` et nom du menu Démarrer **« Kairos Planner »** : le Store exige un nom réservé. Le titre de la fenêtre,
    « À propos » et le dossier des données restent « Kairos » ;
  - application `Kairos.exe`, `Windows.FullTrustApplication` (capacité restreinte `runFullTrust`, justifiée dans
    Partner Center : application de bureau empaquetée) ;
  - **écritures du système de fichiers non virtualisées** (`desktop6:FileSystemWriteVirtualization` à `disabled`,
    capacité restreinte `unvirtualizedResources`, choix A du propriétaire du 2026-11-25) : Kairos écrit dans le vrai
    `%LOCALAPPDATA%\Kairos` (`DataDirectory`), comme le `.msi`. Sans cela, Windows redirige ces écritures vers un dossier
    privé au paquet, invisible pour le `.msi` et effacé à la désinstallation. Le registre reste virtualisé (Kairos n'y
    écrit pas) ;
  - Windows 10 1903 (`10.0.18362.0`) au minimum, version minimale de `unvirtualizedResources` ; testé jusqu'à
    `10.0.26100.0` ;
  - six langues déclarées (`en-US` d'abord, `fr-FR`, `es-ES`, `de-DE`, `ru-RU`, `it-IT`) : Partner Center n'ouvre une
    fiche que pour une langue du paquet.
- **Version** : `make_msix.msix_version` applique la règle de l'installeur (`AppVersion.packageVersion`,
  `desktopApp/build.gradle.kts`) et ajoute `.0`, le quatrième nombre que le Store réserve : `4.6.0` → `4.6.99.0`,
  `4.6.0-beta.2` → `4.6.2.0`. Elle croît donc d'une version à l'autre, comme l'exige le Store. Seules les finales y sont
  soumises.
- **Logos** (`packaging/msix/Assets/`, versionnés, générés par `make_app_icons.py`, `msix_assets`) : le logo à coins
  arrondis des icônes de bureau, en `StoreLogo` (50 px), `Square150x150Logo` (150 px), `Square44x44Logo` (44 px), chacun
  aux échelles 100, 200 et 400 (`.scale-NNN`), plus `Square44x44Logo.targetsize-{16,24,32,48,256}_altform-unplated`
  (barre des tâches, explorateur). Fond du manifeste `transparent`.
- **`make_msix.py <image> <dossier>`** (sans dépendance) : copie l'image dans le dossier, y écrit le manifeste rempli
  (version lue dans `kmp/gradle.properties`) et les logos, pose le marqueur **`app/kairos-managed`** et refuse une
  image qui porte déjà le marqueur portable `app/kairos-portable` (§ Raccourci des versions portables) : une copie du
  Store ne doit ni proposer de raccourci ni chercher de mise à jour.
- **CI** (`kmp-release.yml`, job `build`, Windows, étape « Paquet MSIX » **avant** « Nommer les fichiers », qui pose le
  marqueur portable dans l'image) : `make_msix.py`, puis `makepri` (Windows SDK du runner, le plus récent) indexe les
  seuls logos dans `resources.pri` (un dossier à part qui ne contient que `Assets/` : indexer l'image entière mettrait
  ses milliers de fichiers dans l'index) dans **un seul** `resources.pri` (sans la section `<packaging>` de la configuration
  par défaut, qui éclatait les échelles en `resources.scale-200.pri` et `resources.scale-400.pri`, faits pour des paquets de
  ressources : dans un MSIX unique, Windows ne les lit pas ; constaté au premier build de test du 2026-11-25, l'étape
  échoue désormais si un autre `.pri` apparaît), puis `makeappx pack`, qui valide le manifeste. Sortie
  `Kairos-windows-x64<étiquette>.msix`, artefact **`store-windows-x64<étiquette>`**, gardé 30 jours (le temps de la
  soumission), qui ne commence pas par `dist-` : **ni la release ni `SHA256SUMS`** ne le reprennent. Un build de test
  manuel le produit aussi, pour l'essayer avant le tag.
- **Application** : `SystemPackage.detect()` trouve le marqueur `kairos-managed` à côté des JAR de l'image sous Windows
  comme sous Linux (`PortableCopy.imageDirs`) : pas de `DesktopUpdates`, donc ni vérification, ni bandeau, ni carte
  « Mises à jour » (`mises-a-jour.md`). Une copie du `.msi` ou du zip n'a pas le marqueur et garde la vérification.
- **Essayer un paquet sans le Store** (le `.msix` n'est pas signé) : Windows en mode développeur, extraire le `.msix`
  (c'est un zip) dans un dossier, puis `Add-AppxPackage -Register <dossier>\AppxManifest.xml` ;
  `Get-AppxPackage SKOHscripts.KairosPlanner | Remove-AppxPackage` le retire. Vérifier : le menu Démarrer montre
  « Kairos Planner », Réglages → Données montre `%LOCALAPPDATA%\Kairos\…`, aucune carte « Mises à jour », et les
  données du `.msi` sont là.
- **À faire par le propriétaire** (rien de cela ne peut se faire depuis le dépôt) :
  1. Lancer un build de test (bureau) et essayer l'artefact `store-…` comme ci-dessus.
  2. Dans Partner Center, première soumission : téléverser le `.msix` ; justifier `runFullTrust` et
     `unvirtualizedResources` (« Desktop app packaged with MSIX; it keeps its data in the same %LOCALAPPDATA% folder
     as its MSI installer, so that switching to the Store version keeps the user's data and uninstalling does not
     delete it. ») ; politique de confidentialité `https://skohscripts.github.io/Kairos/en/privacy/`
     (`publication.md`) ; fiches en anglais et en français, captures de bureau `packaging/msix/screenshots/<langue>/`
     (`publication.md` § Captures), classification par âge.
- **Soumission automatique (différée)** : après la première soumission à la main, le job ferait `msstore publish
  <.msix> -id 9MX45HFZ42TF` (Microsoft Store Developer CLI) sur un tag final, seulement si les secrets existent, comme
  l'AUR. Il faut une application Microsoft Entra associée au compte Partner Center (rôle Manager) et quatre secrets :
  identifiant du locataire, du vendeur, du client et secret du client. La soumission reprend la fiche de la précédente
  et attend la certification (jusqu'à trois jours ouvrés).

### Web (`kmp/webApp`)

- Kotlin/Wasm (`wasmJs`, module de sortie `kairos`, script `kairos.js`),
  `ComposeViewport(document.body)` avec
  `KairosApp(Platform.WEB) { WebServices.open() }` (base en mémoire dans un
  worker sql.js, persistance OPFS et fichier lié : `export-import.md` §
  Version web).
- `index.html` charge `kairos-web.js` (fonctions navigateur,
  `globalThis.KairosWeb`) **avant** `kairos.js`. La distribution contient
  aussi `kairos-sqljs.worker.js`, `sql-wasm.js` et `sql-wasm.wasm` (copiés par
  `webpack.config.d/sqljs.js`).
- `index.html` : fond `#FAF8FF` (`#121318` en sombre ; `#FFF8F4` et `#18120B` avant le 2026-10-27), favicon, bloc `#kairos-loading` (cadran
  vide en SVG, relais de la page de démarrage, `demarrage.md`, +
  « Chargement de Kairos… ») retiré par `main()` au démarrage. Un test
  `WebAssembly.validate` d'un module WasmGC minimal remplace le texte par
  « navigateur trop ancien » (bilingue) si WasmGC manque (Chrome ou Edge avant
  119).
- Distribution : `:webApp:wasmJsBrowserDistribution`
  (`webApp/build/dist/wasmJs/productionExecutable/`).

### GitHub Pages (`pages.yml`)

- Construit toujours depuis `main` ; `/preview/` : la version web construite
  depuis `main` (préversion) ; `/app/` : la version web (`Kairos-web.zip`) de
  la dernière release **stable** `v3.*` ou plus récente (`v4.*`…, jamais une préversion), absente tant
  qu'il n'y en a pas ; `/` : la page de téléchargement. Déclencheurs,
  assemblage et liens : `publication.md` § Page de téléchargement.
- Déploiement : `actions/upload-pages-artifact` puis `actions/deploy-pages`
  (environnement `github-pages`).
- **Prérequis, une fois, par le propriétaire du dépôt** : Settings → Pages →
  Source : « GitHub Actions » (un workflow ne peut pas activer Pages avec le
  jeton par défaut).

### CI et release (GitHub Actions)

- `kmp.yml` (« Kairos CI » ; push sur `main`, PR, lancement manuel ; chemins `kmp/**` hors `kmp/tools/**`, et le
  workflow lui-même) : JDK 21 Temurin, une **matrice de quatre jobs parallèles** (2026-11-15 ; le quatrième, Android
  Lint, le 2026-11-18), chacun avec son Gradle :
  - « Tests JVM » : plateforme Android 37, tests JVM (`core` dont les tests différentiels, `data`, `ui`, `desktopApp`
    dont les tests d'interface de la vue Jour) ;
  - « Tests wasm et web » : tests du modèle et de la synchronisation dans Chrome (`:core:wasmJsBrowserTest`, liste de
    classes), puis version web (`:webApp:wasmJsBrowserDistribution`) ;
  - « Bureau » : image de bureau Linux (`createDistributable`) et son **auto-test** ; captures en artefact
    `kmp-ci-screens` (7 jours) ;
  - « Android Lint » : plateforme Android 37, `:androidApp:lintDebug` (échec sur une erreur, pas sur un avertissement ;
    aucune erreur au premier passage, le 2026-11-18, donc pas de baseline) ; le rapport texte est affiché dans le journal
    par une étape du job (pas par `textOutput = file("stdout")`, obsolète en AGP 9, qui écrit un fichier `stdout` dans
    le module) ; rapports en artefact `kmp-ci-lint` (7 jours). C'est le seul contrôle propre à Android de la CI : les tests d'interface rendent les écrans
    sur la JVM et `androidApp` n'a pas de tests.
  Les deux jobs de tests ajoutent au résumé du lancement un **tableau des tests** par module et par tâche (tests, échecs,
  ignorés, durée) et la liste des tests en échec : `kmp/tools/report_tests.py`, qui lit les rapports JUnit de Gradle,
  lancé même en échec (`if: always()`), sans jamais faire échouer le job ; testé par `test_report_tests.py`.
  `fail-fast: false` : un job rouge n'arrête pas les autres. L'APK n'y est plus construit (2026-11-06) : `fdroid-check.yml`, déclenché par les
  mêmes changements, le construit deux fois (F-Droid et reproductibilité). Une PR qui reçoit un nouveau commit annule le
  run précédent ; un push sur `main` ou un lancement manuel ne s'annule jamais. Un test en échec affiche son message et sa
  pile complets (`testLogging` du `build.gradle.kts` racine) : un
  dépassement de délai d'un test d'interface y nomme l'attente qui a
  manqué. Les attentes des tests d'interface sont en temps réel : sur un
  runner à deux cœurs, avec les modules testés en parallèle, 5 s ont manqué
  par intermittence (`M5ScreensUiTest`), d'où 15 s.
- `kmp-release.yml` (« Kairos release » ; tags `v[3-9].*` et `v[1-9][0-9].*`, lancement manuel) : la release sur un
  tag, le **build de test** lancé à la main sur une branche (§ Build de test ; avant le 2026-11-15, un workflow à part,
  `kmp-test-build.yml`, qui en dupliquait les constructions) :
  - `resolve` : le commit construit (sur un tag, celui du tag, qui doit valoir `v` + `kairos.versionName` ; à la main,
    § Build de test), drapeau `prerelease` si la version contient `-`, et les cibles de la matrice ;
  - `tests` : tests JVM, **en parallèle des constructions** (2026-11-15 ; avant, en tête du job Android), avec le
    tableau des tests de `report_tests.py` dans le résumé ;
  - `build`, **une matrice** de six cibles (2026-11-15 ; avant, trois jobs `android`, `web` et `desktop`) :
    `android` (`ubuntu-latest`, plateforme Android 37, keystore depuis `KAIROS_KEYSTORE_BASE64`, échec sur un tag
    sans secret, `assembleRelease` → `Kairos-android.apk`), `web` (`ubuntu-latest`, `Kairos-web.zip`), et le bureau,
    `ubuntu-latest` → `linux-x64`, `windows-latest` → `windows-x64`, `macos-latest` → `macos-arm64`,
    `macos-15-intel` → `macos-x64` : `packageDistributionForCurrentOS` + `createDistributable` (`fakeroot` sous
    Linux), **auto-test de l'image empaquetée**, puis `Kairos-<id>.{deb,msi,dmg}` et
    `Kairos-<id>-portable.{tar.gz,zip}`, ceux de Windows signés par SignPath Foundation sur un tag quand c'est
    configuré (§ Signature Windows) ; captures en artefacts `screens-<id>`. Artefacts `dist-<cible>` sur un tag.
    `fail-fast: false`. La liste des cibles vient de `resolve` (`fromJSON`) : un build de test sans le web n'a pas
    d'entrée `web` ;
  - `release` (tag seulement, après `tests` et `build`) : `SHA256SUMS` de tous les `Kairos-*`, release
    GitHub « Kairos <version> » (préversion si suffixe), notes générées. Un
    seul APK par release ;
  - `aur` (version stable) : paquet `kairos-bin` construit et testé dans un conteneur Arch, poussé sur l'AUR sur un
    tag quand `AUR_SSH_PRIVATE_KEY` existe (§ Paquet Arch Linux).

- `metadata.yml` (« Kairos checks », chaque PR sans filtre de chemins, push sur `main`, lancement manuel ; 2026-11-15,
  avant deux workflows « Metadata » et « Kairos version ») : deux jobs parallèles, `metadata` (fiches, page, README,
  menu, identité de l'app, tests des scripts de `kmp/tools/`, `publication.md` § Vérification continue) et `version` (§ Version attendue).
  Quelques secondes chacun : sans filtre de chemins, rien ne leur échappe.
- `pages.yml` : ne se déclenche plus pour `kmp/androidApp/**`, `kmp/desktopApp/**` ni `kmp/tools/**`, sans effet sur la page.

### Artefacts (2026-11-06)

- **Durées de conservation** (au lieu des 90 jours par défaut) : APK F-Droid intermédiaire 1 jour (ne sert qu'au job
  suivant) ; captures de la CI 7 jours ; fichiers de la release (`dist-*`, `unsigned-*`, `screens-*`) 3 jours, puisqu'ils
  sont copiés dans la release GitHub ; paquet AUR 7 jours (poussée à la main possible) ; builds de test 14 jours.
- **Nettoyage** (`artifacts-cleanup.yml`, à la fin de « Kairos CI », « F-Droid » et « Kairos release », ou à la
  main) : `kmp/tools/cleanup_artifacts.py` ne garde, pour chaque workflow, que les artefacts des
  **2 derniers runs terminés** qui en ont (`KEEP_RUNS`) ; il supprime ceux des runs plus anciens et ceux d'un run disparu,
  ne touche jamais un run en cours (une release en cours a besoin des siens), ignore les expirés. Bibliothèque standard
  seulement, jeton du workflow (`actions: write`). La règle est pure (`plan`) et testée (`test_cleanup_artifacts.py`).
- Conséquence : les captures ou le build de test d'une PR plus ancienne que les deux derniers runs disparaissent ; on
  relance au besoin. Releases et builds de test partageant un workflow depuis le 2026-11-15, deux lancements suffisent à
  effacer les fichiers d'un build de test (ceux d'une release sont déjà dans la release GitHub). L'espace compté par GitHub n'est recalculé que toutes les quelques heures.

### Build de test (lancement manuel de « Kairos release »)

- **Déclenchement** : `workflow_dispatch` de `kmp-release.yml` sur une branche, en général `main` (Actions → « Kairos
  release » → « Run workflow »). Champs : `commit` (SHA court ou long ; vide = le dernier commit de la branche
  choisie), et trois cases cochées par défaut, `android`, `desktop`, `web`. Sur la référence d'un tag (ce que fait
  « Kairos tag »), le même lancement est une release et ignore ces champs. Nom du lancement : « Build de test <commit> »
  ou « Release <tag> ».
- **`resolve`** : historique complet, `git rev-parse --verify "$COMMIT^{commit}"`, puis
  `git merge-base --is-ancestor` : le commit doit appartenir à la branche du lancement, sinon échec. Le champ passe par
  une variable d'environnement, jamais interpolé dans le script (injection). Sorties : SHA complet, SHA court,
  `kairos.versionName` du commit, cibles cochées (au moins une, sinon échec). Le résumé du lancement
  (`$GITHUB_STEP_SUMMARY`) dit le commit, la version et les plateformes demandées.
- **`tests`** (toujours) : les tests JVM, en parallèle des constructions : un test en échec rend le lancement rouge
  sans retarder les fichiers.
- **`build`**, les cibles cochées de la même matrice que la release, au même commit, avec un suffixe `-test-<court>`
  dans les noms : `Kairos-android-test-<court>.apk` (signé avec les secrets des releases, donc installable par-dessus
  la version en place, même `versionCode` compris ; sans secrets, fork, APK non signé avec un avertissement),
  `Kairos-web-test-<court>.zip`, `Kairos-<id>-test-<court>.{deb,msi,dmg}` et
  `Kairos-<id>-test-<court>-portable.{tar.gz,zip}` (marqueur portable comme la release), après l'auto-test ; captures
  en artefacts `screens-<id>`. Pas de SignPath : chaque demande attend une approbation manuelle, réservée aux releases.
- **Artefacts** `kairos-test-<court>-<cible>`, gardés **14 jours** (un build de test n'a pas à durer au-delà de la
  release qu'il prépare). Ni `release` ni `follow-up` ; `aur` construit et teste le paquet sans le publier si le bureau
  Linux est construit et la version finale.
- Aucun job ne publie ni n'écrit dans le dépôt hors d'un tag.

### Décisions et pièges tracés

- **Microsoft Store en MSIX** (propriétaire, 2026-11-25, #215) : le Store accepte un `.msi` seulement signé Authenticode
  par une autorité reconnue, et la signature SignPath (#117) attend toujours l'acceptation ; le MSIX est signé par le
  Store. Contreparties : un nouvel artefact construit à la main (`makeappx`), et la vérification des mises à jour
  coupée (le Store s'en charge). Données non virtualisées (choix A) plutôt que le dossier privé du paquet (choix B,
  sans capacité restreinte, mais données effacées à la désinstallation et séparées du `.msi`). Nom « Kairos Planner »
  (« Kairos » était réservé par un autre éditeur). Le `.msix` reste un artefact du lancement, hors de la release : non
  signé, il ne s'installerait nulle part et égarerait les utilisateurs. Écartés : attendre SignPath pour soumettre le
  `.msi` ; un outil tiers d'empaquetage (MSIX Packaging Tool, interactif ; Conveyor, non libre).
- **Version attendue calculée** (propriétaire, 2026-11-15) : avec des mineures au fil de l'eau, le numéro et le moment
  de publier se décident plus souvent ; un contrôle à chaque PR (« Kairos version ») et une PR de passage de version
  après chaque finale évitent de les oublier. Le contrôle lit les cartes du catalogue et le format du fichier, pas les
  types de commits, qui ne servent qu'à avertir : un `feat(build)` n'est pas une fonctionnalité. Conséquence : la note de
  version d'une finale n'est plus exigée par la CI, seulement au tag. Écartés : un commit direct du passage de version
  sur `main` (le jeton du workflow devrait y écrire) ; un simple rappel (rien ne changerait la version).
- **Mineures au fil de l'eau** (propriétaire, 2026-11-15, à partir de la 4.3.0) : une mineure sort en finale dès
  qu'une ou quelques fonctionnalités sont prêtes ; une beta seulement pour un chantier risqué. Rouvre le cycle « beta
  puis finale » du 2026-11-05, où chaque mineure passait par au moins une beta et un gel des fonctionnalités : en 4.1,
  neuf betas ont accumulé les fonctionnalités (les cloches et la méthode GTD, prévues pour la 4.2.0, y sont entrées)
  pendant que les utilisateurs de la stable (F-Droid, AUR, page de téléchargement) attendaient la finale. La 4.2.0
  garde sa `4.2.0-beta.1`. Les outils n'ont rien à changer : ni `set_version.py` ni « Kairos tag » n'exigeaient de
  beta avant une finale. Écartés : publier les fonctionnalités en correctifs (`4.2.1`, `4.2.2`) : le numéro ne dirait
  plus si une version ajoute quelque chose, et une carte du catalogue n'annonce jamais un `X.Y.Z` avec `Z > 0`
  (`FeatureCatalogTest.aFeatureArrivesInAMinorOrMajorVersion`) ; des betas plus courtes : la stable attendrait
  toujours. Contrepartie : moins d'essais avant de donner une fonctionnalité à tous, compensés par la CI, l'auto-test
  des images et les correctifs `X.Y.Z`, possibles dès le lendemain d'une finale.
- **Une seule app pour la beta et la stable** (propriétaire, 2026-11-15, à partir de la 4.2.0) : qui veut la dernière
  version, beta ou non, l'a dans la même app « Kairos », sur Android et sur le bureau. Rouvre le canal séparé « Kairos
  Preview » du cycle des versions (2026-11-05). Contreparties : la formule du `versionCode` change (les betas passent
  au-dessus de la finale précédente, ce que l'ancienne formule interdisait : les codes 40 101 à 40 199 sont ceux des
  correctifs 4.1.x) ; qui installe une beta ne revient à la stable précédente qu'en désinstallant ; les installations
  « Kairos Preview » existantes restent des apps à part (rien n'est prévu pour elles, choix du propriétaire). Écartés :
  changer seulement le nom (deux icônes « Kairos ») ; un code de beta entre deux finales sans changer la formule (pas de
  place).
- **`(yes || true) | sdkmanager`** (2026-11-15) : dans la matrice `build` de la release, le shell `bash` déclaré ajoute
  `-o pipefail` ; `yes`, tué par « Broken pipe » quand `sdkmanager` se ferme, faisait échouer l'étape « Plateforme
  Android 37 » du premier tag `v4.2.0-beta.1` (les anciens jobs utilisaient le shell par défaut, sans `pipefail`). Même
  écriture dans tous les workflows, pour qu'un futur `pipefail` ne la casse pas.
- **Android Lint et tableau des tests** (propriétaire, 2026-11-18, d'après la CI d'un autre projet Android libre) :
  retenus parce que peu coûteux (un job parallèle de plus, un script sans dépendance). Écartés pour l'instant : des tests
  instrumentés sur émulateur (+10 à 20 min, et `androidApp` n'a pas encore de tests ; à réserver à la release ou à un
  lancement nocturne le jour venu) ; les tests unitaires en debug et en release (même code, deux fois le temps).
- **Nettoyage des workflows** (propriétaire, 2026-11-15) : les noms affichés perdent « Kairos 3 » (« Kairos CI »,
  « Kairos release », artefacts `dist-*` au lieu de `kairos3-*`), les fichiers gardent le leur (badge du README, liens
  des specs, historique des lancements). Des matrices là où les jobs ne diffèrent que par leurs paramètres (CI,
  constructions de la release) et du parallélisme partout où un job attendait un autre sans en avoir besoin (tests de
  la release, APK du runner de la vérification F-Droid). Le build de test rejoint la release, qui avait déjà un
  lancement manuel de validation : une seule définition des constructions. « Metadata » et « Kairos version »
  deviennent « Kairos checks », lancé sur chaque PR (quelques secondes ; rouvre pour ces contrôles le déclencheur
  resserré du 2026-11-06). Contrepartie acceptée : la CI découpée consomme plus de minutes de runner (Gradle et la
  compilation de `core` dans chaque job) pour attendre moins. Écartés : un workflow réutilisable (`workflow_call`)
  appelé par la release et le build de test (deux entrées pour la même chose) ; une matrice pour les deux APK de la
  vérification F-Droid (l'un dans l'image F-Droid, l'autre sur le runner : aucune étape commune) ; couper les tests JVM
  par module (six jobs, encore plus de minutes). Les noms des vérifications d'une PR changent (« Tests JVM »,
  « Bureau »… au lieu de « build ») : une règle de protection de branche qui les exige est à mettre à jour.
- **Simplification des workflows** (propriétaire, 2026-11-06) : annulation du run précédent d'une PR, déclencheurs
  resserrés (un texte ne relance plus la construction F-Droid, un script de `kmp/tools/` ne relance plus la CI), APK
  construit seulement par `fdroid-check.yml`. **Gardé** : le re-test sur `main` après fusion (deux PR fusionnées l'une
  après l'autre ne seraient sinon jamais testées ensemble avant la release). Écartés : un filtre de chemins par job
  (action tierce `paths-filter`), préféré à un workflow `metadata.yml` séparé.
- **Nettoyage des artefacts par workflow, pas par nom** : les builds de test portent le SHA dans leur nom ; garder « le
  dernier de chaque nom » ne les aurait jamais supprimés. Un script maison plutôt qu'une action tierce (dépendances
  libres et figées, `architecture.md`).

- **Arch Linux : paquet AUR `kairos-bin` publié par la CI** (2026-11-02, choix du propriétaire, 4.0.0). Tiré du
  `.deb` plutôt que de l'archive portable : celle-ci porte le marqueur `kairos-portable`, et Kairos installé par pacman
  proposerait de « s'ajouter au menu ». Écartés : un paquet compilé depuis les sources (Gradle et un JDK chez chaque
  utilisateur, long et fragile, pour le même résultat) ; un `.pkg.tar.zst` joint aux releases (`pacman -U`, sans mise
  à jour). Publication **automatique** sur tag, **conditionnelle** au secret (forks jamais bloqués), versions stables
  seulement (une préversion dans `kairos-bin` la pousserait à tous). Le paquet est **installé et lancé dans un vrai
  Arch** avant chaque envoi : ses dépendances ne se déduisent pas de celles du `.deb` (noms Debian), seul un essai
  les prouve. Clé d'hôte de l'AUR épinglée plutôt qu'acceptée au premier contact : la clé privée ne doit parler qu'à
  l'AUR.
- **Marqueur `kairos-managed` posé par le `PKGBUILD`, pas par la release** : le même `.deb`, installé à la main par
  `dpkg`, n'a personne pour le mettre à jour et garde la vérification. Un fichier plutôt qu'une option JVM : le
  `Kairos.cfg` du lanceur fait partie de l'image du `.deb`, le réécrire couplerait le paquet à son format.
- **`v4.*` ajouté aux tags de `kmp-release.yml`** (2026-11-02) : le filtre `v3.*` aurait ignoré le tag `v4.0.0`, et
  avec lui la release et la première publication AUR.
- **Build de test séparé de la release** (2026-11-03) : un workflow à part plutôt qu'un champ « commit » ajouté au
  lancement manuel de `kmp-release.yml`, pour que le chemin de release reste celui des tags et que le build de test ne
  puisse rien publier par construction. Écartés : un commit hors de la branche du lancement (un SHA de fork serait
  construit avec la clé de signature) ; un APK non signé par défaut (il faudrait désinstaller Kairos, et ses données,
  pour l'essayer).

- **Windows : SignPath Foundation** (2026-10-29, issue #117, décision du propriétaire) : gratuit pour un projet libre
  construit depuis son dépôt public, ce que Kairos est (licence MIT à l'époque, AGPL v3 ou ultérieure depuis le 2026-11-21, release par GitHub Actions, APK reproductible).
  Prix accepté : l'éditeur affiché est « SignPath Foundation », pas le nom de l'auteur, chaque release attend une
  approbation manuelle, et une politique de signature doit être publiée. Écartés : Azure Artifact Signing (environ
  10 USD par mois, vérification d'identité restreinte pour les particuliers selon le pays) ; un certificat OV d'une
  autorité (200 à 400 € par an, clé sur jeton matériel ou HSM en ligne). Signature **sur tag seulement** : une demande
  par construction de PR noierait l'approbateur. **Conditionnelle** : sans configuration, le workflow construit
  comme avant, pour que forks et contributeurs ne soient jamais bloqués. Le README garde la consigne SmartScreen
  jusqu'à la vérification d'une release réellement signée (choix du propriétaire).
- **macOS : ni signature Developer ID ni notarisation pour l'instant** (2026-10-29, issue #116, décision du
  propriétaire). Elles exigent l'Apple Developer Program, **99 USD par an**, au nom d'une personne ou d'une
  organisation ; aucune autre voie ne retire l'avertissement de Gatekeeper (une signature ad hoc ou un certificat
  auto-signé laissent « développeur non identifié »). Le compte n'est pas pris : le `.app`, le `.dmg` et le zip portable
  restent non signés, le workflow ne change pas, et le README (anglais et français) comme les six pages de `site/`
  gardent la marche à suivre, **corrigée le même jour** : depuis macOS 15 (Sequoia), le clic droit → « Ouvrir » ne
  contourne plus Gatekeeper ; on passe par Réglages Système → Confidentialité et sécurité → « Ouvrir quand même », le
  clic droit restant cité pour macOS 14 et avant. **Si la décision est rouverte**, la voie retenue
  d'avance : un certificat « Developer ID Application » importé dans un trousseau temporaire du runner, la signature
  et la notarisation de Compose Desktop (`macOS { signing { … } notarization { … } }` : `codesign` avec runtime
  renforcé, `notarytool`, agrafage), et une **clé d'API App Store Connect** (`.p8`, révocable seule) plutôt que
  l'identifiant Apple et un mot de passe d'application ; certificat, mot de passe du `.p12` et clé en secrets GitHub
  seulement, et sans eux une construction non signée comme aujourd'hui (forks, contributeurs). L'APK, la vérification
  `reproducible` (F-Droid) et `SHA256SUMS` ne sont pas concernés.

- **Préversions installables à côté de la version stable** : identifiant
  Android `.preview`, produit de bureau distinct ; les versions finales
  utilisent les identifiants définitifs.
- **Dossier de données `…/Kairos/v3`** : chemin inchangé depuis la 3.0.0, c'est
  là que vivent les données des utilisateurs.
- **Instance unique par fichier surveillé, pas par socket** : aucun port
  ouvert, rien qu'un pare-feu d'entreprise puisse bloquer.
- **Auto-test sans fenêtre** : `ImageComposeScene` fait le rendu en mémoire
  (Skia), donc il marche sur les runners de CI sans affichage, et vérifie que
  l'image empaquetée (runtime réduit compris) sait réellement rendre
  l'interface.
- **Web dans ce conteneur** : la distribution web exige un paquet npm servi par
  `codeload.github.com`, bloqué dans l'environnement de développement (403).
  La compilation Wasm y est vérifiée, et la sortie compilée y est testée dans
  Chromium (`architecture.md` § Décisions) ; la distribution complète est
  construite par la CI GitHub.
- **Tags posés par le propriétaire du dépôt** : l'environnement de
  développement ne peut pousser que sa branche de travail, pas de tag. Un
  workflow qui créerait lui-même le tag a été écarté (contournement d'une
  restriction de l'environnement). Le tag `v<version>` est donc posé à la
  main sur le commit du jalon (`git tag` + `git push`, ou « Draft a new
  release » dans GitHub avec la branche comme cible), ce qui déclenche
  `kmp-release.yml`.
- **Runner macOS Intel** : `macos-15-intel`, dernier runner Intel de GitHub.
  S'il disparaît, la version macOS x64 est abandonnée (Apple Silicon reste).
