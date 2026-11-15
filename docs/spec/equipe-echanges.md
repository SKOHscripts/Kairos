# Espace Équipe : échanges par fichier (paquets et rapports)

_Rôle : faire circuler le travail entre le manager et les membres qui ont
eux aussi Kairos, sans serveur ni réseau : le manager envoie à un membre un
**paquet** de ses tâches ; le membre les traite dans son Kairos personnel et
renvoie un **rapport** d'avancement que le manager intègre. Fichiers :
`kmp/data/.../TeamExchangeCodec.kt` (formats), `kmp/core/.../core/
team/exchange/` (`PackBuilder.kt`, `PackMerge.kt`, `ReportBuilder.kt`,
`ReportMerge.kt`, `TeamOrigin.kt` avec `ReceivedTasks`, purs),
`core/model/ExchangeNote.kt` (note d'un échange), `kmp/ui/.../team/ExchangeDialogs.kt`
et `ExchangeFiles.kt`, la carte Données (`SettingsScreen`) et la fiche
membre ; jeu d'exemples des captures : `desktopApp/.../ExchangeSeed.kt`.
Formats voisins de l'export (`export-import.md`), mais **fusion ciblée** et
non remplacement._

État : **implémentée (jalon E6, 2026-10-01)**.

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
- Un paquet de **sa propre équipe** (le manager qui s'importe son propre
  envoi) est refusé : ces tâches sont déjà dans son espace Équipe.
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
  créneaux), hormis les tâches qu'il a **proposées** au manager (3.9.0,
  § Tâches proposées par un membre) et sa note sur chaque tâche reçue.
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
  - rapport d'un membre dont la fiche a été **supprimée** depuis : refusé ;
  - fichier d'un membre **archivé** : intégré (le travail est réel), avec
    avertissement.
- Sauvegarde automatique avant intégration, comme pour la réception.

#### Notes jointes aux échanges (3.9.0, issue #120)

Quand un membre rapporte 70 %, il ne peut pas dire « bloqué en attendant la
clé d'API », et le manager ne peut pas lui répondre : le contexte passe par
d'autres canaux et n'atteint jamais la tâche.

- Chaque tâche échangée a une **note courte** de chaque côté, pas un fil de
  discussion : le manager écrit la sienne dans la fiche d'une tâche d'équipe
  (« Note pour le membre »), le membre la sienne dans la fiche d'une tâche
  reçue (« Note pour le manager ») ; 500 caractères au plus, texte brut.
- Le **paquet** emporte la note du manager, le **rapport** celle du membre.
  Chaque échange porte au plus la **dernière** note de chaque côté.
- La fiche montre la note reçue de l'autre côté, avec son **auteur** et sa
  **date** (celle du paquet ou du rapport) : « Note de Léa, 3 oct. ». Un
  échange sans note efface la note reçue précédente (elle n'est plus
  d'actualité).
- Les aperçus avant réception (paquet) et avant intégration (rapport)
  listent les notes reçues. Chez le manager, une note reçue entre dans le
  **journal** de la tâche (« Note de Léa : … »), comme une note envoyée
  (« Note envoyée : … »).
- Le champ rappelle que la note voyage **en clair** dans le fichier, qui
  n'est pas chiffré ; elle ne contient que ce qu'on y écrit.

#### Tâches proposées par un membre (3.9.0, issue #121)

En travaillant, un membre découvre des tâches (un bug, une étape oubliée)
qui ont leur place au backlog de l'équipe. Les faire recopier par le
manager perd du détail et du temps.

- Chez le membre, la fiche d'une **tâche personnelle** (ni reçue, ni
  sous-tâche) propose, dès qu'il a reçu des tâches d'un manager,
  l'interrupteur **« Proposer à Corentin (Équipe Plateforme) »**. La tâche
  part alors dans chaque rapport à ce manager comme une **proposition** :
  titre, description, durée estimée, type ; jamais ses champs privés
  (priorité, points, dates, sessions, notes, projet).
- Chez le manager, l'aperçu du rapport liste les propositions **à part** :
  pour chacune, son titre (modifiable), sa description, sa durée et son
  type, et un choix **« Plus tard »** (par défaut), **« Ajouter au
  backlog »** ou **« Refuser »**. Rien n'est créé avant « Intégrer ». Une
  proposition ajoutée devient une tâche d'équipe **au backlog** (sans
  titulaire : l'assigner reste au manager), journalisée comme créée par le
  rapport de ce membre.
- Intégrer à nouveau le même rapport, ou un rapport suivant qui porte la
  même proposition, ne crée **aucun doublon** : une proposition déjà ajoutée
  ou refusée n'est plus proposée au choix (identité stable). « Plus tard »
  la laisse revenir au rapport suivant.
- Quand le manager envoie ensuite cette tâche au membre, son Kairos la
  **rattache** à la tâche qu'il avait proposée (pas de doublon) : elle
  devient une tâche reçue, marquée « de Corentin ».
- Un rapport sans proposition est identique à celui d'avant.

#### Onglet « Échanges » : tout au même endroit (manager)

L'écran Équipe a deux onglets, **« Membres »** (l'écran d'avant) et
**« Échanges »**, dès qu'il y a au moins un membre. L'onglet « Échanges »
rassemble ce qui était réparti entre la fiche du membre et les Réglages :

- **« Comment partager l’avancement »** : les quatre étapes en clair
  (envoyer ses tâches ; le membre les importe, travaille et renvoie son
  avancement ; recevoir son rapport ici ; le voir dans le Suivi, ou saisir
  l'avancement à la main pour un membre sans Kairos), et le rappel que les
  fichiers ne sont pas chiffrés.
- **« Recevoir un rapport… »** : choisit le fichier reçu du membre et ouvre le
  même aperçu que « Importer » des Réglages (« Intégrer » / « Annuler »). Un
  fichier qui n'est pas un échange d'équipe (sauvegarde complète) n'est pas
  appliqué : un message renvoie vers Réglages → Données → Importer ; un
  paquet est reçu normalement (aperçu), mais son aperçu dit déjà « de votre
  propre équipe » et le refuse.
- **Une ligne par membre actif** (sauf « moi ») : son nom, l'état de
  l'échange (« Paquet envoyé le 28 sept. » ou « Aucun paquet envoyé » ;
  « Rapport reçu le 1 oct. » ou « Aucun rapport reçu »), le signal
  **« En attente de son rapport »** (contour + icône, jamais une teinte) quand
  un paquet a été envoyé après le dernier rapport intégré, et le bouton
  **« Envoyer ses tâches… »** (le même que celui de la fiche). Toucher le
  nom ouvre la fiche du membre.
- Un lien **« Visite de l’espace Équipe »** vers la visite guidée.

Le membre, lui, n'a pas d'espace Équipe : il garde « Importer » et
« Renvoyer l'avancement… » dans Réglages → Données. Pour qu'il les trouve,
l'Accueil, la visite de Kairos (dernière étape) et l'aide des Réglages
l'expliquent (`accueil.md`).

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
- Notes (3.9.0) : la note du manager arrive chez le membre avec son auteur
  et la date du paquet, celle du membre chez le manager avec la date du
  rapport ; recevoir deux fois le même échange ne change ni la note ni sa
  date ; un échange sans note efface la note reçue ; un paquet ou un rapport
  sans note est identique à celui d'avant (`ExchangeNotesTest`,
  `TeamExchangeCodecTest`).
- Propositions (3.9.0) : un rapport porte les tâches proposées et rien de
  leurs champs privés ; « Ajouter au backlog » crée une tâche d'équipe au
  backlog avec le titre éventuellement modifié ; « Refuser » la retient ;
  réintégrer le rapport ne crée aucun doublon ; un paquet qui la renvoie au
  membre la rattache à sa tâche (`ProposalsTest`) ; l'interrupteur de la fiche
  propose la tâche (`TeamExchangeUiTest.aPersonalTaskCanBeProposedToTheManager`).
- L'onglet « Échanges » donne, par membre, la date du dernier paquet, celle du
  dernier rapport et « En attente de son rapport » seulement quand un paquet
  est plus récent que le dernier rapport (`ExchangeStatusTest`) ; « Envoyer ses
  tâches… » y fonctionne comme dans la fiche, et « Recevoir un rapport… »
  ouvre l'aperçu d'intégration (`M6GuideUiTest.theExchangesTabListsMembersAndReceivesAReport`).

### Hors périmètre / différé

- Transport automatique (courriel, dossier synchronisé surveillé, réseau
  local) : Kairos n'a pas de permission réseau ; le transport est l'affaire
  de l'utilisateur.
- Feuille de partage Android : écartée le 2026-09-30, l'usage managérial
  étant surtout sur ordinateur ; **fichier seulement**.
- Chiffrement ou signature des fichiers (comme l'export) : les paquets
  contiennent des titres de tâches ; l'écran de l'envoi le rappelle.
- Accepter une proposition en l'assignant directement à son auteur : elle
  arrive au backlog, l'assignation reste un geste du manager (3.9.0).
- Fil de commentaires (plusieurs messages, réponses) : une note par côté
  suffit (3.9.0, issue #120) ; le fil reste différé.

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
  "tasks": [ { "uid": "uuid", "parentUid": null, "title": "…", "priority": 1, "fibonacciPoints": 5, "taskType": "Développement", "deadline": "2026-10-10", "estimatedMinutes": 480, "progressPercent": 40, "description": null } ],
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
  (colonne `team_member.uid`). Tirées par le dépôt (`data`,
  `kotlin.uuid.Uuid.random()`), jamais par `core` (`equipe.md`
  § Décisions).
- `decode` : même contrat d'erreurs que `ExportCodec` (`NOT_AN_EXPORT`,
  `TOO_NEW`, `CORRUPTED`), champs inconnus ignorés. Les décodages du dépôt
  lèvent `ImportException`. Un rapport dont un temps passé est négatif ou
  dépasse `ReportFile.MAX_SPENT_MINUTES` (600 000 min, 10 000 h sur une seule
  tâche) est `CORRUPTED` : l’aperçu montrait « 1666666 h 39 » (test exploratoire
  du 2026-10-11).
- Aiguillage du bouton « Importer » : `TeamExchangeCodec.detect(text)` lit
  `format` seul (`kairos-export` → `EXPORT`, `kairos-team-pack` → `PACK`,
  `kairos-team-report` → `REPORT`, autre → `NOT_AN_EXPORT`), puis le
  décodeur du format.
- Modèle pur des deux fichiers dans `core/team/exchange/ExchangeModels.kt`
  (`TeamPack`, `PackTask`, `PackDependency`, `ExternalBlocker`,
  `TeamReport`, `ReportTask`, `ReportSubtask`) ; `TeamExchangeCodec` ne fait
  que la traduction JSON.

### Notes (3.9.0, issue #120)

- **Modèle** : `Task.noteOut` (la note que **ce** côté enverra, texte, vide par
  défaut) et `Task.noteIn: ExchangeNote?` (la dernière note reçue de l'autre
  côté : `text`, `at`, `from`). `ExchangeNote.MAX_LENGTH` = 500 ; au-delà, un
  texte reçu est tronqué, une saisie est bornée par le champ.
- **Base** : colonnes `task.note_out` (texte, `''` par défaut) et
  `task.note_in` (texte nul ; « instant␟auteur␟texte », séparateur U+001F,
  comme `TeamOrigin`), ajoutées par `9.sqm` (schéma 10, avec
  `team_member.week_hours`). **Export** : `noteOut` et `noteIn` d'une tâche,
  absents quand vides (un export sans note ne change pas).
- **Formats** : `PackTask.note` et `ReportTask.note`, facultatifs (`note`
  dans le JSON, omis quand nul) : une version antérieure les ignore ; un
  paquet ou un rapport sans note est identique à celui d'avant.
- **Envoi** : `PackBuilder` pose `note` = `noteOut` de la tâche (vide = nul) ;
  `recordPackSent` écrit, pour chaque tâche qui en porte une, un événement
  `note` (valeur : le texte, membre : destinataire, source `manual`).
- **Réception** (`PackMerge`, champ du manager) : `noteIn` = la note du paquet
  (auteur : nom du manager, date : `exportedAt`), gardée telle quelle si le
  texte n'a pas changé (pas de nouvelle date), effacée si le paquet n'en porte
  pas ; `PackField.NOTE` dans les champs changés ; `PackMergePlan.notes` liste
  (titre, texte) des notes nouvelles pour l'aperçu.
- **Rapport** (`ReportBuilder`) : `note` = `noteOut` de la tâche reçue.
- **Intégration** (`ReportMerge`) : `ReportChange.Note(from, to)` quand le
  texte change ; `noteIn` = (texte, `reportedAt`, nom du membre), nul si le
  rapport n'en porte plus ; le dépôt journalise un événement `note` (source
  `report`, membre : celui qui rapporte) quand une note arrive.
- **Journal** : `TeamEventKind.NOTE` (« note »), affiché « Note de Léa : … »
  (source `report`) ou « Note envoyée : … » (sinon) dans l'historique.
- **Interface** : `EditTaskDialog`, section « Note » d'une tâche d'équipe et
  d'une tâche reçue (`ExchangeNoteSection`) : la note reçue (« Note de Léa,
  3 oct. ») puis le champ de la note à envoyer, avec l'aide « Part avec le
  prochain paquet (rapport), en clair : le fichier n'est pas chiffré. » ;
  `TaskEdit.noteOut` (nul = inchangé). Aperçus : `ExchangePreviews` liste
  « Notes : « titre » : texte ».
- **Décisions** : *une note par côté*, pas un fil (demande de l'issue) ;
  *la note reçue est un champ de l'autre côté* : écrasée à chaque échange,
  effacée par un échange qui n'en porte pas (écarté : la garder, au risque
  d'afficher une consigne périmée) ; *date = celle du fichier* (écarté : une
  date de saisie, qui ferait sortir une donnée de plus) ; *aucune note dans
  un rapport ou un paquet sans note* (format inchangé).

### Propositions (3.9.0, issue #121)

- **Membre** : `Task.proposedTo` (clé d'origine `TeamOrigin.key` du manager
  visé, `null` sinon ; colonne `task.proposed_to`, `9.sqm` ; export
  `proposedTo`, omis quand nul). `TaskEdit.proposal` (`ProposalChange(to)`,
  `null` = inchangée), appliqué par `updateTask` à l'enregistrement de la
  fiche (« Annuler » n'écrit rien) : tâche personnelle, sans origine ni mère ;
  pose un `teamUid` (UUID tiré par le dépôt) s'il n'y en a pas, l'identité de
  la proposition.
  `ReportBuilder.proposals` : tâches personnelles à faire, sans origine,
  `proposedTo` = cette équipe ; `TeamReport.proposals` (`ReportProposal` :
  `uid`, `title`, `description`, `estimatedMinutes`, `taskType`), champ JSON
  `proposals` **omis quand vide**. Une tâche proposée que le paquet suivant
  contient est **adoptée** par `PackMerge` (règle des sous-tâches remontées) :
  elle prend l'origine, et cesse d'être une proposition (`origin` non nulle).
- **Manager** : `ReportMergePlan.proposals` (`PlannedProposal` : la
  proposition et son état `NEW`, `IN_TEAM` — une tâche d'équipe a déjà ce
  `teamUid` — ou `REJECTED` — `TeamSettings.rejectedProposals`, uids refusés,
  vide par défaut et jamais écrit vide). `integrateReport(text, decisions)` :
  `ProposalDecision.Add(title)` crée la tâche d'équipe au backlog (`teamUid` =
  uid de la proposition, description, durée, type ; titre nettoyé, celui de
  la proposition s'il est vide ; événement `created`, source `report`,
  membre = celui qui rapporte) ; `ProposalDecision.Reject` ajoute l'uid aux
  refusées ; sans décision, rien. Seules les propositions `NEW` acceptent
  une décision.
- **Interface** : membre, section « Proposer au manager » de la fiche d'une
  tâche personnelle (`ProposalSection`, un interrupteur par équipe d'origine) ;
  manager, section « Propositions » de l'aperçu du rapport, une carte par
  proposition `NEW` (titre modifiable, résumé, boutons segmentés « Plus
  tard » / « Ajouter » / « Refuser ») et la mention des déjà traitées.
- **Décisions** : *identité = `teamUid` de la tâche du membre* (écarté : un
  identifiant de proposition à part, qui aurait demandé un rattachement de
  plus) ; *« Plus tard » par défaut* (rien n'est créé sans choix explicite) ;
  *refus retenu côté manager* (écarté : un retour au membre, qui demanderait
  un paquet) ; *au backlog, sans titulaire* (demande de l'issue).

### Envoi (`PackBuilder`, pur)

- `PackBuilder.build(snapshot, memberId, packId, exportedAt)` : les tâches
  d'équipe **à faire** du membre, plus leurs sous-tâches à faire à tout
  niveau (quel que soit leur assigné) ; `null` s'il n'y en a aucune
  (`KairosRepository.exportPack` renvoie alors `null`). Une sous-tâche
  faite côté manager sort donc du paquet, et sera marquée retirée chez le
  membre. Un paquet **vide** est valide : il marque retirées toutes les
  tâches reçues auparavant (voir § Interface).
- Dépendances entre tâches du paquet : `dependencies` ; un bloqueur hors
  paquet sort en `externalBlockers` (titre, nom de son assigné, sans nom
  s'il est au backlog).
- Journal : un événement `sent` par tâche envoyée (valeur : `packId`,
  membre : destinataire, source `manual`), écrit par `recordPackSent`
  **après** l'enregistrement du fichier (§ Interface).

### Réception (`PackMerge`, pur)

- Entrées : l'instantané du membre, le paquet, l'instant ; sortie : un
  `PackMergePlan` (`created`, `updated`, `removed`, `unchanged`,
  `dependencyAdds`, `dependencyRemoves`, `skippedDependencies`,
  `externalBlockers`, `refusal`) appliqué par le dépôt en **une**
  transaction (`previewPack` calcule le plan sans écrire, `receivePack`
  l'applique et renvoie `PackReport(plan, applied)`).
- Refus `OWN_TEAM` : un paquet de sa propre équipe (même identité que
  `TeamSettings.identity`) n'est pas reçu.
- Tâche reçue : `space = PERSONAL` (le membre n'a pas d'espace Équipe),
  `teamUid` = celui du paquet, `origin` = `TeamOrigin` encodée : identité
  et nom de l'équipe, nom du manager, `uid` et nom du membre désigné, cinq
  champs séparés par U+001F (retiré des noms à l'écriture). L'`uid` du
  membre sert à adresser le rapport ; les rapports se regroupent par
  identité d'équipe (`TeamOrigin.key`).
- Correspondance par `teamUid` **et** équipe d'origine ; champs du manager
  écrasés, champs du membre gardés (liste du § 1). Les écritures de
  réception ne touchent pas `updatedAt` (la date de fin des statistiques du
  membre en dépend).
- Retirées : `origin` gardée, `originRemoved = true` (colonne
  `task.origin_removed`, 0 par défaut) ; jamais de suppression. Les
  recevoir à nouveau dans un paquet ultérieur remet `originRemoved` à
  faux.
- Sous-tâches créées par le membre et déjà remontées (elles ont reçu un
  `teamUid` au premier rapport) : le paquet suivant qui les contient les
  **adopte** (origine posée) au lieu de les dupliquer ; un champ que le
  paquet laisse vide garde la valeur du membre.
- Dépendances : celles du paquet remplacent les précédentes **entre tâches
  reçues** de ce manager ; celles que le membre a posées avec ses propres
  tâches sont gardées. Une dépendance du paquet qui fermerait un cycle avec
  celles du membre est écartée (`skippedDependencies`). Bloqueurs externes :
  ni persistés ni bloquants (le membre ne voit pas leur état), ils ne
  figurent que dans l'aperçu de réception.
- Le membre règle l'avancement d'une tâche reçue
  (`KairosRepository.setReceivedProgress`, arrondi à la dizaine comme
  `setProgress`, `startedOn` posé au-dessus de 0, sans journal : le membre
  n'a pas d'espace Équipe).
- `clearTeamData` ne touche pas aux tâches reçues : chez le membre, ce sont
  des tâches personnelles. `Workspaces.hasTeamData` les compte (export 2).

### Rapport (`ReportBuilder`, pur)

- `ReportBuilder.build(snapshot, originKey, now, timeZone)` : les tâches
  reçues **non retirées** de cette équipe ; `null` s'il n'y en a aucune
  (`KairosRepository.buildReport` renvoie alors `null`).
- Par tâche : état ; `doneOn` = jour local de `updatedAt` d'une tâche
  faite ; avancement (100 pour une tâche faite) ; `startedOn` = celui de la
  tâche, sinon le jour de la première session, sinon le jour du rapport si
  un avancement est déclaré ; temps passé total (sessions et temps manuel).
- Nouvelles sous-tâches du membre : un `teamUid` leur est posé au premier
  rapport (écriture technique, `updatedAt` inchangé), stable ensuite.

### Intégration (`ReportMerge`, pur)

- Entrées : l'instantané d'équipe du manager, le rapport, le fuseau ;
  sortie : un `ReportMergePlan` (`member`, `memberArchived`, `updates` en
  `ReportChange` `Status` / `Progress` / `Started` / `Spent` de → vers,
  `unchanged`, `newSubtasks`, `ignored`, `fromFormerHolder`, `refusal`) ;
  `previewReport` le calcule sans écrire, `integrateReport` l'applique en
  une transaction et renvoie `ReportIntegration(plan, applied)`.
- Refus : `WRONG_TEAM` (identité d'équipe différente), `UNKNOWN_MEMBER`
  (fiche du membre supprimée depuis l'envoi), `OLDER` (`reportedAt` ≤ le
  dernier rapport intégré de ce membre, colonne
  `team_member.last_report_at`, mise à jour à chaque intégration),
  `FROM_THE_FUTURE` (`reportedAt` de plus d'un jour après l'instant du
  manager, `ReportMerge.FUTURE_TOLERANCE` : l'intégrer poserait
  `last_report_at` dans le futur et ferait refuser tous les vrais rapports
  suivants ; le contrôle n'a lieu que si `plan` reçoit `now`, ce que font
  `previewReport` et `integrateReport`).
- Avancement intégré sans arrondi (borné à 0-100) ; date de commencement :
  la **plus ancienne** des deux (celle du membre n'est souvent qu'un
  repli, le jour du rapport, et ne doit pas effacer un commencement connu
  du manager) ; une tâche rouverte sans avancement repasse à 0.
- Temps passé : le rapport porte un **total** ; le manager stocke le
  dernier total reçu (colonne `task.reported_minutes`), que
  `TimeTracking.spentMinutesByTask` ajoute aux sessions et au temps manuel
  (point de passage unique : calibration, facteurs d'erreur des
  prévisions, statistiques). Un total reçu remplace le précédent : pas de
  double comptage d'un rapport à l'autre. N'étant pas daté, il n'entre pas
  dans les facteurs de capacité hebdomadaires de `ForecastData`.
- `doneOn` du rapport sert de date de fin : l'événement `done` est daté de
  midi local ce jour-là, sans dépasser l'instant courant (jour du rapport à
  défaut), pour que débit et délais ne dépendent pas du jour
  d'intégration. Terminer une tâche ferme son chrono et crée l'occurrence
  suivante d'une récurrente, comme `toggleDone`.
- Journal : source `report` ; le temps rapporté a son propre événement
  `time` (de → vers, en minutes). Le membre d'un événement `report` est
  celui qui rapporte, pas l'assigné actuel : c'est ce qui signale « reçu de
  l'ancien titulaire ».
- Nouvelles sous-tâches : créées comme tâches d'équipe du même assigné,
  journalisées ; une sous-tâche déjà connue (même `uid`) n'est pas recréée,
  seul son état suit.

### Interface

- **Envoyer** (`ui/team/ExchangeFiles.kt`, `sendPack`) : section
  « Échanges » de la fiche d'un membre (`MemberSheetContent`), absente pour
  « moi » ; bouton « Envoyer ses tâches… » absent pour un membre archivé ;
  rappel que le fichier n'est pas chiffré ; date du dernier rapport intégré
  (`lastReportAt`) ou « Aucun rapport intégré pour l'instant ».
  - `KairosRepository.preparePack` prépare le paquet **sans écrire**
    (`PreparedPack` : texte, `packId`, membre, `uid` envoyés,
    `previouslySent`) ; `FileService.saveText` enregistre
    `kairos-paquet-<membre>-AAAAMMJJ-HHMM.json` (`fileSlug`, `fileStamp`) ;
    **seulement si le fichier est écrit**, `recordPackSent` journalise
    l'envoi (événements `sent` sur les tâches d'équipe portant encore ces
    `uid`). Annuler la boîte d'enregistrement ne laisse aucune trace.
    `exportPack` (préparer puis journaliser aussitôt) reste pour les tests
    et le jeu du self-test.
  - Membre sans tâche à faire : jamais servi → « n'a aucune tâche à faire »,
    aucun fichier ; déjà servi (`previouslySent` : un événement `sent` pour
    lui) → le **paquet vide** est enregistré (« il retire les tâches
    envoyées à Léa ») : sans lui, un membre dont tout le travail est
    réaffecté ne l'apprendrait jamais.
- **Onglet « Échanges »** (`ui/team/ExchangeHub.kt`, `ExchangeHub`) :
  `TeamMembersScreen` met une `SecondaryTabRow` (« Membres », « Échanges »)
  au-dessus de son contenu, seulement s'il y a des membres ; l'onglet
  choisi est `NavState.exchangesTab` (le guide peut ainsi ouvrir
  l'onglet ; `TeamMembersScreen(…, nav)`, valeur propre par défaut pour les
  captures). Le contenu d'avant est inchangé sous « Membres ».
  - **État par membre** : `ExchangeStatus.of(members, events)`
    (`core/team/exchange/ExchangeStatus.kt`, pur) → `MemberExchange(member,
    lastPackAt, lastReportAt, awaitingReport)` pour les membres actifs hors
    « moi », ordre de `TeamMembers.active`. `lastPackAt` = date du plus récent
    événement `sent` dont `memberId` est le membre ; `lastReportAt` = celui de
    la fiche ; `awaitingReport` ⇔ `lastPackAt != null` et (`lastReportAt ==
    null` ou `lastReportAt < lastPackAt`).
  - « Envoyer ses tâches… » appelle `sendPack` (inchangé) ; « Recevoir un
    rapport… » lit le fichier (`FileService.openText`), `readImportedFile`
    puis `ExchangePreviewDialog` / `applyExchange`, comme la carte Données.
    Un `ImportedFile.Full` n'est pas appliqué (message
    `exchange_hub_full_file`, jamais un remplacement de toutes les données
    depuis l'écran Équipe).
- **Importer** (carte Données, `SettingsScreen`) : `readImportedFile`
  aiguille par `TeamExchangeCodec.detect` ; un export complet garde son
  dialogue « Remplacer toutes les données ? » (`export-import.md`), un
  paquet ou un rapport ouvre un **aperçu** (`ExchangePreview`, calculé par
  `previewPack` / `previewReport`, sans écriture).
  - Confirmer (`applyExchange`) : sauvegarde `avant-reception-paquet-…json`
    ou `avant-integration-rapport-…json` (`BackupStore`), puis
    `receivePack` ou `integrateReport` ; sauvegarde en échec → rien n'est
    reçu ni intégré. Message de bilan dans la snackbar.
  - Aperçu de paquet : « Recevoir N tâches de Corentin (Équipe
    Plateforme) ? » (N = créées + mises à jour + inchangées non retirées),
    « Mettre à jour les tâches reçues de … ? » quand il n'y a que des
    retraits ; listes Nouvelles, Mises à jour (champs changés), Retirées
    (« elles ne sont pas supprimées »), inchangées, dépendances ajoutées /
    retirées / écartées (boucle), bloqueurs externes « à titre
    d'information », avertissement si les tâches déjà reçues visaient un
    autre membre. Un paquet sans changement visible (`isNoop` ou sans
    `visibleUpdates`) n'offre que « Fermer » : « Rien à changer ».
  - Aperçu de rapport : « Intégrer le rapport de Léa ? », date du rapport,
    changements de → vers (statut, avancement, commencement, temps passé),
    « Avancement reçu de l'ancien titulaire », nouvelles sous-tâches,
    lignes ignorées et pourquoi, avertissement membre archivé.
  - Refus (`OWN_TEAM`, `WRONG_TEAM`, `UNKNOWN_MEMBER`, `OLDER`) : titre
    « Paquet non reçu » / « Rapport non intégré », la raison, « Fermer ».
  - Dialogue `Dialog` + `Surface` à coins de 28 dp et liste défilante, sur
    le modèle d'`EditTaskDialog` plutôt qu'`AlertDialog` : celui-ci ne se
    dessine pas dans la scène hors écran du self-test, qui capture donc
    `ExchangePreviewCard` (public pour cela, comme `ExchangePreview`,
    `ImportedFile` et `readImportedFile`).
- **Renvoyer l'avancement** (carte Données, `sendReport`) : un bouton par
  origine de `KairosRepository.origins()` (« Renvoyer l'avancement… »
  s'il n'y en a qu'une, « Renvoyer l'avancement à Corentin (Équipe
  Plateforme)… » sinon), invisible sans tâche reçue non retirée ;
  `buildReport` puis `kairos-rapport-<membre>-AAAAMMJJ-HHMM.json`.
- **Marques** (`ReceivedMark`, `day/TaskRow.kt`) : sur les lignes de la vue
  Jour et dans la fiche d'édition, badge neutre `Groups` « de Corentin » ;
  retirée : « retirée par Corentin » en contour avec icône d'alerte (forme,
  pas couleur : design system § Pas d'ambre). La phrase complète (« tâche
  reçue de Corentin ») est la description pour les lecteurs d'écran.
- **Édition d'une tâche reçue** (`EditTaskDialog`, `ReceivedTaskSheet`) :
  la marque, une aide (« titre, description… sont remplacés à chaque
  nouveau paquet ; l'avancement est le tien »), le curseur d'avancement
  (`ProgressField`, extrait de la fiche d'équipe) qui appelle
  `setReceivedProgress`. Une tâche retirée offre « Garder comme tâche
  personnelle » (`KairosRepository.detachOrigin` : `origin`, `teamUid` à
  `null`, `originRemoved` à faux, `updatedAt` inchangé ; un paquet ultérieur
  qui la contiendrait en recréerait une autre), au-dessus de la rangée
  Supprimer / Annuler / Enregistrer (quatre boutons débordaient à 360 dp).
- Journal (`TaskHistory`) : « Envoyée dans un paquet » (`sent`), « Temps
  passé rapporté : N min » (`time`).
- Registre : **tutoiement** côté membre (Réglages, aperçu de paquet,
  fiche d'une tâche reçue), comme le reste de l'espace Perso ;
  **vouvoiement** côté manager, comme le reste de l'espace Équipe.
- Self-test : jeu `ExchangeSeed` (manager Claire, membre Alex, deux paquets
  et un rapport) ; captures `team-exchange-pack`, `team-exchange-report`,
  `team-exchange-sheet` (et `-narrow` à 360 dp), `exchange-day`,
  `exchange-day-narrow`, `exchange-settings`.
- Tests : `TeamExchangeUiTest` (aiguillage, aperçus, sauvegarde préalable
  et son échec, refus, envoi et annulation, paquet vide, marques, garder,
  base solo sans marque ni bouton), `FileSlugTest`.

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
- **Origine à cinq champs dans une colonne texte** plutôt qu'une table
  d'origines : une tâche reçue porte tout ce qu'il faut pour l'afficher et
  adresser le rapport, sans jointure ni nettoyage d'orphelins.
- **Bloqueurs externes non stockés** : le membre ne peut ni les voir
  évoluer ni les débloquer ; les garder figés induirait en erreur.
- **Deux nouveaux événements (`sent`, `time`)** plutôt qu'une réutilisation
  de `report` : le journal du manager distingue l'envoi, l'état et le
  temps.

- **Onglet « Échanges » plutôt que sixième destination ou nouveau
  réglage** (2026-10-01) : la limite MD3 est de cinq destinations par espace
  (`navigation-theme.md`), et l'envoi, la réception et l'état par membre se
  lisent ensemble. Un onglet de l'écran Équipe (`SecondaryTabRow`, comme la
  fiche d'une tâche) les rassemble sans toucher à la navigation. La fiche du
  membre garde sa section « Échanges » (envoyer depuis la fiche reste le
  geste le plus direct), et « Importer » des Réglages reste l'entrée du
  membre : l'onglet ne remplace rien, il centralise.
- **« En attente de son rapport » se déduit du journal** (2026-10-01) :
  dernier événement `sent` du membre contre `lastReportAt`, sans colonne ni
  réglage de plus (`ExchangeStatus`, pur). Écarté : une colonne
  `last_pack_at` sur `team_member` (migration, export et bases solo
  concernés pour une information déjà dans le journal). Limite : le journal
  porte l'envoi **par tâche** (`sent`) ; un paquet vide (retraits) n'en écrit
  aucun (`recordPackSent` ne journalise rien sans tâche), il ne déclenche donc pas l'attente.
- **Signal par la forme, jamais par la teinte** : « En attente » est un
  `Flag` à contour et icône `Schedule` (pas `Warning` : ce n'est pas un
  risque), comme les autres états à surveiller de l'espace Équipe.
- **« Recevoir un rapport… » n'applique jamais un export complet**
  (2026-10-01) : le remplacement de toutes les données reste dans Réglages →
  Données, avec sa confirmation, loin d'un écran où l'on ne s'y attend pas.
  Un paquet reçu par ce bouton est traité comme par « Importer » (aperçu,
  refus `OWN_TEAM`).
- **Le membre reste servi par l'Accueil et la visite** : rien n'est ajouté à
  son espace Perso (pas de nouveau bouton) ; l'Accueil et la dernière étape
  de la visite de Kairos disent où importer et renvoyer (`accueil.md`).

### Impacts sur les specs existantes (reportés)

- `export-import.md` : aiguillage de « Importer » par `format`, bouton
  « Renvoyer l'avancement… ».
- `modele-donnees.md` : `team_member.uid`, `team_member.last_report_at`,
  `task.reported_minutes`, `task.origin`, `task.origin_removed`.
- `vue-jour.md` : marque des tâches reçues et retirées.
- `temps-reel-chrono.md` / `statistiques.md` : temps rapporté compté dans le
  temps passé des tâches d'équipe.
- `accueil.md` : bouton « ? », page Accueil, visite guidée (onglet « Échanges »
  de la visite de l'espace Équipe) ; `navigation-theme.md` : « ? » et écran
  secondaire `Secondary` ; `equipe.md` : écran Équipe à deux onglets.

### Arbitrage du 12/10 (passe QA)

- Un avancement rapporté à 100 % (ou borné à 100 % depuis une valeur aberrante)
  **ne termine pas** la tâche : l'état « fait » ne vient que du statut du rapport
  (`ReportMerge`), jamais du pourcentage. Une tâche « En cours à 100 % » est un
  état valide (le membre n'a pas encore clos), le manager la voit et peut la
  terminer depuis le Suivi.
