# Bilan de la semaine et du mois

_Rôle : dire en une carte ce qui s'est passé sur la période affichée (fait, avancé, réunions, reste), prêt à être lu ou
collé en réunion hebdomadaire ; et alléger l'en-tête des vues Semaine et Mois qui le portent. Fichiers couverts :
`kmp/core/src/commonMain/.../core/recap/Recap.kt` (modèle, pur), `kmp/ui/.../recap/RecapCard.kt` (carte, texte copié,
en-tête de période `PeriodHeader`), leur appel dans `week/WeekScreen.kt` et `month/MonthScreen.kt`. Tests :
`core/.../recap/RecapTest.kt`, `desktopApp/.../RecapUiTest.kt`._

État : 4.1.0 (issue #233), implémentée. Dépend des prévisions par tâche (#232, `previsions-taches.md` § Étape C) pour la
ligne « Réaliste ».

## 1. Besoin métier (cahier des charges)

### Objectif / problème

En réunion hebdomadaire on dit « cette semaine j'ai fait ceci et cela, on a parlé de tel sujet en réunion, et voilà ce
qui reste ». La vue Semaine ne donnait que deux lignes grises (« Temps réel cette semaine… », « Réaliste… ») ; les tâches
faites étaient dispersées dans sept cartes, sans leur temps, les réunions aussi, et la vue Mois n'avait rien. Avec les
prévisions (#232), les informations se sont multipliées : il faut un endroit où la période se résume, lisible d'un coup
d'œil, détaillable à la demande.

### Comportement attendu (utilisateur)

- **Carte « Bilan de la semaine »** (vue Semaine) et **« Bilan du mois »** (vue Mois), sous l'en-tête de période (et sous
  « Rechercher / filtrer » dans la semaine), avant la grille. Toujours visibles, quatre chiffres :
  - « tâche(s) faite(s) » (le nombre ; avec des points : « 3 · 13 pts ») ;
  - « temps chronométré » ;
  - « en réunion ou créneau » (durée des créneaux occupés déjà terminés) ;
  - « à échéance, pas encore faite(s) » (icône d'avertissement si l'une est en retard).
- Sous les chiffres, « Détail » (replié ; **ouvert d'office pour une période passée**, dont le bilan est l'objet) et
  « Copier le bilan ». Le détail liste, sections vides omises :
  - **« Fait »** : les tâches terminées dans la période, par projet puis titre, chacune avec son projet et le temps
    chronométré dans la période (« 2 h 10 ») s'il y en a ;
  - **« Avancé, pas fini »** : les tâches encore à faire chronométrées dans la période, la plus travaillée d'abord, avec
    ce temps ;
  - **« Réunions et créneaux »** : les créneaux occupés déjà terminés, **regroupés par titre** (« Point d'équipe ×5 ·
    2 h 30 »), le plus long d'abord ; les fenêtres de deep work n'y sont pas (ce qu'on y a fait est dans les tâches) ;
  - **« Reste »** : les tâches à faire dont l'échéance tombe dans la période, par échéance (« échéance 13 nov. »), en
    contour d'avertissement si elle est passée ;
  - **« Où est passé le temps »** : une barre par type de tâche (types non vides), la plus longue d'abord ;
  - la ligne « Réaliste : 12 h à 22 h … » des prévisions (semaine courante ou à venir seulement, inchangée).
- Rien sur la période : la carte dit « Rien de fait ni de chronométré sur cette période. », sans chiffres ni boutons.
- **« Copier le bilan »** met dans le presse-papiers le même contenu en texte brut (titre « Bilan · semaine du 09/11 » ou
  « Bilan · novembre 2026 », puis les sections en listes à tirets, puis « Temps chronométré : 14 h (Dev 6 h · Réunion
  5 h) ») ; message « Bilan copié. ». Rien n'est écrit ni envoyé.
- **Filtre** (vue Semaine) : il réduit les tâches du bilan et leur temps (bilan d'un projet pour la réunion de ce
  projet) ; les créneaux, qui n'ont ni projet ni type, restent tous.
- Espace Perso seulement, comme les deux vues.
- **En-tête de période** (les deux vues) : une seule ligne « ‹  Semaine du 09/11  › » (deux boutons à icône de 48 dp,
  décrits « Semaine précédente » / « Semaine suivante », « Mois précédent » / « Mois suivant »), et, hors de la période
  courante, « Revenir à aujourd'hui ». Remplace le titre suivi d'une ligne de deux grands boutons à texte.
- **Carte d'un jour** (vue Semaine) : le « — » ne s'affiche plus quand le jour a un créneau ou une journée de calendrier
  (il disait « rien » sous une liste de créneaux).

### Critères de succès

- Fait, avancé, créneaux regroupés et terminés seulement, reste et retard, temps par tâche dans la période, temps par
  type, filtre, bornes de la période (`RecapTest`).
- La carte montre les chiffres, « Détail » ouvre les sections, « Copier le bilan » pose le texte attendu dans le
  presse-papiers ; une semaine passée s'ouvre dépliée ; le mois a sa carte ; l'en-tête tient à 360 dp et « Revenir à
  aujourd'hui » ramène à la semaine courante (`RecapUiTest`).
- « Semaine suivante », « Mois suivant » restent atteignables par leur description (`M4ScreensUiTest`,
  `MonthViewUiTest`).
- Captures `desktop-week-recap.png`, `desktop-week-recap-narrow.png` et `desktop-week-recap-past.png` (base à
  historique), regardées.

### Hors périmètre / différé

- Notes prises pendant une réunion rattachées au créneau (un créneau n'a pas de note) ; bilan de l'espace Équipe
  (`equipe-backlog-suivi.md` a « Faites ces 7 jours ») ; export du bilan en fichier ou en image (le presse-papiers
  suffit pour une réunion) ; période libre (les Stats ont les fenêtres longues).
- Date de complétion exacte : « faite dans la période » lit la dernière modification, comme les vues Jour et Semaine.

## 2. Solution technique

### Calcul (`core/recap/Recap.kt`)

`Recap.build(snapshot, start, end, now, timeZone, filter)` :

- `done` : tâches `DONE` retenues par le filtre dont la date **locale** de `updatedAt` est dans `[start, end]`, triées
  `(projectTag, title)` ; `advanced` : tâches `TODO` retenues ayant des minutes dans la période, minutes décroissantes
  puis titre. Chaque ligne (`Recap.Line`) porte les minutes de **la période** : `TimeTracking.spentMinutesByTask` sur
  `TimeTracking.sessionsInRange` (sessions commencées dans la période ; le temps saisi à la main, sans date, n'y entre
  pas).
- `slots` (`Recap.Slot` : titre, nombre, minutes) : créneaux `BUSY` du `[start, end]` (ponctuels et
  `Recurrence.expandRecurringBlocks`, fériés et exceptions compris, comme `WeekView`) dont la fin est `<= now`,
  regroupés par titre, minutes décroissantes puis titre.
- `left` : tâches `TODO` retenues dont `deadline` est dans la période, par échéance puis titre ; `late` = celles dont
  l'échéance est avant aujourd'hui.
- `spentMinutes`, `spentByType` : sessions de la période (celles des tâches retenues si le filtre est actif), types non
  vides et minutes > 0, minutes décroissantes. `points` = somme des points des tâches faites.
- `WeekView` ne porte plus `spentMinutes` ni `spentByType` : le bilan les remplace (un seul calcul).

### Interface (`ui/recap/RecapCard.kt`)

- `RecapCard(recap, title, heading, today, language, footer)` : `Panel` des statistiques (carte à contour, icône
  `Checklist`) ; quatre `Figure` (valeur `titleLarge`, libellé `bodySmall`, sans fond) dans un `FlowRow` à cellules
  égales, 4 par ligne dès 560 dp, sinon 2 × 2 ; `BarRow` pour les types. « Détail » : `KairosTextButton`
  avec `expandedState` ; état `remember(recap.start)` initialisé à `recap.end < aujourd'hui`. `footer` : la ligne
  « Réaliste » fournie par `WeekScreen`.
- `recapText(recap, heading, …)` : le texte copié, construit avec les mêmes ressources ; `LocalClipboardManager`, échec
  signalé par `file_error` (comme « Copier le diagnostic », `bureau-avance.md`).
- `PeriodHeader(title, previous, next, onPrevious, onNext, onToday)` : `IconButton` (48 dp) à `contentDescription` de part
  et d'autre du titre `titleMedium` (une ligne, `weight(1f, fill = false)`), le groupe centré, puis `KairosTextButton` « Revenir à aujourd'hui » (`day_back_today`, le texte de la vue Jour) si `onToday` n'est
  pas nul (`nav.week` / `nav.month` remis à `null`).
- `WeekScreen` : `Recap.build(snapshot, lundi, dimanche, now, fuseau, filtre)` (snapshot avec les calendriers du
  téléphone) ; `MonthScreen` : du 1er au dernier jour du mois, sans filtre.
### Décisions et pièges tracés

- **Chiffres toujours visibles, détail à la demande** (2026-11-11) : la semaine courante sert d'abord à planifier, la
  grille doit rester haute ; une semaine passée ne sert qu'au bilan, il s'ouvre donc seul. Écarté : un troisième mode
  « Bilan » dans la bascule Semaine / Mois (le bilan est celui de la période affichée, pas une autre vue).
- **Créneaux regroupés par titre** : sept « Déjeuner » noyaient la réunion dont on veut parler ; « ×7 · 7 h » le dit en
  une ligne. Écarté : exclure les créneaux récurrents (un point d'équipe quotidien compte).
- **Créneaux terminés seulement** : un bilan dit ce qui a eu lieu ; la réunion de demain est dans la grille.
- **Le filtre ne touche pas les créneaux** : ils n'ont aucune facette ; les masquer dès qu'un filtre est actif ferait
  disparaître le temps de réunion sans raison visible.
- **Presse-papiers plutôt qu'un fichier** : le texte part dans un compte rendu ou une messagerie ; aucun format à
  choisir, rien à ranger.
- **Décision rouverte, en-tête** (`vue-semaine.md` : « titre au-dessus d'une Row des deux boutons ») : le titre placé
  entre deux boutons **à texte** s'écrivait une lettre par ligne à 360 dp ; entre deux boutons **à icône** (96 dp à
  deux) il tient. Gain : une ligne de 48 dp, et le retour à aujourd'hui, qui demandait de quitter la destination.
- **Décision rouverte, mois** (`vue-mois.md` § Hors périmètre, « temps chronométré du mois ») : l'issue #233 le demande.
- **Des chiffres nus, pas les tuiles des Stats** : `StatTile` a 150 dp de large au moins ; dans la carte, à 360 dp, les
  quatre s'empilaient sur 400 dp et repoussaient la grille hors de l'écran. Deux par ligne, sans fond, le bilan tient en
  120 dp. Les chevrons sont collés au titre : aux deux bords d'une fenêtre de 1 200 dp, on ne les associait plus au titre.
- **Pas de couleur de plus** : carte à contour neutre, chiffres neutres ; le seul bloc teinté de la semaine reste
  aujourd'hui. Le retard passe par le contour et l'icône (`WarnBadge`), jamais par l'ambre.
