# Espace Équipe : vision, activation, membres et isolation du mode solo

_Rôle : la spec chapeau du chantier « Kairos manager ». Elle fixe le
périmètre, les décisions de cadrage, l'activation de l'espace Équipe, la
navigation entre les deux espaces, les membres d'équipe, l'**isolation
stricte du mode solo** et le découpage en jalons. Les autres specs du
chantier détaillent chaque domaine :_

| Spec | Couvre |
|---|---|
| `equipe.md` (celle-ci) | Vision, activation, espaces, navigation, membres, absences, isolation solo, modèle et stockage communs, jalons. |
| [`equipe-backlog-suivi.md`](equipe-backlog-suivi.md) | Backlog d'équipe, catégories (types), assignation, réaffectation, états et avancement, tableau de suivi, journal. |
| [`equipe-charge.md`](equipe-charge.md) | Capacité, effort restant, charge individuelle, globale et par catégorie, plan de charge, suggestion de répartition. |
| [`equipe-simulation.md`](equipe-simulation.md) | Prévisions Monte Carlo (débit et effort), probabilités d'échéance, criticité, issue la plus probable, scénarios « Et si… ? ». |
| [`equipe-echanges.md`](equipe-echanges.md) | Paquets de tâches (manager → membre) et rapports d'avancement (membre → manager) par fichier. |

_Fichiers prévus : `kmp/core/.../core/team/` (modèle et calculs purs),
`kmp/data/` (migration `1.sqm`, dépôt), `kmp/ui/.../team/` (écrans).
Skills Claude Code du chantier : `.claude/skills/kairos-equipe/`,
`.claude/skills/kairos-monte-carlo/` (et, transverses,
`.claude/skills/kairos-spec/`, `.claude/skills/kairos-ecran/`)._

État : **jalons E1 à E4 implémentés (2026-10-01)** — E3 : backlog,
suivi et journal (`equipe-backlog-suivi.md`) ; E4 : capacité, charge et
répartition (`equipe-charge.md`) — : espaces, filtre
`personalView`, migrations `1.sqm` et `2.sqm`, export 2, `TeamSettings`,
carte Équipe, sélecteur d'espace, coquille à deux espaces, membres et
absences, suppression des données d'équipe (Prévisions encore en état
vide). **E5 et E6 : spécifiés, non implémentés** ; ce qui les
concerne ci-dessous et dans les quatre autres specs décrit du code à
venir. Les specs existantes n'ont reçu que ce que E1 et E2 ont réellement
codé ; chaque spec du
chantier liste en fin de document les « Impacts sur les specs existantes »
qui restent à reporter aux jalons suivants.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos répond aujourd'hui à « qu'est-ce que **je** fais maintenant ». Un
manager se pose en plus d'autres questions, sur **son équipe** :

- qui fait quoi, qui est surchargé, qui a de la marge ;
- que reste-t-il dans le backlog, et à qui le confier ;
- où en est chaque sujet, qu'est-ce qui traîne ou change trop souvent de
  mains ;
- quand le lot en cours sera-t-il fini, avec quelle confiance, et quelles
  échéances sont en danger ;
- que se passe-t-il si quelqu'un part en congé, si un renfort arrive, si on
  ajoute dix sujets.

Kairos doit offrir un **espace Équipe** qui répond à ces questions, sans
rien changer pour qui ne l'active pas.

### Décisions de cadrage (2026-09-30, avec le propriétaire du dépôt)

1. **Un seul utilisateur : le manager.** Les membres sont des fiches dans
   la base du manager, pas des comptes. Kairos reste hors ligne, sans
   serveur, sans permission réseau (F-Droid, `publication.md`) ; la
   décision « pas de synchronisation » du 2026-09-28 (`export-import.md`)
   n'est **pas** rouverte.
2. **Échanges optionnels par fichier** : le manager peut envoyer à un
   membre un « paquet » de ses tâches, et le membre renvoyer un
   « rapport » d'avancement (`equipe-echanges.md`). Un membre sans Kairos
   reste suivi à la main par le manager.
3. **Même base, espace séparé** : les tâches d'équipe vivent dans la même
   base que les tâches personnelles, mais dans un espace distinct. Le
   manager peut être membre de sa propre équipe (« C'est moi ») : les
   tâches d'équipe qui lui sont assignées apparaissent alors dans sa vue
   Jour personnelle, et une seule ligne en base porte les deux usages.
4. **Une seule équipe** par base. Plusieurs équipes : hors périmètre
   (§ Hors périmètre).
5. **Les catégories sont les types de tâche** existants (Réglages → Types de
   tâches) : pas de nouveau concept. Filtres, charge et prévisions « par
   catégorie » se lisent sur `Task.taskType`
   (`equipe-backlog-suivi.md` § Catégories).
6. **Specs et skills d'abord** : la PR qui ouvre le chantier ne contient que
   ces specs et les skills Claude Code associés.

### Comportement attendu (utilisateur)

#### Activation

- Réglages → nouvelle carte **Équipe**, placée après « Apparence ». Elle
  contient un interrupteur « Gestion d'équipe » (désactivé par défaut) et la
  phrase « Gérer une équipe : membres, backlog partagé, charge et
  prévisions. Sans effet sur vos tâches personnelles. ».
- L'interrupteur suit la règle des Réglages : il fait partie du formulaire
  et prend effet à « Enregistrer » (`reglages.md`).
- Une fois activé **et enregistré**, la carte montre aussi « Nom de
  l'équipe » et « Votre nom (dans les paquets) », chacun avec sa phrase
  d'aide (puis, aux jalons suivants, les paramètres de charge et de
  prévision, `equipe-charge.md` et `equipe-simulation.md`).
- **Désactiver** l'espace demande confirmation (« Masquer l'espace Équipe ?
  Ses données sont conservées et reviendront si vous le réactivez. »). Rien
  n'est supprimé ; tout ce qui relève de l'équipe disparaît de l'interface,
  y compris les tâches d'équipe assignées à « moi » dans la vue Jour.
- Supprimer définitivement les données d'équipe : bouton « Supprimer les
  données d'équipe… » de la carte, confirmation qui annonce le nombre de
  membres et de tâches, sauvegarde automatique préalable comme pour un
  import (`export-import.md`) ; sans sauvegarde réussie, rien n'est
  supprimé.

#### Deux espaces, un sélecteur en haut une fois le mode activé

- Le mode « gestion d'équipe » s'**active dans les Réglages** (carte
  Équipe). Tant qu'il ne l'est pas, rien d'autre n'apparaît : un
  utilisateur solo ne voit jamais le sélecteur.
- Mode activé (valeur **enregistrée**) : un **sélecteur d'espace**
  « Perso | Équipe » (bouton segmenté MD3) apparaît **en haut** : en tête
  du rail, sous le logo, quand il y a un rail ; sinon dans la barre
  d'application, à droite du titre. En largeur compacte (< 600 dp), les
  segments ne montrent que leurs icônes (`Person`, `Groups`), avec leur
  libellé pour le lecteur d'écran, **quelle que soit la largeur** : le rail
  (80 dp) n'a pas la place d'un libellé, et la barre du haut doit tenir à
  360 dp à côté du titre. Le segment choisi se lit à son conteneur
  (`secondaryContainer`).
- Le sélecteur agit **aussitôt** (c'est de la navigation, pas un réglage) :
  il ne passe pas par « Enregistrer ».
- Décision du 2026-09-30 (révisée le même jour) : un premier choix plaçait
  le sélecteur dans les Réglages ; il est revenu en haut, **conditionné à
  l'activation**, ce qui protège aussi l'utilisateur solo tout en gardant
  la bascule à un toucher pour le manager.
- L'espace **Perso** garde exactement ses cinq destinations (Notes, Jour,
  Semaine, Stats, Réglages).
- L'espace **Équipe** a ses cinq destinations :
  1. **Suivi** : le tableau de l'équipe, par membre et par état
     (`equipe-backlog-suivi.md`) ; destination d'ouverture de l'espace ;
  2. **Backlog** : les tâches d'équipe non assignées, triées par score,
     et leur distribution ;
  3. **Équipe** : les membres, leur capacité et leur charge, la charge
     globale et par catégorie (`equipe-charge.md`) ;
  4. **Prévisions** : simulations et scénarios (`equipe-simulation.md`) ;
  5. **Réglages** : le même écran que dans l'espace Perso.
- Changer d'espace ouvre la première destination de l'autre espace (Jour
  ou Suivi). L'espace courant est retenu d'une session à l'autre.
- Dans l'espace Équipe, le titre de la barre d'application est préfixé du
  nom de l'équipe s'il est renseigné (« Équipe Plateforme · Suivi »).

#### Membres

- Destination Équipe → « Ajouter un membre » : **nom** (obligatoire),
  **rôle** (texte libre, facultatif), **quotité** (en %, 100 par défaut,
  de 1 à 100), **heures par jour** (défaut : journée de travail des
  Réglages, `workdayEndHour − workdayStartHour` moins une heure, bornée à
  1…24), case **« C'est moi »** (au plus un membre la porte ; la cocher
  sur un membre la retire à l'autre).
- **Absences** d'un membre : liste de plages de dates (début, fin
  incluses, libellé facultatif : « Congés », « Formation »…). Une absence
  retire ses jours ouvrés de la capacité (`equipe-charge.md`).
- **Archiver** un membre (départ de l'équipe) : ses tâches ouvertes
  repassent au backlog après confirmation (« 4 tâches ouvertes
  retourneront au backlog. ») ; ses tâches faites restent à son nom pour
  l'historique et les prévisions. Un membre archivé n'est plus proposé à
  l'assignation ; il peut être réactivé.
- **Supprimer** un membre n'est possible que s'il n'a jamais eu de tâche ;
  sinon seul l'archivage est proposé (l'historique garde son nom).
- Les membres sont triés par nom ; « C'est moi » est marqué d'une icône et
  du mot « moi ».

#### Destination Équipe (jalon E2, avant la charge)

- En tête, le bouton **« Ajouter un membre »** (bouton primaire plein,
  icône `PersonAdd`). Sans aucun membre : un état vide qui explique à quoi
  servent les membres, avec le même bouton.
- Une carte par **membre actif** (« moi » d'abord, puis par nom) : nom,
  rôle, quotité et heures par jour (« 80 % · 7 h/j »), et la **prochaine
  absence** à venir ou en cours (« Absent du 12 au 16 oct. », icône
  `EventBusy`). La charge s'y ajoutera au jalon E4 (`equipe-charge.md`).
- Section repliable **« Anciens membres (n) »** pour les membres archivés.
- Toucher une carte ouvre la **fiche du membre** (plein écran sur
  téléphone, dialogue en largeur ≥ 600 dp) : les champs du membre, la
  liste de ses absences (ajouter, modifier, supprimer ; début et fin par
  le sélecteur de dates MD3, libellé facultatif), puis « Archiver » (ou
  « Réactiver ») et « Supprimer » (seulement s'il n'a jamais eu de tâche).
  Un seul « Enregistrer » pour les champs, comme dans les Réglages ; il
  ferme la fiche, comme archiver, réactiver et supprimer. Les absences
  s'enregistrent une à une, aussitôt (suppression d'une absence sans
  confirmation). Une **nouvelle** fiche n'a ni absences ni archivage : le
  membre n'existe pas encore ; on les gère en rouvrant sa carte.
- Une absence se choisit dans un sélecteur de plage MD3 : un seul jour
  touché donne une absence d'un jour ; « Enregistrer » reste inactif sans
  date. Libellé de carte : « Absent du 12 au 16 oct. · Congés », l'année
  seulement si elle n'est pas l'année en cours.
- Un membre archivé ne peut pas être « C'est moi » : l'archivage retire la
  marque, la réactivation ne la rend pas.
- Tri par nom : ordre lexicographique du nom en minuscules, sans
  collation propre à une langue (`core` pur) ; « Émile » passe donc après
  « Zoé ». Assumé tant que personne ne s'en plaint.
- Validation des champs à l'enregistrement, erreur à la place de l'aide :
  nom vide (« Le nom est obligatoire. »), quotité hors 1-100 ou non
  entière, heures par jour hors 1-24 ou non numériques (virgule ou point).
  Une absence dont la fin précède le début est refusée
  (« La fin doit suivre le début. »).
- Réglages → carte Équipe, mode activé : **« Supprimer les données
  d'équipe… »** (bouton texte, couleur d'erreur). Confirmation qui compte
  ce qui part (« 3 membres et 12 tâches d'équipe seront supprimés. »),
  sauvegarde automatique préalable (`avant-suppression-equipe-AAAAMMJJ-HHMM.json`,
  même mécanisme que l'import) ; sans sauvegarde réussie, rien n'est
  supprimé. Les réglages d'équipe (nom, manager, mode activé) restent. Le
  bouton est visible dès que le mode est activé et enregistré, même sans
  donnée (les compteurs disent alors 0).

#### Isolation du mode solo (invariant « sans incidence »)

Tant que l'espace Équipe n'a **jamais été activé** :

- les écrans sont **identiques** à ceux d'avant le chantier, pixel pour
  pixel, à l'exception de la seule carte « Équipe » des Réglages (fermée :
  un interrupteur et sa phrase) ;
- ordonnancement, vue Jour, vue Semaine, statistiques, chrono, notes,
  récurrence : mêmes entrées, mêmes sorties (les tests différentiels contre
  Kairos 2 passent sans changement) ;
- un **export** est octet pour octet celui qu'aurait produit la version
  d'avant, à `appVersion` et `exportedAt` près (même `formatVersion`,
  mêmes champs, réglages compris, § Export) ;
- les données d'exemple d'une base neuve ne contiennent rien d'équipe ;
- l'accueil au premier lancement ne parle pas d'équipe.

Espace **activé puis désactivé** : les mêmes garanties d'affichage et de
calcul valent (les données d'équipe sont en base mais invisibles), sauf
l'export, qui les emporte pour ne jamais les perdre.

Espace **activé** : l'espace Perso ne change que par les tâches d'équipe
assignées à « moi », qui y apparaissent marquées « Équipe »
(`equipe-backlog-suivi.md` § Tâches assignées à moi). Les statistiques
personnelles les comptent (c'est du temps que je passe) ; aucune autre tâche
d'équipe n'entre dans un calcul personnel.

### Critères de succès

- Base neuve ou existante, espace jamais activé : tous les tests existants
  passent sans modification ; captures `--self-test` et
  `--store-screenshots` identiques à celles d'avant, carte Réglages
  exceptée ; export identique à l'octet, `appVersion` et `exportedAt`
  mis à part (`TeamIsolationTest.soloExportIsByteIdentical`).
- Activer, créer deux membres et trois tâches d'équipe dont une assignée à
  « moi », désactiver : la vue Jour, la vue Semaine et les stats sont
  celles d'avant l'activation (`TeamIsolationTest.personalSnapshotFollowsTheWrites`,
  `TeamSpaceUiTest.teamTasksReachThePersonalDayOnlyWhenAssignedToMeAndTheModeIsOn`).
- Réactiver : membres, tâches, journal et scénarios sont revenus
  inchangés.
- Espace activé : une tâche d'équipe assignée à « moi » apparaît dans la
  vue Jour ; assignée à un autre membre, elle n'y est pas.
- Archiver un membre remet ses tâches ouvertes au backlog et journalise
  chaque retour (`equipe-backlog-suivi.md` § Journal).
- Aucun écran de l'espace Équipe ne déborde horizontalement à 360 dp ;
  cibles tactiles ≥ 48 dp (`accessibilite.md`).

### Hors périmètre / différé

- **Multi-utilisateur synchronisé** (serveur, pair à pair, comptes) :
  écarté (décision 1). Toute évolution rouvre explicitement la décision du
  2026-09-28 et la contrainte « sans permission réseau ».
- **Plusieurs équipes** dans une base, sous-équipes, organigramme.
- Droits et rôles d'accès (il n'y a qu'un utilisateur).
- Notifications aux membres, messagerie, commentaires en fil.
- Intégrations (Jira, GitLab, calendriers) : retirées de Kairos 3.
- Gestion RH (validation des congés, compteurs), budget, coûts, feuilles de
  temps officielles.
- Compétences des membres (matrice de compétences) : non demandées ;
  l'affinité par catégorie (`equipe-charge.md` § Suggestion) en tient lieu.
- Thème sombre (inchangé : un seul thème clair).

## 2. Solution technique

### Espaces et filtre central (`core/team/Workspaces.kt`, pur)

- `TaskSpace` : `PERSONAL` (code `0`, défaut) ou `TEAM` (code `1`) ; nouveau
  champ `Task.space`. Code inconnu à la lecture : `PERSONAL` (règle des
  codes de `modele-donnees.md`).
- `Workspaces.personalView(snapshot): KairosSnapshot` : **le** filtre qui
  protège le mode solo. Tâches gardées : toutes les `PERSONAL`, plus, si
  `teamModeEnabled` (§ Modèle), les `TEAM` assignées au membre « moi ».
  Dépendances gardées : celles dont la tâche **bloquée** est gardée ; un
  bloqueur hors vue reste lu dans la base complète (il bloque tant qu'il est
  à faire, comme un bloqueur inconnu aujourd'hui dans `DayView`), et son
  titre est fourni à part pour « Bloquée par ». Sessions gardées : celles
  des tâches gardées. Notes, créneaux, réglages : inchangés.
- `Workspaces.teamView(snapshot): TeamSnapshot` : tâches `TEAM`, membres,
  absences, journal, scénarios, dépendances entre tâches d'équipe, sessions
  des tâches d'équipe.
- **Point de passage obligé** : le dépôt calcule `personalView` une fois à
  chaque rechargement et le publie dans `KairosRepository.personalSnapshot`
  (`StateFlow`, à côté de `snapshot`, qui reste la base complète). Tous les
  écrans et calculs de l'espace Perso (vue Jour, vue Semaine, Notes, Stats,
  `ChronoWatcher`, `ChronoSync` Android) lisent `personalSnapshot` ; seuls
  l'export, les sauvegardes, la persistance web et les écrans d'équipe
  lisent `snapshot`. Aucun écran n'appelle le filtre lui-même.
- Sur une base sans tâche `TEAM`, `personalView` rend un instantané
  **égal** à l'entrée (propriété testée sur les 480 scénarios différentiels
  existants) : c'est ce qui garantit l'isolation sans toucher aux moteurs.
- Les tâches d'équipe n'ont pas de dépendance vers une tâche personnelle ni
  l'inverse : `updateTask` refuse un bloqueur d'un autre espace (le
  sélecteur de bloqueurs ne les propose pas).

### Modèle (`core/team/`, pur)

- `TeamMember` : `id`, `uid` (échanges), `name`, `role`,
  `availabilityPercent` (1-100), `hoursPerDay` (décimal, 1-24), `isSelf`,
  `archived`, `lastReportAt` (échanges), `createdAt`, `updatedAt`.
- `MemberAbsence` : `id`, `memberId`, `start`, `end` (dates locales,
  incluses, `end ≥ start`), `label`.
- Champs ajoutés à `Task` (tous facultatifs, sans effet sur une tâche
  `PERSONAL`) : `space`, `assigneeId`, `progressPercent` (0-100),
  `startedOn` (date locale), `teamUid` (UUID texte, posé à la création
  d'une tâche d'équipe, sert aux échanges et aux scénarios) ; pour les
  échanges (`equipe-echanges.md`) : `origin` (équipe et manager d'une
  tâche **reçue**), `originRemoved`, `reportedMinutes`.
- `TeamEvent` (journal), `TeamScenario` : `equipe-backlog-suivi.md` et
  `equipe-simulation.md`.
- `TeamSnapshot` : la vue d'équipe ci-dessus, entrée de tous les calculs
  d'équipe (charge, prévisions, suivi).
- Réglages : **un seul champ ajouté à `Settings`**, `team: TeamSettings?`,
  `null` tant que l'espace n'a jamais été activé. `TeamSettings`
  (sérialisable, mêmes règles que `Settings` : champ absent = défaut, champ
  inconnu ignoré, sans migration) : `enabled` (`false`), `name` (`""`),
  `managerName` (`""`), `identity` (UUID de l'équipe, posé à la première
  activation, technique, non affiché), `lastSpace` (technique), et ceux des
  specs de suivi, de charge et de simulation (noms donnés sans le préfixe
  `team` dans le code : `teamWipLimit` des specs est
  `TeamSettings.wipLimit`). `teamModeEnabled` désigne dans ces specs
  `settings.team?.enabled == true`. Champs validés par `SettingsForm`
  (clés préfixées `team.`, bornes données dans chaque spec).

### Stockage (`data`)

- **Une migration additive par jalon**, qui n'ajoute que ce que le jalon
  utilise (aucune table ni colonne sans code qui la lit : bijectivité) :
  - **E1, `1.sqm`** (`user_version` 1 → 2) :
    `ALTER TABLE task ADD COLUMN space INTEGER NOT NULL DEFAULT 0`,
    `ALTER TABLE task ADD COLUMN assignee_id INTEGER`,
    `CREATE TABLE team_member (id, uid, name, role, availability_percent,
    hours_per_day, is_self, archived, created_at, updated_at)`, index
    `task(space)` et `task(assignee_id)` ;
  - **E2, `2.sqm`** (`user_version` 2 → 3) : `member_absence (id, member_id,
    start, end, label, created_at)`, index `member_id` ;
  - **E3, `3.sqm`** (`user_version` 3 → 4) : `task.progress_percent`,
    `task.started_on`, `task.team_uid` (en fin de table, index),
    `team_event (id, task_id, task_title, member_id, kind, from_value,
    to_value, source, at)` (index `task_id`, `member_id`) ;
  - **E5, `4.sqm`** (`user_version` 4 → 5) : `team_scenario` ;
  - **E6** : `task.origin`, `task.origin_removed`, `task.reported_minutes`,
    `team_member.last_report_at` (`equipe-echanges.md`).
  Références sans `FOREIGN KEY` (parti pris existant).
- Une base 2.x importée (`migration-2x.md`) n'a aucune tâche d'équipe :
  `Kairos2Import` n'écrit pas les nouvelles colonnes (défauts SQL).
- `verifyMigrations` couvre la migration ; test d'ouverture d'une base
  version 1 réelle (fixture) : aucune ligne perdue, toutes les tâches
  `PERSONAL`.

### Dépôt (`KairosRepository`)

Opérations transactionnelles (jalon E2 ; à partir d'E3, chacune écrira
aussi son événement de journal dans la même transaction,
`equipe-backlog-suivi.md` § Journal) :

- membres :
  - `createMember(nom, rôle, quotité, heures, moi): Long?` : nom nettoyé,
    vide → `null` ; `uid` tiré ici (`Uuid.random()`) ; quotité et heures
    ramenées dans leurs bornes (garde-fou, la fiche valide avant) ;
  - `updateMember(id, …): Boolean` ; « C'est moi » passe par ces deux
    opérations (pas de `setSelf` séparé) : le poser sur un membre le
    retire aux autres dans la même transaction ; ignoré pour un archivé ;
  - `archiveMember(id)` : ses tâches **à faire** repassent au backlog
    (`assigneeId = null`, `updatedAt` posé), faites et archivées restent à
    son nom, « C'est moi » retiré ; `restoreMember(id)` ; les deux rendent
    `false` si le membre est inconnu ou déjà dans l'état visé ;
  - `deleteMember(id)` : refusé si une tâche, de tout statut et de tout
    espace, lui a été assignée (`TeamMembers.hasHadTask` ; à partir d'E3,
    aussi s'il a un événement de journal) ; supprime ses absences ;
- absences : `addAbsence(membre, début, fin, libellé): Long?`,
  `updateAbsence`, `deleteAbsence` ; refusées si `fin < début` ou membre
  inconnu (un membre archivé en accepte) ;
- tâches d'équipe : voir `equipe-backlog-suivi.md` § Dépôt (E3) ;
- `clearTeamData()` : supprime membres, absences et tâches `TEAM`
  (sous-tâches comprises, avec leurs dépendances et sessions ; à partir
  d'E3 et E5, journal et scénarios aussi) et remet `assigneeId` à `null`
  sur les tâches restantes ; garde `settings.team`. La sauvegarde
  préalable est faite par l'interface (`clearTeamDataWithBackup`).

### Export (`ExportCodec`)

- `formatVersion` **2** : ajoute `members`, `absences`, `teamEvents`,
  `teamScenarios` et, sur les tâches, les champs d'équipe.
- **Règle d'isolation** : une base **sans aucune donnée d'équipe** (aucun
  membre, aucune tâche `TEAM`, aucun événement, aucun scénario, aucune tâche
  reçue) s'exporte en `formatVersion` 1, champs d'équipe omis. Si l'espace
  n'a en plus jamais été activé (`settings.team == null`), l'export est
  celui d'avant, octet pour octet (hors `appVersion` et `exportedAt`) :
  `ExportCodec` encode les valeurs par défaut (`encodeDefaults = true`) mais
  omet les `null` (`explicitNulls = false`), d'où le choix d'un
  `Settings.team` nul plutôt que de réglages à plat. Sinon
  `formatVersion` 2.
- `decode` accepte 1 et 2 ; au-delà, `TOO_NEW` comme aujourd'hui. Un
  export 1 importé donne une base sans données d'équipe.
- Les paquets et rapports sont d'**autres** formats (`format` différent),
  décrits par `equipe-echanges.md`.

### Interface (`ui/team/`, `ui/navigation/`)

- `NavEntry` (interface scellée : libellé, titre, icône, icône pleine),
  implémentée par `Destination` (inchangée, cinq entrées) et par
  `TeamDestination` (`BOARD`, `BACKLOG`, `MEMBERS`, `FORECAST`, `SETTINGS`,
  `START = BOARD`) ; `Space` (`PERSONAL`, `TEAM`), `navigation/Space.kt`.
  `AppShell` affiche les entrées de l'espace courant (paramètres `space`,
  `teamDestination`, `teamModeEnabled`, `teamName`, `onNavigateTeam`,
  `onSpaceChange`) ; en espace Perso, son rendu est celui d'avant (captures
  `--self-test` identiques à l'octet). `NavigationLayout.choose` est
  inchangé.
- `KairosShell` suit `settings.team` seul (`distinctUntilChanged`) ; espace
  initial `TEAM` si le mode est activé et `lastSpace == "team"`, sinon
  `PERSONAL`. Changer d'espace ouvre `Destination.DAY` ou
  `TeamDestination.BOARD` et écrit `lastSpace` par `updateSettings`. Mode
  désactivé alors qu'on est dans l'espace Équipe : l'espace est dérivé
  aussitôt à `PERSONAL` (aucune image intermédiaire), destination Jour, et
  `lastSpace` repasse à `"personal"` pour qu'une réactivation ou un
  redémarrage ne rouvre pas l'espace Équipe.
- Titre de page en espace Équipe : `title_team_named` = « <nom saisi> ·
  <titre> » si un nom d'équipe est renseigné (« Équipe Plateforme · Suivi »),
  sinon le titre seul ; « À propos et guide » n'est jamais préfixé. Avec le
  sélecteur dans la barre du haut, le titre passe sur deux lignes au plus,
  avec ellipse.
- `SpaceSelector` (`ui/team/`) : `SingleChoiceSegmentedButtonRow`, groupe
  annoncé « Espace », deux segments de 48 × 48 dp, icônes seules
  (`contentDescription` « Perso » / « Équipe »), sans la coche du
  composant MD3 (elle élargissait chaque segment à 70 dp). Affiché par
  `AppShell` seulement si `teamModeEnabled` : sous le logo dans l'en-tête du
  `NavigationRail` (le rail s'élargit alors d'environ 80 à 103 dp, en mode
  équipe seulement), sinon en action de la `TopAppBar`.
- `TeamSettingsCard` (`ui/settings/`), après `AppearanceCard` : titre,
  phrase, `SwitchRow` du champ `team.enabled`, puis, si le mode enregistré
  est activé, `SettingInput` de `team.name` et `team.managerName`.
  Désactivation : `SettingsScreen.save()` valide, puis, si le mode
  enregistré passe de vrai à faux, ouvre un `AlertDialog` (« Masquer »,
  « Annuler ») avant d'écrire ; « Annuler » n'écrit rien et laisse le
  formulaire modifié. Le message « Réglages enregistrés. » est émis avant
  l'écriture, l'écran quittant la composition dès que le mode tombe.
- `TeamScreens.kt` (`ui/team/`) : `TeamDestinationScreen` aiguille vers
  `TeamBoardScreen`, `TeamBacklogScreen`, `TeamMembersScreen`,
  `ForecastScreen` (jalon E1 : état vide centré, 720 dp au plus : icône,
  titre, phrase de ce que fera l'écran, « Disponible dans une prochaine
  version. ») ou `SettingsScreen` (le même qu'en espace Perso).
- Lecteurs : `DayScreen`, `WeekScreen`, `NotesScreen`, `StatsScreen`,
  `ChronoWatcher` et `ChronoSync` (Android) lisent `personalSnapshot` ;
  restent sur `snapshot` le thème (`KairosApp`), `Replace.kt`,
  `SettingsScreen`, `Updates.kt` (réglages seuls), `WebServices.kt`,
  `AndroidServices.kt` (bilan de migration) et la coquille.
- Icônes ajoutées au jalon E1 (`tools/make_icons.py`, `KairosIcons.kt`
  regénéré) : `Groups`, `GroupsFilled`, `Person`, `ViewKanban`,
  `ViewKanbanFilled`, `Stacks`, `StacksFilled`, `Monitoring`,
  `MonitoringFilled`. Prévues aux jalons suivants : `PersonAdd`,
  `SwapHoriz` (réaffecter), `EventBusy` (absence), `Casino` (tirage),
  `Science` (scénario), `History` (journal).
- Tests : `WorkspacesTest`, `SettingsFormTest` (`core`),
  `TeamIsolationTest` (`data`), `TeamSpaceUiTest` (`desktopApp` : mode solo
  sans sélecteur ; activation, espace Équipe et retour ; confirmation de la
  désactivation ; tâches d'équipe dans la vue Jour seulement assignées à
  « moi » et mode activé). Auto-test : captures `desktop-settings-full.png`
  (solo), `desktop-team.png`, `desktop-team-narrow.png` (360 dp),
  `desktop-team-settings.png`.
- Jalon E2 (`ui/team/`) : `TeamMembersScreen(services)` (bouton, état vide,
  cartes, « Anciens membres (n) » repliable), `MemberSheet` (branché au
  dépôt) et `MemberSheetContent` (sans dépôt, rendu par l'auto-test),
  `AbsenceEditor` / `AbsenceEditorContent` (`DateRangePicker`),
  `ClearTeamDataButton` (carte Équipe, qui reçoit désormais `services`),
  `TeamDates` (libellés de dates). `LocalWindowWidth` et `isCompactWidth`
  (`navigation/WindowWidth.kt`) donnent aux dialogues la largeur de la
  fenêtre (la même que `NavigationLayout.choose`) : plein écran sous
  600 dp, dialogue de 640 dp au plus au-delà. `Replace.kt` : `saveBackup`
  (partagé avec `replaceWithBackup`, comportement inchangé) et
  `clearTeamDataWithBackup`. Les écrans d'équipe lisent `snapshot` (base
  complète). Calcul pur : `core/team/MemberForm` (validation par champ,
  `FieldError` des Réglages réutilisé, heures par jour par défaut),
  `TeamMembers` (ordre, actifs, archivés, prochaine absence,
  `hasHadTask`, tâches ouvertes), `MemberAbsence`. Accords au singulier et
  au pluriel par des clés `_one` / `_many` (le dépôt n'a pas de ressources
  de pluriel). Icônes : `PersonAdd`, `EventBusy`, `Archive`, `Unarchive`.
  Tests : `MemberFormTest`, `TeamMembersTest`, `TeamMembersRepositoryTest`
  (migration 2 → 3 et 1 → 3 comprises), `TeamMembersUiTest`. Auto-test :
  `desktop-team-members[-narrow].png`, `desktop-team-member-sheet[-narrow].png`,
  `desktop-team-absence[-narrow].png`.
- Textes : toutes les chaînes en `values/` **et** `values-en/`, apostrophe
  typographique (`StringsParityTest`).

### Décisions et alternatives écartées

- **Une colonne `space` sur `task` plutôt qu'une table `team_task`** : les
  tâches d'équipe réutilisent tel quel le score WSJF, les dépendances, la
  récurrence, les sous-tâches, le chrono et la fiche d'édition ; et une
  tâche assignée à « moi » est **une seule ligne** visible des deux
  espaces, sans copie à réconcilier. Coût : le filtre `personalView`
  devient un point de passage obligé, protégé par les tests d'isolation.
- **Une base séparée pour l'équipe** : écartée, elle interdit la tâche
  partagée avec « moi » et double les sauvegardes.
- **Une sixième destination « Équipe »** : écartée, MD3 limite une barre de
  navigation à cinq entrées (décision de `navigation-theme.md`). Deux
  espaces de cinq destinations gardent l'espace Perso intact.
- **Espace désactivé = données masquées, pas supprimées** : désactiver ne
  doit jamais être destructeur ; la suppression est un geste distinct,
  confirmé et précédé d'une sauvegarde.
- **Export 1 tant qu'il n'y a pas de données d'équipe** : un export doit
  rester lisible par une version antérieure tant que rien ne l'exige.
- **Piège tracé : `encodeDefaults = true`** dans `ExportCodec` et dans le
  JSON des réglages en base. Des réglages d'équipe ajoutés à plat dans
  `Settings` apparaîtraient dans tout export et dans toute base, même en
  mode solo ; regroupés dans un `Settings.team` nul, ils n'existent pas
  tant que l'espace n'a jamais été activé.
- **Catégories = types** (décision 5) : pas de second axe de classement à
  maintenir ; la liste des types est commune aux deux espaces.
- **`teamUid` en plus de `id`** : les identifiants entiers ne sont stables
  que dans une base ; les échanges de fichiers ont besoin d'une identité
  qui survit d'une base à l'autre. Les UUID (`teamUid`, `TeamMember.uid`,
  `TeamSettings.identity`) sont tirés par le **dépôt** (`data`,
  `kotlin.uuid.Uuid.random()`), comme l'identité d'équipe au jalon E1 :
  `core` reste sans hasard ni horloge, et aucun appelant ne peut oublier
  d'en fournir un.

### Jalons du chantier

| Jalon | Contenu | Specs |
|---|---|---|
| **E0** | Specs et skills (cette PR). | toutes |
| **E1** | Fondations : migration `1.sqm`, `Task.space` et `Task.assigneeId`, `TeamMember` (lu, écrit par `replaceAll` et l'import seulement), `personalView` branché partout, `TeamSettings` et carte Équipe (activation, nom, manager), sélecteur d'espace en haut (mode activé seulement), coquille à deux espaces (écrans d'équipe en état vide), export 2 et règle d'isolation, tests d'isolation. | `equipe.md` |
| **E2** | Membres et absences (création, fiche, archivage, « C'est moi »), destination Équipe sans charge, « Supprimer les données d'équipe… ». | `equipe.md` |
| **E3** | Backlog, assignation, réaffectation, suivi, journal, tâches assignées à moi. | `equipe-backlog-suivi.md` |
| **E4** | Capacité, charge, plan de charge, suggestion de répartition. | `equipe-charge.md` |
| **E5** | Prévisions Monte Carlo et scénarios. | `equipe-simulation.md` |
| **E6** | Paquets et rapports par fichier. | `equipe-echanges.md` |

Chaque jalon est une PR qui met à jour la spec (état « implémentée »), les
specs existantes impactées, le README (fonctionnalité utilisateur) et les
notes de version Fastlane s'il est publié. E1 doit être sans aucun effet
visible en mode solo : c'est le jalon qui prouve l'isolation.

### Questions ouvertes (à trancher au plus tard au jalon indiqué)

Tranchées le 2026-09-30 :

- ~~E1 : sélecteur d'espace à 360 dp~~ → en haut (rail ou barre
  d'application) seulement si le mode est activé ; icônes seules en
  largeur compacte (§ Deux espaces).
- ~~E3 : assigné d'une tâche récurrente~~ → l'occurrence suivante **garde
  l'assigné** (`equipe-backlog-suivi.md` § États et avancement).
- ~~E6 : partage direct Android~~ → **fichier seulement** : l'usage
  managérial est surtout sur ordinateur (`equipe-echanges.md`).

Relevées en implémentant E1, tranchées le 2026-09-30, codées en E3 :

- `Recurrence.calendarOccurrences` : la clé d'une série « le N du mois »
  devient (titre, jour du mois, **espace**) : une série Perso et une série
  d'équipe de même titre restent distinctes. L'assigné, d'abord retenu
  dans la clé, en a été retiré en codant : réaffecter une occurrence
  aurait changé sa série et fait naître une occurrence de plus chez
  l'ancien titulaire. L'occurrence créée prend l'assigné du membre le plus
  récent de la série.
- Sous-tâches créées en lot par `updateTask` (`newSubtasks`) : elles
  prennent l'**espace et l'assigné de la mère** (règle de
  `equipe-backlog-suivi.md` § Assignation).

### Impacts sur les specs existantes

Reportés au jalon E1 : `modele-donnees.md` (champs de `Task`,
`TeamMember`, `Settings.team`, `team_member`, migration `1.sqm`,
`personalSnapshot`), `export-import.md` (`formatVersion` 2 et règle
d'isolation), `recurrence.md` (espace et assigné des occurrences),
`navigation-theme.md` (espaces, icônes), `reglages.md` (carte Équipe),
`distribution.md` (captures de l'auto-test), `architecture.md` (`core/team/`,
`ui/team/`), `vue-jour.md`, `vue-semaine.md`, `notes-capture.md`,
`statistiques.md`, `temps-reel-chrono.md` (lecture de `personalSnapshot`),
index `docs/spec/README.md`.

Reportés au jalon E2 : `modele-donnees.md` (`MemberAbsence`,
`member_absence`, `2.sqm`, opérations membres et absences),
`export-import.md` (`absences`), `navigation-theme.md` (icônes),
`reglages.md` (suppression des données d'équipe), `distribution.md`
(captures).

Restent à reporter : `modele-donnees.md` (tables et colonnes des jalons
E2-E6, opérations d'équipe du dépôt), `vue-jour.md` (marque « Équipe » des
tâches assignées à moi, E3), `reglages.md` (réglages de charge et de
simulation, E3-E5).
