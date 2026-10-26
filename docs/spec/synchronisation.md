# Synchronisation par fichier : fusion automatique entre appareils

_Rôle : faire de la copie tenue à jour dans un fichier lié (3.10.0) le point de rencontre de plusieurs appareils : à chaque
lecture, Kairos **fusionne** le fichier avec ses données, sans compte, sans serveur, sans permission réseau, et sans jamais
perdre en silence ce qu'un appareil a fait. Fichiers couverts (à créer ou à modifier à l'implémentation) :
`kmp/core/.../core/sync/` (`Hlc.kt`, `SyncScope.kt`, `SyncRecords.kt`, `SyncData.kt`, `SyncReconcile.kt`, `SnapshotMerge.kt`,
`SyncRepair.kt`, `SyncSummary.kt`), `kmp/data/` (`11.sqm`, `Kairos.sq`, `KairosStore.kt`, `ExportCodec.kt`,
`KairosRepository.kt`), `kmp/ui/.../app/` (`LinkedFileSync.kt`, `LinkedStorage.kt`, `StorageBanner.kt`, `Replace.kt`),
`kmp/ui/.../settings/SettingsScreen.kt` (carte « Données »), `kmp/ui/.../sync/` (aperçu et « À revoir »),
`kmp/desktopApp/.../DesktopLinkedFile.kt`, `kmp/androidApp/.../AndroidLinkedFile.kt`, `kmp/webApp/.../WebServices.kt`
(`WebLinkedStorage`), `kmp/core/.../engine/DurationEstimator.kt` (ordre des échantillons). Tests (à créer) : `HlcTest`,
`SyncReconcileTest`, `SnapshotMergeTest`, `SnapshotMergePropertiesTest`, `SyncRepairTest`, `SyncScopeTest`,
`SyncCodecTest`, `SyncRepositoryTest`, `SyncMigrationTest`, `SyncIsolationTest`, `LinkedFileSyncTest` (étendu),
`SyncUiTest`, `LearnedDurationsSyncTest`._

État : **décidée le 2026-10-26, à implémenter (4.0.0)** (issues #88 et #100, décision du propriétaire du 2026-10-26) ;
spécifiée le 2026-10-26, **non implémentée** : rien de ce document n'existe encore dans le code, et les specs existantes ne
sont pas modifiées (voir « Impacts sur les specs existantes »). La moitié « écriture » de #88, le fichier lié et sa garde
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
- **Propre à chaque appareil** : la langue, le thème et sa couleur, les rappels, la vérification des mises à jour, le son des
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
  réconciliation après une écriture d'un seul élément en moins de 20 ms sur 5 000 tâches (`SnapshotMergeTest`,
  `SyncReconcileTest`).
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
| `Settings` | champs de planification (liste ci-dessous) | `language`, `themeColor`, `themeMode`, `updateCheckEnabled`, `timerAlertSound`, `reminders`, `guide`, `team` |
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
- **Déterministe** : enregistrements triés par `uid`, clés triées. Deux appareils qui ont convergé écrivent les mêmes octets, à
  `exportedAt`, `appVersion` et réglages locaux près.
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

### Stockage (`data`, schéma 12)

Additif (`11.sqm`, marqueur `sync_state` de `KairosStore.MARKERS`, test `ExchangeMigrationTest.everyMigrationHasAMarker`), vide
pour qui ne synchronise pas, **sans toucher à aucune table existante** (donc sans changer l'ordre positionnel des insertions) :

- `sync_state(key TEXT PRIMARY KEY, value TEXT NOT NULL)` : `device`, `label`, `clock`, `seen` (JSON), `lastMerge` (JSON : heure,
  résumé), `since`, `examples` (identifiants des exemples posés, voir « Identité »). **La clé `device` seule dit que la base est
  synchronisée.**
- `sync_identity(kind TEXT, local_id INTEGER, uid TEXT NOT NULL, PRIMARY KEY (kind, local_id))`, index unique `(kind, uid)`.
- `sync_mark(kind TEXT, uid TEXT, field TEXT, wall INTEGER, counter INTEGER, device TEXT, hash TEXT, PRIMARY KEY (kind, uid,
  field))` ; `field` vaut `@born`, `@base` ou le nom d'un champ ; `hash` est l'empreinte de la valeur marquée.
- `sync_review(id INTEGER PRIMARY KEY AUTOINCREMENT, kind, uid, field, title, loser_value, loser_wall, loser_counter,
  loser_device, kept INTEGER, created_at)` : « À revoir », **locale** (ni fichier, ni export).

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
- États : `LinkedFileState.Changed` est **retiré** ; ajouts `NeedsConfirmation(name, preview)` et `Unreadable(name, reason)`
  (`reason` : `NOT_READABLE`, `TOO_NEW`, `CLOCK`) ; `WriteFailed`, `Linked`, `NotLinked`, `NeedsPermission`, `SaveFailed`,
  `Unsupported` inchangés. Un flux séparé `SyncStatus(lastMerge, reviewCount, clockNote)` porte le reste.
- `LinkedStorage` : `overwrite()` ne sert plus qu'à `Unreadable` ; `readLinked()` et `acknowledge()` disparaissent (plus
  d'« L'importer ») ; nouveaux `mergeNow()` et `mergeFile()` ; `openFile()` rend le contenu et l'interface demande
  « Fusionner » ou « Remplacer ».
- Le web (`WebLinkedStorage`) suit le même contrat : `linkRead` avant `linkWrite` à chaque passe, la minuterie ne tourne que
  l'onglet visible ; l'instantané de l'OPFS (`kairos.json`) reste la persistance complète du navigateur et ne se fusionne
  jamais (seul le fichier lié se fusionne).
- Android : aucune permission de plus ; la fusion tourne au premier plan seulement (pas de `WorkManager`, pas d'alarme).

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

### Chrono et sessions (technique)

- `WorkSession` : `uid` aléatoire, `taskId` et `startedAt` immuables, `endedAt` = plus petite fin non nulle (registre `min`).
- Après fusion : `SyncRepair` (une seule session ouverte), puis `ChronoWatcher`, `ChronoSync` (Android), rappels et widgets se
  recalculent d'après le nouvel état comme pour une écriture locale. `startTimer` reste « fermer ailleurs puis ouvrir » ; une
  session ouverte venue d'un autre appareil est une session ouverte comme une autre.
- Le temps passé (`TimeTracking.spentMinutesByTask`) est la somme des sessions de l'état fusionné et de
  `manualTimeSpentMinutes` : la réunion des appareils, sans double compte puisque les sessions ont un `uid`.

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

Dans `ui` et `desktopApp` : `LinkedFileSyncTest` (étendu : minuterie, premier rapprochement, `Unreadable`, pas de ping-pong,
écritures simultanées), `SyncUiTest` (aperçu, « À revoir », bandeau, fusion manuelle, 360 dp), `LearnedDurationsSyncTest`.

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
- **Chrono visible sur tous les appareils** (décision de conception) : une session ouverte appartient à la base, non à
  l'appareil qui l'a lancée ; arrêtée de n'importe où, elle est arrêtée partout. Écarté : un chrono propre à l'appareil (le temps
  passé serait faux et deux chronos tourneraient pour la même personne).

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `export-import.md` : format 3 (`sync`, `uid`, marques), `FORMAT_VERSION` = 3, lecture 1 à 3 ; « Fichier lié » : la garde
  `Changed` et « L'importer » remplacées par la fusion (états `NeedsConfirmation` et `Unreadable`, minuterie, `mergeNow`,
  `mergeFile`), « Ouvrir un fichier Kairos » propose « Fusionner » ; « Hors périmètre » (fusion, 4.0.0) ; carte Données.
  (La seule modification faite maintenant : une phrase de renvoi vers cette spec.)
- `modele-donnees.md` : schéma 12 et ses tables `sync_*`, marqueur, `replaceAll(snapshot, uids)`, entonnoir de réconciliation ;
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

#### Hors périmètre / différé

- **Apprentissage entre les membres d'une équipe** : différé, justifié. (1) Les données d'équipe ne sont pas dans la
  portée de la synchronisation (voir « Décisions »). (2) Une durée apprise décrit le **rythme d'une personne** : mélanger les temps
  de plusieurs membres donnerait une durée qui n'est celle de personne ; l'espace Équipe a déjà son calibrage par type et par
  points, sur les tâches terminées de l'équipe (`Effort.teamCalibration`, `durees-apprises.md`). (3) Le temps d'un membre ne
  parvient au manager que par les rapports, en **total par tâche** (`reportedMinutes`), jamais en sessions : il manque la matière
  de l'échantillon, et en demander plus change le format des échanges (`equipe-echanges.md`) et l'étendue de ce qu'un membre
  accepte de partager. À rouvrir, avec le modèle appris (#99), si une demande précise apparaît.
- **L'estimateur appris (#99)** : ses poids seront des données ; les synchroniser ou les recalculer est une décision de cette
  spec-là.
- **Corriger les estimations** (`statistiques.md`) : action explicite, inchangée ; ses modifications de tâches se synchronisent
  comme toute modification.
