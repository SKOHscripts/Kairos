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

État : **spécifiée le 2026-09-30, non implémentée.** Aucune ligne de code ne
correspond encore à ces cinq specs : elles sont l'étape 1 du workflow
(`CLAUDE.md`). Les specs existantes (`modele-donnees.md`,
`navigation-theme.md`, `reglages.md`, `export-import.md`, `vue-jour.md`,
`statistiques.md`) **ne sont pas modifiées** tant que le code ne l'est pas,
pour rester bijectives ; chaque spec du chantier liste en fin de document
les « Impacts sur les specs existantes » à reporter au moment de
l'implémentation.

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
  contient un interrupteur « Espace Équipe » (désactivé par défaut) et la
  phrase « Gérer une équipe : membres, backlog partagé, charge et
  prévisions. Sans effet sur vos tâches personnelles. ».
- L'interrupteur suit la règle des Réglages : il fait partie du formulaire
  et prend effet à « Enregistrer » (`reglages.md`).
- Une fois activé, la carte montre aussi les réglages de l'équipe (nom de
  l'équipe, nom du manager pour les paquets, et les paramètres de charge et
  de prévision, `equipe-charge.md` et `equipe-simulation.md`).
- **Désactiver** l'espace demande confirmation (« Masquer l'espace Équipe ?
  Ses données sont conservées et reviendront si vous le réactivez. »). Rien
  n'est supprimé ; tout ce qui relève de l'équipe disparaît de l'interface,
  y compris les tâches d'équipe assignées à « moi » dans la vue Jour.
- Supprimer définitivement les données d'équipe : bouton « Supprimer les
  données d'équipe… » de la carte, confirmation qui annonce le nombre de
  membres et de tâches, sauvegarde automatique préalable comme pour un
  import (`export-import.md`) ; sans sauvegarde réussie, rien n'est
  supprimé.

#### Deux espaces, un sélecteur

- Espace Équipe activé : un **sélecteur d'espace** « Perso | Équipe »
  (bouton segmenté MD3) apparaît en tête du rail (sous le logo) ou, sans
  rail, dans la barre d'application.
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
  celles d'avant l'activation (`TeamIsolationTest.disabledSpaceIsInvisible`).
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
- **Point de passage obligé** : tous les écrans et calculs de l'espace Perso
  (`DayView.build`, `WeekView`, `TaskStats`, `ChronoWatcher`, notifications
  Android, raccourcis) reçoivent `personalView(snapshot)` au lieu de
  `snapshot`. Le filtre est appliqué une fois, là où l'interface lit l'état
  du dépôt (`KairosShell`), jamais dans chaque écran.
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

- **Première migration** du schéma : `1.sqm` (la base passe de
  `user_version` 1 à 2), **additive** seulement :
  - `ALTER TABLE task ADD COLUMN space INTEGER NOT NULL DEFAULT 0`, puis
    `assignee_id INTEGER`, `progress_percent INTEGER`, `started_on TEXT`,
    `team_uid TEXT`, et, pour les échanges (`equipe-echanges.md`),
    `origin TEXT`, `origin_removed INTEGER NOT NULL DEFAULT 0`,
    `reported_minutes INTEGER` ;
  - `CREATE TABLE team_member (…)`, `member_absence (…)`,
    `team_event (…)`, `team_scenario (…)` ;
  - index `task(space)`, `task(assignee_id)`, `task(team_uid)`,
    `member_absence(member_id)`, `team_event(task_id)`,
    `team_event(member_id)`.
  Références sans `FOREIGN KEY` (parti pris existant).
- Une base 2.x importée (`migration-2x.md`) n'a aucune tâche d'équipe :
  `Kairos2Import` n'écrit pas les nouvelles colonnes (défauts SQL).
- `verifyMigrations` couvre la migration ; test d'ouverture d'une base
  version 1 réelle (fixture) : aucune ligne perdue, toutes les tâches
  `PERSONAL`.

### Dépôt (`KairosRepository`)

Opérations ajoutées, transactionnelles, chacune journalisée dans la même
transaction (`equipe-backlog-suivi.md` § Journal) :

- membres : `createMember`, `updateMember`, `setSelf`, `archiveMember`
  (remet les tâches ouvertes au backlog), `restoreMember`, `deleteMember`
  (refusé si le membre a une tâche ou un événement) ;
- absences : `addAbsence`, `updateAbsence`, `deleteAbsence` (refusées si
  `end < start`) ;
- tâches d'équipe : voir `equipe-backlog-suivi.md` § Dépôt ;
- `clearTeamData()` : supprime membres, absences, journal, scénarios et
  tâches `TEAM` (avec leurs dépendances et sessions), après la sauvegarde
  faite par l'interface.

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

- `Space` (enum `PERSONAL`, `TEAM`) et `TeamDestination` (enum `BOARD`,
  `BACKLOG`, `MEMBERS`, `FORECAST`, `SETTINGS`), à côté de `Destination`,
  inchangée. `KairosShell` tient `space` (`rememberSaveable`, et retenu
  entre sessions par le réglage technique `lastSpace`) ; la coquille
  (`AppShell`) reçoit la liste de destinations de l'espace courant.
  `NavigationLayout.choose` est inchangé : rail, barre haute ou barre basse
  selon les règles actuelles, cinq entrées au plus dans chaque espace.
- Sélecteur : `SingleChoiceSegmentedButtonRow` MD3 à deux segments, icônes
  `Person` et `Groups`, affiché seulement si `teamModeEnabled` ; cible
  48 dp. Espace désactivé alors qu'on était dans l'espace Équipe : retour à
  l'espace Perso, destination Jour.
- Écrans : `TeamBoardScreen`, `TeamBacklogScreen`, `TeamMembersScreen`
  (+ `MemberSheet` : fiche, absences), `ForecastScreen` ; carte
  `TeamSettingsCard` dans `SettingsScreen`.
- Icônes à ajouter à `tools/make_icons.py` (jamais à la main) : `Groups`,
  `GroupsFilled`, `Person`, `PersonAdd`, `ViewKanban`, `ViewKanbanFilled`,
  `Stacks`, `StacksFilled` (backlog), `Monitoring`, `MonitoringFilled`
  (prévisions), `SwapHoriz` (réaffecter), `EventBusy` (absence), `Casino`
  (tirage), `Science` (scénario), `History` (journal), `Share` (paquet).
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
  qui survit d'une base à l'autre. Généré par l'interface
  (`kotlin.uuid.Uuid.random()`) et passé au dépôt, `core` restant sans
  hasard ni horloge.

### Jalons du chantier

| Jalon | Contenu | Specs |
|---|---|---|
| **E0** | Specs et skills (cette PR). | toutes |
| **E1** | Fondations : migration `1.sqm`, `space` et champs d'équipe, `personalView` branché partout, réglages et carte Équipe, sélecteur d'espace et coquille à deux espaces (écrans vides), export 2 et règle d'isolation, tests d'isolation. | `equipe.md` |
| **E2** | Membres et absences, destination Équipe (sans charge). | `equipe.md` |
| **E3** | Backlog, assignation, réaffectation, suivi, journal, tâches assignées à moi. | `equipe-backlog-suivi.md` |
| **E4** | Capacité, charge, plan de charge, suggestion de répartition. | `equipe-charge.md` |
| **E5** | Prévisions Monte Carlo et scénarios. | `equipe-simulation.md` |
| **E6** | Paquets et rapports par fichier. | `equipe-echanges.md` |

Chaque jalon est une PR qui met à jour la spec (état « implémentée »), les
specs existantes impactées, le README (fonctionnalité utilisateur) et les
notes de version Fastlane s'il est publié. E1 doit être sans aucun effet
visible en mode solo : c'est le jalon qui prouve l'isolation.

### Questions ouvertes (à trancher au plus tard au jalon indiqué)

- **E1** : le sélecteur d'espace dans la barre d'application sur téléphone
  tient-il à 360 dp avec le titre ? Repli prévu : une icône `Groups` qui
  ouvre un menu à deux entrées.
- **E3** : une tâche d'équipe récurrente garde-t-elle son assigné à
  l'occurrence suivante ? Proposition : oui.
- **E6** : faut-il un partage direct (feuille de partage Android) des
  paquets, en plus de l'enregistrement de fichier ?

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `modele-donnees.md` : champs de `Task`, nouvelles tables, migration
  `1.sqm`, réglages ajoutés, opérations du dépôt.
- `navigation-theme.md` : espaces, sélecteur, destinations d'équipe,
  nouvelles icônes.
- `reglages.md` : carte Équipe et ses champs.
- `export-import.md` : `formatVersion` 2 et règle d'isolation.
- `vue-jour.md`, `vue-semaine.md`, `statistiques.md`,
  `temps-reel-chrono.md` : lecture de `personalView`, marque « Équipe ».
- `architecture.md` : paquets `core/team/` et `ui/team/`.
- `docs/spec/README.md` : lignes de l'index passées d'« à implémenter » à
  leur jalon.
