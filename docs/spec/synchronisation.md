# Synchronisation par fichier : fusion automatique entre appareils

_Rôle : faire de la copie tenue à jour dans un fichier lié (3.10.0) le point de rencontre de plusieurs appareils : à chaque
lecture, Kairos **fusionne** le fichier avec ses données, sans compte, sans serveur, sans permission réseau, et sans jamais
perdre en silence ce qu'un appareil a fait. Fichiers couverts (à créer ou à modifier à l'implémentation) :
`kmp/core/.../core/sync/` (`Hlc.kt`, `SyncScope.kt`, `SyncRecords.kt`, `SyncData.kt`, `SyncReconcile.kt`, `SnapshotMerge.kt`,
`SyncRepair.kt`, `SyncSummary.kt`), `kmp/data/` (`12.sqm`, `Kairos.sq`, `KairosStore.kt`, `ExportCodec.kt`,
`KairosRepository.kt`), `kmp/ui/.../app/` (`LinkedFileSync.kt`, `LinkedStorage.kt`, `StorageBanner.kt`, `Replace.kt`),
`kmp/ui/.../settings/SettingsScreen.kt` (carte « Données »), `kmp/ui/.../sync/` (aperçu et « À revoir »),
`kmp/desktopApp/.../DesktopLinkedFile.kt`, `kmp/androidApp/.../AndroidLinkedFile.kt`, `kmp/webApp/.../WebServices.kt`
(`WebLinkedStorage`), `kmp/core/.../engine/DurationEstimator.kt` (ordre des échantillons). Tests (à créer) : `HlcTest`,
`SyncReconcileTest`, `SnapshotMergeTest`, `SnapshotMergePropertiesTest`, `SyncRepairTest`, `SyncScopeTest`,
`SyncCodecTest`, `SyncRepositoryTest`, `SyncMigrationTest`, `SyncIsolationTest`, `LinkedFileSyncTest` (étendu),
`SyncUiTest`, `LearnedDurationsSyncTest`._

État : **décidée le 2026-10-26, implémentée, livrée en 4.0.0** (issues #88 et #100, passe de QA du 2026-11-04 comprise), en PR empilées, une par étape :
1) cœur pur (`core/sync` : horloge, portée, enregistrements, réconciliation) ; 2) fusion et réparations ; 3) base, format 3 et
dépôt ; 4) cycle du fichier lié ; 5) interface ; 6) durées apprises partagées (#100) ; 7) passe de robustesse.
**Étape 1 livrée** : `Hlc.kt` (`Hlc`, `Stamp`, `Context`, `Drift`), `SyncScope.kt` (`RecordKind`, `RecordRef`, portée),
`SyncRecords.kt` (levée et reconstruction), `SyncData.kt` (`Marks`, `SyncData`), `SyncReconcile.kt` (`activate`,
`reconcile`) ; tests `HlcTest`, `SyncScopeTest`, `SyncRecordsTest`, `SyncReconcileTest`. **Étape 2 livrée** :
`SnapshotMerge.kt` (`merge`, `Synced`, `Rec`, `Conflict`, `KeptAfterDeletion`, `LocalChanges`, `MergeOutcome`), `SyncRepair.kt`,
`Canonical.kt`, `SyncReconcile.legacy` ; tests `SnapshotMergeTest`, `SnapshotMergePropertiesTest` (simulateur `SyncSim`),
`SyncRepairTest`. **Étape 3 livrée** : format 3 (`ExportCodec.encode(…, sync)`, `ExportCodec.decodeSynced`), schéma 13
(`12.sqm`, tables `sync_*`), `SyncStore.kt`, `SyncTypes.kt` (`SyncReviewItem`, `LastMerge`, `MergeResult`), dépôt
(`sync`, `syncReview`, `lastMerge`, `enableSync`, `renameDevice`, `merge`, `resolveReview`, `deleteKept`, `clearReview`,
`replaceAll(snapshot, uids)`) ; tests `SyncCodecTest`, `SyncRepositoryTest`, `SyncMigrationTest`. **Étape 4 livrée** : le
fichier lié fusionne (`LinkedMerge`, `LinkedFileSync`, `WebLinkedStorage`, `SyncSummary`), états `NeedsConfirmation` et
`Unreadable`, bandeau du premier rapprochement et du fichier illisible, choix « Fusionner » / « Remplacer » à l'ouverture,
relecture au premier plan et chaque minute ; dépôt `restoreSynced`, `hasOwnData`, `mergedSnapshot` ; tests `LinkedFileSyncTest`,
`LinkedFileUiTest`, `SyncSummaryTest` (voir « Précisions de l'implémentation (étape 4) »). **Étape 5 livrée** : interface
(`ui/sync/SyncUi.kt` : section de la carte Données, bandeau et dialogue « À revoir », « Fusionner un fichier Kairos… » et son
aperçu, message après une fusion), fiche ouverte pendant une fusion (`EditBase`), carte du guide `sync`, README ; tests
`SyncUiTest`, `SyncRepositoryTest.formOpenedDuringMergeKeepsMergedFields` (voir « Précisions de l'implémentation (étape 5) »).
**Étape 6 livrée** (#100) : ordre commun des occurrences (`SyncOrder`, `KairosSnapshot.sampleOrder`, `Sample.order`) pour les
durées apprises et le modèle ; test `LearnedDurationsSyncTest` (voir « Durées apprises partagées » § Implémentation).
**Étape 7 livrée** (robustesse) : « Remplacer » et « Importer » reprennent les identités du fichier, note d'horloge en avance
(`Drift.ahead`), « À revoir » gardée par le web, borne de taille du fichier lié, test de vitesse insensible à la charge (voir
« Précisions de l'implémentation (étape 7) »). La moitié « écriture » de #88, le fichier lié et sa garde
contre une modification faite ailleurs, est **implémentée en 3.10.0** (`export-import.md`) : cette spec la remplace.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Beaucoup d'utilisateurs ont deux appareils : le PC du bureau et le téléphone, ou le poste professionnel et le poste
personnel. Kairos garde ses données **sur l'appareil**, sans compte ni serveur (décision fondatrice, `modele-donnees.md`).
Depuis la 3.10.0, un appareil peut tenir à jour un fichier dans un dossier que Syncthing, Nextcloud, Dropbox ou OneDrive
synchronisent. Mais le fichier n'est qu'une **copie** : si l'autre appareil l'a changé, Kairos n'y écrit plus et propose
« Écraser » (la modification de l'autre appareil est perdue), « L'importer » (celle de cet appareil est perdue) ou
« Délier » (la synchronisation s'arrête). Dans tous les cas, l'utilisateur perd quelque chose ou renonce.

Il faut que les deux appareils **convergent** : ce qui est fait sur l'un se retrouve sur l'autre, ce qui est fait sur les deux
en même temps se **combine**, et ce qui est vraiment contradictoire est **montré**, jamais tranché en silence. Le transport
reste l'affaire de l'outil tiers : Kairos ne parle à personne, il lit et écrit un fichier.

### Comportement attendu (utilisateur)

#### Brancher un deuxième appareil

- Premier appareil : comme en 3.10.0, Réglages → Données → « Créer un fichier » dans le dossier synchronisé.
- Deuxième appareil : « Ouvrir un fichier Kairos » sur le même fichier. Si cet appareil a déjà des données, Kairos demande
  **« Fusionner »** (recommandé : garde tout ce qui existe des deux côtés), **« Remplacer »** (comme avant : les données de
  l'appareil sont remplacées après sauvegarde automatique) ou « Annuler ». Un appareil neuf, qui n'a que les exemples
  intacts de Kairos, reçoit les données du fichier sans que les exemples s'y ajoutent.
- Les appareils déjà liés en 3.10.0 continuent : le fichier passe au nouveau format à la première écriture. Si un autre
  appareil l'a modifié entre-temps, c'est le premier rapprochement ci-dessous.

#### Premier rapprochement : confirmé

La **première** fois qu'un appareil rencontre les données d'un autre, si la fusion doit **changer le fichier** (cet appareil a
des éléments que le fichier n'a pas) ou si des éléments **diffèrent**, Kairos **n'applique rien sans demander** et montre ce qui
va arriver : « Ce fichier a été écrit par un autre appareil. Fusionner garde tout ce qui existe des deux côtés : 12 tâches ici
seulement, 40 dans le fichier seulement, 3 différentes. » avec « Fusionner » et « Annuler » (annuler ne fusionne rien, n'écrit
rien, et un fichier qu'on venait d'ouvrir n'est pas lié). Les éléments « différents » sont ceux que les deux appareils ont
fait évoluer séparément avant de se connaître : ils se retrouvent dans « À revoir » après la fusion (voir plus bas). C'est
le seul moment où la fusion demande une confirmation avant d'agir ; l'appareil qui reçoit en retour les données du second, et
dont le fichier contient déjà tout, n'est pas interrogé.

#### Au quotidien : fusion silencieuse quand c'est sûr

- Chaque modification est écrite dans le fichier 2 secondes plus tard, comme en 3.10.0.
- Kairos **relit** le fichier quand il s'ouvre, quand l'application revient au premier plan, toutes les minutes tant qu'elle est
  visible, et juste avant chaque écriture. Si le fichier a changé, il est **fusionné** avec les données de
  l'appareil, puis réécrit seulement si la fusion apporte quelque chose de neuf au fichier.
- Une fusion **sûre** s'applique sans rien demander. C'est le cas quand elle n'a ajouté que des éléments, appliqué des
  modifications de champs que cet appareil n'avait pas touchés depuis la dernière rencontre, ou retiré des éléments supprimés
  ailleurs que cet appareil n'avait pas modifiés depuis. L'écran se met à jour tout seul, et un message d'une ligne dit ce
  qui a changé : « Fusionné avec « kairos.json » : 3 tâches ajoutées, 1 modifiée. » (« 1 supprimée » y figure aussi). Une
  fusion qui ne change rien n'affiche rien.
- La carte Données garde la trace de la dernière fusion : « Dernière fusion : 26/10 à 14:32, 3 tâches ajoutées, 1 modifiée. »
  (utile quand la fusion a eu lieu pendant que Kairos était en arrière-plan).
- **Pas de doublon** : une tâche récurrente terminée sur les deux appareils donne **une** occurrence suivante ; les occurrences
  du mois d'une série « le N du mois » ne sont pas créées en double ; un calendrier réimporté sur les deux appareils reste un
  seul calendrier.

#### Modifier la même chose sur deux appareils

- **Champs différents d'une même tâche** (l'un change la priorité, l'autre l'échéance) : les deux modifications sont gardées.
- **Même champ, même moment** (les deux appareils ont changé l'échéance de la même tâche avant d'avoir échangé) : c'est un
  **conflit réel**. Pour que les deux appareils affichent la même chose sans attendre, la valeur écrite **en dernier** est
  appliquée provisoirement, et l'autre est **gardée** à portée de main. Un bandeau (vue Jour, carte Données) dit
  « 2 conflits à revoir ».
- **« À revoir »** liste les conflits **tâche par tâche** (ou créneau par créneau…) : le titre, le champ en jeu
  (« Échéance »), les deux valeurs côte à côte avec l'appareil et l'heure de chacune, celle qui est appliquée marquée. Un
  geste par conflit : « Garder celle-ci » ou « Prendre l'autre ». Choisir l'autre valeur est une **modification comme les
  autres** : elle part dans le fichier et s'impose partout. Ne rien choisir est permis : les appareils restent d'accord, et
  l'autre valeur est gardée 30 jours. « Tout garder le plus récent » ferme la liste.
- **Supprimée d'un côté, modifiée de l'autre** : la suppression ne détruit jamais une modification qu'elle n'a pas vue.
  L'élément est **conservé** et signalé dans « À revoir » : « « Titre » avait été supprimée sur Android mais modifiée ici :
  conservée. » avec « Supprimer à nouveau ». Si la suppression avait vu la modification, l'élément disparaît partout.

#### Hors ligne, rattrapage

- Sans réseau, sans accès au dossier ou fichier momentanément inaccessible : Kairos fonctionne comme toujours, les données
  restent dans la base. Quand le fichier redevient lisible (l'outil tiers a fini sa synchronisation), la fusion rattrape tout
  d'un coup : plusieurs jours de modifications des deux côtés se combinent comme une seule fusion.
- Le fichier peut être **illisible un instant** (l'outil tiers le copie) : Kairos réessaie à la lecture suivante sans rien
  écrire. Au bout de trois essais : « Le fichier lié « … » est illisible. Kairos n'y écrit plus tant qu'il l'est. » avec
  « Réessayer », « Écraser » (y écrire les données de cet appareil) et « Délier ». Un fichier d'une version plus récente de
  Kairos : « Ce fichier vient d'une version plus récente de Kairos. Mets l'application à jour. »
- Si l'outil tiers a créé une **copie en conflit** (par exemple `kairos.sync-conflict-….json`) parce que deux appareils ont
  écrit en même temps, rien n'est perdu : chaque appareil a gardé ses données et les remet dans le fichier à sa prochaine
  fusion. La copie peut aussi se fusionner à la main.

#### Fusionner un fichier Kairos à la main

Carte Données → **« Fusionner un fichier Kairos… »** (toutes les plateformes, Firefox et Safari compris, où le fichier lié
n'existe pas) : choisir n'importe quel export (copie en conflit, ancienne sauvegarde, export d'un autre appareil). Kairos
montre toujours l'**aperçu** de la fusion (« +3 tâches, 1 modifiée, 2 conflits ») avant d'appliquer. Fusionner une sauvegarde
faite **depuis la synchronisation** ne ressuscite pas ce qui a été supprimé depuis ; une sauvegarde **d'avant** ne sait pas ce
qui a été supprimé : ses éléments reviennent, et l'aperçu les compte comme ajoutés. « Importer » reste le remplacement complet.

#### Chrono et sessions de travail

- Un chrono lancé sur un appareil apparaît **en cours** sur l'autre à la prochaine fusion ; on peut l'y arrêter. Le temps
  passé est la **réunion** des sessions des deux appareils (jamais comptées deux fois).
- Si deux chronos ont été lancés en même temps sur deux appareils, la fusion garde **le plus récent** en cours et arrête
  l'autre à l'instant où le plus récent a commencé : il n'y a toujours qu'un chrono à la fois (`temps-reel-chrono.md`).
- Les alertes du chrono (dépassement, chrono oublié, pause) valent sur tout appareil qui voit la session ouverte.

#### Ce qui est partagé, ce qui reste propre à l'appareil

- **Partagé** : les tâches (et sous-tâches, dépendances, récurrences), les créneaux et leurs occurrences modifiées, les notes,
  les sessions de travail, les calendriers importés (`.ics`) et leurs journées entières, et les **réglages de planification**
  (heures de travail, durées, pondérations, jours fériés, types de tâche, horaires par jour, durées apprises, noms des niveaux).
- **Propre à chaque appareil** : la langue, le thème et sa couleur, les rappels, la vérification des mises à jour, les cloches des
  alertes, ce que le guide a retenu, les calendriers du téléphone (jamais stockés), et l'espace Équipe.
- **L'espace Équipe n'est pas synchronisé** (voir « Hors périmètre ») : un utilisateur qui ne l'a pas activé n'en voit jamais
  la moindre trace, quel que soit le fichier qu'il fusionne.

### Critères de succès

Chacun est prouvé par un test (à créer ; nom entre parenthèses).

- **Convergence** : deux ou trois appareils qui font des modifications quelconques, dans un ordre quelconque, puis échangent
  leurs fichiers dans un ordre quelconque, finissent avec le même contenu (`SnapshotMergePropertiesTest.convergence`).
- **La fusion est commutative, associative et idempotente** à l'identifiant local près (`SnapshotMergePropertiesTest`).
- **Rien de perdu en silence** : toute valeur écrite qui n'est pas écrasée par une écriture plus récente est dans l'état
  fusionné ou dans « À revoir » ; aucun élément jamais supprimé ne disparaît ; une suppression ne détruit pas une modification
  qu'elle n'a pas vue (`SnapshotMergePropertiesTest.noSilentLoss`, `SnapshotMergeTest.deletionKeepsUnseenEdit`).
- **Une suppression qui a vu tout l'élément se propage** (`SnapshotMergeTest.deletionPropagates`) ; une sauvegarde d'un appareil
  synchronisé, fusionnée plus tard, ne ressuscite rien de ce qui a été supprimé depuis
  (`SnapshotMergeTest.oldBackupDoesNotResurrect`) ; une sauvegarde d'avant la synchronisation voit ses éléments comptés comme
  ajoutés dans l'aperçu (`SnapshotMergeTest.preSyncFileAddsEverything`).
- **Champs différents d'une même tâche : les deux gardés ; même champ concurrent : un conflit, l'autre valeur gardée**
  (`SnapshotMergeTest.differentFieldsMerge`, `SnapshotMergeTest.sameFieldConflict`).
- **Une écriture simultanée de deux appareils ne perd rien** : le fichier d'un appareil écrasé par l'autre est complété à la
  fusion suivante (`LinkedFileSyncTest.simultaneousWritesConverge`).
- **Pas de ping-pong** : deux appareils qui ont convergé n'écrivent plus rien (`LinkedFileSyncTest.noWriteWhenNothingNew`).
- **Horloge** : un appareil dont l'horloge retarde de plusieurs jours ou avance de plusieurs heures ne fait ni perdre ni
  écraser une modification postérieure à sa lecture du fichier (`HlcTest`, `SnapshotMergeTest.clockSkew`).
- **Pas de doublon** : occurrence suivante d'une récurrente terminée des deux côtés, occurrences « le N du mois », calendrier
  réimporté, exceptions d'occurrence, exemples (`SnapshotMergeTest.noDuplicates`, `SyncRepositoryTest`).
- **Chrono** : au plus une session ouverte après la fusion ; une session arrêtée des deux côtés garde la première fin
  (`SyncRepairTest`).
- **Premier rapprochement** confirmé quand le fichier doit changer ou que des éléments diffèrent, jamais appliqué avant, et
  silencieux pour l'appareil dont le fichier contient déjà tout ; fusion manuelle toujours précédée de l'aperçu (`SyncUiTest`,
  `LinkedFileSyncTest.firstContact`).
- **Compatibilité arrière** : les formats 1 et 2 se lisent et se fusionnent ; un export de base jamais synchronisée est
  identique à l'octet près à celui d'avant (`SyncCodecTest`, `SyncIsolationTest.exportWithoutSyncIsByteIdentical`,
  `TeamIsolationTest.soloExportIsByteIdentical` inchangé) ; une version 3.x refuse proprement un fichier de format 3.
- **Isolation du mode solo** : fusionner le fichier d'un utilisateur d'équipe n'introduit aucune donnée d'équipe dans une base
  solo (`SyncIsolationTest.noTeamDataEntersASoloBase`).
- **Sauvegarde avant fusion qui modifie ou supprime** ; sans elle, la fusion n'a pas lieu (`SyncRepositoryTest`).
- **Durées apprises identiques sur les deux appareils après fusion** (`LearnedDurationsSyncTest`, voir la dernière section).
- **Performances** : fusion de 5 000 tâches de chaque côté en moins de 1 s sur le bureau, hors du fil d'interface ;
  réconciliation après une écriture d'un seul élément au moins deux fois plus rapide qu'un rattrapage complet, et sous 200 ms,
  sur 5 000 tâches (`SnapshotMergeTest`, `SyncReconcileTest` ; seuil relatif depuis l'étape 7).
- Textes dans les six langues (`StringsParityTest`) ; 360 dp sans débordement, cibles de 48 dp (captures `sync-*` de
  l'auto-test). Vérifié à la main (ce que la CI ne voit pas) : deux appareils réels avec Syncthing, avec Nextcloud
  (fournisseur de documents d'Android), copie en conflit, et le web dans Edge ou Chrome (sélecteurs du navigateur, que
  Playwright ne pilote pas).

### Hors périmètre / différé

- **Un serveur Kairos, un compte, une connexion** : la décision « pas de permission réseau, aucun compte »
  (`connexions-externes.md`) tient. Le transport est celui de l'outil tiers.
- **Le temps réel** : la fusion a lieu à l'ouverture, au retour au premier plan, à la minute et avant l'écriture, en plus du
  délai de l'outil tiers. Pas de service en arrière-plan, pas de notification « un autre appareil a modifié ».
- **L'espace Équipe** : membres, absences, journal, scénarios, tâches d'équipe, tâches assignées, tâches **reçues** d'un manager,
  réglages d'équipe. Les justifications sont dans « Décisions » ; à reprendre avec « plusieurs équipes par base » (#118), qui
  touche la même identité d'équipe.
- **Le chiffrement du fichier** : le fichier est en clair, comme tout export. Qui veut le protéger chiffre le dossier
  synchronisé avec un outil du système, sans que Kairos le sache.
- **L'apprentissage des durées entre les membres d'une équipe** (#100) : voir la dernière section.
- **Un fichier par appareil** dans un dossier (alternative écartée), **la fusion de texte à la main** (description d'une
  tâche modifiée des deux côtés : on choisit une version, on ne les mêle pas), **l'historique des modifications** et
  l'annulation d'une fusion (la sauvegarde automatique en tient lieu).

## 2. Solution technique

### Vue d'ensemble

```
 appareil A                         fichier lié (outil tiers)                      appareil B
 base ── réconcilier ──► marques ──► kairos.json ◄── lire ─── fusion pure ◄── marques ◄── base
          (uid, horloge)            (format 3 : uid + marques)   (core/sync)
```

Trois idées, dans l'ordre :

1. **Identité stable** : chaque enregistrement a un `uid` qui ne change jamais et qui est le même sur tous les appareils.
2. **Marques** : chaque appareil date par une **horloge hybride** chaque champ qu'il modifie, et sait **jusqu'où** il a vu les
   écritures de chaque appareil (le *contexte*, `seen`).
3. **Fusion d'états**, pure : le fichier contient l'état complet d'un appareil ; fusionner deux états est une fonction de `core`
   déterministe, commutative, associative et idempotente. Chaque champ prend la valeur de la marque la plus récente (« dernier
   écrivain par champ ») ; une suppression se déduit du contexte (« l'autre a vu l'élément et ne l'a plus ») sans pierre
   tombale ; les écritures **simultanées** d'un même champ sont détectées par le contexte et listées dans « À revoir ».

### Portée de la synchronisation (`SyncScope`)

Pur. Une seule définition, utilisée par la réconciliation, la fusion et l'export.

| Enregistrement | Synchronisé quand | Champs locaux (jamais fusionnés) |
|---|---|---|
| `Task` | `space == PERSONAL` **et** `origin == null` | `assigneeId`, `progressPercent`, `startedOn`, `teamUid`, `origin`, `originRemoved`, `reportedMinutes`, `teamExcluded`, `noteOut`, `noteIn`, `proposedTo` |
| `TimeBlock` | toujours (créneaux saisis et importés) | — |
| `BlockException` | toujours | — |
| `TaskDependency` | les deux tâches sont synchronisées | — |
| `WorkSession` | sa tâche est synchronisée | — |
| `Note` | toujours | — |
| `CalendarSource`, `DayMark` | toujours (les créneaux du téléphone ne sont jamais stockés) | — |
| `Settings` | champs de planification (liste ci-dessous) | `language`, `themeColor`, `themeMode`, `textZoom`, `updateCheckEnabled`, `timerAlertSound`, `reminders`, `guide`, `team`, `subtaskChain` (#209) |
| `TeamMember`, `MemberAbsence`, `TeamEvent`, `TeamScenario`, tâche d'équipe, tâche reçue | **jamais** | tout |

- Un champ local d'un enregistrement **déjà présent** garde la valeur locale ; sur un enregistrement qui **arrive**, il prend sa
  valeur par défaut.
- Réglages : un **registre par champ de premier niveau** de `Settings` (`schedule`, `learning`, `levelNames` valent chacun un
  registre entier : pas de fusion à l'intérieur). L'ensemble des champs partagés est la constante `SyncScope.SHARED_SETTINGS`
  (tout `Settings` moins les champs locaux ci-dessus) ; un champ ajouté plus tard à `Settings` est **partagé par défaut** sauf
  inscription dans `LOCAL_SETTINGS` (test `SyncScopeTest.everySettingIsClassified` : chaque champ est classé).
- La portée ne dépend jamais de l'appareil : sur deux appareils, la même donnée est dedans ou dehors.

### Identité : `uid`

- `uid` : texte, 100 caractères au plus, **unique par genre d'enregistrement** (`RecordRef(kind, uid)`). Il ne change jamais ; il
  est indépendant de l'identifiant entier de la base, qui reste **propre à l'appareil** (AUTOINCREMENT) et que le fichier garde
  pour ses références internes.
- **Aléatoire** (`Uuid.random()` tiré dans `data`, comme `TeamMember.uid`, `core` reçoit le générateur en paramètre) : tâches
  saisies, créneaux saisis, notes, sessions de travail.
- **Déterministe** quand deux appareils peuvent créer « la même chose » chacun de leur côté, pour que la fusion les confonde :
  - occurrence suivante d'une récurrente terminée : `next:<uid de la tâche terminée>` ;
  - tâche née de la conversion d'une note : `conv:<uid de la note>` (QA du 2026-11-04 : la même note convertie sur deux
    appareils avant qu'ils se voient donnait deux tâches ; `SyncRepositoryTest.aNoteConvertedOnTwoDevicesGivesOneTask`) ;
  - occurrence « le N du mois » : `cal:<uid de la série>:<AAAA-MM>` ;
  - calendrier importé : `cal:<nom en minuscules, espaces retirés>` (l'identité d'un calendrier est son nom, `connexions-externes.md`) ;
    créneau importé : `ics:<uid du calendrier>:<UID iCalendar>:<début>` ; journée entière : `mark:<uid du calendrier>:<UID>:<début>` ;
  - exception d'occurrence : `exc:<uid du créneau>:<AAAA-MM-JJ>` (une par créneau et par jour, déjà une règle) ;
  - dépendance : `dep:<uid de la tâche>:<uid du bloqueur>` ;
  - exemples d'une base neuve : `example:<n>` (n = 1 à 10), avec la **marque d'origine** (voir plus bas), pour que les exemples de
    deux appareils soient les mêmes et ne ressuscitent jamais une suppression. Le dépôt retient dans `sync_state` (clé
    `examples`) les identifiants posés par `ExampleData` sur une base neuve de la 4.0 ; à l'activation, un exemple resté tel que
    posé (même empreinte) prend `example:<n>`, un exemple que l'utilisateur a modifié devient une donnée ordinaire (`legacy:…`) ;
    sur une base née avant la 4.0, les exemples sont des tâches ordinaires.
- **Migration, sans rétro-remplissage aléatoire** : à l'activation (premier lien, première fusion), chaque enregistrement qui n'a
  pas d'`uid` reçoit `legacy:<genre>:<id>:<createdAt en millisecondes>`. Deux appareils qui ont une origine commune (l'un a
  importé l'export de l'autre) ont gardé les mêmes `id` et `createdAt` : ils calculent **le même** `uid`, donc se reconnaissent
  sans aucun échange. Deux enregistrements créés séparément avec le même `id` ont des `createdAt` différents : jamais confondus.
  C'est pourquoi la migration n'est pas aléatoire (écarté : un UUID par appareil à la migration, qui dédoublerait toute la base
  au premier rapprochement).
- Références : dans le fichier elles restent des `id` (le fichier est cohérent en lui-même) ; la fusion les lit comme des `uid`
  (`parentId` → `uid` de la mère, `seriesId`, `convertedTaskId`, `blockId`, `taskId` et `blockerId` des dépendances et des
  sessions, `sourceId`), et les réécrit en `id` locaux à l'application du résultat. Une référence vers un enregistrement
  supprimé ou hors portée se lit **vide** (mère inconnue : la tâche devient de premier niveau, comme aujourd'hui), une
  dépendance ou une exception sans l'un de ses bouts disparaît. L'identité d'un enregistrement supprimé reste dans
  `sync_identity` tant qu'une session de travail le référence (les sessions d'une tâche supprimée restent, `modele-donnees.md`).

### Horloge hybride et marques (`Hlc`, `Stamp`)

- `Hlc(wall: Long, counter: Int)` : `wall` = millisecondes depuis 1970, `counter` ≥ 0. Ordre : `wall` puis `counter`.
  Texte `"<wall>.<counter>"`.
- `Stamp(hlc, device)` : ordre total `(wall, counter, device)` ; texte `"<wall>.<counter>@<device>"`. `device` : identité de
  l'installation, 12 caractères hexadécimaux tirés une fois à l'activation (`sync_state`). Deux identités réservées : `device = ""`
  est la **marque d'origine** (`Stamp.ORIGIN`, horloge 0) des exemples, **couverte par tout le monde** ; `device = "legacy"` est
  celle des fichiers d'avant la synchronisation, **couverte par personne**.
- **Pure, l'heure est un paramètre** :
  - `Hlc.tick(last, nowMillis)` : `wall' = max(last.wall, now)`, `counter' = 0` si `wall'` a avancé, sinon `last.counter + 1`
    (débordement de 65 535 : `wall' + 1`, `counter' = 0`) ;
  - `Hlc.receive(last, remote, nowMillis)` : même règle avec `max(last.wall, remote.wall, now)`.
  Une horloge locale en retard ne fait donc **jamais** produire une marque inférieure à une marque déjà vue : une modification
  faite après avoir lu le fichier l'emporte toujours sur ce qui y était.
- **Dérive entre appareils** : elle n'affecte que l'ordre de deux écritures **simultanées** (laquelle est « la plus récente »),
  jamais la causalité, et ces écritures-là sont de toute façon proposées dans « À revoir ». Un fichier qui contient une marque à
  plus de 24 h dans le futur de l'horloge locale est fusionné, avec la note « L'horloge d'un appareil semble en avance. » dans la
  carte Données. À plus de 30 jours, le fichier est refusé comme illisible (marque corrompue ou horloge absurde) : une marque
  énorme ferait avancer l'horloge de tous les appareils pour de bon. Constantes `DRIFT_WARN` et `DRIFT_MAX`.
- **Marques d'un enregistrement** (`Marks`) : `born` (marque de naissance, immuable, ordre minimal à la fusion), `base` (marque
  par défaut des champs qui n'ont pas la leur ; égale à `born` pour un enregistrement né après l'activation, à `updatedAt` pour un
  enregistrement migré) et, **seulement pour les champs modifiés**, `fields[nom] = Stamp`. Un champ sans marque propre a `base`.
- **Contexte** (`seen`) : `Map<device, Hlc>` : « j'ai incorporé toutes les écritures de cet appareil jusqu'à cette horloge ».
  Valide parce qu'on échange des **états complets** (jamais un morceau). `covers(seen, stamp)` = `stamp.device == ""` ou
  (`stamp.device != "legacy"` et `seen[stamp.device] >= stamp.hlc`). L'appareil met `seen[lui-même]` à jour à chaque `tick`. Au plus 32 appareils : au-delà, on
  retire du contexte les appareils qui ne portent plus aucune marque dans l'état (risque accepté : une résurrection, la
  direction sûre).

### Réconciliation locale (`SyncReconcile`)

Le dépôt a de nombreux chemins d'écriture mais **un seul entonnoir** (`KairosRepository.write`, qui relit toute la base après
chaque transaction) : aucun chemin n'a à dater quoi que ce soit. La réconciliation s'y branche et **déduit** les marques de la
différence.

```kotlin
fun reconcile(snapshot: KairosSnapshot, data: SyncData, previous: KairosSnapshot?, nowMillis: Long, newUid: () -> String): SyncData
```

Pur et idempotent (une seconde passe ne change rien). Pour chaque enregistrement synchronisé :
- sans `uid` (nouveau) : un `uid` (aléatoire ou déterministe), `born = base = tick` ;
- avec `uid` : pour chaque champ synchronisé, compare l'empreinte de sa valeur (64 bits, FNV-1a, comme `LinkHash`) à celle de la
  marque ; différente → nouvelle marque `tick` pour ce champ ; une seule marque `tick` par réconciliation ;
- un `uid` connu dont l'enregistrement n'existe plus : ses marques sont retirées (la suppression se lit à l'absence, voir la
  fusion) ;
- `previous` (l'état publié avant l'écriture) sert à ne hacher que ce qui a changé (égalité de valeur des modèles) : le coût
  d'une écriture reste proportionnel à ce qu'elle touche ; sans `previous` (ouverture), tout est vérifié, ce qui **rattrape** un
  arrêt entre la transaction et la réconciliation.
- **Activation** (migration) : si la clé `device` de `sync_state` est absente, la tirer, donner à chaque enregistrement un `uid` (`legacy:…` ou
  `example:…`), `born = Stamp(createdAt, device)`, `base = Stamp(updatedAt ?? createdAt, device)` ; pour les réglages
  `base = Stamp(0, device)` ; `seen[device]` = la plus grande de ces marques ; l'horloge démarre au plus grand de ces
  instants et de l'heure courante. Une marque `base` de migration est donc **celle de l'appareil** : deux copies d'une
  même origine, migrées séparément, ont des marques différentes pour des valeurs égales (aucun conflit tant que les valeurs
  sont égales) et se départagent par `updatedAt` quand elles diffèrent.
- `updatedAt` et `createdAt` ne sont **pas** des registres à marque : `createdAt` = le plus petit des deux, `updatedAt` = le plus
  grand (sinon deux modifications de champs différents se contrediraient toutes deux sur `updatedAt`).
- Un changement qui ne vient pas d'une écriture locale (une référence devenue vide parce que la mère a été supprimée ailleurs)
  est consigné comme une modification locale au passage suivant ; tous les appareils le font à l'identique, sans conflit.

#### Précisions de l'implémentation (étape 1)

- **Empreinte de chaque champ** : `Marks.hashes` garde l'empreinte de la valeur de **tous** les champs synchronisés, pas
  seulement des champs marqués : c'est elle qui permet à une passe sans `previous` (ouverture) de voir une modification.
  `Marks.stamps` ne porte que les champs modifiés depuis la naissance ou la migration. Un champ jamais haché (ajouté par une
  version suivante) reçoit son empreinte **sans** marque : sa valeur garde `base`.
- **Champs levés**, par genre (noms du JSON d'export, références en `uid`, `SyncRecords.REFERENCES`) : tâche (`title`,
  `description`, `priority`, `deadline`, `projectTag`, `status`, `estimatedMinutes`, `pinnedStart`, `parentId`, `recurrence`,
  `scheduledDate`, `recurrenceDayOfMonth`, `recurrenceDayOfWeek`, `recurrencePeriod`, `taskType`, `fibonacciPoints`,
  `manualTimeSpentMinutes`, `seriesId`) ; créneau (`title`, `start`, `end`, `kind`, `recurrence`, `sourceId`, `sourceUid`) ;
  exception (`blockId`, `date`, `skipped`, `title`, `start`, `end`) ; dépendance (`taskId`, `blockerId`) ; session (`taskId`,
  `startedAt`, `endedAt`) ; note (`body`, `status`, `convertedTaskId`) ; calendrier (`name`, `hideTitles`, `importedAt`) ;
  journée entière (`title`, `start`, `end`, `sourceId`, `sourceUid`). `createdAt` et `updatedAt` (tâche, note) sont les deux
  registres particuliers, hors des champs. Valeur : texte JSON compact (`SyncRecords.canonicalJson`, défauts et `null` écrits).
  Réglages : un registre par champ de premier niveau, même texte.
- **Portée** : une session de travail n'est dans la portée que si sa tâche l'est ; un créneau importé ou une journée entière,
  que si son calendrier est stocké. Conséquence assumée : supprimer une tâche fait sortir ses sessions de la portée ; elles
  restent dans la base de l'appareil qui a supprimé (comme aujourd'hui, `modele-donnees.md`) et disparaissent des autres, ce
  qui garde la portée identique partout.
- **Identités fantômes** (étape 2) : une tâche supprimée que référence encore une **référence molle** de la portée (`parentId`,
  `seriesId` d'une tâche, `convertedTaskId` d'une note) garde son `uid` dans `sync_identity`, **sans marques**. Sans cela, la
  même référence se lèverait `uid` sur un appareil et vide sur un autre, sous la même marque : la fusion dépendrait de l'ordre
  des échanges (trouvé par `SnapshotMergePropertiesTest`). À la fusion, une référence vers un `uid` inconnu ici reçoit un
  identifiant local **négatif** (jamais attribué par la base, `AUTOINCREMENT`, donc jamais repris par une tâche créée ensuite) :
  la référence molle pointe vers une tâche absente, ce qu'elle sait déjà faire (elle se lit vide). L'identité disparaît quand
  plus rien ne la référence.
- **`uid` déterministes** : occurrence « le N du mois » = `cal:<uid de la série>:<recurrencePeriod>` ; occurrence suivante =
  `next:<uid de la terminée>`, reconnue quand **une seule** tâche de la même série est passée à « Faite » dans la même écriture
  (comparaison avec `previous`) ; sans `previous`, ou si plusieurs candidates, l'`uid` est aléatoire. Calendrier :
  `cal:` + nom en minuscules **sans aucun blanc**. Un `uid` déterministe de plus de 100 caractères devient
  `<préfixe>:#<empreinte hexadécimale>` (`SyncReconcile.bounded`), lui aussi déterministe. Un `uid` déterministe déjà pris par
  un autre enregistrement du même genre retombe sur l'aléatoire. Les nouveaux reçoivent leur `uid` par identifiant croissant.
- **Activation** : `born = Stamp(createdAt, device)`, `base = Stamp(max(updatedAt ?? createdAt, createdAt), device)` ;
  l'horloge démarre au plus grand de ces instants et de l'heure courante, et `seen[device]` vaut cette horloge.
- **Coût d'une passe** : avec `previous`, seuls les enregistrements dont le modèle a changé, ou qui référencent un
  enregistrement entré ou sorti de la portée, sont levés et hachés : 5,6 ms pour une écriture sur 5 000 tâches (bureau, à
  chaud ; le test compare depuis l'étape 7 l'écriture au rattrapage complet, voir « Précisions (étape 7) »).

### Fusion (`SnapshotMerge`, pure, dans `core`)

```kotlin
data class Synced(val snapshot: KairosSnapshot, val data: SyncData)
fun merge(ours: Synced, theirs: Synced, nowMillis: Long): MergeOutcome
data class MergeOutcome(val merged: Synced, val changes: LocalChanges, val conflicts: List<Conflict>,
                        val kept: List<KeptAfterDeletion>, val repairs: List<Repair>, val writeNeeded: Boolean)
```

Aucune horloge, aucun hasard, aucune E/S. `ours` est supposé réconcilié ; `theirs` vient du fichier (ou d'un export ancien, voir
« Format »). Étapes :

1. **Lever** les deux côtés en `Map<RecordRef, Rec>` : `Rec = (born, base, fields: Map<nom, (valeur texte, Stamp)>)`, références
   en `uid`, enregistrements hors portée ignorés. Valeurs : le texte canonique du JSON d'export (`null` est une valeur).
2. **Contexte et horloge** : `seen = max point à point des deux contextes` ; horloge = `Hlc.receive(ours.clock, plus grande marque
   de theirs, now)`.
3. **Pour chaque `RecordRef` de l'union** :
   - présent des deux côtés : `born` = le plus petit ; chaque champ prend la valeur de la **marque la plus grande** (égalité de
     marque et de valeur : rien ; égalité de marque et valeurs différentes, possible seulement entre deux copies migrées :
     départage par la valeur texte la plus petite, déterministe) ; registres particuliers : `createdAt` min, `updatedAt` max,
     `WorkSession.endedAt` = la **plus petite fin non nulle** (deux appareils qui arrêtent la même session n'entrent pas en
     conflit, la première fin vaut ; `null` = en cours perd) ;
   - présent d'un côté seulement (« A ») : l'enregistrement **survit** s'il porte au moins une marque (`born`, `base` ou un champ)
     **non couverte** par le contexte de l'autre côté (« B ») ; sinon B l'a vu entièrement et l'a supprimé, il disparaît.
     Si `born` est couvert mais qu'un champ ne l'est pas, B l'avait supprimé mais A l'a modifié depuis sans le savoir : il
     **survit** et est consigné dans `kept` (« modifiée ici, supprimée ailleurs »).
4. **Conflit** (champ d'un enregistrement des deux côtés, valeurs différentes, marques différentes) : si `covers(theirs.seen,
   marque de ours)` ou `covers(ours.seen, marque de theirs)`, l'un des deux a **vu** l'autre et l'a dépassé : pas de conflit,
   la marque la plus grande gagne. Sinon les écritures sont **simultanées** : la plus grande marque l'emporte
   provisoirement et un `Conflict(ref, champ, valeur perdue, marque perdue, marque gagnante)` est produit.
5. **Réparations** (`SyncRepair`, déterministes, sur l'état fusionné ; voir plus bas).
6. **Abaisser** vers un `KairosSnapshot` : les enregistrements déjà connus gardent leur `id` local ; les nouveaux reçoivent des
   `id` à partir du plus grand `id` du genre, dans l'ordre des `uid` ; les références redeviennent des `id` ; les champs
   locaux se règlent comme dans « Portée ». Les enregistrements hors portée de `ours.snapshot` sont recopiés **sans changement**.
7. `changes` : par genre, `added`, `modified` (champs changés), `removed`, pour le résumé ; `writeNeeded` = l'état fusionné,
   restreint à la portée, diffère de celui de `theirs` (marques et contexte compris), `exportedAt`, `appVersion` et réglages
   locaux exclus.

**Propriétés** (prouvées par `SnapshotMergePropertiesTest`, voir plus bas), à l'`id` local près (comparaison sur la forme
canonique `Canonical.of(snapshot, data)` : enregistrements triés par `uid`, références en `uid`) :
- commutativité `merge(a, b) = merge(b, a)` ; associativité ; idempotence `merge(a, a) = a` et `merge(merge(a, b), b) =
  merge(a, b)` ;
- monotonie : fusionner ne retire du contexte aucune écriture ;
- `merge` ne touche à aucun enregistrement hors portée.

La règle de survie (étape 3) est celle des ensembles « ajout gagnant » à contexte causal (δ-CRDT), appliquée aux enregistrements ;
les registres de champs sont des « dernier écrivain gagnant ». Les propriétés ci-dessus sont des **tests**, pas des
affirmations : si l'associativité ne tenait pas sur un cas, la règle de survie serait à revoir avant tout code d'interface.

#### Précisions de l'implémentation (étape 2)

- **Les réparations ne font pas partie de la fusion** : `SnapshotMerge.merge` est la jointure pure (commutative, idempotente) ;
  `SyncRepair.repair` se calcule ensuite sur l'état installé et s'applique comme une écriture locale (une marque de cet
  appareil). Les mêmes valeurs étant calculées partout, les marques différentes ne font aucun conflit.
- **Après la fusion, le dépôt réconcilie sans état précédent** (passe complète) : c'est elle qui consigne, comme écriture
  locale identique sur tous les appareils, ce que l'abaissement a changé (dépendance tombée avec son bloqueur…).
- **Fichier d'avant la synchronisation** : `SyncReconcile.legacy` donne ses `uid` de migration et ses marques `legacy`. Comme
  personne ne couvre `legacy`, ses éléments ne pourraient plus jamais être supprimés ailleurs : la fusion **adopte** toute
  marque `legacy` de l'état fusionné comme une écriture locale de cette fusion (une seule marque fraîche, le contexte de
  l'appareil avance).
- **Valeurs égales** : la plus grande des deux marques ; **base** fusionnée = la plus petite des deux bases, chaque champ dont la
  marque effective en diffère garde la sienne. **`endedAt`** : la plus petite fin non nulle, avec la marque de la valeur retenue.
- **Associativité** : vraie sauf dans un cas, prouvé et accepté. Un élément supprimé d'un côté (la suppression avait vu
  certaines de ses valeurs) et conservé grâce à une modification non vue ailleurs revient avec la copie qui l'a gardé : selon
  l'ordre des échanges, ses autres champs peuvent être ceux d'avant la suppression. Corriger demanderait des pierres tombales
  (écartées). La **convergence** reste garantie dans tous les cas (`SnapshotMergePropertiesTest.convergence`) ;
  l'associativité est vérifiée sur chaque histoire sans élément conservé (88 sur 120).
- **Abaissement** : identifiants neufs au-delà du plus grand identifiant du genre **et** des identifiants connus de
  `sync_identity` ; une référence molle levée vide ici parce qu'elle vise **hors de la portée** (note convertie en tâche
  d'équipe) garde sa valeur locale tant que la fusion ne l'a pas changée ; une dépendance hors portée vers une tâche que la fusion
  retire tombe avec elle.
- **`writeNeeded` et le résumé** se calculent sur les enregistrements déjà levés (`Canonical.from`) : 5 000 tâches de chaque côté,
  toutes différentes, fusionnent en 0,2 s sur le bureau (borne du test : 1 s).
- `Conflict(ref, champ, valeur perdue, marque perdue, marque gagnante, valeur gardée)` ; `ref` nul pour un réglage partagé.
- Tests de propriétés : `SyncSim`, trois appareils, 120 histoires à graine fixe de 45 à 60 opérations (création, modification,
  suppression, récurrente terminée, chrono, dépendance, note, réglage) entremêlées d'échanges ; identifiants jamais réutilisés,
  comme la base. « Rien de perdu en silence » : chaque écriture est appliquée, dépassée par une écriture qui l'a vue (elle ou une
  valeur égale), supprimée avec son élément par une suppression qui l'a vue, ou listée dans un conflit.
- CI : `HlcTest`, `SyncRecordsTest` et `SyncRepairTest` tournent aussi dans Chrome (wasm) : horloge, empreintes et texte
  canonique y sont les mêmes au bit près.

### Réparations (`SyncRepair`)

Invariants que la saisie garantit et que deux appareils indépendants peuvent casser. Fonctions pures de l'état fusionné ; leur
résultat est appliqué comme une **modification locale ordinaire** (marque fraîche) et chaque appareil calcule la **même** : au
tour suivant les valeurs sont égales, donc sans conflit.

- **Une seule session ouverte** : parmi les sessions sans fin, la plus récente (`startedAt`, puis `uid`) reste ouverte ; chacune
  des autres est arrêtée au `startedAt` de la suivante (ce que fait `startTimer` aujourd'hui).
- **Mère en boucle** : si des `parentId` forment un cycle (A fait de X la fille de Y, B de Y la fille de X), la tâche de plus
  grand `uid` du cycle redevient de premier niveau.
- **Rien d'autre** : un cycle de dépendances est déjà neutralisé par le moteur (« un cycle ne masque aucune tâche »,
  `dependances.md`) ; une exception d'occurrence est unique par `(créneau, jour)` par construction de son `uid`.

### Format du fichier (`ExportCodec`, `formatVersion` 3)

- **Un export de base jamais synchronisée ne change pas d'un octet** (formats 1 et 2, `TeamIsolationTest.soloExportIsByteIdentical`).
  Dès qu'une installation est synchronisée (la clé `device` de `sync_state` existe), son export est de format **3** : un export 2 plus ce qui suit.
  Le fichier lié et « Exporter » écrivent le même format. `FORMAT_VERSION` passe à 3.
- Racine : `"sync": { "device", "label", "seen": { "<device>": "<hlc>" }, "labels": { "<device>": "<nom>" }, "settings":
  { "base": "<stamp>", "stamps": { "<champ>": "<stamp>" } } }`.
- Par enregistrement synchronisé : `"uid"`, `"born"` (marque), `"base"` (omis s'il vaut `born`) et `"stamps": { "<champ>":
  "<stamp>" }` (omis s'il est vide). Les noms de champs sont ceux du JSON d'export.
- **Déterministe** : enregistrements dans l'ordre des identifiants locaux (comme les formats 1 et 2), clés de `stamps` triées.
  Deux appareils convergés n'écrivent **pas** les mêmes octets (les identifiants locaux diffèrent) : c'est `writeNeeded`, calculé
  sur la forme canonique, qui évite le ping-pong, pas la comparaison des octets.
- **Identités fantômes** : `"sync": { …, "phantoms": { "<identifiant local>": "<uid>" } }` (omis s'il n'y en a pas), pour que les
  références molles vers une tâche supprimée se lisent ailleurs comme ici.
- **Tout le reste du fichier est inchangé** : champs des enregistrements (les `id` restent, les références aussi), réglages
  complets, données d'équipe de l'appareil qui écrit (`hasTeamData`, version 2). Le fichier lié reste donc **un export complet
  et valide** de l'appareil qui l'a écrit en dernier ; seule la portée est fusionnée, le reste est « écrit tel quel par le
  dernier écrivain » et ignoré à la lecture par les autres.
- **Lecture** : `ExportCodec.decode(text): KairosSnapshot` est inchangée (ignore `sync`, comme tout champ inconnu) ; la nouvelle
  `decodeSynced(text): SyncedExport(snapshot, data)` rend aussi `uid`, marques et contexte. Un fichier de format 1 ou 2 (aucun
  `uid`, aucun contexte) se fusionne en donnant à ses enregistrements les `uid` de migration (`legacy:…`) et des marques
  `Stamp(updatedAt ou createdAt, "legacy")`, `LEGACY_DEVICE = "legacy"` étant **un appareil que nul contexte ne couvre jamais** :
  ses éléments absents d'ici sont des **ajouts** (le fichier ne dit pas ce qui a été supprimé depuis), ses valeurs différentes
  des conflits. Un fichier de format 3, lui, porte son contexte : une ancienne sauvegarde d'un appareil synchronisé ne
  ressuscite que ce que le contexte d'ici n'a pas vu.
- Validations (`CORRUPTED`, rien n'est fusionné en partie) : `uid` uniques par genre et de 100 caractères au plus, marques bien
  formées, au plus 32 appareils, mêmes bornes qu'aujourd'hui (titres, priorités, points…), marque à plus de `DRIFT_MAX` dans le
  futur. `TOO_NEW` pour un format supérieur à 3.
- Une version 3.x lit un format 3 comme `TOO_NEW` (« version plus récente ») : elle refuse au lieu d'enlever en silence `uid`
  et marques puis de réécrire un fichier qui les aurait perdus. Sa garde de 3.10.0 voit le fichier changé et n'y écrit pas.

### Stockage (`data`, schéma 13)

Additif (`12.sqm`, marqueur `sync_state` de `KairosStore.MARKERS`, test `ExchangeMigrationTest.everyMigrationHasAMarker`; Numérotation : `11.sqm` (schéma 12) est déjà pris par la migration de la couleur par défaut (`apparence.md`) ; `connexions-en-ligne.md` et `synchronisation.md` visent toutes deux `12.sqm` (schéma 13) : celle qui est implémentée en second prend le numéro suivant.), vide
pour qui ne synchronise pas, **sans toucher à aucune table existante** (donc sans changer l'ordre positionnel des insertions) :

- `sync_state(key TEXT PRIMARY KEY, value TEXT NOT NULL)` : `device`, `label`, `clock`, `seen` (JSON), `lastMerge` (JSON : heure,
  résumé), `since`, `examples` (identifiants des exemples posés, voir « Identité »). **La clé `device` seule dit que la base est
  synchronisée.**
- `sync_identity(kind TEXT, local_id INTEGER, uid TEXT NOT NULL, PRIMARY KEY (kind, local_id))`, index unique `(kind, uid)`.
- `sync_mark(kind TEXT, uid TEXT, born TEXT, base TEXT, stamps TEXT, hashes TEXT, PRIMARY KEY (kind, uid))` : **une ligne par
  enregistrement** (implémentation, étape 3 ; la première version de cette spec prévoyait une ligne par champ, soit 100 000
  lignes pour 5 000 tâches). `stamps` : `champ=marque` séparés par des tabulations ; `hashes` : `champ=empreinte hexadécimale`
  pour chaque champ. Réécrite seulement quand ses marques changent (`SyncStore.save`, par différence).
- `sync_review(id INTEGER PRIMARY KEY AUTOINCREMENT, kind, uid, field, title, loser_value, loser_stamp, winner_value,
  winner_stamp, kept INTEGER, created_at)` : « À revoir », **locale** (ni fichier, ni export) ; `kind` et `uid` nuls pour un
  réglage partagé, `kept = 1` pour un élément conservé.
- `sync_state` porte aussi `clock`, `seen`, `labels` et `settings` (marques des réglages partagés, même codage qu'une ligne de
  `sync_mark`).

### Dépôt (`KairosRepository`)

- `write { … }` : après `readAll()`, `SyncReconcile.reconcile` (dans sa propre transaction ; un arrêt entre les deux est rattrapé
  à l'ouverture, voir plus haut). Aucun chemin d'écriture n'a à connaître les marques.
- `replaceAll(snapshot, uids: Map<…>? = null)` : l'import et les exemples réécrivent `sync_identity` d'après les `uid` du fichier
  (sinon `legacy:…`), conservent `device`, contexte et marques existantes, puis réconcilient : **importer = une série de
  modifications locales** (un élément absent du fichier est supprimé, un élément revenu est recréé avec une marque neuve, une
  valeur différente est une modification). Restaurer une vieille sauvegarde par « Importer » reste donc un vrai retour en
  arrière qui se propage, contrairement à « Fusionner ».
- `merge(theirs: Synced, mode)` (nouveau) : `Dispatchers.Default` pour `SnapshotMerge.merge` ; si le résultat modifie ou supprime
  quelque chose, **sauvegarde** `avant-fusion-AAAAMMJJ-HHMM.json` (`BackupStore`), sinon rien n'est écrit ; une seule
  transaction installe le résultat (`replaceAll` d'après l'état abaissé, plus `sync_*`) et enregistre `sync_review` ; puis
  `onChanged`, ce qui met à jour rappels, widgets, notifications du chrono et écran comme après toute écriture.
- Sauvegarde avant fusion : seulement si elle modifie un champ existant ou supprime ; **au plus une toutes les 30 minutes** ;
  rotation propre au préfixe (`avant-fusion-`, les 5 plus récentes) pour ne pas chasser les sauvegardes manuelles des 10
  conservées par `BackupStore` (`FolderBackups`, `keep = 10`, qui devient une rotation par préfixe). Sans sauvegarde réussie,
  la fusion n'a pas lieu (message « Sauvegarde impossible : rien n'a été fusionné. »), réessayée à la passe suivante.
- Entrée : `restoreConflict(reviewId, chooseOther)` : prendre l'autre valeur = écrire la valeur perdue comme une modification
  locale (marque fraîche) ; garder = classer l'item. Un item dont le champ a depuis reçu une autre marque que la gagnante est
  retiré tout seul. Rétention : 30 jours, 200 items (les plus vieux partent d'abord).

#### Précisions de l'implémentation (étape 3)

- **Entonnoir** : `write` garde l'état publié avant la transaction, relit la base, puis `reconcileAfter` réconcilie et
  enregistre la différence dans sa propre transaction. `load` (ouverture) réconcilie sans état précédent : rattrapage.
- **Import** : `replaceAll(snapshot, uids)` prend les `uid` du fichier (`decodeSynced(...).data.uids`) ou, à défaut, ceux de
  migration (`SyncReconcile.legacy`), puis réconcilie **sans état précédent** : un `uid` connu dont une valeur diffère reçoit une
  marque fraîche, un `uid` absent disparaît, un nouveau naît. Une base qui ne synchronise pas n'en garde aucune trace.
- **Fusion** : `merge(theirs, backup)` calcule `SnapshotMerge.merge` sur `Dispatchers.Default`, appelle `backup(état d'avant)`
  si la fusion modifie ou supprime (échec : `MergeResult.BackupFailed`, rien n'est écrit), installe dans **une** transaction
  (lignes, `sync_*`, « À revoir », `lastMerge` si quelque chose a changé ici), réconcilie sans état précédent, puis applique les
  réparations comme une écriture locale. Le nommage et la rotation des sauvegardes (`avant-fusion-…`) appartiennent à
  l'appelant (étape 4), qui a le `BackupStore`.
- **Activation** : `enableSync(label)` tire l'identité (12 caractères hexadécimaux d'un `Uuid` aléatoire), applique
  `SyncReconcile.activate` avec les exemples restés tels que posés (`sync_state.examples` : `genre|id|empreinte` de leurs champs,
  écrit par `open` sur une base neuve, `ExampleMarks`). Conséquence voulue de la marque d'origine : un appareil **sans** les
  exemples (base vidée, importée) les supprime chez l'autre à la première fusion, comme une suppression ordinaire ; deux
  appareils neufs les partagent sans doublon. **Confirmé par l'utilisateur le 2026-11-04.**
- **Première rencontre et réglages** : à l'activation, chaque appareil date ses réglages `Stamp(0, appareil)`. Deux appareils
  dont un réglage diffère donnent donc un **conflit** à leur première fusion (la plus grande identité l'emporte, l'autre valeur
  va dans « À revoir ») : c'est le comportement voulu du premier rapprochement, pas une préférence silencieuse. **Confirmé
  par l'utilisateur le 2026-11-04.**
- **« À revoir »** : `resolveReview(id, takeOther)` écrit la valeur écartée (`SyncEdit.setField`, ou `setSetting` pour un
  réglage) comme une modification locale ; `deleteKept` supprime à nouveau (`SyncEdit.remove`, une tâche emporte ses
  dépendances) ; `clearReview` vide la liste. À chaque relecture, un conflit dont le champ porte désormais une autre marque que
  la gagnante est retiré, comme un élément conservé qui n'existe plus ; rétention 30 jours et 200 éléments.
- **Tests d'isolation** : `SyncRepositoryTest.nothingIsStoredBeforeActivation` (aucune donnée `sync_*`, export en format 1 sans
  champ de synchronisation) et `noTeamDataEntersASoloBase` (fichier d'un manager : ses tâches perso arrivent, ni membre, ni tâche
  d'équipe, ni réglage d'équipe) ; `SyncCodecTest.anExportWithoutSyncIsUnchanged`. Ils remplacent le `SyncIsolationTest` prévu.
- Les tests de migration qui simulent un schéma antérieur retirent aussi les tables `sync_*` (sinon le marqueur du schéma 13
  les dispense de migrer) ; « format plus récent » se teste désormais avec le format 4.

### Fichier lié : cycle de synchronisation (`LinkedFileSync`)

`LinkedFileSync` (commun au bureau et à Android, même contrat `LinkedStorage` pour le web) garde `DEBOUNCE_MS = 2 000` et son
verrou. La garde `Changed` disparaît. Une **passe** (sous le verrou) :

1. lire le fichier ; échec → `WriteFailed` (comme aujourd'hui) ;
2. si son empreinte (`LinkHash`) égale celle du dernier texte écrit ou vu **et** qu'il n'y a rien à écrire : fin ;
3. décoder (`decodeSynced`) ; illisible → compteur d'échecs, état `Unreadable` au troisième, **rien n'est écrit** ; `TOO_NEW` :
   `Unreadable(TOO_NEW)` tout de suite ;
4. premier rapprochement (le contexte de cet appareil ne connaît aucun autre appareil et l'empreinte du fichier diffère de celle
   du dernier texte écrit) : la fusion est calculée **sans être appliquée** (`merge` est pure) ; si `writeNeeded` ou si
   `conflicts` n'est pas vide, état `NeedsConfirmation(aperçu)` et **arrêt** jusqu'à la réponse ; sinon la passe continue ;
5. `repository.merge(theirs)` ; résumé dans `lastMerge` ; si `outcome.writeNeeded` ou si des changements locaux attendent
   (`dirty`) : encoder l'état fusionné et écrire (atomique sur le bureau, `"wt"` sur Android, inchangé) ; mémoriser l'empreinte du texte
   **écrit** ou, s'il n'y a rien à écrire, du texte lu.

Déclencheurs : `changed(snapshot)` (écriture après `DEBOUNCE_MS`), `flush()` (arrière-plan, fermeture), `start` (ouverture),
reprise au premier plan (`MainActivity.onStart`, focus de la fenêtre), **minuterie `POLL_MS = 60 000`** tant que l'application
est visible (lecture et empreinte seulement ; on ne décode que si l'empreinte a changé), et `mergeNow()` (bouton).

- **Pas de ping-pong** : on n'écrit que si `writeNeeded` (étape 5). Deux appareils convergés, qui lisent l'un le fichier de
  l'autre, ne produisent plus aucune écriture, donc plus aucun déclenchement de l'outil tiers.
- **Écriture simultanée** : si l'autre appareil écrit entre notre lecture et notre écriture, l'outil tiers garde une version
  (parfois une copie en conflit) ; l'état qui n'y figure plus **n'est pas supprimé** à la fusion suivante (une absence ne
  supprime que si le contexte de l'autre a vu l'élément) : chacun remet ses données dans le fichier. C'est la raison
  principale de la fusion d'états (voir « Décisions »).
- États : `LinkedFileState.Changed` est **retiré** ; ajouts `NeedsConfirmation(name, summary)` et `Unreadable(name, reason)`
  (`reason` : `NOT_READABLE`, `TOO_NEW`, `CLOCK`) ; `WriteFailed`, `Linked`, `NotLinked`, `NeedsPermission`, `SaveFailed`,
  `Unsupported` inchangés. Un flux séparé `SyncStatus(lastMerge, reviewCount, clockNote)` porte le reste (étape 5).
- `LinkedStorage` : `overwrite()` ne sert plus qu'à `Unreadable` ; `readLinked()` et `acknowledge()` disparaissent (plus
  d'« L'importer ») ; nouveaux `mergeNow()` et `mergeFile()` (étape 5) ; `openFile()` rend le contenu et l'interface demande
  « Fusionner » ou « Remplacer ».
- Le web (`WebLinkedStorage`) suit le même contrat : `linkRead` avant `linkWrite` à chaque passe, la minuterie ne tourne que
  l'onglet visible ; l'instantané de l'OPFS (`kairos.json`) reste la persistance complète du navigateur et ne se fusionne
  jamais (seul le fichier lié se fusionne).
- Android : aucune permission de plus ; la fusion tourne au premier plan seulement (pas de `WorkManager`, pas d'alarme).

#### Précisions de l'implémentation (étape 4)

- **Une passe commune** (`ui/app/LinkedMerge.kt`) pour le bureau, Android et le web : elle reçoit le texte lu et rend
  `Done(write, seenHash, outcome)`, `Confirm(summary)`, `Retry`, `Unreadable(reason)` ou `BackupFailed` ; la lecture,
  l'écriture et le verrou restent à l'appelant (`LinkedFileSync`, `WebLinkedStorage`). Elle active la synchronisation au premier
  besoin (`enableSync`, puis le nom « Android · 3f9c » : nom de la plateforme et 4 caractères de l'identité ; « Windows »,
  « macOS », « Linux » sur le bureau, « Web » dans le navigateur).
- **Écrire ou non** : après une fusion, on compare la forme canonique de l'état d'ici (réparations comprises) à celle du
  fichier lu (`Canonical.of`), plutôt que `outcome.writeNeeded` seul, qui ignore les réparations faites après la fusion ; une
  écriture locale en attente (`dirty`) s'écrit toujours. Le signal `onChanged` qu'émet la fusion elle-même n'est pas une
  écriture locale : le dépôt garde l'état publié (`mergedSnapshot`) et le fichier lié l'ignore (comparaison d'identité).
- **Premier rapprochement** : le contexte de l'appareil ne connaît aucun autre appareil (identités réservées exclues) et le
  fichier a été écrit par une autre identité. **« Annuler » délie** le fichier, qui reste tel quel : un état « lié mais en
  attente » relancerait la question à chaque relecture. Pendant l'attente (et pendant `Unreadable`), ni les relectures ni les
  écritures programmées n'agissent ; les modifications locales attendent la réponse.
- **Ouvrir un fichier Kairos** : sur un appareil qui a des données à lui (`KairosRepository.hasOwnData` : un enregistrement de la
  portée qui n'est pas un exemple resté tel que posé), dialogue « Fusionner » (recommandé), « Remplacer » (comme l'import :
  sauvegarde `avant-import-…`, puis le fichier est la référence du lien) ou « Annuler » ; sur un appareil neuf, fusion directe.
  Choisir « Fusionner » vaut confirmation : pas de second aperçu.
- **Illisible** : trois lectures non décodables d'affilée → `Unreadable(NOT_READABLE)` ; `TOO_NEW` tout de suite ; une marque à
  plus de 30 jours dans le futur → `Unreadable(CLOCK)` (rien n'est fusionné). Le bandeau propose « Réessayer », « Écraser »
  (sauf `TOO_NEW` : écraser perdrait ce que cette version ne sait pas lire) et « Délier ».
- **Sauvegarde avant fusion** : `avant-fusion-<date>.json` dans les sauvegardes de l'appareil, **une par heure au plus**
  (sinon la rotation à 10 fichiers ne garderait que celles de fusions rapprochées) ; un échec de sauvegarde n'applique rien
  (`WriteFailed` sur le bureau et Android, `SaveFailed` sur le web).
- **Relecture** : `LinkedFileSync.setVisible` (bureau : fenêtre non réduite, `DesktopServices` la rend visible à l'ouverture ;
  Android : `onResume` / `onStop`) lance une passe puis une toutes les `POLL_MS` ; `resume()` au retour du focus de la fenêtre.
  Web : minuterie de `POLL_MS`, onglet visible seulement (`pageVisible`, `document.visibilityState`).
- **Web** : l'instantané de l'OPFS est écrit au format 3 **avec l'état de synchronisation du navigateur** et rechargé par
  `KairosRepository.restoreSynced` (identité, contexte, marques), sans quoi chaque rechargement de la page ferait un nouvel
  appareil. Un instantané d'avant la 4.0 (format 1 ou 2) se recharge comme avant (`replaceAll`). « À revoir » n'est pas dans
  l'instantané : depuis l'étape 7 elle a son propre fichier de l'OPFS. La restauration depuis le fichier lié (données du
  navigateur perdues) reste un remplacement, le fichier devenant la référence, avec ses identités (étape 7).
- **Textes** : `sync_first_contact` (sans pluriel : « Tâches ici seulement : 12 · … »), `sync_open_title`, `sync_open_body`,
  `sync_merge`, `sync_unreadable`, `sync_too_new`, `sync_clock`, six langues ; `linked_intro` dit la fusion ; retirés :
  `linked_changed`, `linked_import`, `linked_import_confirm_body`, `linked_open_confirm_body`, `web_link_open_confirm_body`.
- **Contrat** `LinkedStorage` : `adoptOpened(merge)`, `confirmMerge`, `cancelMerge`, `mergeNow`, `retry`, `overwrite` (fichier
  illisible seulement) ; `readLinked`, `acknowledge` et l'état `Changed` sont retirés.

### Interface (`ui`)

Voir la skill `kairos-ecran` ; couleurs par rôles, aucun `Color(0x…)`.

- **Carte Données** (lié) : « Kairos fusionne ce fichier avec tes données à chaque changement. » ; « Enregistré aussi dans le
  fichier « kairos.json ». » ; « Dernière fusion : … » ; note d'horloge éventuelle ; **« Fusionner maintenant »**,
  **« Fusionner un fichier Kairos… »**, **« À revoir (N) »** si N > 0, **« Délier »** ; le nom de l'appareil (« Android · 3f9c »,
  modifiable, 30 caractères) sert à dire d'où vient une valeur.
- **Bandeau** (`StorageBanner`, vue Jour) : remplace celui de la modification ailleurs. `NeedsConfirmation` : le texte du premier
  rapprochement avec « Fusionner » et « Annuler » (qui ne fusionne ni n'écrit rien) ; `Unreadable` : « Réessayer », « Écraser », « Délier » ; « N conflits à
  revoir » avec « Voir ». Neutre (`surfaceContainerHigh`) sauf les échecs (conteneur d'erreur, charte § Bannières).
- **Message de fusion** : snackbar (`LocalMessages`) seulement si quelque chose a changé et que l'application est au premier
  plan.
- **Aperçu** (« Ouvrir un fichier Kairos » non vide, « Fusionner un fichier Kairos… », premier rapprochement) : dialogue qui
  compte `+N tâches`, `N modifiées`, `N supprimées`, `N conflits`, `N autres éléments`, avec « Fusionner » (désactivé pendant
  l'écriture et dialogue maintenu ouvert jusqu'à la fin, piège de `connexions-externes.md`) et « Annuler ».
- **« À revoir »** : liste (une carte « outlined » par conflit, jamais d'ombre), valeurs côte à côte avec
  appareil et heure, la valeur appliquée marquée d'une icône et d'un texte (jamais la couleur seule), « Garder celle-ci » /
  « Prendre l'autre », « Supprimer à nouveau » pour un élément conservé, « Tout garder le plus récent ». Titre de
  l'enregistrement, nom de champ en langage de l'interface (réutilise les libellés des fiches).
- **Fiche ouverte pendant une fusion** : une fiche d'édition enregistre tous ses champs (`TaskEdit`) ; une fusion survenue
  pendant qu'elle est ouverte serait défaite à l'enregistrement. `updateTask` et les autres éditions complètes ne réécrivent donc
  que les champs que l'utilisateur a **changés dans la fiche** (comparés à l'état à l'ouverture) : test
  `SyncRepositoryTest.formOpenedDuringMergeKeepsMergedFields`.
- Textes : clés `sync_*` dans les six langues (`values*/strings.xml`, `StringsParityTest`), pluriels de `i18n.md`.
- Carte du guide `sync` (chapitre Organiser, `since = "4.0.0"`, ouvre Réglages → Données) et « Nouveau dans cette version » :
  **mettre à jour tous les appareils** avant de fusionner (`visite-guidee.md`).

#### Précisions de l'implémentation (étape 5)

- **Composants** (`ui/sync/SyncUi.kt`) : `SyncDataSection` (carte Données, sous le bandeau du fichier lié : « Kairos fusionne ce
  fichier… » si un fichier est lié, « Dernière fusion : 4/11 à 07:28. Ajoutés : 0 · modifiés : 1 · supprimés : 0. », « Cet
  appareil : Windows · 3f9c » avec « Renommer » (dialogue, 30 caractères), « Fusionner maintenant » si lié, « À revoir (N) » si
  N > 0) ; rien tant que la base ne synchronise pas et qu'aucun fichier n'est lié. `MergeFileButton` à côté d'« Exporter » et
  d'« Importer », sur toutes les plateformes. `SyncReviewBanner` dans la vue Jour, sous celui du fichier lié. `SyncMergeMessages`
  dans la coquille.
- **« À revoir »** : `AlertDialog` défilant, une `OutlinedCard` (12 dp, sans ombre) par élément : titre de l'enregistrement
  (« Réglages » pour un réglage), libellé du champ (ceux des fiches : titre, description, priorité, points, échéance, durée, projet,
  type, date programmée, récurrence, heure fixe ; un autre champ garde son nom), « Appliquée : 2 (Android · 8a21, 4/11 07:28) »
  marquée de l'icône `Check`, « Autre valeur : 1 (Windows · 3f9c, …) » de l'icône `History` (jamais la couleur seule). Les valeurs
  canoniques s'affichent sans guillemets, « — » pour rien. Le nom d'un appareil vient du contexte (`labels`), sinon ses 4
  premiers caractères. **Le dialogue reste ouvert quand la liste se vide** (« Rien à revoir. », « OK ») ; le bandeau, lui,
  disparaît : le dialogue appartient à l'écran (vue Jour, carte Données), pas au bandeau, et le bandeau n'est un élément de la
  liste de la vue Jour que s'il y a quelque chose à revoir (un élément vide garderait son espacement et décalerait la vue, donc
  les captures des magasins). « Tout garder le plus récent » vide la liste et ferme.
- **Fusion d'un fichier choisi** : `FileService.openText`, `decodeSynced`, activation au besoin (même nom que le fichier lié,
  `AppServices.deviceLabel`), aperçu calculé sans rien écrire (`SnapshotMerge.merge`) : « Tâches ajoutées : 1 · modifiées : 0 ·
  supprimées : 0. Autres éléments : 0. Conflits : 0. » (ou « Ce fichier n'apporte rien de neuf. »), « Fusionner » désactivé et
  dialogue maintenu pendant l'écriture ; sauvegarde `avant-fusion-…` (`saveBackup`) si la fusion modifie ou supprime. Un fichier
  dont une marque est à plus de 30 jours dans le futur donne « fichier illisible » (`file_error`), sans aperçu. Le fichier lié,
  s'il y en a un, reçoit le résultat : une fusion **faite hors d'une passe** est une modification locale pour lui (seule la fusion
  de sa propre passe est ignorée, drapeau `inPass`).
- **Message de fusion** : `SyncMergeMessages` lit `lastMerge` et montre « Fusion faite. Ajoutés : 3 · modifiés : 1 ·
  supprimés : 0. » à chaque nouvelle fusion qui a changé quelque chose ici, fichier lié ou fichier choisi (la valeur présente à
  l'ouverture ne déclenche rien). Il ne nomme pas le fichier : `lastMerge` ne dit pas d'où vient la fusion. Les comptes sont des
  enregistrements de toute sorte, comme ceux de la carte Données. Aucun pluriel : des libellés suivis d'un nombre (`i18n.md`).
- **Fiche ouverte pendant une fusion** : `updateTask(…, opened = EditBase(tâche, bloqueurs))` ; chaque champ de la tâche prend
  la valeur de la fiche si l'utilisateur l'a changée depuis l'ouverture, sinon la valeur actuelle ; les bloqueurs ajoutés ou
  retirés dans la fiche s'appliquent à l'ensemble actuel. Branché sur la fiche de la vue Jour (et la fiche d'une tâche reçue) ;
  la fiche d'une tâche d'équipe n'en a pas besoin (hors portée). Sans `opened`, tout s'écrit comme avant (création, autres
  chemins).
- **Carte du guide** `sync` (`FeatureCatalog`, chapitre Organiser, 4.0.0, toutes plateformes, icône `Sync` ajoutée à
  `make_icons.py`, cible Réglages → Données), donc dans « Nouveau dans cette version » ; le texte dit de mettre d'abord tous les
  appareils à jour. La carte `linked_file` dit désormais la fusion. README (anglais et français) : ligne « Nouveau en 4.0 »,
  puce « Synchronisation entre appareils », puce « Fichier lié » corrigée, ligne du tableau.
- **Note d'horloge** : ajoutée à l'étape 7 (dérivée, `Drift.ahead`) ; le flux `SyncStatus` prévu n'est pas créé : la carte lit
  directement `lastMerge`, `syncReview` et le contexte.
- **Captures** de l'auto-test : `desktop-sync-data[-narrow]`, `desktop-sync-review[-narrow]`, `desktop-sync-day-narrow` (deux
  bases d'exemple rapprochées, un conflit de priorité).

#### Précisions de l'implémentation (étape 7)

- **Doublons après « Remplacer » ou « Importer »** (défaut trouvé à la relecture) : remplacer les données d'une base
  synchronisée par un fichier de format 3 leur donnait des identités de migration (`legacy:…`) au lieu de celles du fichier ;
  un enregistrement créé ailleurs une fois la synchronisation active (`uid` aléatoire) revenait alors **en double** chez
  l'autre appareil à la fusion suivante. Désormais : `ImportedFile.Full` et le choix « Remplacer » gardent les identités du
  fichier (`decodeSynced(...).data.uids`, celles de migration pour un format 1 ou 2), `replaceWithBackup(…, uids)` les passe à
  `replaceAll`, et `adoptOpened(merge = false)` active la synchronisation **avant** le remplacement (bureau, Android, web) ;
  la restauration web depuis le fichier lié fait de même (`ensureSync` puis `replaceAll(snapshot, uids)`). Test
  `LinkedFileSyncTest.replacingThenEditingNeverDuplicatesTheOtherDevicesData` (une note créée après l'activation : en double
  avant la correction).
- **Horloge en avance** : `Drift.ahead(data, maintenant)` (pur) dit si le contexte ou l'horloge de la base porte une marque à plus
  de `WARN_MILLIS` (24 h) dans le futur ; la carte Données montre alors « Un appareil a écrit le fichier avec une horloge en
  avance de plus d'un jour : vérifie sa date et son heure… » (icône `Warning` et texte, `sync_clock_ahead`, six langues).
  **Dérivé, jamais stocké** : essayé d'abord dans `LastMerge`, la trace était réécrite par chaque fusion suivante (le fichier
  garde la marque future) et masquait la vraie dernière fusion ; la note disparaît d'elle-même quand l'heure rattrape la marque.
  Test `SyncRepositoryTest.aFileFromAClockAheadIsSeenUntilTimeCatchesUp`.
- **« À revoir » sur le web** : `KairosRepository.exportSyncExtras()` / `importSyncExtras(texte)` (« À revoir » et la dernière
  fusion, `SyncExtrasJson`), écrits par le navigateur dans `kairos-sync.json` de l'OPFS à chaque changement de la liste ou de la
  dernière fusion, relus après `restoreSynced`. Locaux : jamais dans le fichier lié ni dans un export. Test
  `SyncRepositoryTest.theWebReviewListSurvivesAReload`.
- **Taille du fichier lié** : au-delà de `LinkedMerge.MAX_CHARS` (64 Mi caractères), le fichier n'est pas décodé :
  `Unreadable(NOT_READABLE)`, rien n'est écrit (`LinkedFileSyncTest.anOversizedFileIsNeverDecoded`).
- **Fils** : les drapeaux de `LinkedFileSync` lus depuis plusieurs fils (`link`, `dirty`, `visible`, `inPass`) sont
  `@Volatile` (`kotlin.concurrent`, commun aux trois plateformes).
- **Test de vitesse** : `SyncReconcileTest.aSingleWriteOnFiveThousandTasksIsFast` dépassait sa borne de 20 ms sous la charge de
  la suite entière (33 et 35 ms), seul il passait (vérifié trois fois). Il compare désormais l'écriture incrémentale au
  rattrapage complet, mesurés en alternance (même charge) : au moins deux fois plus rapide (environ trois fois mesuré : 4 à 5 ms
  contre 14), avec un plafond absolu large de 200 ms contre une régression quadratique.

### Chrono et sessions (technique)

- `WorkSession` : `uid` aléatoire, `taskId` et `startedAt` immuables, `endedAt` = plus petite fin non nulle (registre `min`).
- Après fusion : `SyncRepair` (une seule session ouverte), puis `ChronoWatcher`, `ChronoSync` (Android), rappels et widgets se
  recalculent d'après le nouvel état comme pour une écriture locale. `startTimer` reste « fermer ailleurs puis ouvrir » ; une
  session ouverte venue d'un autre appareil est une session ouverte comme une autre.
- Le temps passé (`TimeTracking.spentMinutesByTask`) est la somme des sessions de l'état fusionné et de
  `manualTimeSpentMinutes` : la réunion des appareils, sans double compte puisque les sessions ont un `uid`.
- **Limite connue, différée (#193, consignée le 2026-11-04)** : les sessions d'une tâche **supprimée** divergent entre
  appareils. L'appareil qui supprime les garde, l'autre les retire après fusion, et le temps travaillé n'est plus le même des
  deux côtés. Un essai de correction (la session suit la portée de sa tâche dans `SyncScope`) cassait l'associativité et
  « aucune perte silencieuse » de `SnapshotMergePropertiesTest` ; il a été retiré. À trancher : garder les sessions partout, ou
  les retirer partout avec une pierre tombale qui garde la fusion associative.

### Isolation du mode solo

- **Aucune donnée de synchronisation avant l'activation** : pas de clé `device`, ni `sync_identity` ni `sync_mark`, export
  identique à l'octet près
  (`SyncIsolationTest.exportWithoutSyncIsByteIdentical`).
- **Aucune donnée d'équipe n'entre par la fusion** : la portée exclut membres, absences, journal, scénarios, tâches d'équipe,
  assignées ou reçues, et les champs d'équipe des tâches perso ; `Workspaces.hasTeamData` d'une base solo reste faux après toute
  fusion, `settings.team` reste nul (`SyncIsolationTest.noTeamDataEntersASoloBase`, y compris avec un fichier écrit par un
  utilisateur d'équipe).
- Le format 3 d'une installation solo ne porte aucun champ d'équipe (mêmes champs nullables que le format 2 : `hasTeamData` faux
  → aucun).
- `TeamIsolationTest` reste inchangé et vert.

### Robustesse

- Le fichier est une entrée **non fiable** : décodage et validations de `ExportCodec` d'abord, bornes de taille (uid, marques, 32
  appareils), `CORRUPTED` sinon ; rien n'est jamais fusionné en partie.
- Deux installations qui partagent un `device` (machine virtuelle clonée, dossier des données synchronisé et ouvert sur deux
  machines, ce que `bureau-avance.md` déconseille déjà) faussent le contexte : la voie prévue est le fichier lié, pas la base.
  « Délier » puis relier ne change pas l'`device`. Un bouton « Nouvelle identité d'appareil » reste une piste, non retenue.
- La sauvegarde avant fusion (`avant-fusion-…`) est la dernière ligne de défense, pas le mécanisme : le mécanisme est que
  l'absence ne supprime que ce que l'autre a vu.

### Tests (à créer)

Dans `core` (pur, tournent aussi hors JVM), **à graine fixe** (comme `ForecastRandom`, skill `kairos-monte-carlo`), sans
bibliothèque de test par propriétés :

- `HlcTest` : `tick` monotone avec horloge qui recule, débordement du compteur, `receive`, ordre total des `Stamp`, aller-retour
  du texte.
- `SyncReconcileTest` : idempotence, un champ modifié = une marque, valeur remise à l'ancienne = une marque, suppression, `uid`
  déterministes (`next:`, `cal:`, `ics:`, `exc:`, `dep:`), migration `legacy:` identique sur deux copies d'une origine commune,
  exemples à marque d'origine.
- `SnapshotMergeTest` : champs différents, même champ (conflit), conflit non détecté quand l'un a vu l'autre, suppression vue
  (se propage) ou non vue (élément conservé), vieille sauvegarde sans résurrection, dérive d'horloge, doublons évités,
  registres `createdAt`, `updatedAt` et `endedAt`, références orphelines, hors portée intact, `writeNeeded`, 5 000 tâches en moins
  de 1 s.
- `SnapshotMergePropertiesTest` : des milliers d'historiques aléatoires (3 appareils, créations, modifications de champs,
  suppressions, récurrences terminées, chronos) échangés dans un ordre aléatoire ; commutativité, associativité, idempotence,
  **convergence**, **absence de perte silencieuse**, hors portée intact, forme canonique.
- `SyncRepairTest` : sessions ouvertes, mères en boucle, application déterministe.
- `SyncScopeTest` : chaque champ de `Settings` et de chaque modèle est classé (synchronisé ou local).

Dans `data` : `SyncCodecTest` (formats 1, 2 et 3, octet pour octet sans synchronisation, `CORRUPTED`, `TOO_NEW`),
`SyncRepositoryTest` (entonnoir, rattrapage après arrêt, import = modifications locales, sauvegarde avant fusion, fiche ouverte
pendant une fusion, `sync_review`), `SyncMigrationTest` (11 → 12 sans rien modifier), `SyncIsolationTest`.

Dans `ui` et `desktopApp` : `LinkedFileSyncTest` (livré à l'étape 4 : deux appareils réels autour d'un même fichier,
convergence, pas de ping-pong, suppression propagée, premier rapprochement confirmé ou annulé, `Unreadable`, `TOO_NEW`, échec
d'écriture, « Remplacer »), `LinkedFileUiTest` (bandeau du premier rapprochement, « Fusionner » / « Remplacer » à l'ouverture),
`SyncSummaryTest` (`core`), `SyncUiTest` (livré à l'étape 5 : un conflit revu depuis la vue Jour et « Prendre l'autre », un
fichier fusionné à la main après son aperçu, nom de l'appareil et dernière fusion dans la carte Données) (aperçu, « À revoir », bandeau, fusion manuelle, 360 dp), `LearnedDurationsSyncTest`.

### Décisions et alternatives écartées

- **Fusionner des états, pas rejouer un journal ni comparer à une base** (décision du 2026-10-26). Le fichier est un support
  asynchrone où deux appareils peuvent écrire presque en même temps : l'outil tiers n'en garde alors qu'une version. Une
  **fusion à trois voies** (comparer chaque côté à « la dernière version synchronisée ») lit l'absence des modifications
  de l'autre appareil comme une annulation et **les défait en silence** ; une fusion d'états, elle, ne supprime que ce que
  l'autre côté a vu. Écartés : le journal d'opérations (le fichier grossit sans borne, compaction à prévoir) et l'écrasement
  « le plus récent gagne » au niveau du fichier (c'est la garde de 3.10.0 sans le choix).
- **Dernier écrivain par champ, avec les conflits simultanés listés** (et non un conflit bloquant) : une modification de champs
  différents d'une même tâche est le cas courant et ne doit demander rien. Un conflit **bloquant** laisserait les appareils
  divergents tant que personne ne répond, et deux réponses différentes se contrediraient (aller-retour). Ici les appareils
  convergent toujours, tout de suite, et la valeur écartée reste proposée. Écartés : dernier écrivain **par enregistrement**
  (perd la modification d'un champ quand l'autre appareil en change un autre), et fusion de texte caractère par caractère
  (CRDT de texte : disproportionné pour une description).
- **Pas de pierres tombales** : une suppression se déduit du contexte (« l'autre a vu l'élément et ne l'a plus »). Aucune
  croissance sans borne, aucun délai de péremption à régler (une pierre tombale expirée ressuscite l'élément sur un appareil
  absent longtemps), et la règle « une suppression ne détruit pas une modification qu'elle n'a pas vue » en découle sans cas
  particulier. Coût : le contexte (`seen`) à tenir juste, et l'ajout gagnant en cas de doute (la direction sûre).
- **Horloge hybride, pas d'horloge murale seule** : l'ordre de deux écritures **causalement** liées ne dépend jamais de la dérive
  (l'horloge absorbe tout ce qu'elle lit). La dérive ne départage que des écritures simultanées, déjà montrées à l'utilisateur.
  Écarté : tolérance fixe de quelques minutes (une montre en retard de dix minutes perdrait des modifications). Une vraie horloge
  logique pure (Lamport) est écartée : elle ne dit pas « quand » dans « À revoir ».
- **Un `uid` de migration déterministe** (`legacy:`) : voir « Identité ». Écartés : UUID aléatoire par appareil, identifiants
  entiers préfixés par l'appareil (casse `AUTOINCREMENT` et tout le dépôt).
- **Réconciliation par différence à l'entonnoir d'écriture** plutôt que dater dans chaque opération du dépôt (de nombreux
  chemins d'écriture) : un seul endroit, testable, qui rattrape aussi un arrêt entre transaction et marques, une migration et un
  import. Coût : une passe O(n) à l'ouverture, une passe proportionnelle à ce qui change ensuite.
- **Réconciliation sur les modèles actuels, tables `sync_*` à part** : ni colonne ni modèle existant ne change (aucune
  insertion positionnelle à retoucher, `KairosSnapshot` inchangé) ; au prix d'une jointure par `uid` à l'application d'une
  fusion.
- **`WorkSession.endedAt` = la plus petite fin non nulle** : arrêter deux fois la même session sur deux appareils n'est pas un
  conflit.
- **Suppression ≠ perte d'une modification vue ou non** : décision « ajout gagnant sur modification non vue » (ci-dessus) ;
  écarté : « suppression gagnante » (perd du travail en silence), « confirmer chaque suppression » (inutile quand elle a tout vu).
- **Deux appareils qui créent « la même chose »** (occurrence suivante, mois d'une série, calendrier, exception, dépendance,
  exemples) ont des `uid` déterministes plutôt qu'une déduplication après coup par titre : plus sûr et moins cher.
- **Réglages de planification partagés, présentation et notifications locales** : un téléphone et un PC qui planifient avec des
  heures de travail différentes donneraient deux journées différentes pour les mêmes données (et des durées apprises
  différentes) ; la langue, le thème, les rappels, le son et l'état du guide sont des préférences de l'appareil.
- **Un seul fichier, pas un par appareil** (écarté : `kairos-<appareil>.json` dans un dossier, chaque appareil n'écrit que le
  sien et lit les autres) : pas d'écriture concurrente, mais il faut l'accès à un **dossier** (Storage Access Framework en
  arbre de documents, poignée de dossier sur le web) et casse le « un fichier » de la 3.10.0. La fusion d'états rend la
  concurrence inoffensive ; à reprendre si l'usage montre des copies en conflit fréquentes.
- **Pas de verrou de fichier** (écarté) : l'outil tiers copie le fichier de façon asynchrone, un verrou local n'a aucun effet sur
  l'autre appareil, et un verrou partagé resterait posé par un appareil éteint.
- **Pas de fusion par l'outil tiers** (écarté) : Syncthing, Nextcloud ou Dropbox ne savent que garder une version ou créer une
  copie en conflit ; un fichier JSON complet n'est pas fusionnable ligne à ligne sans comprendre les enregistrements.
- **CRDT complet (écarté)** : bibliothèque dépendante (libre mais lourde, F-Droid et reproductibilité), structures de texte et
  de liste dont Kairos n'a pas besoin, et un format opaque. On garde le noyau utile (marques par champ, contexte causal),
  en Kotlin pur de `core`, lisible, testé par propriétés.
- **Premier rapprochement confirmé, fusions suivantes silencieuses** : au premier contact, on ignore qui a modifié quoi
  (deux appareils d'origine commune qui ont divergé donnent autant de conflits que de différences) ; ensuite, le contexte le dit.
- **Pas de synchronisation d'équipe** (propriétaire, 2026-10-26, justifié) : (1) l'identité de l'équipe (`settings.team.identity`)
  est tirée à l'activation, deux appareils activés chacun de leur côté ont **deux équipes** : la fusion ne peut pas trancher, il
  faudrait une décision de l'utilisateur et un parcours dédié ; (2) « C'est moi » est unique par base et le journal, les paquets et
  les rapports ont déjà leurs identités et leurs fusions (`teamUid`, `TeamMember.uid`, `equipe-echanges.md`), à ne pas doubler ;
  (3) le besoin (un manager sur deux appareils) est plus rare, et touche la même identité que « plusieurs équipes par base » (#118) ;
  (4) tout ce qui est hors portée **ne peut pas fuir** dans une base solo : l'invariant central de `equipe.md` se teste sur une
  liste fermée. La portée est une constante (`SyncScope`) : la rouvrir est une décision explicite.
- **Deux comportements confirmés après la QA d'avant 4.0** (2026-11-04) : (1) tant que la confirmation du premier
  rapprochement n'a pas de réponse, le fichier lié n'est ni fusionné ni réécrit (les passes automatiques attendent, les
  modifications locales restent en attente) : c'est voulu, rien ne se fusionne sans accord au premier contact ; (2) le fichier
  lié d'un manager contient ses données d'équipe en clair, comme tout export complet : la fusion les ignore chez les autres
  (`SyncScope`), et le chiffrement reste hors périmètre.
- **Chrono visible sur tous les appareils** (décision de conception) : une session ouverte appartient à la base, non à
  l'appareil qui l'a lancée ; arrêtée de n'importe où, elle est arrêtée partout. Écarté : un chrono propre à l'appareil (le temps
  passé serait faux et deux chronos tourneraient pour la même personne).

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `export-import.md` : format 3 (`sync`, `uid`, marques), `FORMAT_VERSION` = 3, lecture 1 à 3 ; « Fichier lié » : la garde
  `Changed` et « L'importer » remplacées par la fusion (états `NeedsConfirmation` et `Unreadable`, minuterie, `mergeNow`,
  `mergeFile`), « Ouvrir un fichier Kairos » propose « Fusionner » ; « Hors périmètre » (fusion, 4.0.0) ; carte Données.
  (Fait à l'étape 4 pour le fichier lié et « Ouvrir un fichier Kairos » ; le reste avec l'étape 5.)
- `modele-donnees.md` : schéma 13 et ses tables `sync_*`, marqueur, `replaceAll(snapshot, uids)`, entonnoir de réconciliation ;
  « Hors périmètre » : la synchronisation n'en est plus.
- `connexions-externes.md` : `uid` déterministes des calendriers, créneaux, journées ; « réimporter » vu comme une suppression
  et une réinsertion de mêmes `uid` (sans effet de synchronisation si rien n'a changé) ; synchronisation hors périmètre → ici.
- `recurrence.md` : `uid` déterministes de l'occurrence suivante (`next:`), des occurrences « le N du mois » (`cal:`) et des
  exceptions (`exc:`).
- `temps-reel-chrono.md` : une session ouverte peut venir d'un autre appareil ; réparation « une seule session ouverte ».
- `durees-apprises.md` : « Hors périmètre » (apprentissage entre appareils → fait par la fusion ; entre membres → différé,
  justifié ici) ; ordre des échantillons (voir la dernière section) ; critère d'invariance à l'ordre.
- `reglages.md` : champs partagés et champs locaux (`SyncScope`) ; carte Données.
- `equipe.md` : l'espace Équipe n'est pas synchronisé ; invariant d'isolation étendu à la fusion (`SyncIsolationTest`).
- `architecture.md` : paquet `core/sync/` (pur) ; tables `sync_*` de `data`.
- `i18n.md` (clés `sync_*`), `visite-guidee.md` (carte `sync`, « Nouveau dans cette version »), `distribution.md` (aucune
  permission, aucune dépendance de plus), `bureau-avance.md` (dossier de données et deux instances).
- `docs/spec/README.md` (index : `synchronisation.md`), `docs/ROADMAP.md` (#88 et #100 : spec écrite, 4.0.0).
- **README (EN et FR) : oui, c'est une fonctionnalité** (fusion entre appareils par le fichier lié), plus une carte du catalogue
  (`FeatureCatalog.kt`, `FeatureCatalogTest.readmeFeaturesAreCovered`) ; notes de version Fastlane FR et EN, et ligne de
  « Nouveau dans cette version » demandant de mettre à jour tous les appareils.

### Durées apprises partagées (#100)

#### Besoin

Une tâche récurrente chronométrée sur le téléphone doit nourrir la durée apprise du PC, et inversement : « relever le courrier »
pris 12 minutes cinq fois sur le téléphone ne doit pas rester 30 minutes (la valeur par défaut) sur le PC. L'option retenue par le
propriétaire (**(b)**) est **un calcul dérivé de la base fusionnée, sans nouveau format** : Kairos ne stocke ni n'échange
aucune durée apprise ; chaque appareil la recalcule (`durees-apprises.md` : « dérivée, jamais écrite ») d'après des données qui,
une fois fusionnées, sont les mêmes partout.

#### Pourquoi la fusion suffit

Tout ce que lit `DurationEstimator` est dans la portée synchronisée : les tâches terminées et leur `seriesId`, leur titre, leur
espace (toujours Perso ici : une tâche reçue d'un manager n'entre pas dans la portée, ni dans une série personnelle), les
sessions de travail et `manualTimeSpentMinutes` (donc le temps passé), et les réglages `learning` (actif, historique, seuil) et
heures de travail (borne de l'arrondi). La fusion ne produit donc aucun nouveau champ : le fichier de format 3 n'a que des
`uid` et des marques, communs à toute la synchronisation.

Pour que deux bases fusionnées donnent **la même** durée, trois points sont à tenir :

1. **La série est la même.** `seriesId` est une référence : elle est lue en `uid` (la première tâche de la série) et réécrite
   en `id` local à chaque appareil ; la comparaison de `DurationEstimator.estimate` (`sample.seriesId == task.seriesId` ou
   `sample.taskId == task.seriesId`) reste correcte parce que l'abaissement est cohérent sur un appareil. L'occurrence suivante
   d'une série terminée sur deux appareils est **une** (`next:<uid>`) : sans cela la série compterait deux occurrences
   fantômes et la médiane serait faussée.
2. **L'ordre des échantillons est le même.** Aujourd'hui `estimate` retient les `history` derniers échantillons par `taskId`
   décroissant (« jamais `updatedAt` », modifiable après coup). Après fusion les `id` sont locaux et leur ordre peut différer
   d'un appareil à l'autre. Avec la synchronisation active, l'ordre devient **`createdAt` décroissant, puis `uid`** (immuable et
   commun) ; **sans synchronisation il reste l'`id`, octet pour octet** (`DifferentialTest` et fichiers d'or inchangés).
   `Sample` porte la clé d'ordre.
3. **Les réglages sont les mêmes.** `learning` et les heures de travail sont des réglages partagés (sinon deux appareils
   appliqueraient deux seuils).

Le reste est inchangé : médiane, écrêtage des aberrants (> 3 × la médiane), arrondi à 5 minutes, bornes, repli série puis
titre, jamais au-dessus d'une estimation explicite, provenance visible.

#### Critères de succès

- Deux bases qui ont chacune chronométré des occurrences d'une même série, une fois fusionnées dans les deux sens, donnent
  les mêmes estimations (`DurationEstimator.estimates` comparées par `uid`) : test de propriétés à graine fixe
  (`LearnedDurationsSyncTest`).
- Une série terminée sur les deux appareils garde **une** occurrence suivante et le même `seriesId` logique
  (`LearnedDurationsSyncTest.nextOccurrenceIsNotDuplicated`).
- Sans synchronisation, rien ne change (`DurationEstimatorTest`, `DifferentialTest` inchangés).
- En attendant la fusion, chaque appareil calcule sur ses seules données : c'est attendu, pas un défaut.

#### Implémentation (étape 6)

- `core/sync/SyncOrder.of(snapshot, data)` : le rang de chaque tâche de la portée, `createdAt` croissant puis `uid` (0 = la plus
  ancienne) ; les tâches sans `uid` n'en ont pas. Le dépôt d'une base synchronisée le joint à chaque état publié
  (`KairosSnapshot.sampleOrder`, `publish`, et dès `enableSync`) ; il n'est ni stocké, ni exporté, ni comparé par la fusion.
  Sans synchronisation, `sampleOrder` est `null`.
- `DurationEstimator.Sample.order` : le rang commun, sinon l'identifiant (une tâche sans rang dans une base synchronisée passe
  après, par identifiant). `HistoryEstimators` (série, titre), `ModelEstimator` (corpus, voisins, départages, quantiles),
  `ModelIndex` (tri, empreinte), `ModelGate` (préfixes) ordonnent par `order` au lieu de `taskId` ; l'identité d'une occurrence
  (exclure la tâche elle-même, nommer les voisins) reste `taskId`.
- **Choix** : un champ du snapshot plutôt qu'un paramètre de plus aux quatorze appels de `DurationEstimator` (vue Jour, équipe,
  chrono, widgets…) : l'ordre suit les données partout où elles vont, sans rien changer aux appelants.
- **Choix** : le modèle (#99) suit le même ordre (il n'a pas de poids à synchroniser : un index recalculé des données, comme
  les durées apprises) ; deux appareils fusionnés construisent le même corpus, la même garde et les mêmes estimations.
- `LearnedDurationsSyncTest` (`data`) : 20 graines, deux appareils qui chronomètrent chacun à leur tour (identifiants locaux
  dans des ordres différents), fusionnés dans les deux sens, donnent les mêmes durées apprises par `uid` ; sans l'ordre commun,
  le test échoue (vérifié : 40 contre 55 minutes dès la graine 1). Ordre identique des deux côtés et absent sans
  synchronisation ; occurrence suivante unique d'une série terminée des deux côtés, même `seriesId` logique.

#### Hors périmètre / différé

- **Apprentissage entre les membres d'une équipe** : différé, justifié. (1) Les données d'équipe ne sont pas dans la
  portée de la synchronisation (voir « Décisions »). (2) Une durée apprise décrit le **rythme d'une personne** : mélanger les temps
  de plusieurs membres donnerait une durée qui n'est celle de personne ; l'espace Équipe a déjà son calibrage par type et par
  points, sur les tâches terminées de l'équipe (`Effort.teamCalibration`, `durees-apprises.md`). (3) Le temps d'un membre ne
  parvient au manager que par les rapports, en **total par tâche** (`reportedMinutes`), jamais en sessions : il manque la matière
  de l'échantillon, et en demander plus change le format des échanges (`equipe-echanges.md`) et l'étendue de ce qu'un membre
  accepte de partager. À rouvrir, avec le modèle appris (#99), si une demande précise apparaît.
- **L'estimateur appris (#99)** : tranché à l'étape 6, il n'a pas de poids (un index recalculé des données) et suit l'ordre
  commun (§ Implémentation ci-dessus).
- **Corriger les estimations** (`statistiques.md`) : action explicite, inchangée ; ses modifications de tâches se synchronisent
  comme toute modification.
