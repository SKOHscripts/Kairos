---
name: kairos-equipe
description: Guide d'implémentation de l'espace Équipe (mode manager) de Kairos - membres, backlog d'équipe, assignation et réaffectation, suivi et journal, capacité et charge, suggestion de répartition, échanges par fichier - avec l'invariant central d'isolation du mode solo. À utiliser pour tout travail sur les jalons E1 à E6, sur core/team, ui/team, le filtre personalView, les tables team_*, ou dès qu'un changement pourrait faire apparaître une donnée d'équipe dans l'espace Perso.
---

# Implémenter l'espace Équipe

Specs (à lire **avant** de coder, dans cet ordre) :

1. `docs/spec/equipe.md` : cadrage, isolation, modèle commun, jalons E0-E6,
   questions ouvertes.
2. La spec du jalon : `equipe-backlog-suivi.md` (E3), `equipe-charge.md`
   (E4), `equipe-simulation.md` (E5, voir aussi la skill
   `kairos-monte-carlo`), `equipe-echanges.md` (E6).

Workflow : skill `kairos-spec` (spec d'abord, bijectivité) ; interface :
skill `kairos-ecran`.

## Décisions de cadrage à ne pas re-trancher

(`equipe.md` § Décisions de cadrage, 2026-09-30.) Un seul utilisateur, le
manager ; pas de synchronisation ni de réseau ; échanges par fichier ; même
base, espace séparé (`task.space`) ; une seule équipe ; **catégories =
types de tâche** ; « C'est moi » relie les deux espaces. Si une demande les
remet en cause, le signaler et rouvrir la décision explicitement.

## L'invariant : sans incidence en mode solo

C'est la propriété la plus importante du chantier. Elle se garantit par
construction, pas par des `if` dispersés :

- **Un seul filtre** : `Workspaces.personalView(snapshot)` (`core/team/`).
  Tout ce qui est « Perso » (vue Jour, vue Semaine, stats, chrono,
  notifications, raccourcis) le reçoit ; il est appliqué **une fois**, dans
  `KairosShell`, là où l'interface lit l'état du dépôt. Ne jamais passer
  `repository.snapshot` brut à un écran Perso.
- **Réglages** : un seul champ `Settings.team: TeamSettings?`, `null` tant
  que l'espace n'a jamais été activé. **Ne jamais** ajouter un réglage
  d'équipe à plat dans `Settings` : `encodeDefaults = true` le ferait
  apparaître dans tout export et toute base solo.
- **Export** : `formatVersion` 1 sans donnée d'équipe, 2 sinon
  (`equipe.md` § Export).
- **Migrations** : additives seulement (`1.sqm` au jalon E1) ; défauts SQL
  qui laissent chaque tâche existante `PERSONAL`.
- **Interface solo** : la seule différence visible est la carte « Équipe »
  des Réglages, fermée. Aucune autre chaîne, icône ou destination.

Tests d'isolation à garder verts à chaque jalon :

- `personalView(s) == s` pour toute base sans tâche `TEAM` (propriété, sur
  les scénarios différentiels existants) ;
- `TeamIsolationTest.soloExportIsByteIdentical`,
  `TeamIsolationTest.disabledSpaceIsInvisible` ;
- tous les tests existants **sans modification** : un test existant qu'il
  faudrait changer est le signe d'une fuite dans l'espace Perso ;
- captures `--self-test` en mode solo comparées à celles de `main`.

## Règles de code du chantier

- `core/team/` reste **pur** : membres, capacité, effort, plan de charge,
  états, signaux, suggestion, fusion des paquets et rapports. Ni horloge
  (jour et instant en paramètres), ni hasard (UUID et graines générés par
  l'interface), ni I/O.
- **Réutiliser les moteurs**, ne pas les dupliquer : `Scheduling.sortKey` et
  `wsjfScore` (ordre et score), `Dependencies` (blocage, cycles, urgence
  héritée), `Workdays` (jours ouvrés, fériés), `Staleness`, `TimeTracking`,
  `TaskStats.fibonacciCalibration` (effort calibré).
- **Journal écrit par le dépôt** : toute opération qui modifie une tâche
  d'équipe écrit son `TeamEvent` dans la même transaction. Pas de chemin
  de modification sans événement, y compris depuis la vue Jour (source
  `self`).
- **États dérivés** (`TeamStates.of`) de `status`, `assigneeId`,
  `startedOn` : ne pas ajouter de colonne d'état.
- **Identités stables** : `teamUid` (tâches), `TeamMember.uid`,
  `TeamSettings.identity` servent aux échanges et aux scénarios ; les `id`
  entiers ne sortent jamais d'une base.
- Pas de dépendance entre une tâche `TEAM` et une tâche `PERSONAL`.
- Alertes par la **forme** (contour + icône), jamais d'ambre ; dépassement
  de charge en `error` (seul rouge permis ici, avec P0).
- Cinq destinations dans l'espace Équipe (Suivi, Backlog, Équipe,
  Prévisions, Réglages) ; `Destination` de l'espace Perso inchangée.

## Déroulé d'un jalon

1. Relire la spec du jalon et ses « Questions ouvertes » ; trancher avec
   l'utilisateur celles du jalon avant de coder.
2. Coder `core` d'abord, avec ses tests (`core/src/jvmTest/.../team/`).
3. Puis `data` (migration, dépôt, codec) et ses tests.
4. Puis `ui` (skill `kairos-ecran`), chaînes FR + EN.
5. Vérifier :
   ```bash
   cd kmp
   ./gradlew :core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest
   ./gradlew :desktopApp:run --args=--self-test=/tmp/captures
   ```
   et regarder les captures en mode solo **et** espace activé.
6. Tracer : État « implémentée (jalon En) », report des « Impacts sur les
   specs existantes » dans ces specs, index `docs/spec/README.md`,
   `README.md` pour les fonctionnalités visibles.
