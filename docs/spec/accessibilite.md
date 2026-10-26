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
- **Tab ne piège jamais le clavier** : dans un champ multiligne (description,
  sous-tâches, « Types proposés »), `Modifier.tabLeavesField()` (`app/TabLeavesField.kt`)
  fait passer au champ suivant (Maj+Tab : précédent) au lieu d'insérer une
  tabulation.
- Le bouton « ? » de la barre du haut est nommé « Aide » ; les cartes de
  l'Accueil sont des cartes entières cliquables lues avec leur titre et leur
  phrase ; la visite guidée annonce « Étape n sur N » ; l'accompagnement de la première tâche (`premiere-tache.md`) annonce chaque étape par une région vivante polie, cerne le champ expliqué sans se limiter à la couleur et laisse le focus dans la fiche ; l'onglet « Échanges » est un
  onglet MD3 (`SecondaryTabRow`), et son signal « En attente de son rapport »
  est un texte avec icône (`accueil.md`, `equipe-echanges.md`).
- **Aucune action ne dépend d'un geste au pointeur seul** (glisser, déposer, pincer, appui long sans équivalent) :
  chaque manipulation a un chemin par bouton ou menu, nommé, atteignable au clavier, au lecteur d'écran et au toucher.
  Déplacer une tâche : « Déplacer… » (« Déplacer vers un autre jour… ») de la vue Jour et de la vue Semaine ; la poser à une
  heure : le champ « Heure fixe » de la fiche (`vue-jour.md`) ; l'attribuer dans l'espace Équipe : « Réaffecter à… ».
  **Glisser-déposer : confirmé le 2026-10-26 (issue #102), il n'y en aura pas** (§ Décisions).

### Critères de succès

- « Rechercher / filtrer » est un bouton « replié », puis « déplié » après un
  clic (`M5ScreensUiTest`).
- Aucune `IconButton` sans nom lu (revue du code).

### Hors périmètre / différé

- Audit sur appareil avec TalkBack : à faire par le propriétaire du dépôt
  (pas d'émulateur dans l'environnement de développement).
- Thème à contraste élevé.
- **Glisser-déposer** sur la frise, dans les listes, entre jours ou entre membres : exclu, décision confirmée le
  2026-10-26 (issue #102, fermée « not planned » par le propriétaire). Pas de glisser « en plus » du menu non plus. Raisons,
  alternatives écartées et condition pour rouvrir : § Décisions.

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
- **Pas de glisser-déposer : décision confirmée** (2026-10-26, issue #102 « Glisser-déposer sur la frise », décision du
  propriétaire ; l'issue est fermée « not planned »). Elle reprend et étend celle prise en codant le jalon E3 pour l'espace
  Équipe (`equipe-backlog-suivi.md` § Décisions, validée le 2026-10-01) et les « hors périmètre » de `ordonnancement.md`,
  `vue-semaine.md` et `planning-personnalise.md`. Le chemin « Déplacer… » (vue Jour et vue Semaine, `vue-jour.md`,
  `vue-semaine.md`) et le champ « Heure fixe » de la fiche reste le **seul** chemin, sur toutes les plateformes.
  - *Pourquoi* : (1) un glisser est un geste au pointeur seul : il n'existe pas au clavier, ni en navigation par balayage du
    lecteur d'écran (TalkBack, VoiceOver), ni au contacteur ; le rendre accessible reviendrait à reconstruire le menu qui
    existe déjà (actions personnalisées de sémantique) ; (2) à 360 dp, la frise passe en fin de liste (`vue-jour.md`) : la tâche
    qu'on saisit et l'heure où la poser ne tiennent pas à l'écran, il faudrait défiler pendant le geste, et ce défilement
    diffère entre toucher (Android), souris (bureau) et navigateur (Wasm) : trois comportements à tenir et à tester pour un
    gain faible ; (3) la sémantique serait
    ambiguë : la frise est un résultat **calculé**, déposer une tâche à 14 h voudrait dire « épingler » (une heure fixe, qui
    change le calcul du jour) alors que déplacer vers un autre jour programme sans épingler ; un menu nomme l'effet, un
    glisser le cache ; (4) la charte garde une seule interface commune aux trois plateformes, sans geste propre à l'une.
  - *Envisagé et écarté* : **le glisser comme raccourci en plus du chemin accessible** (la tentation naturelle : « le menu
    reste, le glisser accélère »). Écarté : deux chemins à garder d'accord et à tester sur trois plateformes, un geste de
    plus à enseigner (aide, catalogue, captures), un risque de les voir diverger ; et le gain (aller plus vite à la souris)
    est déjà couvert par le menu en deux touches (« Déplacer… », « Demain »). Écarté aussi : un glisser réservé au bureau
    (une plateforme à part, contre le point 4).
  - *Condition pour rouvrir* (explicitement, en citant cette décision et avec le propriétaire) : **toutes** les conditions
    suivantes. Un besoin établi par plusieurs retours d'utilisateurs, pas un seul, que le menu ne couvre pas ; un
    prototype qui offre le même effet **sans pointeur** (actions personnalisées de sémantique TalkBack, clavier) et dont
    chaque geste a son équivalent nommé ; la preuve qu'il tient à 360 dp, sur Android, bureau et web, avec les tests
    d'interface correspondants ; et une réponse écrite au point 3 (ce que signifie déposer sur une frise calculée).
