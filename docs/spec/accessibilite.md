# Accessibilité

_Rôle : Kairos utilisable au lecteur d'écran (TalkBack, NVDA, VoiceOver,
Orca), au clavier et au toucher. Fichiers couverts :
`kmp/ui/.../app/A11y.kt` (`heading`, `expandedState`, `disclosure`) et ses
usages (`day/Filters.kt`, `day/DayScreen.kt`, `day/TaskRow.kt`,
`day/EditTaskDialog.kt`, `day/PointsGuide.kt`, `notes/NotesScreen.kt`,
`week/WeekScreen.kt`, `stats/StatsScreen.kt`, `settings/`). Test :
`M5ScreensUiTest.disclosuresAreSpokenAsButtonsWithTheirState`._

État : **jalon M5** (plan § 9 : TalkBack, cibles de 48 dp, contrastes).
Les règles déjà suivies depuis M1 sont reprises ici pour en faire le
registre.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Un lecteur d'écran doit savoir ce qu'est chaque élément (bouton, titre,
interrupteur), ce qu'il fait et dans quel état il est ; un doigt doit
pouvoir le toucher sans viser.

### Comportement attendu (utilisateur)

- Toute icône qui agit seule a un nom lu (« Modifier », « Supprimer »,
  « Démarrer le chrono »…) ; une icône décorative à côté d'un texte n'est pas
  lue deux fois.
- Les en-têtes qui déplient une section (« Rechercher / filtrer »,
  « Sans créneau aujourd'hui »…, « Traité / archivé », description d'une
  tâche, « Options avancées », « Comment estimer les points ? », « Comment
  qualifier ? », « Backlog ») sont lus comme des boutons, suivis de
  « déplié » ou « replié ».
- Les titres de section (vue Jour, Notes, Stats, cartes des Réglages) sont
  des titres : on y saute avec le lecteur d'écran.
- Cibles tactiles de 48 dp au moins (pastilles, interrupteurs, cases à
  cocher avec leur libellé, en-têtes dépliables), sauf la description
  dépliable d'une ligne de tâche, dont toute la largeur se touche. C'est
  l'**aire tactile** qui compte, pas la hauteur dessinée : un bouton de 36 dp,
  une puce de 32 dp ou un segment de 40 dp restent touchables sur 48 dp
  (`densite.md`, vérifié par `assertTouchHeightIsEqualTo(48.dp)`).
- Textes et fonds : rôles MD3 du schéma généré (texte « on-… » sur son
  conteneur), qui garantissent le contraste ; jamais une information portée
  par la couleur seule (icône et texte avec, `statistiques.md`,
  `navigation-theme.md`).
- Raccourcis clavier documentés dans l'interface (`vue-jour.md`).

### Critères de succès

- « Rechercher / filtrer » est un bouton « replié », puis « déplié » après un
  clic (`M5ScreensUiTest`).
- Aucune `IconButton` sans nom lu (revue du code).

### Hors périmètre / différé

- Audit sur appareil avec TalkBack : à faire par le propriétaire du dépôt
  (pas d'émulateur dans l'environnement de développement).
- Thème à contraste élevé.

## 2. Solution technique

Espace Équipe (jalon E3, `equipe-backlog-suivi.md`) : la sélection multiple du Backlog passe par des cases à cocher dès 600 dp de fenêtre (l'appui long n'est qu'un raccourci en dessous) ; les signaux d'une carte du Suivi sont des badges avec texte, jamais une icône ou une couleur seule ; pas de glisser-déposer, le menu « Réaffecter à… » est le seul chemin.

- `Modifier.heading()` : sémantique de titre.
- `Modifier.expandedState(déplié)` : `stateDescription` « déplié » /
  « replié » (textes traduits), pour un `TextButton` qui ouvre une section.
- `Modifier.disclosure(déplié, heading, minHeight, onToggle)` : `clickable`
  de rôle bouton, état, hauteur minimale de 48 dp (sauf `minHeight = false`
  pour la description d'une ligne de tâche), titre si demandé.
- Interrupteurs des Réglages : ligne `toggleable` de rôle interrupteur,
  `Switch(onCheckedChange = null)` (`reglages.md`) ; cases à cocher :
  `LabeledCheckbox` (depuis M2).

### Décisions et pièges tracés

- **Une ligne dépliable n'était qu'un `clickable`** : TalkBack la lisait
  comme un texte sans action ni état ; corrigé par `disclosure`.
- **Pas de 48 dp pour la description d'une tâche** : la charte fixe la
  densité des lignes (corps 14 px) ; la description se touche sur toute sa
  largeur.
