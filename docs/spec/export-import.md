# Export, import et données de la version web

_Rôle : sauvegarder ou transférer toutes ses données dans un fichier, les
restaurer, et garder les données de la version web à l'abri d'un nettoyage du
navigateur. Fichiers couverts : `kmp/data/.../ExportCodec.kt`,
`kmp/ui/.../settings/SettingsScreen.kt` (carte « Données »),
`kmp/ui/.../app/Replace.kt` (`replaceWithBackup`, `fileStamp`),
`kmp/ui/.../app/` (`AppServices.kt`, `LinkedStorage.kt`, `StorageBanner.kt`),
`kmp/webApp/src/wasmJsMain/` (`WebServices.kt`, `WebInterop.kt`,
`resources/kairos-web.js`, `resources/kairos-sqljs.worker.js`),
`kmp/webApp/webpack.config.d/sqljs.js`, et les services de fichiers et de
sauvegarde du bureau et d'Android (`DesktopServices.kt`, `AndroidServices.kt`,
`MainActivity.kt`) ; fichier lié sur toutes les plateformes (3.10.0, issue #137) :
`kmp/ui/.../app/LinkedFileSync.kt`, `kmp/desktopApp/.../DesktopLinkedFile.kt`,
`kmp/androidApp/.../AndroidLinkedFile.kt`. Tests : `KairosRepositoryTest`,
`LinkedFileSyncTest`, `DesktopLinkedFileTest`, `LinkedFileUiTest`._

État : **jalon M1**, implémenté ; fichier lié du bureau et d'Android, « Délier » et garde contre une modification
faite ailleurs : **3.10.0** (issue #137, choix du propriétaire du 2026-10-25).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos 3 ne synchronise rien (décision du 2026-09-28) : chaque appareil a sa
base. Il faut donc pouvoir :

- **sauvegarder** ses données dans un fichier et les **restaurer** ;
- les **transférer** d'un appareil à l'autre (bureau → téléphone, poste pro
  → poste perso) ;
- sur un **poste professionnel verrouillé**, utiliser la version web sans
  perdre ses données quand le navigateur est nettoyé (profil d'entreprise
  vidé à la fermeture, nettoyage manuel).

### Comportement attendu (utilisateur)

- Réglages → catégorie « Données » → carte **Données** (suivie de « Réglages par défaut », `reglages.md`) :
  - le texte rappelle que les données restent sur l'appareil ; sur le
    bureau, le dossier des données est affiché ;
  - **Exporter** : enregistre un fichier `kairos-export-AAAAMMJJ-HHMM.json`
    (boîte d'enregistrement du système ; téléchargement dans le navigateur) ;
  - **Importer** : choisir un fichier, confirmer « Remplacer toutes les
    données ? ». L'import **remplace** tout (tâches, créneaux, notes,
    réglages) ; une **sauvegarde automatique** des données actuelles est
    faite juste avant, et sans elle l'import n'a pas lieu. Un message confirme
    le nombre de tâches importées.
  - **Importer** reconnaît aussi un **paquet de tâches** ou un **rapport
    d'avancement** de l'espace Équipe : au lieu de « Remplacer toutes les
    données ? », un aperçu propose de les recevoir ou de les intégrer, sans
    rien remplacer (`equipe-echanges.md`).
  - **Renvoyer l'avancement…** : visible seulement si la base contient des
    tâches reçues d'un manager (`equipe-echanges.md`).
  - Un fichier qui n'est pas un export Kairos, un export d'une version plus
    récente ou un export abîmé est refusé avec un message clair, sans rien
    changer.
- **Version web** :
  - les données sont enregistrées dans le navigateur à chaque modification ;
  - un bandeau (vue Jour et carte Données) prévient tant qu'elles ne sont pas
    **liées à un fichier** : « Créer un fichier » (nouveau fichier, tenu à jour
    à chaque modification) ou « Ouvrir un fichier Kairos » (un export ou un
    fichier lié existant : ses données remplacent celles du navigateur, après
    confirmation, puis il est tenu à jour) ;
  - une fois lié, la carte Données affiche « Enregistré aussi dans le fichier
    « kairos.json » » ;
  - si le navigateur a perdu ses données, Kairos propose d'autoriser l'accès
    au fichier lié pour les recharger ; tant que ce n'est pas fait, rien
    n'est écrit (le fichier n'est jamais écrasé par des exemples) ;
  - si le navigateur a retiré l'autorisation d'écrire dans le fichier, un
    bandeau propose de la redonner ;
  - Firefox et Safari n'ont pas de fichier lié : le bandeau conseille
    d'exporter régulièrement, ou d'utiliser Edge ou Chrome.
- **Bureau et Android : fichier lié** (3.10.0, issue #137), pour garder une
  copie à jour dans un dossier synchronisé (Syncthing, Nextcloud, Dropbox…).
  Les données restent dans la base ; le fichier en est une copie, au format
  d'export (il s'ouvre partout par « Importer »). Proposé dans la carte
  Données seulement, jamais par un bandeau tant que rien n'est lié :
  - « Kairos peut tenir à jour une copie de tes données dans un fichier de ton
    choix (par exemple dans un dossier Syncthing ou Nextcloud). » ;
  - **« Créer un fichier »** (`kairos.json` proposé) : le fichier est écrit
    tout de suite, puis tenu à jour ;
  - **« Ouvrir un fichier Kairos »** : un export ou un fichier lié existant ;
    ses données remplacent celles de l'appareil après confirmation et une
    sauvegarde automatique, exactement comme « Importer » ; il est ensuite
    tenu à jour ;
  - une fois lié : « Enregistré aussi dans le fichier « kairos.json ». » et
    **« Délier »** (le fichier reste tel quel, Kairos cesse d'y écrire).
  - Écriture 2 secondes après le dernier changement, et aussitôt quand
    l'application passe en arrière-plan ou se ferme.
- **Partout (bureau, Android, web) : modification faite ailleurs.** Si le
  fichier lié n'est plus celui que cet appareil a écrit en dernier (un autre
  appareil l'a changé), Kairos **n'écrit plus** et un bandeau (vue Jour et
  carte Données) dit : « Le fichier lié « … » a été modifié ailleurs (un autre
  appareil ?). Kairos n'y écrit plus tant que tu n'as pas choisi. », avec
  **« Écraser »** (y écrire les données de cet appareil), **« L'importer »**
  (ses données remplacent celles de l'appareil, avec confirmation et
  sauvegarde, comme « Importer ») et **« Délier »**.
- **Échec d'écriture** (dossier disparu, accès retiré, carte mémoire
  retirée) : bandeau d'erreur « Kairos n'arrive plus à écrire dans « … » ; tes
  données sont à l'abri dans Kairos. » avec « Réessayer » et « Délier » ;
  jamais de plantage. Une écriture réussie le fait disparaître.
- Le lien est **propre à l'appareil** : ni dans les réglages, ni dans
  l'export.
- Version web : « Délier » s'ajoute à « Ouvrir un fichier Kairos » quand un
  fichier est lié ; « Ouvrir un fichier Kairos » fait désormais la sauvegarde
  automatique d'« Importer » avant de remplacer les données.

### Critères de succès

- Exporter puis importer redonne **exactement** la même base, identifiants
  et réglages compris (`KairosRepositoryTest.exportThenImportGivesBackTheSameDatabase`).
- Aucun import sans sauvegarde préalable réussie.
- Un fichier refusé ne modifie rien.
- Version web : une tâche ajoutée survit au rechargement de la page (vérifié
  dans Chromium) ; un instantané illisible au démarrage donne un écran
  d'erreur, jamais une base vide qui l'écraserait.

- Fichier lié (`LinkedFileSyncTest`, `DesktopLinkedFileTest`) : une seule
  écriture 2 s après une rafale de changements, écriture aussitôt à la mise en
  arrière-plan, écriture atomique sur le bureau (jamais de fichier à moitié
  écrit), fichier écrit = export valide qui redonne la même base, fichier
  modifié ailleurs jamais écrasé sans choix, échec d'écriture signalé puis
  levé par une écriture réussie, lien retrouvé au lancement ; carte Données
  et bandeau (`LinkedFileUiTest`). Android vérifié à la main (dossier
  Syncthing et fournisseur de documents Nextcloud ; accès retiré → bandeau).
  Version web : démarrage et carte Données vérifiés dans Chromium ; la garde et
  « Délier » passent par les sélecteurs du navigateur, que Playwright ne pilote
  pas : à vérifier à la main dans Edge ou Chrome.

### Hors périmètre

- Recharger tout seul le fichier quand un autre appareil l'a changé, résoudre
  un conflit, fusionner : synchronisation (#88, 4.0.0). Décidée le 2026-10-26, à implémenter : `synchronisation.md`.
- Fusion de deux bases (sans synchronisation, une fusion créerait des
  doublons indétectables) : l'import remplace.
- Chiffrement des exports.

## 2. Solution technique

### Format (`ExportCodec`)

JSON UTF-8 indenté :

```json
{
  "format": "kairos-export",
  "formatVersion": 2,
  "appVersion": "3.0.0-alpha.2",
  "exportedAt": "2026-09-28T07:00:00Z",
  "settings": { "...": "réglages (Settings), tous les champs" },
  "tasks": [ { "id": 1, "title": "…", "priority": 0, "fibonacciPoints": 3, "deadline": "2026-09-30", "status": "todo", "createdAt": "…", "updatedAt": "…" } ],
  "timeBlocks": [ { "id": 1, "title": "…", "start": "2026-09-28T13:00", "end": "2026-09-28T14:00", "kind": "busy", "recurrence": "" } ],
  "dependencies": [ { "id": 1, "taskId": 8, "blockerId": 7, "createdAt": "…" } ],
  "workSessions": [ { "id": 1, "taskId": 1, "startedAt": "…", "endedAt": null, "createdAt": "…" } ],
  "notes": [ { "id": 1, "body": "…", "status": "open", "convertedTaskId": null } ]
}
```

- Champs des tâches : ceux du modèle (`modele-donnees.md`), en camelCase ;
  valeurs `null` omises ; codes de statut et de récurrence de la base.
- **Série d'une tâche récurrente** (3.4, `durees-apprises.md`) : `seriesId` n'est écrit que s'il est non nul, **sans changer la
  version du format** (un champ de plus que les anciens lecteurs ignorent) ; une tâche sans série s'exporte comme avant
  (`LearnedDurationsRepositoryTest.theSeriesSurvivesAnExportAndIsAbsentWhenThereIsNone`). Même règle pour `settings.learning`.
- **Créneaux** (3.8.0) : `recurrence` peut valoir `monthly_day` ou `monthly_weekday` (`recurrence.md`) ; une version
  antérieure lit un code inconnu comme `""` (le créneau devient ponctuel à sa date d'origine). `blockExceptions`
  (`[ { "id", "blockId", "date": "2026-09-29", "skipped": true, "title", "start": "12:30", "end": "13:30", "createdAt" } ]`,
  `skipped` écrit seulement s'il est vrai, titre et heures seulement s'ils remplacent) n'est écrit **que s'il y en a**,
  sans changer la version du format ni compter comme donnée d'équipe : l'export d'une base qui n'en a jamais posé
  ne change pas (`KairosRepositoryTest.deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem`) ; une version
  antérieure l'ignore. À l'import : identifiants uniques, une exception par `(blockId, date)`, `blockId` d'un créneau
  du fichier, fin après début quand les deux sont là, sinon « illisible ».
- **Calendriers importés** (3.10.0, `connexions-externes.md` § Stockage) : `calendarSources`
  (`[ { "id", "name", "hideTitles": true, "importedAt", "createdAt" } ]`, `hideTitles` seulement s'il est vrai),
  `dayMarks` (`[ { "id", "title", "start": "2026-10-28", "end": "2026-10-30", "sourceId", "sourceUid", "createdAt" } ]`)
  et, par créneau, `sourceId` et `sourceUid`, écrits **seulement s'il y en a** : sans changer la version du format ni
  compter comme donnée d'équipe ; l'export d'une base sans calendrier ne change pas. Une version antérieure les ignore
  (créneaux importés lus comme manuels, aucun badge). À l'import : identifiants uniques, nom non vide, `sourceId` d'un
  calendrier du fichier, journée dans l'ordre, sinon « illisible ».
- **Version 2** (espace Équipe, `equipe.md` § Export) : `members` (membres
  d'équipe), `absences` (ajouté au jalon E2), `teamEvents` (journal, jalon
  E3), `teamScenarios` (jalon E5) — un lecteur 2 plus ancien ignore les champs qu'il ne connaît pas —
  et, sur les tâches, `progressPercent`, `startedOn`, `teamUid` (E3), `space` (`"team"`, écrit seulement pour une
  tâche d'équipe), `assigneeId`, `origin`, `originRemoved` (écrit
  seulement s'il est vrai) et `reportedMinutes` (E6), `teamExcluded` (écrit
  seulement s'il est vrai, `equipe-moi.md`) ; sur les membres,
  `lastReportAt` (E6).
- **Une base sans donnée d'équipe** (`Workspaces.hasTeamData` faux : ni
  membre, ni absence, ni événement, ni tâche d'équipe, ni tâche assignée,
  ni tâche reçue d'un manager, ni tâche perso exclue de l'équipe) s'exporte en
  `formatVersion` 1, sans aucun champ d'équipe : c'est l'export de la
  3.0.0, octet pour octet hors `appVersion` et `exportedAt`, prouvé contre un
  fichier de référence produit avant le chantier
  (`TeamIsolationTest.soloExportIsByteIdentical`). Pour cela, les champs
  d'équipe du JSON sont nullables à défaut `null` : `encodeDefaults = true`
  les écrirait sinon. Un `settings.team` non nul (espace activé puis vidé)
  reste écrit dans les réglages d'un export 1 ; une version antérieure
  l'ignore (champ inconnu).
- `decode` (en deux temps, marque d'ordre des octets UTF-8 ignorée — un
  fichier du Bloc-notes de Windows en commence ; `TeamExchangeCodec.detect` et les
  décodeurs de paquet et de rapport l'ignorent aussi, sinon l'aiguillage du bouton
  « Importer » refusait le fichier avant que `ExportCodec` ne le lise) :
  - pas du JSON, pas un objet, `format` différent ou `formatVersion` absente
    → `ImportException(NOT_AN_EXPORT)` ;
  - `formatVersion` supérieure à 2 → `TOO_NEW` (1 et 2 sont lues ; un
    export 1 donne une base sans donnée d'équipe) ;
  - un export Kairos dont un champ est mal typé ou à valeur invalide →
    `CORRUPTED`. (Une liste absente reste vide, `tasks` comprise : le
    remplacement est confirmé et précédé d'une sauvegarde.) Valeur invalide :
    date ou instant illisible, réglages refusés par `SettingsForm.validate`
    (mêmes bornes et règles que le formulaire), identifiant en double dans
    une table, titre de plus de 10 000 caractères, priorité hors 0-2, points
    hors de l'échelle de Fibonacci, durée négative, dépendance vers une tâche
    absente ou vers elle-même, créneau qui finit avant de commencer ;
  - champs inconnus ignorés, champs absents à leur valeur par défaut
    (réglages compris).
  Rien n'est jamais importé en partie. Ces contrôles datent du test
  exploratoire du 2026-10-11 : un fichier aux réglages hors bornes (heure
  25) était accepté puis faisait planter les vues Jour et Semaine ; des
  identifiants en double faisaient quitter l'application sur une erreur
  SQLite brute ; un titre de 2 Mo gelait l'interface.

### Carte Données (`SettingsScreen.DataCard`)

- Exporter : `ExportCodec.encode(snapshot, version, maintenant)` puis
  `FileService.saveText(nom)` ; message « Export enregistré. » si écrit ;
  erreur de fichier → « Le fichier n'a pas pu être lu ou écrit. ».
- Importer : `FileService.openText()` → `readImportedFile`, qui aiguille
  par `TeamExchangeCodec.detect` (un fichier iCalendar, reconnu avant par `IcsParser.isCalendar`, ouvre l'aperçu
  d'import de calendrier avec sauvegarde `avant-import-calendrier-…`, `connexions-externes.md`) : un paquet ou un rapport ouvre son aperçu
  et sa fusion ciblée (`equipe-echanges.md` § Interface) ; un export suit
  le chemin ci-dessous. `decode` (message selon la raison
  en cas d'échec) → `AlertDialog` de confirmation → `BackupStore.save(
  "avant-import-AAAAMMJJ-HHMM.json", export actuel)` ; si la sauvegarde
  échoue, message d'erreur et **pas d'import** ; sinon `replaceAll` puis
  message « Import terminé : n tâches. ». Sauvegarde puis remplacement :
  `replaceWithBackup` (`ui/app/Replace.kt`) ; horodatage des noms : `fileStamp`.
- Messages : snackbar de la coquille (`LocalMessages`).

### Fichiers et sauvegardes par plateforme

| Plateforme | `FileService` | `BackupStore` |
|---|---|---|
| Bureau | `FileDialog` AWT (enregistrer, ouvrir) | `<données>/backups/`, 10 plus récentes |
| Android | `ActivityResultContracts.CreateDocument` (un par type : JSON, CSV, PNG) et `OpenDocument` (Storage Access Framework, aucune permission) | `files/backups/`, 10 plus récentes |
| Web | téléchargement (`Blob` + lien, type d'après l'extension) et `<input type=file>` | OPFS `backups/`, 10 plus récentes |

`saveText` déduit le type du fichier de l'extension du nom (`mimeOf` : `.csv` → `text/csv`, `.png` → `image/png`,
sinon `application/json`). `saveImage` (PNG) sert à l'export des statistiques, distinct de l'export de données : il
n'écrit qu'un fichier de lecture, jamais relu par Kairos (`statistiques.md` § Export).

### Version web

- **Base** : SQLite (sql.js 1.14.2, MIT) **en mémoire** dans un worker
  (`kairos-sqljs.worker.js`, protocole du worker SQLDelight :
  `exec`, `begin_transaction`, `end_transaction`, `rollback_transaction`),
  piloté par `WebWorkerDriver`. `webpack.config.d/sqljs.js` copie
  `sql-wasm.js` et `sql-wasm.wasm` à côté de `kairos.js` ; le worker les
  charge par chemin relatif (`importScripts`, `locateFile`).
- **Persistance** (`WebServices`, `WebLinkedStorage`) : à chaque nouvel état
  du dépôt, l'export JSON est écrit dans l'OPFS (`kairos.json`) puis, si un
  fichier est lié et autorisé, dans ce fichier. Échec d'écriture OPFS →
  état `SaveFailed` (bandeau d'erreur) ; fichier lié non autorisé →
  `NeedsPermission(restore = false)`.
- **Démarrage** : `navigator.storage.persist()` (limite l'éviction) ; lecture
  de l'OPFS ; si vide et fichier lié autorisé, lecture du fichier ;
  instantané décodé (illisible → échec d'ouverture, écran d'erreur) ; base
  ouverte ; `replaceAll(instantané ou exemples)`. Si l'OPFS est vide et que le
  fichier lié attend une autorisation : état `NeedsPermission(restore =
  true)` et **aucune écriture** tant que l'utilisateur n'a pas autorisé
  (sinon les exemples écraseraient la copie du navigateur, puis le fichier).
- **Fichier lié** (`kairos-web.js`, API File System Access, Edge et Chrome) :
  poignée gardée dans IndexedDB (base `kairos`, magasin `handles`, clé
  `linked-file`) ; accord `readwrite` vérifié (`queryPermission`) ou demandé
  (`requestPermission`, sur clic).
  - `linkCreate` : `showSaveFilePicker` (nom proposé `kairos.json`), écriture,
    poignée gardée ;
  - `linkOpen` : `showOpenFilePicker`, lecture, poignée **en attente** ;
    `linkAdopt` la garde après confirmation (avec l'accord d'écriture), puis
    les données du fichier remplacent celles du navigateur ;
  - `linkAuthorize` : redemande l'accord ; en cas de restauration, relit le
    fichier et le recharge dans la base.
- États (`LinkedFileState`) : `Unsupported` (pas d'API), `NotLinked`,
  `Linked(nom)`, `NeedsPermission(nom, restore)`, `SaveFailed(détail)`.
  `StorageBanner` : vue Jour, sauf `Linked` ; carte Données, toujours.
  Bandeau neutre (`surfaceContainerHigh`), sauf `SaveFailed` en conteneur
  d'erreur (charte § Bannières).

### Fichier lié du bureau et d'Android (3.10.0, issue #137)

- `LinkedStorage` (`ui/app`) devient le contrat commun aux trois plateformes :
  `state`, `createFile`, `openFile` / `adoptOpened`, `authorize` (web),
  `unlink`, `overwrite` (écrire malgré une modification faite ailleurs),
  `readLinked` (contenu actuel du fichier, pour « L'importer ») puis
  `acknowledge(texte)` (le contenu importé devient la référence), `retry` ;
  `browser` (vrai sur le web : bandeau d'incitation et textes du
  navigateur). Nouveaux états : `Changed(nom)`, `WriteFailed(nom)`.
- `LinkedFileSync` (`ui/app`, commun au bureau et à Android) met en œuvre
  `LinkedStorage` sur un `LinkedFileIo` de plateforme (choisir un fichier à
  créer ou à ouvrir, lire, écrire, nom affiché, lien gardé) :
  - `changed(instantané)` (branché sur `onChanged` du dépôt) garde le dernier
    instantané et programme l'écriture `DEBOUNCE` = 2 s plus tard (une
    rafale = une écriture) ; `flush()` écrit tout de suite ce qui attend
    (arrière-plan, fermeture) ;
  - avant d'écrire, le fichier est relu : si son **empreinte**
    (`LinkHash.of` : longueur et FNV-1a 64 bits du texte) diffère de celle du
    dernier texte écrit ou adopté, état `Changed`, rien n'est écrit ; lecture
    ou écriture impossible → `WriteFailed` (l'écriture suivante réessaie) ;
  - le lien (`SavedLink` : cible, nom, empreinte) est gardé par la plateforme
    à chaque écriture réussie et relu au lancement.
- Bureau (`DesktopLinkedFile`) : `FileDialog` ; écriture dans un fichier
  temporaire du même dossier puis `Files.move(ATOMIC_MOVE)` (repli
  `REPLACE_EXISTING` si le système de fichiers refuse l'atomique) ; lien dans
  `linked-file.json` du dossier des données ; `flush` à la fermeture de la
  fenêtre.
- Android (`AndroidLinkedFile`) : `CreateDocument` / `OpenDocument` du Storage
  Access Framework et `takePersistableUriPermission` (lecture et écriture) :
  **aucune permission** dans le manifeste ; écriture en mode `"wt"` (certains
  fournisseurs ignorent la troncature en `"w"` et laissent des octets en
  trop) ; nom affiché par `OpenableColumns.DISPLAY_NAME` ; lien dans les
  préférences de l'application ; `flush` dans `onStop`.
- Web (`WebLinkedStorage`) : même garde (empreinte du dernier texte écrit,
  gardée dans IndexedDB avec la poignée, `linkRead` avant `linkWrite`),
  `unlink` oublie la poignée (`linkForget`). Écriture à chaque changement,
  sans délai, comme avant.
- Interface : `StorageBanner` sert les trois plateformes. Carte Données :
  toujours (lié, pas lié, à résoudre). Vue Jour : sur le web comme avant ;
  sur le bureau et Android seulement `Changed` et `WriteFailed`. « Ouvrir un
  fichier Kairos » et « L'importer » passent par `replaceWithBackup`.

### Décisions et pièges tracés

- **Fichier lié = copie au format d'export, la base reste la référence**
  (#137) : synchroniser une base SQLite vivante (`-wal`, `-shm`) par
  Syncthing ou OneDrive la corrompt ou crée des copies en conflit ; un export
  écrit d'un bloc ne risque rien.
- **Empreinte du contenu plutôt que date de modification** : les fournisseurs
  de documents d'Android ne donnent pas une date fiable, et une écriture de
  cet appareil ne doit jamais se prendre pour une modification étrangère.
- **Rien n'est écrit sur une modification faite ailleurs** sans choix
  (propriétaire, 2026-10-25) : deux appareils liés au même fichier
  s'écraseraient sinon l'un l'autre sans un mot. Recharger tout seul, c'est
  de la synchronisation (#88).
- **Pas de bandeau d'incitation sur le bureau ni sur Android** (propriétaire,
  2026-10-25) : les données y sont déjà dans un fichier.

- **Instantané JSON plutôt que SQLite persistant dans l'OPFS** : le VFS OPFS
  de SQLite exige `SharedArrayBuffer`, donc des en-têtes COOP/COEP que GitHub
  Pages ne sait pas envoyer. L'instantané réutilise le format d'export :
  le fichier lié est un export valide, qui s'ouvre aussi sur le bureau et
  sur Android. Coût : réécrire tout le fichier à chaque modification,
  négligeable pour un outil personnel.
- **Worker sql.js maison** : celui de SQLDelight
  (`@cashapp/sqldelight-sqljs-worker`) charge `/sql-wasm.wasm` à la racine du
  site, introuvable sous `/Kairos/app/`. Même protocole, chemins relatifs.
- **Pas d'écriture pendant une restauration en attente** : écrire les
  exemples dans l'OPFS rendrait la restauration impossible au rechargement
  suivant.
- **L'import remplace, jamais ne fusionne** (plan § 5.2).
