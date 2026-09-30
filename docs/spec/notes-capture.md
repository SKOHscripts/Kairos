# Notes (capture GTD)

_Rôle : une capture libre (« brain dump ») en amont de « À traiter » :
écrire une idée sans décider tout de suite si c'est une tâche. Fichiers
couverts : `kmp/core/src/commonMain/.../core/notes/NoteConversion.kt`,
`data/KairosRepository.kt` (`createNote`, `editNote`, `convertNote`,
`archiveNote`, `deleteNote`) et `Kairos.sq` (`insertNote`, `updateNote`,
`deleteNote`), `kmp/ui/.../notes/NotesScreen.kt`. Le modèle `Note` est
décrit par `modele-donnees.md`, l'entrée de navigation par
`navigation-theme.md`. Tests : `core/.../notes/NoteConversionTest.kt`,
`KairosRepositoryTest.notesAreCapturedEditedConvertedArchivedAndDeleted`,
`M4ScreensUiTest.noteIsCapturedThenTurnedIntoATask`._

État : **jalon M4**. Reprend le besoin de `docs/spec/notes-capture.md`
(Kairos 2).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La capture de la vue Jour crée déjà une **tâche** (titre seul, à
qualifier). Or l'étape amont de GTD est la capture au sens large : une idée,
un rappel, un « il faudrait que… » qui n'est pas encore une tâche. Forcer un
titre de tâche à ce stade impose une décision prématurée qui freine la
capture.

### Comportement attendu (utilisateur)

- Destination « Notes » (avant « Jour » : capturer avant de traiter). En
  tête, toujours visible : un seul champ, le corps de la note (plusieurs
  lignes), et « Capturer ». Ctrl+Entrée (Cmd+Entrée sur Mac) capture ; le
  champ se vide et garde le curseur. L'aide sous le champ cite ce raccourci
  sur le bureau et le web ; sur Android, sans clavier physique, elle dit
  seulement « Rien d’autre à décider maintenant : tu la traiteras plus
  tard. ». Aucun autre champ : ni priorité, ni
  points, ni échéance.
- Dessous, « Notes (N) » : les notes en attente, la plus récente en tête ;
  « Rien en attente. » si aucune. Chaque note offre :
  - **→ Tâche** : la tâche créée arrive dans « À traiter » de la vue Jour ;
    titre = première ligne non vide, description = tout le reste (rien n'est
    perdu, rien n'est inventé). Un message confirme « Tâche créée :
    « … » ». La note est archivée, liée à sa tâche, jamais supprimée.
  - **Modifier** : dialogue avec le corps ; « Enregistrer » inactif si le
    corps est vide.
  - **Archiver** : classée sans suite, conservée.
  - **Supprimer** : définitif, après confirmation.
- « Traité / archivé (N) », replié, présent s'il y a des notes traitées :
  leur texte atténué et, pour une note convertie, « → voir la tâche
  créée », qui ouvre la vue Jour.

### Critères de succès

- Capturer « Appeler le plombier / à propos de la fuite » puis « → Tâche »
  crée la tâche « Appeler le plombier », description « à propos de la
  fuite », archive la note et la lie à la tâche
  (`M4ScreensUiTest`, `KairosRepositoryTest`).
- Une note d'une ligne donne une description vide ; les lignes blanches de
  bord sont retirées, le reste est gardé tel quel ; un titre de plus de 200
  caractères est tronqué et la ligne entière reprise en tête de la
  description (`NoteConversionTest`, mêmes cas que Kairos 2).
- Une note archivée quitte la liste active ; une note supprimée ne
  réapparaît nulle part.

### Hors périmètre / différé

- Priorité, points, échéance ou projet sur une note : posés après
  conversion, sur la tâche.
- Recherche, filtres, autre tri que « plus récente d'abord », rappel d'une
  note non traitée (comme Kairos 2).

## 2. Solution technique

Depuis le jalon E1 de l'espace Équipe, l'écran Notes lit `repository.personalSnapshot` (la vue Perso, `equipe.md` § Espaces et filtre central) et non la base complète ; sans tâche d'équipe, c'est la même instance.

- `NoteConversion.fields(corps)` : titre = première ligne non blanche sans
  ses espaces de bord, tronquée à `TITLE_MAX` (200) ; description = les
  lignes suivantes, moins les lignes blanches de tête et de queue ; titre
  tronqué → la ligne entière en tête de la description (séparée par une
  ligne vide s'il y a une suite). Corps blanc → `("", "")`.
- Dépôt : `createNote` nettoie le corps (`trim`) et refuse un corps vide ;
  `editNote` idem, garde statut et lien ; `convertNote` (note ouverte
  seulement) insère la tâche (`newTask`, « À traiter ») et archive la note
  avec `convertedTaskId` dans **une** transaction, rend l'identifiant de la
  tâche (`null` si la note n'existe plus, est déjà traitée ou n'a pas de
  titre) ; `archiveNote` ; `deleteNote`.
- `NotesScreen(services, onOpenTasks)` : `LazyColumn` de 840 dp au plus ;
  capture dans une carte (`surfaceContainerLow`), `onPreviewKeyEvent` pour
  Ctrl/Cmd+Entrée ; aide `notes_capture_hint`, ou `notes_capture_hint_touch`
  si `LocalPlatform` vaut `ANDROID` ; « → Tâche » en `Button` (action principale de la
  ligne), « Modifier » et « Archiver » en `TextButton`, supprimer en
  `IconButton` ; message par `LocalMessages` (snackbar de la coquille) ;
  « Traité / archivé » : en-tête cliquable à chevron, état gardé
  (`rememberSaveable`). `onOpenTasks` ouvre la vue Jour d'aujourd'hui.

### Décisions et pièges tracés

- **« Modifier » branché** : Kairos 2 avait la route d'édition sans bouton
  (« prête à être branchée ») ; Kairos 3 la branche.
- **Un corps vide à l'édition garde l'ancien** : Kairos 2 enregistrait un
  corps vide (note blanche, inconvertible). Écart volontaire ; le dialogue
  désactive de toute façon « Enregistrer ».
- **Lien vers la tâche = vue Jour**, pas la tâche elle-même : elle est dans
  « À traiter », en tête de l'écran (comme Kairos 2, lien vers `/kairos`).
