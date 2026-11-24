# Habitudes : ancrer une tâche récurrente, sur une base scientifique

_Rôle : aider à installer une habitude à partir d'une tâche récurrente (déclencheur stable choisi par l'utilisateur, suivi
de l'automatisme par une question validée), et montrer dans Kairos les sources scientifiques de chaque affirmation.
Fichiers couverts : `kmp/core/.../core/habit/Habits.kt`, `core/habit/HabitScience.kt`, `core/model/Task.kt`,
`core/model/Settings.kt` (`habitsEnabled`, `GuideSettings.habitsDismissed`), `core/engine/Recurrence.kt`,
`core/settings/SettingsForm.kt`, `core/guide/Tips.kt`, `core/sync/SyncRecords.kt`, `kmp/data/.../16.sqm`, `Kairos.sq`,
`KairosStore.kt`, `Mappers.kt`, `KairosRepository.kt`, `ExportCodec.kt`, `kmp/ui/.../ui/habit/` (`HabitQuestion.kt`,
`HabitCurve.kt`, `HabitCards.kt`, `HabitSources.kt`), `ui/day/` (`DayScreen.kt`, `EditTaskDialog.kt`, `EditField.kt`,
`TaskRow.kt`), `ui/week/WeekScreen.kt`, `ui/stats/StatsScreen.kt`, `ui/settings/` (`SettingsFormCards.kt`,
`SettingsCategory.kt`, `SettingsScreen.kt`), `ui/guide/` (`FeatureCatalog.kt`, `FirstTaskCoach.kt`, `DiscoverDialog.kt`,
`GuideContent.kt`, `TipDialog.kt`), `ui/navigation/` (`AppShell.kt`, `NavState.kt`), `ui/screens/DestinationScreen.kt`,
`kmp/tools/make_habits_page.py`, `make_icons.py`, `.github/workflows/pages.yml`. Tests : partie 2 § Tests._

État : issue #284, jalon 4.6.0. **Implémentée en 4.6.0-beta.1** (PR #289).
Cadrage avec le propriétaire le 2026-11-24, sur deux revues de littérature (§ Base scientifique) : les choix de l'issue
que la science ne soutient pas sont écartés (§ Décisions de cadrage).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les récurrences, les durées apprises et l'observation des heures de travail font de Kairos un bon point d'appui pour
installer une habitude : on part de ce qu'on fait déjà, sans s'imposer une heure arbitraire. L'issue #284 demande de le
mettre en avant, d'accompagner la première habitude, de distinguer les habitudes à l'écran et d'appuyer le tout sur des
sources scientifiques.

La science de l'habitude dit trois choses utiles ici (détail et références au § Base scientifique) :

- une habitude est un comportement **déclenché automatiquement par un signal de contexte**, appris par **répétition dans
  ce même contexte** ;
- l'installer prend **des semaines à des mois**, avec une très grande variabilité d'une personne à l'autre ; un oubli
  isolé ne compromet pas le processus ;
- l'automatisme se mesure par **auto-évaluation** (échelle validée) ; aucune mesure tirée de journaux d'activité n'est
  validée.

Kairos doit donc aider à **choisir et tenir un déclencheur stable**, mesurer l'automatisme **en le demandant**, et ne rien
affirmer qui ne soit sourcé.

### Comportement attendu (utilisateur)

1. **Une habitude = une série récurrente** de l'espace Perso, de règle **quotidienne, jours ouvrés ou hebdomadaire**.
   Les règles mensuelles (« mensuelle », « le N du mois ») sont exclues : une douzaine de répétitions par an ne suffit
   pas à installer un automatisme, et les études portent sur des gestes quotidiens ou hebdomadaires. Deux entrées :
   - **déclarée** : sur une tâche récurrente, l'action « En faire une habitude » ;
   - **proposée** : quand une série devient régulière (§ 7), Kairos le propose par un **conseil au bon moment**
     (`conseils.md`) : « « Méditer » revient régulièrement vers 7 h. En faire une habitude ? ». « Ne plus me le
     proposer » vaut **pour cette série** : elle n'est plus proposée.
2. **Déclencheur**. En faisant d'une série une habitude, on choisit :
   - une **heure fixe**, préremplie avec l'heure observée de la série quand Kairos en a une, sinon vide ; l'utilisateur
     la valide ou la change. Ensuite elle **ne dérive plus** : Kairos ne la déplace jamais de lui-même ;
   - une phrase facultative « **Après…** » (« Après mon café »), affichée sur la tâche : le signal de routine auquel
     l'habitude s'accroche.
3. **Question d'automatisme**. À la coche d'une occurrence faite, Kairos pose de temps en temps la question validée
   « **« Méditer », c'est quelque chose que je fais automatiquement.** » (titre de la série à la place de
   « Méditer »), de 1 « Pas du tout d'accord » à 7 « Tout à fait d'accord ». En anglais, l'item validé mot pour mot :
   « “Meditate” is something I do automatically. » :
   - à la **première** occurrence faite après la déclaration (point de départ de la courbe) ;
   - puis au plus **une fois tous les 14 jours** par habitude ;
   - un toucher suffit ; on peut l'ignorer (fermer = pas de réponse, la question revient à l'occurrence suivante) ;
   - **une seule question à la coche** : si « Temps passé ? » (`durees-reelles.md`) est dû en même temps, la question
     d'automatisme **passe d'abord** et « Temps passé ? » n'est pas posé ; la tâche, restée sans temps, rejoint la revue
     des tâches faites sans temps (Bilan), comme pour des coches groupées.
4. **Statut** d'une habitude, dérivé des réponses seulement :
   - « **Habitude en cours** » dès la déclaration ;
   - « **Habitude installée** » quand la courbe se stabilise haut (critère au § 7) ;
   - une installée qui redescend nettement redevient « en cours » (§ 7).
5. **Félicitations** : quand une habitude devient « installée », **un** message factuel, une seule fois (« « Méditer » : tu dis le faire automatiquement. C'est le signe d'une habitude installée. » : il reprend
   le mot de la question, sans prêter à l'utilisateur une réponse qu'il n'a pas donnée). Ni badge, ni points, ni compteur de jours
   d'affilée, et **aucun message** quand la régularité s'interrompt.
6. **Interruption**. Un oubli isolé ne compte pas. Après une **longue interruption** (28 jours sans occurrence faite),
   la question est reposée à la prochaine occurrence faite, sans message culpabilisant ; le statut ne change que selon
   la réponse.
7. **Seuils de produit** (décisions de produit, **pas** des faits scientifiques ; présentés comme tels dans
   l'explication) :
   - *série régulière* (pour la proposition), toutes conditions réunies :
     - la série existe depuis **14 jours** au moins ; on examine ses **42 derniers jours** au plus (6 semaines, durée
       minimale observée pour le sport par Kaushal et Rhodes, 2015) ;
     - **75 %** au moins des occurrences prévues sur cette période sont faites, et **5** au moins (quotidienne : 32 sur
       42 ; hebdomadaire : 5 sur 6) ;
     - **deux tiers** au moins des occurrences faites l'ont été à **une heure** au plus de leur heure médiane (fenêtre
       de 2 h, la largeur des tranches des meilleures heures). Heure d'une occurrence : le début de son premier chrono,
       sinon l'heure de la coche ; une coche **groupée** (moins d'une minute après la précédente, `durees-reelles.md`)
       ne compte pas pour l'heure : cocher par lots ne dit rien de l'heure réelle ;
     - la série n'est ni déjà une habitude, ni écartée ;
     - l'heure proposée au préremplissage est l'heure médiane, arrondie au quart d'heure ;
   - *installée* : au moins **trois réponses**, les **deux dernières à 6 ou 7** (donc environ un mois au moins après la
     déclaration : le palier haut et stable des courbes d'automatisme) ;
   - *redescendue* : une installée dont la dernière réponse tombe à 4 ou moins redevient « en cours ».
8. **Aspect** : une habitude porte une **marque sobre** (icône neutre, nommée « Habitude » pour le lecteur d'écran) dans
   la vue Jour et la vue Semaine, et sa section dans la fiche ; aucune couleur nouvelle (`DESIGN_SYSTEM.md` : « tout le reste est neutre »). Pas de compteur.
9. **Fiche de la tâche** : une section « Habitude » : l'interrupteur « En faire une habitude », le déclencheur (heure,
   « Après… »), le statut et la courbe des réponses. Éteindre l'interrupteur retire l'habitude (la série reste, les
   réponses sont gardées tant que la série existe).
10. **Statistiques** : une carte « Habitudes » résume les habitudes en cours et installées (statut, dernière réponse,
    petite courbe).
11. **Guide** :
    - **accompagnement** de la première habitude sur la vraie fiche, comme la première tâche récurrente : choisir le
      geste (simple), l'heure, la phrase « Après… », et ce qui attend (des semaines à des mois, un oubli ne compte pas) ;
    - **une carte** du catalogue « Découvrir Kairos » (une fonctionnalité = une carte, six langues).
12. **Réglages → Planification → carte « Habitudes »**, à côté de « Durées apprises » :
    - interrupteur « **Habitudes** », **activé par défaut** ; désactivé : ni question, ni proposition, ni marque, ni
      carte Statistiques ; les données sont gardées ;
    - une **explication** courte de la démarche (déclencheur stable, répétition, question d'automatisme, seuils de
      produit) ;
    - « **Sources scientifiques** » ouvre un dialogue : chaque **affirmation** courte, son **niveau de preuve** (solide,
      modéré, faible) et ses **références complètes** (auteurs, année, titre, revue, DOI). Le DOI s'ouvre dans le
      navigateur (comme le lien de don, `soutien.md`) ; tout le reste fonctionne hors ligne.
13. **Une seule base scientifique**. Les affirmations et leurs références vivent dans **une source unique**, qui
    alimente l'écran des sources **et** une page « Habitudes » du site, **générée** depuis elle (comme la carte du code,
    `carte-du-code.md`) ; le README résume et renvoie à cette page. Une affirmation sans source vérifiée ne peut pas
    s'afficher.
14. **Textes publics** (README, site, fiches des magasins) : seulement ce que la base soutient. Jamais « 21 jours »,
    jamais une durée promise (« 66 jours » est une médiane calculée sur 39 personnes, avec des durées de 18 à 254 jours),
    jamais « scientifiquement prouvé » ni « Kairos crée des habitudes ».
15. **Honnêteté de la question** : l'item validé est en anglais (« Behaviour X is something I do automatically ») ; la
    version française de Kairos est une traduction, non validée en français. L'explication le dit, et dit que les
    auteurs le réservent aux cas où une mesure brève est nécessaire (c'est le cas : une question à la coche).
16. **Espace Perso seulement.** Aucune donnée d'habitude dans l'espace Équipe ; une tâche d'équipe ne peut pas devenir
    une habitude (`equipe.md`, isolation).
17. **Données** : la déclaration, le déclencheur et les réponses sont des données de l'utilisateur : exportées,
    importées et synchronisées comme les tâches. Ce que retient le conseil (séries écartées) reste propre à l'appareil,
    comme le reste des conseils.

### Critères de succès

Vérifiables ; les tests seront nommés en partie 2.

- Une tâche récurrente Perso quotidienne, jours ouvrés ou hebdomadaire peut être déclarée habitude ; une tâche
  d'équipe, non récurrente ou mensuelle ne le peut pas.
- L'heure d'une habitude n'est jamais modifiée par Kairos ; « Préférer mes heures habituelles » ne la déplace pas.
- La question est posée à la première occurrence faite, puis au plus une fois tous les 14 jours par habitude, et
  reposée après 28 jours sans occurrence faite ; quand elle est posée, « Temps passé ? » ne l'est pas et la tâche
  apparaît dans la revue des tâches faites sans temps.
- Un oubli isolé ne change ni le statut ni la courbe.
- Le statut suit le seul critère du § 7 ; le message de félicitations ne s'affiche qu'une fois par passage à
  « installée ».
- Une série écartée n'est plus proposée ; le conseil respecte le rythme et les interdits de `conseils.md`.
- Interrupteur désactivé : aucune question, proposition, marque ni carte ; réactivé, tout revient, données intactes.
- Chaque affirmation de l'écran des sources a au moins une référence vérifiée ; un test le garantit.
- Aucun texte public ne contient « 21 jours » ni une durée promise.
- Les données d'habitude survivent à un export puis un import.
- Rien n'apparaît dans l'espace Équipe.

### Hors périmètre / différé

- **Adapter le créneau en continu** à l'heure observée (aucune preuve qu'il aide ; un déclencheur stable est mieux
  soutenu) : l'observation sert seulement à **préremplir** l'heure une fois.
- **Compteur de jours d'affilée** (*streak*), badges, points, classements : rompre une série nuit à l'engagement, et
  rien ne montre qu'une récompense installe une habitude.
- **Déclarer « installée » sans réponse** de l'utilisateur, à partir des seules données : aucune mesure par journaux
  n'est validée.
- Questionnaire complet (SRHI à 12 items, SRBAI à 4 items) : l'item unique est validé et suffit.
- Notifications du système pour une habitude (les rappels à heure fixe existants, `rappels.md`, restent disponibles).
- Perdre une habitude (mauvaise habitude à rompre) : autre démarche, autres preuves.
- Habitudes d'équipe.

### Points ouverts

Aucun : besoin validé par le propriétaire le 2026-11-24.

## 2. Solution technique

Implémentée dans la PR #289 (version `4.6.0-beta.1`, chantier risqué : colonnes nouvelles, synchro, question à la coche ;
`distribution.md` § Cycle des versions).

### Modèle : l'habitude est portée par les occurrences de la série

Comme l'heure fixe et la récurrence, l'habitude est un **attribut de la série recopié d'occurrence en occurrence**, pas
une entité à part. Trois champs de `Task` (`core/model/Task.kt`), nuls ou vides par défaut :

- `habitSince: Instant?` : date de la déclaration ; non nul = la série est une habitude. **Recopié** par
  `Recurrence.nextOccurrence`, comme `pinnedStart`. Les chemins calendaires (`calendarOccurrences`, `recreateSkipped`)
  ne le recopient pas : ils ne servent qu'aux règles mensuelles, non éligibles.
- `habitAfter: String` (vide par défaut, 80 caractères au plus, `Habits.MAX_AFTER`) : la phrase « Après… ». **Recopiée**.
- `habitScore: Int?` (1 à 7) : la réponse donnée **à la coche de cette occurrence**. **Jamais recopiée**.

L'heure de l'habitude est l'**heure fixe** existante (`pinnedStart`) : aucun champ dupliqué, et Kairos ne la déplace
jamais (seul l'utilisateur la change). Retirer l'habitude remet `habitSince` à nul et `habitAfter` à vide sur l'occurrence
ouverte : les suivantes n'héritent plus ; les réponses restent sur les occurrences faites. Redéclarer repart d'un nouveau
`habitSince` : seules les réponses postérieures comptent. Une série passée à une règle mensuelle garde ses champs mais
n'est plus éligible : l'habitude y est ignorée (ni question, ni marque, ni section).

Écarté : des tables `habit` et `habit_answer` avec deux types de synchro. Il faudrait rattacher une série entre appareils
(son identifiant est celui de sa première tâche, nul tant qu'elle n'est pas faite, et local), et doubler le chemin de
synchro des dérangements ; les champs de tâche passent par la synchro champ par champ et l'export sans rien d'autre.

### Calcul pur : `core/habit/Habits.kt`

Fonctions pures (heure, fuseau et fériés en paramètres, `architecture.md`) :

- `eligible(task)` : espace Perso, `origin == null`, règle `DAILY`, `WEEKDAYS` ou `WEEKLY` (`ELIGIBLE_RULES`), pas d'exemple ;
  `isHabit(task)` : déclarée et éligible.
- `seriesKey(task) = seriesId ?: id` et `seriesOf(task, tasks)` (idiome de `HistoryEstimators`).
- `answers(series)` : les `habitScore` des occurrences faites, postérieures au dernier `habitSince`, dans l'ordre de leur
  `updatedAt` ; `scores(task, tasks)`, `statusOf(task, tasks)`.
- `status(scores)` : parcours avec hystérésis. `IN_PROGRESS` au départ ; `INSTALLED` dès 3 réponses et les 2 dernières ≥ 6
  (`INSTALLED_MIN_ANSWERS`, `INSTALLED_SCORE`) ; une installée revient à `IN_PROGRESS` à une réponse ≤ 4 (`DROP_SCORE`).
  `justInstalled(before, score)` déclenche les félicitations.
- `asks(snapshot, task, now, batched)` : réglage actif, coche non groupée, tâche à faire et habitude, et aucune réponse
  depuis la déclaration ou la dernière il y a 14 jours au moins (`ASK_EVERY_DAYS`). La règle « reposer après 28 jours sans
  occurrence faite » en découle (28 jours sans occurrence faite, c'est 28 jours sans réponse) : pas de code à part,
  `HabitsTest.theQuestionIsAskedAgainAfterTwentyEightDaysWithoutADoneOccurrence` le prouve.
- `batchedChecks(tasks)` : les coches groupées **reconstituées après coup** (l'écran ne les retient pas) : une tâche Perso
  faite dont une autre a sa date de fin moins de `SpentReview.BATCH_SECONDS` (60 s) avant.
- `doneMinute(task, firstStarts, batched, tz)` : heure d'une occurrence faite = début de son premier chrono
  (`DurationEstimator.firstStarts`), sinon sa date de fin (`updatedAt`) ; `null` pour une coche groupée sans chrono.
- `regular(series, today, tz, holidays, firstStarts, batched)` : le seuil « série régulière » (§ 7) : ancienneté ≥ 14 jours
  (`MIN_AGE_DAYS`) ; fenêtre = les 42 jours pleins avant aujourd'hui (`WINDOW_DAYS`), bornée au début de la série ;
  occurrences prévues selon la règle (`expected` : jours ouvrés hors week-ends et fériés ; hebdomadaire au jour de
  `recurrenceDayOfWeek`, sinon de l'échéance, sinon une par semaine pleine) ; faites ≥ 75 % et ≥ 5 (`MIN_DONE_RATIO`,
  `MIN_DONE`, faites plafonnées aux prévues) ; à une heure au plus de la médiane (`HOUR_TOLERANCE_MINUTES = 60`) pour ⅔
  au moins des faites (`MIN_STABLE_RATIO`). Rend `Regularity` (heure médiane arrondie au quart d'heure) ou `null`.
- `suggestions(snapshot, today, tz, holidays, dismissed)` : séries Perso avec une occurrence à faire éligible, pas déjà
  habitude, pas écartées, régulières ; les plus régulières d'abord (part à heure stable, puis faites, puis titre). Vide
  réglage coupé. `observedMinute(...)` : l'heure observée d'une série (préremplissage). `active(tasks)` : une occurrence à
  faire par habitude active, par titre (Statistiques). `cleanAfter(text)` : bords retirés, 80 caractères.

### Base scientifique dans le code : `core/habit/HabitScience.kt` + ressources

- `HabitScience.REFERENCES` : `Reference(id = "R6", citation, doi)`, citation APA en texte (non traduite) ; seules les
  20 références citées par une affirmation (celles de contexte du § Base scientifique n'y sont pas).
- `HabitScience.CLAIMS` : `Claim(id = "H1", level = EvidenceLevel.SOLID | MODERATE | WEAK, refs)` ; `doiUrl(doi)`.
- Textes : `habit_claim_h1` … `habit_claim_h13` dans les **six langues** ; `CLAIM_TEXTS` (`ui/habit/HabitSources.kt`)
  les relie aux identifiants.
- `HabitScienceTest` : chaque affirmation a ≥ 1 référence existante, aucune référence orpheline, identifiants H1 à H13,
  DOI de la forme `10.xxxx/…`. `HabitStringsTest` : un texte par affirmation, aucun texte orphelin.

### Données (`data`)

- Migration `16.sqm` (schéma 16 → 17) : `habit_since TEXT`, `habit_after TEXT NOT NULL DEFAULT ''`, `habit_score INTEGER`
  en fin de `task` (même ordre dans `Kairos.sq`). Marqueur `17L` dans `KairosStore.MARKERS`. Requêtes `insertTask`,
  `insertTaskWithId`, `updateTask` et `Mappers` complétés. Les montages de tests d'anciens schémas retirent ces colonnes.
- `KairosRepository.updateTask` : `TaskEdit.habit: HabitChange?` (`HabitChange(on, after)`, `null` = inchangée) déclare
  (date gardée si déjà déclarée, phrase nettoyée) ou retire l'habitude ; déclarée seulement si la tâche, règle après
  édition comprise, est éligible. La fiche ouverte pendant une fusion (`keepUnchanged`) garde `habitSince` et `habitAfter`
  champ par champ.
- `answerHabit(taskId, score)` : écrit `habitScore` (borné 1 à 7) avec `touch = false` (la réponse ne déplace pas la date de
  fin), sur une habitude éligible ; rend vrai au passage à « installée ».
- **Export** (`ExportCodec.TaskJson`) : `habitSince`, `habitAfter`, `habitScore` facultatifs, omis à leur défaut (un export
  sans habitude est inchangé) ; `FORMAT_VERSION` inchangé ; lecture : phrase bornée, score hors 1 à 7 refusé.
- **Synchro** : trois champs de plus dans le registre de la tâche (`SyncRecords.lift` / `buildTask`), fusion champ par champ ;
  un champ ajouté par une version plus récente reçoit son empreinte sans marque (`synchronisation.md`).
- **Isolation** : les champs ne sont jamais posés sur une tâche d'équipe ou reçue (`eligible`).

### Réglages

- `Settings.habitsEnabled: Boolean?` (`null` = activé ; `habitsOn`), comme `askSpentOnDone` : rien d'écrit tant qu'on n'y
  touche pas ; partagé par la synchro (`SHARED_SETTINGS` par défaut). Champ de formulaire `habitsEnabled`, catégorie
  Planification (`SettingsCategory`).
- `SettingsFormCards` : section `HABITS` (icône `SelfImprovement`) juste après « Durées apprises » : interrupteur,
  explication, limites (seuils de produit, traduction non validée) et bouton « Sources scientifiques », qui ouvre
  `HabitSourcesDialog` (un dialogue défilant : le formulaire reste tel quel).
- Mémoire propre à l'appareil : `GuideSettings.habitsDismissed: List<Long>` (séries écartées) ; « Revoir le guide depuis le
  début » l'efface avec le reste.

### Interface (`ui`)

- **Fiche** (`EditTaskDialog`, paramètres `habit: HabitSheet?` et `openHabit`) : `SheetSection.HABIT` après « Dates et
  temps », visible quand `habit` est donné (Perso, réglage actif) et la règle choisie éligible. Interrupteur « En faire une
  habitude » (l'heure observée préremplit l'heure fixe vide, une fois), heure fixe rappelée, heure observée, champ
  « Après… », statut et `HabitCurve` (ligne neutre, valeurs annoncées au lecteur d'écran). `EditField.HABIT` et
  `HABIT_AFTER` (`habit = true`, hors de `EditField.personal`). `HabitChange` n'est envoyé que si la section a changé
  quelque chose (toujours pour une tâche en création marquée habitude).
- **Question à la coche** : `DayScreen.toggleDone`, `batched` d'abord (rien), puis `Habits.asks` **avant**
  `SpentReview.asks` : « Temps passé ? » n'est alors pas posé. `HabitQuestionDialog` : l'item, sept boutons de 48 dp au moins
  dans un `FlowRow` (passent à la ligne à 360 dp), bornes nommées, « Pas maintenant ». Une réponse qui installe l'habitude
  affiche `habit_installed` (message de la coquille). Pas de question en démonstration ; les autres chemins de coche
  (widgets, « Fait, suivante ») n'en posent pas.
- **Marque** : `HabitMark` (`KairosIcons.SelfImprovement`, 16 dp, `onSurfaceVariant`, « Habitude » pour le lecteur
  d'écran) dans `TaskTags` (`RowContext.habitsOn`) et dans `DayCard` de la vue Semaine.
- **Statistiques** : `HabitCards` après les dérangements (hors de `StatsCard` : ni PNG ni CSV) : par habitude active, titre,
  statut, dernière réponse et petite courbe.
- **Conseil** : `TipId.HABIT_SUGGEST` (`recurring`, carte `habits`) ; condition = `Habits.suggestions(...).first()` ;
  `Tip.taskId` et `Tip.seriesKey` ; « Montre-moi » ouvre `GuideTarget.Habit(taskId)` : la coquille va sur la vue Jour et
  pose `NavState.habitRequest`, que `DayScreen` acquitte en ouvrant la fiche sur sa section Habitude ;
  « Ne plus me le proposer » : `Tips.dismissed(memory, id, seriesKey)` ajoute la série à `habitsDismissed`.
- **Accompagnement** : `HabitCoach.STEPS` (le geste, sa taille, l'heure, « Après… », ce qui attend, enregistrer) sur la
  fiche de création d'une quotidienne déjà marquée habitude. Lancé par `GuideTarget.Habit()` (sans tâche) : la carte du
  catalogue, l'entrée « Créer une habitude » de `DiscoverDialog` (chapitre Organiser) et le repli du conseil ;
  `NavState.firstTaskHabit`, `startTask(recurring, habit)`.
- **Catalogue** : carte `habits` (chapitre Organiser, après « Récurrence », `since = "4.6.0"`, toutes plateformes) ; README :
  « Habits » / « Habitudes ».
- **Textes** : six langues ; en anglais, l'item validé mot pour mot.

### README, site, magasins

- `README.md` et `README.fr.md` : une puce et une ligne du tableau des fonctionnalités.
- `kmp/tools/make_habits_page.py` (bibliothèque standard) lit `HabitScience.kt` et les `strings.xml`, écrit
  `habitudes/index.html` (français) et `<langue>/habits/index.html` (en, es, de, ru, it) dans le site assemblé ; étape
  « Page des habitudes » de `.github/workflows/pages.yml`, script ajouté au filtre `paths` ; `--check` vérifie la lecture.
- Fiches des magasins : rien en beta.

### Tests

`HabitsTest`, `HabitScienceTest` (`core`) ; `HabitRepositoryTest` (déclaration depuis la fiche et héritage, équipe et
mensuelle refusées, fin, réponse sans toucher la date de fin et félicitations au seul passage, export identique sans
habitude, aller-retour, base) (`data`) ; `HabitStringsTest`, `StringsParityTest`, `FeatureCatalogTest`, `TipsCatalogTest`,
`TeamTaskCoachTest` (sans les champs d'habitude) (`ui`). Pas encore de test d'interface de bureau de la question ni de
l'accompagnement.

### Décisions et pièges tracés

- **Champs de tâche plutôt que tables** (ci-dessus).
- **Heure = heure fixe existante** : un seul champ d'heure, déjà recopié par la récurrence.
- **Date de fin = `updatedAt`** (choix du propriétaire, 2026-11-24) : seules les actions de l'utilisateur sur la tâche le
  bougent (cocher, décocher, la modifier dans sa fiche), les écritures automatiques le gardent ; pas de champ `doneAt`,
  l'historique existant sert tout de suite. Limite : rouvrir une occurrence faite pour la modifier déplace son heure.
- **Coches groupées reconstituées** à partir des `updatedAt` voisins, car l'écran ne les retient pas.
- **Réponse sans `touch`** : écrire le score ne déplace pas la date de fin de l'occurrence.
- **Coche groupée : pas de question**, comme « Temps passé ? ».
- **Réglage en booléen nullable** plutôt qu'un sous-objet : un seul champ, la règle de `askSpentOnDone`.
- **Sources en dialogue** plutôt qu'en écran secondaire : pas de nouvelle navigation, le formulaire des Réglages reste ouvert.
- **Accompagnement lancé par le guide** (carte, hub, conseil) plutôt qu'à la première bascule de l'interrupteur : la fiche
  d'une tâche existante reste sans panneau ; aucune mémoire à tenir.
- **Carte des Statistiques sans lien vers la fiche** : la fiche s'ouvre depuis la vue Jour.
- **Versions mélangées** : un appareil resté avant la 4.6.0 ne connaît pas les champs d'habitude ; la synchro est prévue
  pour qu'un champ ajouté plus tard garde sa valeur, mais une habitude ne se suit vraiment qu'entre appareils à jour.

## Base scientifique

Vérifiée le 2026-11-24 : chaque DOI résolu (Crossref), métadonnées relevées, résultats relus dans le résumé (PubMed,
Crossref, OpenAlex) ou dans le texte intégral quand il est ouvert (Lally 2010, Buyalskaya 2023, Beshears 2021). Ce
registre a servi de semence à la **base unique** (§ 13) : `core/habit/HabitScience.kt` (références et liens) et les
textes `habit_claim_h1` à `h13` des ressources, qui font foi pour l'application et la page du site. Niveaux : **solide** (méta-analyse ou résultats
convergents), **modéré** (un essai randomisé ou de grandes données observationnelles), **faible** (exploratoire,
corrélationnel, petit échantillon).

### Affirmations (affichées dans l'écran des sources ; texte validé par le propriétaire le 2026-11-24)

| # | Affirmation | Niveau | Références |
|---|---|---|---|
| H1 | Une habitude est un comportement déclenché par un signal de contexte, appris en répétant le même geste dans le même contexte. | Solide | R1, R2 |
| H2 | L'installer prend des semaines à des mois, avec de très grands écarts entre personnes : il n'y a pas de « nombre magique » de jours. | Modéré | R6, R7, R8 |
| H3 | Manquer une occasion isolée ne compromet pas l'installation. | Modéré | R6 |
| H4 | Garder le même contexte (moment, routine) aide l'automatisme à se former. | Modéré | R15, R11, R10 |
| H5 | Une heure fixe sert aussi bien de signal qu'une routine ; c'est la répétition en réponse au signal qui compte. | Modéré | R9 |
| H6 | S'accrocher à un événement de sa journée (« après le déjeuner ») installe mieux l'automatisme qu'un simple rappel, qui peut rendre dépendant de l'appli. | Faible | R16, R17 |
| H7 | Un plan « Si…, alors… » aide à passer à l'action. | Solide | R12 |
| H8 | Choisir soi-même son geste et son moment va avec des habitudes plus fortes ; une fenêtre horaire imposée de l'extérieur n'a pas mieux installé le sport qu'une liberté de moment. | Faible | R7, R26 |
| H9 | L'automatisme se mesure en le demandant ; une seule question validée suffit quand il faut faire bref. | Solide | R4, R5, R3 |
| H10 | Le plaisir qu'on y prend renforce le lien entre répétition et habitude. | Faible | R13 |
| H11 | Un retour verbal positif ne nuit pas à la motivation, contrairement aux récompenses attendues ; un renforcement positif n'a pas aidé l'habitude dans une appli. | Solide (laboratoire) | R23, R16 |
| H12 | Mettre en avant une série rompue fait moins persévérer que mettre en avant une série intacte. | Modéré | R21 |
| H13 | Suivre ses progrès aide à atteindre son but. | Solide | R24 |

Ce que la base **ne** soutient **pas** (et que Kairos ne dit donc pas) : un délai garanti ; qu'adapter le créneau à
l'heure observée aide l'habitude ; qu'une mesure tirée du chrono ou des coches mesure l'automatisme ; que des badges ou
des félicitations installent une habitude ; que Kairos lui-même ait été évalué.

### Références

Psychologie de l'habitude :

- **R1** Wood, W., & Rünger, D. (2016). Psychology of habit. *Annual Review of Psychology, 67*, 289–314.
  https://doi.org/10.1146/annurev-psych-122414-033417 — revue.
- **R2** Gardner, B., Phillips, L. A., & Judah, G. (2016). Habitual instigation and habitual execution: Definition,
  measurement, and effects on behaviour frequency. *British Journal of Health Psychology, 21*(3), 613–630.
  https://doi.org/10.1111/bjhp.12189 — enquête prospective, N = 229 ; l'habitude agit surtout sur le déclenchement.
- **R3** Verplanken, B., & Orbell, S. (2003). Reflections on past behavior: A self-report index of habit strength.
  *Journal of Applied Social Psychology, 33*(6), 1313–1330. https://doi.org/10.1111/j.1559-1816.2003.tb01951.x —
  SRHI, 12 items, 4 études.
- **R4** Gardner, B., Abraham, C., Lally, P., & de Bruijn, G.-J. (2012). Towards parsimony in habit measurement:
  Testing the convergent and predictive validity of an automaticity subscale of the Self-Report Habit Index.
  *International Journal of Behavioral Nutrition and Physical Activity, 9*, 102. https://doi.org/10.1186/1479-5868-9-102
  — SRBAI, 4 items.
- **R5** Gardner, B., Lally, P., & Rebar, A. L. (2024). Can—and should—automaticity be self-reported using a single
  item? A secondary analysis of 16 datasets. *Applied Psychology: Health and Well-Being, 16*(4), 2438–2457.
  https://doi.org/10.1111/aphw.12600 — 16 jeux de données, N = 16 838 ; item recommandé « Behaviour X is something I do
  automatically », à réserver aux cas où une mesure brève est nécessaire.
- **R6** Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010). How are habits formed: Modelling
  habit formation in the real world. *European Journal of Social Psychology, 40*(6), 998–1009.
  https://doi.org/10.1002/ejsp.674 — 96 volontaires, 84 jours ; 95 % de l'automatisme maximal en 18 à 254 jours
  (254 extrapolé) ; médiane 66 jours sur les 39 courbes bien ajustées ; un oubli isolé sans effet notable.
- **R7** Singh, B., Murphy, A., Maher, C., & Smith, A. E. (2024). Time to form a habit: A systematic review and
  meta-analysis of health behaviour habit formation and its determinants. *Healthcare, 12*(23), 2488.
  https://doi.org/10.3390/healthcare12232488 — 20 études, 2 601 participants (11 à risque de biais élevé) ; médianes de
  59 à 66 jours, moyennes de 106 à 154, de 4 à 335 jours (4 études seulement) ; habitudes du matin et choisies soi-même
  plus fortes.
- **R8** Buyalskaya, A., Ho, H., Milkman, K. L., Li, X., Duckworth, A. L., & Camerer, C. (2023). What can machine
  learning teach us about habit formation? Evidence from exercise and hygiene. *PNAS, 120*(17), e2216115120.
  https://doi.org/10.1073/pnas.2216115120 — données objectives (salle de sport N = 30 110 ; lavage des mains N = 3 124) ;
  des mois pour la salle (médianes de 122 à 226 jours), quelques semaines pour le lavage des mains ; habitude mesurée par
  la prévisibilité, pas par l'automatisme.
- **R9** Keller, J., Kwasnicka, D., Klaiber, P., Sichert, L., Lally, P., & Fleig, L. (2021). Habit formation following
  routine-based versus time-based cue planning: A randomized controlled trial. *British Journal of Health Psychology,
  26*(3), 807–824. https://doi.org/10.1111/bjhp.12504 — essai randomisé, N = 192 ; pas de différence heure / routine ;
  médiane de 59 jours chez ceux qui ont installé l'habitude.
- **R10** Kaushal, N., & Rhodes, R. E. (2015). Exercise habit formation in new gym members: A longitudinal study.
  *Journal of Behavioral Medicine, 38*(4), 652–663. https://doi.org/10.1007/s10865-015-9640-7 — N = 111 ; la régularité
  est le premier prédicteur (β = 0,21).
- **R11** Zhu, L., Tao, Y., Zhou, B., Ma, H., Kaushal, N., Ma, X., Guo, Y., Zhao, R., Oguntuase, S. B., Li, H., & Zhang,
  L. (2026). Not all cues are equal: A systematic review and three-level meta-analysis of context consistency, physical
  activity and habit strength. *Applied Psychology: Health and Well-Being, 18*(4), e70204.
  https://doi.org/10.1111/aphw.70204 — 14 études, N = 6 069 ; régularité du contexte et force de l'habitude r = 0,20
  (petit effet), signaux de moment et de routine les plus liés.
- **R12** Gollwitzer, P. M., & Sheeran, P. (2006). Implementation intentions and goal achievement: A meta-analysis of
  effects and processes. *Advances in Experimental Social Psychology, 38*, 69–119.
  https://doi.org/10.1016/S0065-2601(06)38002-1 — 94 tests, d = 0,65 (le nombre de participants n'est pas repris : non
  confirmé à la source).
- **R13** Judah, G., Gardner, B., Kenward, M. G., DeStavola, B., & Aunger, R. (2018). Exploratory study of the impact of
  perceived reward on habit formation. *BMC Psychology, 6*, 62. https://doi.org/10.1186/s40359-018-0270-z — exploratoire,
  N = 118 ; motivation intrinsèque et plaisir.
- **R14** Wood, W., & Neal, D. T. (2016). Healthy through habit: Interventions for initiating & maintaining health
  behavior change. *Behavioral Science & Policy, 2*(1), 71–83. https://doi.org/10.1177/237946151600200109 — synthèse.
- **R15** Ebert, J. E. J., & Lin, X. Y. (2024). Confirming the causal role of consistent contexts in developing a
  walking habit: A randomized comparison with varied contexts. *Annals of Behavioral Medicine, 58*(11), 741–751.
  https://doi.org/10.1093/abm/kaae045 — essai randomisé, N = 127 ; seul le contexte constant a installé l'automatisme
  (mais pas un meilleur maintien de la marche ensuite).
- **R18** Lally, P., & Gardner, B. (2013). Promoting habit formation. *Health Psychology Review, 7*(sup1), S137–S158.
  https://doi.org/10.1080/17437199.2011.603640 — revue (contexte, aucun chiffre repris).
- **R19** Lally, P., Wardle, J., & Gardner, B. (2011). Experiences of habit formation: A qualitative study.
  *Psychology, Health & Medicine, 16*(4), 484–489. https://doi.org/10.1080/13548506.2011.555774 — qualitative, N = 10.

Outils numériques et motivation :

- **R16** Stawarz, K., Cox, A. L., & Blandford, A. (2015). Beyond self-tracking and reminders: Designing smartphone
  apps that support habit formation. *CHI '15*, 2653–2662. https://doi.org/10.1145/2702123.2702230 — revue de 115
  applis et essai de 4 semaines (133 inscrits, 96 jusqu'au bout) ; rappels : répétition mais moins d'automatisme ;
  signal d'événement : plus d'automatisme ; renforcement positif sans effet.
- **R17** Renfree, I., Harrison, D., Marshall, P., Stawarz, K., & Cox, A. (2016). Don't kick the habit: The role of
  dependency in habit formation apps. *CHI EA '16*, 2932–2939. https://doi.org/10.1145/2851581.2892495 — qualitative
  (effectif non confirmé) ; rappels et séries rendent dépendant de l'appli.
- **R20** Pinder, C., Vermeulen, J., Cowan, B. R., & Beale, R. (2018). Digital behaviour change interventions to break
  and form habits. *ACM TOCHI, 25*(3), 15. https://doi.org/10.1145/3196830 — théorique.
- **R21** Silverman, J., & Barasch, A. (2023). On or off track: How (broken) streaks affect consumer decisions.
  *Journal of Consumer Research, 49*(6), 1095–1117. https://doi.org/10.1093/jcr/ucac029 — 7 études ; effet relatif
  (série rompue mise en avant contre série intacte), atténué si la série est « réparable ».
- **R22** Hamari, J. (2017). Do badges increase user activity? A field experiment on the effects of gamification.
  *Computers in Human Behavior, 71*, 469–478. https://doi.org/10.1016/j.chb.2015.03.036 — activité sur une place de
  marché, pas une habitude (contexte).
- **R23** Deci, E. L., Koestner, R., & Ryan, R. M. (1999). A meta-analytic review of experiments examining the effects
  of extrinsic rewards on intrinsic motivation. *Psychological Bulletin, 125*(6), 627–668.
  https://doi.org/10.1037/0033-2909.125.6.627 — 128 études ; récompenses contingentes d = −0,28 à −0,40 ; retour
  positif d = +0,33.
- **R24** Harkin, B., Webb, T. L., Chang, B. P. I., Prestwich, A., Conner, M., Kellar, I., Benn, Y., & Sheeran, P.
  (2016). Does monitoring goal progress promote goal attainment? A meta-analysis of the experimental evidence.
  *Psychological Bulletin, 142*(2), 198–229. https://doi.org/10.1037/bul0000025 — 138 études, N = 19 951, d = 0,40.
- **R25** Michie, S., Richardson, M., Johnston, M., Abraham, C., Francis, J., Hardeman, W., Eccles, M. P., Cane, J., &
  Wood, C. E. (2013). The behavior change technique taxonomy (v1) of 93 hierarchically clustered techniques.
  *Annals of Behavioral Medicine, 46*(1), 81–95. https://doi.org/10.1007/s12160-013-9486-6 — taxonomie (contexte).
- **R26** Beshears, J., Lee, H. N., Milkman, K. L., Mislavsky, R., & Wisdom, J. (2021). Creating exercise habits using
  incentives: The trade-off between flexibility and routinization. *Management Science, 67*(7), 4139–4171.
  https://doi.org/10.1287/mnsc.2020.3706 — expérience de terrain, N = 2 508 ; incitations sans fenêtre imposée : +0,19
  visite par semaine pendant, et moindre baisse après, qu'une fenêtre de 2 h imposée (incitations financières, une
  entreprise).
- **R27** Milkman, K. L., et al. (2021). Megastudies improve the impact of applied behavioural science. *Nature, 600*,
  478–483. https://doi.org/10.1038/s41586-021-04128-4 — 61 293 personnes, 54 interventions ; 8 % seulement gardent un
  effet après l'intervention (contexte : la plupart des effets s'éteignent).
- **R28** Zhu, Y., Long, Y., Wang, H., Lee, K. P., Zhang, L., & Wang, S. J. (2024). Digital behavior change intervention
  designs for habit formation: Systematic review. *Journal of Medical Internet Research, 26*, e54375.
  https://doi.org/10.2196/54375 — cartographie de 41 études (activité physique), pas d'efficacité mesurée.
- **R29** Ma, H., Wang, A., Pei, R., & Piao, M. (2023). Effects of habit formation interventions on physical activity
  habit strength: Meta-analysis and meta-regression. *International Journal of Behavioral Nutrition and Physical
  Activity, 20*, 109. https://doi.org/10.1186/s12966-023-01493-3 — 10 études, SMD = 0,31 ; récompense sociale associée à
  moins d'efficacité.

Les références de contexte (R14, R18 à R20, R22, R25, R27 à R29) étayent le cadrage mais ne portent aucune affirmation
affichée ; la base unique peut les omettre.

## Décisions de cadrage (propriétaire, 2026-11-24)

- **Unité = la série récurrente**, pas le type de tâche : une habitude est un geste précis dans un contexte précis ;
  un type (« Sport ») regroupe des gestes différents. La détection existante par type (`planning-personnalise.md` lot C)
  ne suffit pas.
- **Validation par la question**, pas par les données : seule l'auto-évaluation mesure l'automatisme ; l'item unique
  « Behaviour X is something I do automatically » est validé (Gardner, Lally et Rebar, 2024). Traduction française
  **littérale** (« « Méditer », c'est quelque chose que je fais automatiquement. ») ; aucune validation française du
  SRHI ni du SRBAI trouvée le 2026-11-24, d'où la mention dans l'explication (écartées : « Je fais … automatiquement »,
  qui change la structure de l'item, et « sans y penser », qui perd le mot validé).
- **Déclencheur stable** proposé une fois puis fixé, au lieu d'un créneau qui suit l'observation (écarté : l'adaptation
  continue de l'issue, faute de preuve, et pour éviter la boucle d'auto-renforcement déjà notée dans
  `planning-personnalise.md` § Anti-boucle).
- **Heure fixe + « Après… »** : l'heure sert au planning ; la phrase « Après… » sert de signal de routine (une heure fixe
  vaut un signal de routine pour installer l'habitude, Keller et al., 2021 ; un signal lié à un événement donne plus
  d'automatisme qu'un simple rappel, Stawarz et al., 2015).
- **Déclarée et proposée** : choisir soi-même le geste et le déclencheur va avec des habitudes plus fortes ; la
  proposition aide à repérer ce qui est déjà régulier.
- **Aspect sobre et félicitations uniques**, sans *streak* ni badge (écarté : l'aspect « récompense » de l'issue).
- **Sources dans l'application** : carte des Réglages activée par défaut, affirmation → références, base unique.
- **Perso seulement.**
- **Règles quotidienne, jours ouvrés et hebdomadaire** seulement (2026-11-24).
- **« Installée » = trois réponses, les deux dernières à 6 ou 7** : décision de produit, inspirée du palier des courbes
  de Lally et al. (2010) ; la science ne donne pas de seuil universel.
- **À la coche, l'automatisme passe avant le temps passé** (choix du propriétaire, 2026-11-24) : la question d'habitude
  est rare (tous les 14 jours) et nourrit la courbe ; le temps manquant se rattrape dans la revue du Bilan.
- **Page du site générée** depuis la base unique (écarté : un lien vers le README ; le report après la 4.6.0).
- **Seuil « série régulière »** (2026-11-24) : 14 jours d'ancienneté, 42 jours examinés, 75 % et 5 occurrences faites,
  deux tiers à une heure de la médiane, coches groupées ignorées (écartés : 60 % et ± 1 h 30, trop de fausses
  régularités ; 85 % et ± 45 min, trop rare).
- **Retour à « en cours »** quand la dernière réponse tombe à 4 ou moins, et **mention de la traduction non validée**
  dans l'explication : ajouts validés le 2026-11-24.

## Impacts sur les specs existantes (reportés en 4.6.0-beta.1)

`modele-donnees.md`, `recurrence.md`, `export-import.md`, `synchronisation.md`, `durees-reelles.md`, `conseils.md`,
`premiere-tache.md`, `reglages.md`, `fiche-tache.md`, `vue-jour.md`, `vue-semaine.md`, `statistiques.md`, `publication.md`,
`liste-fonctionnalites.md` : un renvoi à cette spec à l'endroit concerné.
