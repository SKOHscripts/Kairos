# Espace Équipe : échanges par fichier (paquets et rapports)

_Rôle : faire circuler le travail entre le manager et les membres qui ont
eux aussi Kairos, sans serveur ni réseau : le manager envoie à un membre un
**paquet** de ses tâches ; le membre les traite dans son Kairos personnel et
renvoie un **rapport** d'avancement que le manager intègre. Fichiers
prévus : `kmp/data/.../TeamExchangeCodec.kt` (formats), `kmp/core/.../core/
team/exchange/` (`PackMerge.kt`, `ReportMerge.kt`, purs), `kmp/ui/.../team/
ExchangeDialogs.kt`, et la carte Données (`SettingsScreen.DataCard`).
Formats voisins de l'export (`export-import.md`), mais **fusion ciblée** et
non remplacement._

État : **spécifiée le 2026-09-30, non implémentée** (jalon E6).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le manager est le seul à tenir l'équipe dans son Kairos (décision 1 de
`equipe.md`). Sans échange, il doit recopier l'avancement de chacun. Avec
un simple fichier, transporté comme il veut (courriel, messagerie, clé USB,
dossier partagé), un membre qui utilise Kairos reçoit ses tâches et en
renvoie l'avancement, sans que ni l'un ni l'autre ne perde la main sur ses
données.

### Comportement attendu (utilisateur)

#### Côté manager : envoyer un paquet

- Destination Équipe → fiche d'un membre (hors « moi ») → **« Envoyer ses
  tâches… »** : enregistre `kairos-paquet-<membre>-AAAAMMJJ-HHMM.json`
  (boîte d'enregistrement du système ; téléchargement sur le web).
- Le paquet contient les tâches **ouvertes** assignées au membre (et leurs
  sous-tâches), avec ce qu'il faut pour les traiter : titre, description,
  priorité, points, catégorie, échéance, durée estimée, avancement,
  dépendances entre ces tâches, et, pour un bloqueur tenu par un autre
  membre, une mention en lecture seule (« bloquée par « API v2 » (Marc) »).
- Il porte le nom de l'équipe et du manager (Réglages → Équipe) : c'est
  ainsi que le membre saura d'où viennent les tâches.
- Chaque envoi est journalisé sur les tâches concernées (« envoyée à Léa
  dans un paquet »).

#### Côté membre : recevoir un paquet

- Le membre utilise **le bouton « Importer » habituel** (Réglages →
  Données) : Kairos reconnaît un paquet et, au lieu de « Remplacer toutes
  les données ? », propose **« Recevoir 7 tâches de Corentin (Équipe
  Plateforme) ? »** avec le détail : nouvelles, mises à jour, retirées.
- Aucun espace Équipe n'est nécessaire chez le membre : les tâches reçues
  deviennent des **tâches personnelles** ordinaires (score, placement,
  chrono, notes), marquées d'une icône `Groups` et de l'origine
  (« de Corentin »). Rien d'autre ne change dans son Kairos.
- **Recevoir à nouveau** un paquet du même manager met à jour les tâches
  déjà reçues : ce qui relève du manager (titre, description, priorité,
  points, catégorie, échéance, durée estimée, dépendances du paquet) est
  remplacé ; ce qui relève du membre (fait / à faire, avancement, temps
  passé, sessions, heure fixe, date programmée, notes) est **gardé**.
- Une tâche reçue auparavant et **absente** du nouveau paquet (réaffectée
  ailleurs, supprimée par le manager) n'est jamais supprimée : elle est
  marquée « retirée par Corentin » et le membre choisit de la supprimer ou
  de la garder comme tâche personnelle.
- Une **sauvegarde automatique** est faite avant la réception, comme pour
  un import ; sans elle, rien n'est reçu.

#### Côté membre : renvoyer l'avancement

- Réglages → Données : dès que la base contient des tâches reçues, un
  bouton **« Renvoyer l'avancement… »** apparaît (un par manager s'il y en a
  plusieurs). Il enregistre `kairos-rapport-<membre>-AAAAMMJJ-HHMM.json`.
- Le rapport contient, pour chaque tâche reçue de ce manager : son état
  (faite ou non, date de fin), son avancement, sa date de commencement, le
  temps passé total (chrono et saisie manuelle), et les **nouvelles
  sous-tâches** que le membre y a créées (titre, faite ou non). Rien
  d'autre de sa base n'en sort (ni tâches personnelles, ni notes, ni
  créneaux).
- Le membre qui n'a pas Kairos n'envoie rien : le manager saisit
  l'avancement à la main dans le Suivi.

#### Côté manager : intégrer un rapport

- Le manager utilise aussi **« Importer »** : Kairos reconnaît un rapport et
  affiche un **aperçu** : membre, date du rapport, tâches mises à jour
  (« Migration API : 40 % → 70 %, 6 h passées »), sous-tâches ajoutées,
  lignes ignorées et pourquoi. « Intégrer » applique, « Annuler » ne
  change rien.
- Seuls les champs **du membre** sont appliqués (état, avancement,
  commencement, temps passé) ; ceux du manager ne bougent pas. Chaque
  changement est journalisé avec la source `report`.
- Cas particuliers :
  - tâche **réaffectée** depuis le paquet : l'avancement est intégré (le
    travail a été fait), la ligne est signalée « avancement reçu de
    l'ancien titulaire » dans l'aperçu et le journal ;
  - tâche **inconnue** (supprimée depuis) : ignorée, listée ;
  - rapport **plus ancien** qu'un rapport déjà intégré pour ce membre :
    refusé en entier (« Ce rapport est plus ancien que celui du 28 sept.
    déjà intégré. ») ;
  - rapport d'un **autre manager** (autre équipe) : refusé ;
  - fichier d'un membre **archivé** : intégré (le travail est réel), avec
    avertissement.
- Sauvegarde automatique avant intégration, comme pour la réception.

### Critères de succès

- Paquet → réception → travail → rapport → intégration : chez le manager,
  avancement, état et temps passé sont ceux du membre ; chez le membre,
  aucune donnée personnelle n'a été modifiée hors des tâches reçues
  (`TeamExchangeTest.roundTrip`).
- Recevoir deux fois le même paquet ne crée aucun doublon ; une mise à jour
  du manager écrase ses champs et garde ceux du membre
  (`PackMergeTest`).
- Une tâche absente d'un nouveau paquet n'est jamais supprimée
  (`PackMergeTest.removedTasksAreFlaggedNotDeleted`).
- Un rapport plus ancien, d'une autre équipe, ou illisible ne change rien
  (`ReportMergeTest`).
- Un rapport ne contient aucune tâche non reçue, aucune note, aucun
  créneau (`TeamExchangeCodecTest.reportLeaksNothingPersonal`).
- Un export complet (`export-import.md`) n'est jamais pris pour un paquet ou
  un rapport, et inversement.

### Hors périmètre / différé

- Transport automatique (courriel, dossier synchronisé surveillé, réseau
  local) : Kairos n'a pas de permission réseau ; le transport est l'affaire
  de l'utilisateur.
- Feuille de partage Android : écartée le 2026-09-30, l'usage managérial
  étant surtout sur ordinateur ; **fichier seulement**.
- Chiffrement ou signature des fichiers (comme l'export) : les paquets
  contiennent des titres de tâches ; l'écran de l'envoi le rappelle.
- Tâches créées **par** le membre et remontées au manager (hors
  sous-tâches) : différé ; le membre en parle au manager, qui les crée.
- Commentaires échangés : différé.

## 2. Solution technique

### Formats (`TeamExchangeCodec`, `data`)

Deux formats JSON UTF-8 indentés, distincts de l'export par leur champ
`format` :

```json
{
  "format": "kairos-team-pack",
  "formatVersion": 1,
  "appVersion": "3.x.y",
  "packId": "uuid",
  "exportedAt": "2026-09-30T07:00:00Z",
  "team": { "uid": "uuid", "name": "Équipe Plateforme", "manager": "Corentin" },
  "member": { "uid": "uuid", "name": "Léa" },
  "tasks": [ { "uid": "uuid", "parentUid": null, "title": "…", "priority": 1, "fibonacciPoints": 5, "taskType": "Développement", "deadline": "2026-10-10", "estimatedMinutes": 480, "progressPercent": 40 } ],
  "dependencies": [ { "taskUid": "uuid", "blockerUid": "uuid" } ],
  "externalBlockers": [ { "taskUid": "uuid", "title": "API v2", "assignee": "Marc" } ]
}
```

```json
{
  "format": "kairos-team-report",
  "formatVersion": 1,
  "appVersion": "3.x.y",
  "reportedAt": "2026-10-03T16:00:00Z",
  "team": { "uid": "uuid" },
  "member": { "uid": "uuid", "name": "Léa" },
  "tasks": [ { "uid": "uuid", "status": "todo", "doneOn": null, "startedOn": "2026-10-01", "progressPercent": 70, "spentMinutes": 360 } ],
  "newSubtasks": [ { "uid": "uuid", "parentUid": "uuid", "title": "…", "status": "done" } ]
}
```

- Identités stables : `teamUid` des tâches, `uid` de l'équipe (réglage
  `TeamSettings.identity`, `equipe.md` § Modèle) et `uid` de chaque membre
  (nouvelle colonne `team_member.uid`). Générées par l'interface
  (`kotlin.uuid.Uuid.random()`), jamais par `core`.
- `decode` : même contrat d'erreurs que `ExportCodec` (`NOT_AN_EXPORT`,
  `TOO_NEW`, `CORRUPTED`), champs inconnus ignorés.
- Aiguillage du bouton « Importer » : lecture de `format` d'abord
  (`kairos-export`, `kairos-team-pack`, `kairos-team-report`), puis le
  décodeur du format.

### Réception (`PackMerge`, pur)

- Entrées : l'instantané du membre, le paquet ; sortie : un plan de
  fusion (`created`, `updated`, `removed`, `unchanged`) appliqué par le
  dépôt en **une** transaction (`KairosRepository.receivePack`).
- Tâche reçue : `space = PERSONAL` (le membre n'a pas d'espace Équipe),
  `teamUid` = celui du paquet, `origin` = `teamUid de l'équipe` + nom du
  manager (pour l'afficher et regrouper les rapports).
- Correspondance par `teamUid` **et** équipe d'origine ; champs du manager
  écrasés, champs du membre gardés (liste du § 1).
- Retirées : `origin` gardée, `originRemoved = true` (colonne
  `task.origin_removed`, 0 par défaut) ; jamais de suppression. Les
  recevoir à nouveau dans un paquet ultérieur remet `originRemoved` à
  faux.
- Dépendances : celles du paquet remplacent les précédentes **entre tâches
  reçues** de ce manager ; celles que le membre a posées avec ses propres
  tâches sont gardées. Bloqueurs externes : affichés en lecture seule, ils
  ne bloquent pas l'ordonnancement du membre (il ne voit pas leur état).

### Intégration (`ReportMerge`, pur)

- Entrées : l'instantané d'équipe du manager, le rapport ; refus si
  l'équipe diffère ou si `reportedAt` ≤ le dernier rapport intégré de ce
  membre (colonne `team_member.last_report_at`).
- Temps passé : le rapport porte un **total** ; le manager stocke le
  dernier total reçu (colonne `task.reported_minutes`), qui s'ajoute aux
  sessions et au temps manuel de la tâche chez le manager (et non l'écrase)
  dans tous les calculs de temps passé (`equipe-charge.md`,
  `equipe-simulation.md`). Un total reçu remplace le précédent : pas de
  double comptage d'un rapport à l'autre.
- `doneOn` du rapport sert de date de fin (événement « fin » daté de ce
  jour), pour que débit et délais ne dépendent pas du jour d'intégration.
- Nouvelles sous-tâches : créées comme tâches d'équipe du même assigné,
  journalisées.

### Interface

- Carte Données : aiguillage de « Importer » ; bouton « Renvoyer
  l'avancement… » par origine présente (invisible sans tâche reçue : un
  Kairos qui n'a jamais reçu de paquet est inchangé, `equipe.md` §
  Isolation).
- `ExchangeDialogs` : aperçu de réception et d'intégration en
  `AlertDialog` à liste défilante (compteurs, puis lignes), « Recevoir » /
  « Intégrer » en bouton de confirmation, « Annuler ».
- Vue Jour du membre : icône `Groups` + « de Corentin » sur les tâches
  reçues ; « retirée par Corentin » en contour + icône.

### Décisions et alternatives écartées

- **Fusion ciblée plutôt que remplacement** : l'import complet remplace
  tout (`export-import.md`) parce qu'une fusion générale créerait des
  doublons indétectables ; ici, les `teamUid` rendent chaque tâche
  identifiable d'une base à l'autre, et chaque champ a un seul
  propriétaire (manager ou membre) : il n'y a pas de conflit à arbitrer.
- **Propriété des champs plutôt que « le plus récent gagne »** : les
  horloges de deux appareils ne sont pas comparables, et une règle par
  champ est prévisible pour les deux personnes.
- **Tâches reçues en espace Perso chez le membre** : le membre n'a pas à
  activer un mode manager pour recevoir du travail ; l'espace Équipe reste
  l'outil du manager.
- **Même bouton « Importer »** : pas de nouvelle entrée dans l'interface
  solo ; le fichier dit ce qu'il est.
- **Total de temps plutôt que sessions** : le manager n'a pas besoin du
  détail horaire de son équipe, et le rapport en dit moins sur la journée
  du membre.
- **Refus des rapports plus anciens** : intégrer un vieux rapport après un
  récent ferait reculer l'avancement.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `export-import.md` : aiguillage de « Importer » par `format`, bouton
  « Renvoyer l'avancement… ».
- `modele-donnees.md` : `team_member.uid`, `team_member.last_report_at`,
  `task.reported_minutes`, `task.origin`, `task.origin_removed`.
- `vue-jour.md` : marque des tâches reçues et retirées.
- `temps-reel-chrono.md` / `statistiques.md` : temps rapporté compté dans le
  temps passé des tâches d'équipe.
