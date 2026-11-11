# Fiche de tâche : lisibilité et « Bloquée par »

_Rôle : rendre la fiche d'édition d'une tâche lisible d'un coup d'œil (champs regroupés par rôle en sections repliées qui
disent ce qu'elles contiennent), rendre « Bloquée par » praticable avec beaucoup de tâches ouvertes, et permettre de
**corriger** le temps passé d'une tâche, pas seulement d'en ajouter. Fichiers couverts :
`kmp/ui/.../day/EditTaskDialog.kt` (structure, sections), `kmp/ui/.../day/BlockerPicker.kt` (« Bloquée par »),
`kmp/ui/.../day/BlockerChoices.kt` (choix et recherche, pur), `kmp/ui/.../day/EditField.kt` (champ → section),
`kmp/core/.../engine/TimeCorrection.kt` (correction du temps passé, pur), `KairosRepository.updateTask` / `restoreSpent`. Tests : `core/.../engine/TimeCorrectionTest.kt`, `data/.../SpentCorrectionTest.kt`, `ui/.../day/BlockerChoicesTest.kt`,
`desktopApp/.../SubtasksUiTest.kt`, `desktopApp/.../DayScreenUiTest.kt`, capture `desktop-edit-sheet*` de l'auto-test._

État : 4.1.0 (suite de l'issue #233, demande du propriétaire du 2026-11-11), implémentée. Rouvre deux décisions tracées :
la liste de cases de « Bloquée par » (`dependances.md`, `sous-taches.md` § Section) et l'en-tête des sections repliables ;
puis, deuxième passe du même jour, les ouvertures d'office des sections (`sous-taches.md`, `vue-jour.md`), les « Options
avancées » et le temps passé « qui ne fait qu'ajouter » (`temps-reel-chrono.md` § Hors périmètre).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La fiche aligne une quinzaine de champs avec le même espacement, sans titre de groupe : on ne voit pas d'un coup d'œil ce
qui qualifie la tâche, ce qui la date, ce qui l'enchaîne. Les deux sections repliables ressemblent à des liens perdus entre
deux champs et ne disent pas ce qu'elles cachent. Surtout, « Bloquée par » liste **toutes** les tâches ouvertes en cases à
cocher : avec une vraie base (60 tâches ouvertes dans la sauvegarde du propriétaire), la liste fait plusieurs écrans, un
titre long ou une adresse web prend quatre lignes, et la recherche ne fait que réduire ce mur.

### Comportement attendu (utilisateur)

- **Titre et description toujours visibles** (l'identité de la tâche ; le titre est requis à la création), puis, pour une
  tâche reçue, sa marque et son avancement. Tout le reste est rangé dans des **sections repliées par défaut** :
  - **« Équipe »** (tâche de l'espace Équipe) : titulaire, effort, avancement, note ;
  - **« Qualification »** : priorité, taille (points) et son guide, type, projet, et « Compter dans l'équipe » s'il existe ;
  - **« Dates et temps »** : échéance et durée estimée, les suggestions de durée, puis date programmée et heure fixe
    (chaque paire **côte à côte** sur une fiche de 560 dp ou plus, l'une sous l'autre sinon, raccourcis de date dessous),
    récurrence, **temps passé** et « Et si j'accepte ? » ;
  - **« Découpage et enchaînement »** : sous-tâches, « Enchaîner », « Bloquée par ».
- **Chaque en-tête résume sa section**, une ligne de 48 dp avec chevron : « P1 · 5 pts · Réunion · projet Site » (rien :
  « à qualifier ») ; « échéance 14 nov. · 1 h 30 estimées · programmée 12 nov. · à 14:30 · récurrente · 2 h passées » ;
  « 1 sous-tâche(s) · 2 bloqueur(s) » ; « Alex Dupont · 60 % ». Les signaux qui appelaient l'œil dans une section ouverte y
  sont aussi : « conseil : découper » (conseil de découpage) et « le modèle propose 45 min » (durée contestée).
- **Ouvertures** : rien n'est ouvert d'office, même une tâche qui a des bloqueurs ou une date programmée (le résumé le
  dit). L'accompagnement de la première tâche ouvre la section du champ qu'il explique ; « Découper en sous-tâches » ouvre
  « Découpage et enchaînement ». Une section qui contient un champ en erreur ne se replie pas et son résumé le dit
  (« à corriger ») : sinon « Enregistrer » serait grisé sans raison visible.
- **Temps passé, corrigeable** : le champ « Temps passé (min) » montre le **total** de la tâche (chrono plus temps saisi),
  avec dessous « dont 1 h 20 chronométrées ». On peut l'augmenter (temps non chronométré) **ou le baisser** (chrono oublié) :
  - plus : la différence s'ajoute au temps saisi à la main ;
  - moins : le temps saisi baisse d'abord, puis les sessions terminées les plus récentes sont raccourcies jusqu'au total
    voulu ; un chrono en cours n'est jamais raccourci (« Au moins 40 min : chrono en cours. ») ;
  - un message « Temps passé de « X » : 6 h → 1 h » propose « Annuler » dès que des sessions ont été raccourcies ;
  - une tâche d'équipe ou reçue ne touche pas aux sessions : le total ne descend pas sous le chronométré (« Au moins
    1 h 20 chronométrées. ») ; le temps rapporté par un membre n'est ni montré ni modifié ici ;
  - le champ non touché ne change rien, même si un chrono tourne pendant l'édition ; **vidé**, il ne change rien non
    plus (seul « 0 » remet à zéro) : vider le champ était le geste « pas de temps saisi » et ne doit pas effacer le
    chronométré.
- **« Bloquée par »** :
  - les tâches choisies sont des **puces**, une par tâche, titre sur une ligne (coupé par « … »), avec une croix
    « Retirer « X » », seule à retirer (toucher le titre ne fait rien) ;
  - dessous, **un champ de recherche** « Ajouter une tâche qui la bloque… ». Tant qu'on n'a rien tapé, **aucune liste** ;
    dès la première lettre, **six résultats au plus** (même recherche qu'avant : chaque mot, sans casse ni accents), une
    ligne de 48 dp chacun, puis « … et 12 autre(s) : précise la recherche. » s'il en reste ; aucun : « Aucune tâche ne
    correspond à « … ». » ;
  - toucher un résultat l'ajoute aux puces et vide la recherche ; une tâche déjà choisie n'est pas proposée ;
  - une tâche qui attend déjà celle-ci reste proposée mais grisée, avec « (attend déjà cette tâche) » : la choisir
    fermerait une boucle ;
  - sans autre tâche ouverte : « aucune autre tâche », sans champ.
- À 360 dp, les trois boutons du bas tiennent sur une ligne sans couper « Enregistrer ».
- La description s'ouvre sur deux lignes au lieu de trois (elle grandit à la saisie).
- Les « Options avancées » disparaissent : projet et « Compter dans l'équipe » vont dans « Qualification », date
  programmée, heure fixe, récurrence et temps passé dans « Dates et temps ».
- Rien d'autre ne change : mêmes champs, mêmes règles d'enregistrement (sauf le temps passé), mêmes boutons en bas, même
  accompagnement de la première tâche (chaque champ expliqué reste repéré).

### Critères de succès

- Correction du temps : plus ajoute au temps saisi ; moins le baisse puis raccourcit les sessions récentes jusqu'au total
  exact (sessions qui se chevauchent comprises) ; un chrono en cours fixe le plancher ; tâche d'équipe : plancher au
  chronométré ; total inchangé : rien (`TimeCorrectionTest`). Écriture en une transaction, et « Annuler » rend les fins et
  le temps saisi d'avant (`SpentCorrectionTest`).
- Sections : toutes repliées à l'ouverture, résumées ; le coach ouvre celle du champ expliqué (`FirstTaskCoachTest`,
  `TeamTaskCoachTest`, `RecurrenceCoachTest`) ; baisser le temps passé dans la fiche raccourcit la session et « Annuler »
  la rend (`SpentCorrectionUiTest`).

- Recherche et page : rien sans saisie, six au plus, le reste compté, les choisies exclues, les boucles marquées
  (`BlockerChoicesTest.aSearchShowsAPageOfUnchosenMatches`).
- Dans la fiche : la section se résume, la recherche (casse et accents ignorés) propose Epsilon et pas Delta, toucher
  Epsilon en fait une puce que la croix retire, « zzz » ne trouve rien
  (`SubtasksUiTest.theSectionGathersSubtasksAndBlockersAndBlockersCanBeSearched`) ; choisir « Beta » par la recherche
  l'enregistre comme bloqueur (`DayScreenUiTest`).
- Capture `desktop-edit-sheet.png` (1 200 dp) et `desktop-edit-sheet-narrow.png` (360 dp) avec 40 tâches ouvertes : la
  fiche tient sans mur de cases, regardées en clair ; captures des magasins régénérées.

### Hors périmètre / différé

- Corriger une session précise (heure de début ou de fin) : la correction porte sur le total ; « Chronos à vérifier »
  (Stats) reste l'outil des sessions anormales.
- Ordonner les résultats par pertinence (ils gardent l'ordre de la vue Jour) ; choisir un bloqueur au clavier (Entrée sur
  le premier résultat) .

## 2. Solution technique

### Choix et recherche (`BlockerChoices`, pur)

- `BlockerChoices.page(choices, query, chosen, limit = PAGE)` : `null` si `query` est vide ; sinon les choix dont l'id
  n'est pas dans `chosen` et dont le libellé contient chaque mot (`SearchFold`), les `limit` premiers dans `items` et le
  nombre restant dans `more`. `PAGE` = 6. `of` (libellés, lignée, dates, cycles) ne change pas. `filter` et
  `SEARCH_FROM` disparaissent avec la liste de cases.

### Correction du temps passé (`TimeCorrection`, pur ; dépôt)

- `TimeCorrection.total(sessions, manual, now)` : `TimeTracking.totalMinutes` des sessions de la tâche plus `manual`.
  `TimeCorrection.plan(sessions, manual, target, now, trimmable)` rend `Plan(manual, ends, floor)` : `floor` = les minutes
  que rien ne peut retirer (chrono en cours ; tout le chronométré si `trimmable` est faux) ; `target <= floor` est ramené
  au plancher. Si `target` couvre le chronométré, `manual = target - chronométré` (`null` à 0) et aucune fin ne change ;
  sinon `manual = null` et, tant que le total chronométré (mesure `TimeTracking`, chevauchements comptés une fois) dépasse
  la cible, la session terminée la plus récente encore non vide voit sa fin reculer de l'excédent (pas avant son début).
  Une coupe retire au plus l'excédent : le total ne passe jamais sous la cible, et la boucle s'arrête faute de session.
- `TaskEdit.sessionEnds` (identifiant → nouvelle fin) : appliqué par `updateTask` **dans la même écriture** que le reste de
  l'édition, aux seules sessions terminées de cette tâche, si elle est personnelle et non reçue (la règle de
  `setSessionEnds`). `KairosRepository.restoreSpent(id, manual, ends)` rend le temps saisi et les fins d'avant en une
  écriture (« Annuler »). `manualTimeSpentMinutes` reste `>= 0` (export, `modele-donnees.md`).
- Fiche : `spent` (texte du champ) part du total à l'ouverture ; `baseline` le fige. À l'enregistrement, champ inchangé ou
  vide :
  `manualTimeSpentMinutes` d'origine et aucune fin ; changé : le plan. `DayScreen` montre le message à annulation quand
  `sessionEnds` n'est pas vide (`spent_corrected`, `action_revert`).

### Interface

- `BlockerPicker(all, chosen, onChange)` (`day/BlockerPicker.kt`) : une pastille par bloqueur choisi (`Surface`
  `shapes.small` à contour `outlineVariant`, titre `labelLarge` sur une ligne avec « … », 560 dp au plus) ; seule sa
  croix (`KairosRowIconButton` `Close`, décrite `blockers_remove`, 48 dp) retire : un toucher sur le titre ne fait rien ; champ
  `OutlinedTextField` (`blockers_add`, icône `Search`) ; résultats en lignes `clickable` de 48 dp (icône `Add`, libellé
  sur deux lignes au plus), grisées (`enabled = false`, `field_blockers_cycle`) pour une boucle ; `blockers_more`,
  `blockers_search_none`. La recherche vit dans le sélecteur ; la fiche ne garde que l'ensemble choisi.
- `EditTaskDialog` : `SheetSection` (`TEAM`, `QUALIFY`, `TIME`, `SPLIT`) ; `EditField.section` range chaque champ (titre
  et description : aucune) ; `CoachStep.sections` remplace `opensAdvanced` ; `open: Set<SheetSection>`, vide à
  l'ouverture (sauf pour la tâche de la vitrine des captures, `LocalShowcaseTask`, qui ouvre `TIME`), auquel le coach et
  le conseil de découpage ajoutent ; une section en erreur ne se referme pas.
  `SectionToggle` (trait `outlineVariant`, titre `primary`) pour chaque section ; `BoxWithConstraints` : échéance et durée dans une `Row` à poids égaux dès 560 dp, leurs raccourcis de date
  (`DateShortcuts`, sorti de `DateField`) sur toute la largeur dessous ; `SectionToggle(titre, résumé,
  ouvert)` : ligne `clickable` de 48 dp au moins, `expandedState`, titre `titleSmall`, résumé `bodySmall` atténué
  (`edit_summary_*` joints par « · » ; le compte des bloqueurs ne retient que ceux qui ont une puce : un bloqueur déjà
  fait reste enregistré, comme avant, mais n'est ni proposé ni compté), chevron `ExpandMore` / `ExpandLess`. Les `coachField(EditField.…)` ne changent pas. Titres de groupe et de section en
  `primary`, traits en `outlineVariant`. Pied de fiche : marges de 16 dp et
  libellés sur une ligne (à 360 dp, « Enregistrer » se coupait en deux).

### Catalogue et README

- Pas de nouvelle carte : la correction du temps passé enrichit la carte « Chrono par tâche » (`time`,
  `guide_you_time_body`, six langues) et sa puce des deux README ; la fiche repliée n'est pas une fonctionnalité nouvelle.

### Décisions et pièges tracés

- **Recherche avec liste intégrée, pas menu déroulant** (2026-11-11) : un menu ancré à un champ éditable garde le focus
  dans le champ ; sur le bureau, Échap destiné au menu ferme la fiche (`onDismissRequest` du dialogue) et perd la saisie.
  Un menu flottant dans un dialogue qui défile est aussi le plus fragile sur le web et à 360 dp. La liste intégrée, courte
  et sans saisie vide, donne le même geste (taper, toucher) sans ces risques.
- **Rien tant qu'on n'a pas tapé** : montrer « les premières » au focus recréerait le mur, et un ordre arbitraire ne
  servirait à rien ; le titre de la tâche qui bloque est ce que l'on connaît.
- **Décision rouverte** (`sous-taches.md` § Décisions, « les cochées restent visibles pendant la recherche ») : la règle
  est tenue autrement, les choisies sont des puces toujours visibles hors de la liste de résultats.
- **Décision rouverte** (`dependances.md`, « cases à cocher ; l'ensemble coché est l'état voulu ») : l'ensemble des puces
  est l'état voulu, enregistré tel quel.
- **Titres de groupe et traits, pas des cartes dans la fiche** : la fiche est déjà une surface ; des cartes pleines dedans
  ajoutaient un troisième niveau de fond, et des cartes à contour autour de champs à contour enfreignaient « pas de
  contour dans un contour » (`densite.md`).
- **Tout replié, ouvertures d'office comprises** (propriétaire, 2026-11-11, « repliés par défaut ») : rouvre « ouverte
  d'emblée si sous-tâches ou bloqueurs » (`sous-taches.md`) et « options avancées ouvertes si date programmée »
  (`vue-jour.md`). Le résumé de l'en-tête porte désormais ce que l'ouverture d'office montrait.
- **Projet et « Compter dans l'équipe » dans « Qualification »** : ce qui classe la tâche ; récurrence et heure fixe dans
  « Dates et temps » avec la date programmée (la demande : la section de la temporalité). Plus de fourre-tout « Options
  avancées ». Écarté : garder une section « Autres » pour deux champs.
- **Baisser le temps raccourcit des sessions, pas un temps saisi négatif** : l'import refuse un temps saisi négatif
  (`ExportCodec`, `modele-donnees.md`), et un chrono oublié resterait dans les heures de travail, le temps au fil des
  jours, le bilan et « Chronos à vérifier ». Les sessions les plus récentes d'abord : c'est le plus souvent le chrono
  oublié. Rouvre `temps-reel-chrono.md` (« le temps manuel de la fiche ne fait qu'ajouter »).
- **Un champ vidé ne fait rien** : avant, vider « Temps passé manuel » retirait le temps saisi ; garder ce geste comme
  « remettre à zéro » raccourcirait toutes les sessions de la tâche, rattrapable par le seul message « Annuler ».
- **Le champ non touché ne fait rien** : un chrono qui tourne pendant l'édition changerait le total ; réécrire le temps
  saisi à l'enregistrement le fausserait.
- **Un résumé dans l'en-tête replié** : une section fermée qui contient un bloqueur ou une date programmée le dit, sans
  l'ouvrir ; avant, rien ne distinguait une section vide d'une section remplie.
