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
`MainActivity.kt`)._

État : **jalon M1** ; la migration d'une base Kairos 2 (jalon M5) est décrite
par `migration-2x.md`, ses boutons vivent dans la même carte Données.

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

- Réglages → carte **Données** :
  - le texte rappelle que les données restent sur l'appareil ; sur le
    bureau, le dossier des données est affiché ;
  - **Exporter** : enregistre un fichier `kairos-export-AAAAMMJJ-HHMM.json`
    (boîte d'enregistrement du système ; téléchargement dans le navigateur) ;
  - **Importer** : choisir un fichier, confirmer « Remplacer toutes les
    données ? ». L'import **remplace** tout (tâches, créneaux, notes,
    réglages) ; une **sauvegarde automatique** des données actuelles est
    faite juste avant, et sans elle l'import n'a pas lieu. Un message confirme
    le nombre de tâches importées.
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

### Critères de succès

- Exporter puis importer redonne **exactement** la même base, identifiants
  et réglages compris (`KairosRepositoryTest.exportThenImportGivesBackTheSameDatabase`).
- Aucun import sans sauvegarde préalable réussie.
- Un fichier refusé ne modifie rien.
- Version web : une tâche ajoutée survit au rechargement de la page (vérifié
  dans Chromium) ; un instantané illisible au démarrage donne un écran
  d'erreur, jamais une base vide qui l'écraserait.

### Hors périmètre

- Fusion de deux bases (sans synchronisation, une fusion créerait des
  doublons indétectables) : l'import remplace.
- Import d'une base SQLite Kairos 2 : `migration-2x.md`.
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
- **Version 2** (espace Équipe, `equipe.md` § Export) : `members` (membres
  d'équipe), `absences` (ajouté au jalon E2 : un lecteur 2 plus ancien
  l'ignore) et, sur les tâches, `space` (`"team"`, écrit seulement pour une
  tâche d'équipe) et `assigneeId`.
- **Une base sans donnée d'équipe** (`Workspaces.hasTeamData` faux : ni
  membre, ni absence, ni tâche d'équipe, ni tâche assignée) s'exporte en
  `formatVersion` 1, sans aucun champ d'équipe : c'est l'export de la
  3.0.0, octet pour octet hors `appVersion` et `exportedAt`, prouvé contre un
  fichier de référence produit avant le chantier
  (`TeamIsolationTest.soloExportIsByteIdentical`). Pour cela, les champs
  d'équipe du JSON sont nullables à défaut `null` : `encodeDefaults = true`
  les écrirait sinon. Un `settings.team` non nul (espace activé puis vidé)
  reste écrit dans les réglages d'un export 1 ; une version antérieure
  l'ignore (champ inconnu).
- `decode` :
  - JSON illisible ou `format` différent → `ImportException(NOT_AN_EXPORT)` ;
  - `formatVersion` supérieure à 2 → `TOO_NEW` (1 et 2 sont lues ; un
    export 1 donne une base sans donnée d'équipe) ;
  - valeur invalide (date, instant) → `CORRUPTED` ;
  - champs inconnus ignorés, champs absents à leur valeur par défaut
    (réglages compris).

### Carte Données (`SettingsScreen.DataCard`)

- Exporter : `ExportCodec.encode(snapshot, version, maintenant)` puis
  `FileService.saveText(nom)` ; message « Export enregistré. » si écrit ;
  erreur de fichier → « Le fichier n'a pas pu être lu ou écrit. ».
- Importer : `FileService.openText()` → `decode` (message selon la raison
  en cas d'échec) → `AlertDialog` de confirmation → `BackupStore.save(
  "avant-import-AAAAMMJJ-HHMM.json", export actuel)` ; si la sauvegarde
  échoue, message d'erreur et **pas d'import** ; sinon `replaceAll` puis
  message « Import terminé : n tâches. ». Sauvegarde puis remplacement :
  `replaceWithBackup` (`ui/app/Replace.kt`, partagé avec la migration
  Kairos 2) ; horodatage des noms : `fileStamp`.
- Messages : snackbar de la coquille (`LocalMessages`).

### Fichiers et sauvegardes par plateforme

| Plateforme | `FileService` | `BackupStore` |
|---|---|---|
| Bureau | `FileDialog` AWT (enregistrer, ouvrir) | `<données>/backups/`, 10 plus récentes |
| Android | `ActivityResultContracts.CreateDocument("application/json")` et `OpenDocument` (Storage Access Framework, aucune permission) | `files/backups/`, 10 plus récentes |
| Web | téléchargement (`Blob` + lien) et `<input type=file>` | OPFS `backups/`, 10 plus récentes |

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

### Décisions et pièges tracés

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
