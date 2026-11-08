# Retrait de Kairos 2

_Rôle : supprimer de Kairos tout ce qui concerne la version 2 (Python) : import,
migration, écrans, textes, tests différentiels, mentions, sans perdre
l'histoire ni l'identité. Fichiers couverts (à venir) : voir l'inventaire en
partie 2. Tests : `DifferentialTest`, `StatsDifferentialTest` (refaits sur
fichiers d'or figés), `LegacyMigrationTest` (supprimé), test de garde
`NoKairos2MentionTest` (créé en 3.4, supprimé le 2026-11-08)._

État : **issue #64 (retour du propriétaire)**, **implémentée en 3.4.0**, en
**premier** chantier de la version (il allège les autres). **Rouvre
explicitement** les décisions qui reposaient sur « parce que Kairos 2 » (voir
« Décisions »).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Plus personne n'utilise Kairos 2. Sa présence coûte : un écran et des services
d'import à maintenir sur trois plateformes, des textes d'accueil qui parlent d'une
migration sans objet, des tests qui ne tournent qu'avec un dépôt Kairos 2 à
côté, des décisions justifiées par la parité avec une version morte. Le
propriétaire demande de **retirer toute mention de Kairos 2 dès la 3.4** et
de lever les blocages liés à cette version.

### Comportement attendu (utilisateur)

1. L'accueil du premier lancement ne parle plus de migration ni d'import
   Kairos 2 ; Android ne cherche plus une base Kairos 2 au démarrage ; le
   bureau ne propose plus d'importer.
2. Aucun texte d'interface, README, fiche de magasin, page du site ni note de
   version **future** ne mentionne Kairos 2.
3. Les utilisateurs de la 3.x gardent **toutes leurs données** : les migrations
   de la base v3 restent intactes ; aucun fichier de l'utilisateur n'est
   supprimé (un ancien `tasks.db` n'est simplement plus lu).
4. Le comportement du moteur d'ordonnancement, des statistiques et du calendrier
   est **inchangé** : ce qui était vérifié contre Kairos 2 l'est désormais contre
   des fichiers d'or figés.
5. L'histoire reste lisible : les notes de version déjà publiées
   (`29501.txt`, `30000.txt`) et `docs/plan-v3-kotlin.md` ne sont pas réécrits.

### Critères de succès

- Les fichiers d'or reproduisent exactement les sorties jusqu'ici comparées à
  Kairos 2 : `DifferentialTest` (480 scénarios), `StatsDifferentialTest` (200 et
  plus), partie calendaire ; mêmes assertions, source différente (voir
  « Décisions »).
- Plus aucune occurrence de `Kairos 2`, `Kairos2`, `kairos2` ou `tasks.db` hérité
  dans le code, les textes d'interface, `site/` et les fiches des magasins. Plus de
  test de garde pour le vérifier depuis le 2026-11-08 (voir « Décisions »).
- Les bases existantes s'ouvrent comme avant (`ExchangeMigrationTest`) ; `TeamIsolationTest.soloExportIsByteIdentical` passe.
- `StringsParityTest` reste vert après retrait des chaînes.
- Android, bureau et web démarrent sans l'import (`--self-test`, démarrage de
  l'APK).

### Hors périmètre / différé

- Réécrire l'histoire (anciens changelogs, plan de réécriture).
- Changer l'identifiant d'application, le nom, le logo, le dossier de données
  de la 3.x.
- Supprimer des fichiers sur les appareils (ancienne base Kairos 2 laissée en
  place).

## 2. Solution technique

### Inventaire (réalisé)

**Supprimer**

- Code : `core/legacy/Kairos2Import.kt` ; `androidApp/.../AndroidLegacy.kt` ;
  `desktopApp/.../LegacyFiles.kt` ; le flux d'import de `ui/app/Replace.kt` (le fichier reste : `fileStamp`, `fileSlug`,
  `saveBackup`, `replaceWithBackup`, `clearTeamDataWithBackup`) ;
  `MainActivity.openFileInto` ; les champs `legacy` et `migrated` d'`AppServices`
  et leur câblage dans `AndroidServices`, `DesktopServices`, `DataDirectory` ;
  `kmp/tools/gen_legacy_db.py`.
- Interface : `rememberLegacyImport` / `LegacyImportDialog`, le bouton « Importer une base
  Kairos 2.x » de la carte Données des Réglages, et les chaînes `legacy_*`,
  `welcome_legacy_*`, `welcome_migrated`, `welcome_keep_examples` (FR et EN) ;
  l'accueil ne garde que le cas « exemples ».
- Tests : `LegacyMigrationTest`, les cas Kairos 2 de `M5ScreensUiTest`,
  `DesktopTest`, `ExampleDataTest` ; la spec `migration-2x.md` (remplacée par la
  présente spec ; l'historique de la migration reste dans `git` et les tags
  `v3.x`).

**Réécrire sans changer le comportement**

- Commentaires de code qui disent « comme Kairos 2 » (`Scheduling.kt`,
  `Recurrence.kt`, `Dependencies.kt`, `DateTimes.kt`, `Workdays.kt`,
  `Staleness.kt`, `TimeTracking.kt`, `ChronoAlerts.kt`, `DayView.kt`,
  `WeekView.kt`, `NoteConversion.kt`, `TaskStats.kt`, `Task.kt`, `Settings.kt`,
  `SettingsForm.kt`, `TeamStates.kt`, `UpdateCheck.kt`, `ExampleData.kt`, et côté
  `ui/` : `Dates.kt`, `Levels.kt`, `PointsGuide.kt`, `Timeline.kt`, `TaskRow.kt`,
  etc.) : le « pourquoi » devient une décision propre, tracée en spec.
- `data/ExportCodec.kt`, `Kairos.sq`, `KairosRepository.kt` : commentaires seulement ;
  **aucune** modification de schéma.
- Documentation et diffusion : `README.md`, `README.fr.md`,
  `fastlane/**/full_description.txt`, `site/index.html`, `site/en/index.html`,
  `fdroid/README.md`, `.github/workflows/pages.yml` et `kmp-release.yml`
  (commentaires), `docs/DESIGN_SYSTEM.md`, `docs/spec/*` (24 specs citent
  Kairos 2 : chacune est relue à l'implémentation), `CLAUDE.md` et
  `.claude/skills/*`.

**Préserver**

- Identité : nom « Kairos », logo, identifiant d'application, chemins de
  données.
- Migrations de la base v3 (`.sqm`, `KairosStore.MARKERS`,
  `KairosStore.actualVersion`) et formats d'export v3.
- Notes de version publiées et `plan-v3-kotlin.md`.

### Tests différentiels → fichiers d'or figés

`DifferentialTest`, `StatsDifferentialTest` et le test du calendrier lisaient
des fixtures générées par un script Python qui exigeait un dépôt de la version
précédente. Les fixtures **restent** (`core/src/jvmTest/resources/fixtures/`
`scheduling.json`, `dependencies.json`, `calendar.json`, `stats.json`) et
deviennent la **source de vérité** : le script de génération est supprimé (son
historique reste dans `git`, au tag `v3.3.0`), les fixtures ne se régénèrent plus (un
changement voulu de comportement les édite, avec sa justification en spec), et
chaque test garde ses assertions ; les méthodes sont renommées `*_matches_golden`
(`scheduling`, `dependencies`, `calendar`, `dashboard`) et
`thresholds_follow_the_reference`. Ils servent alors d'invariant « rien ne change sans réglage » pour
les chantiers de la 3.4 (`planning-personnalise.md`, `durees-apprises.md`).

### Décisions et pièges tracés

- **Retirer l'import et tout le code, garder l'histoire** (choix du
  propriétaire ; écartés : tout effacer historique compris ; ne retirer que l'import
  en gardant les différentiels contre Kairos 2).
- **Fichiers d'or plutôt que suppression des tests** : sans eux, plus rien ne
  prouve que le moteur n'a pas bougé.
- **Décisions « parce que Kairos 2 » rouvertes** : l'arrondi `pyRound` (demi-pair)
  des statistiques, l'ordre de tri et les valeurs par défaut des réglages
  restent tels quels, mais sont désormais justifiés comme **comportements
  propres de Kairos** (figés par les fichiers d'or), plus comme parité ; aucun
  n'est changé. Toute modification future se décide sans référence à Kairos 2.
- **Ne pas toucher aux migrations v3** : retirer un `.sqm` ou un marqueur
  casserait l'ouverture de bases réelles (leçon de `demarrage.md`).
- **Aucun effacement de fichier utilisateur** : un `tasks.db` hérité devient
  simplement inutilisé.
- **Test de garde supprimé le 2026-11-08.** `NoKairos2MentionTest` (créé en 3.4) a
  été retiré : il datait de la transition entre les deux versions et empêchait de
  raconter l'histoire de l'outil. Ce qui reste de la règle : ni migration, ni import,
  ni support de l'ancienne version, et pas de mention dans l'interface.
- **Chemin des données du bureau inchangé** : `DataDirectory` garde la racine
  `Kairos` de platformdirs et le sous-dossier `v3` ; seuls ses commentaires changent.
- **Histoire d'origine (2026-11-08).** Page du site `histoire/` et `en/story/`
  (`publication.md`), résumée dans la section « Pourquoi j'ai construit Kairos » des
  README (après « Pourquoi Kairos ? ») : un
  script Python en terminal, puis une interface locale lancée par systemd, puis la
  réécriture en Kotlin. Ce n'est pas une fonctionnalité (hors catalogue, hors tableau
  « Feature list ») : c'est du récit de projet, voulu pour que les lecteurs s'y
  reconnaissent. Le récit peut nommer les anciennes versions.
