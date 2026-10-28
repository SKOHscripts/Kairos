# Réglages

_Rôle : tout ce qui se règle dans Kairos, depuis un seul écran rangé par catégories, avec une
validation par champ. Fichiers couverts :
`kmp/core/src/commonMain/.../core/settings/SettingsForm.kt` (champs, bornes,
validation, pur), `kmp/core/.../model/Settings.kt` (modèle, décrit par
`modele-donnees.md`), `kmp/ui/.../settings/` (`SettingsScreen.kt`, `SettingsCategory.kt`,
`SettingsFormCards.kt`). La carte Données est décrite par `export-import.md`,
la carte Mises à jour par `mises-a-jour.md`, la carte
Apparence par `apparence.md`, la carte Langue par `i18n.md`. Tests :
`core/.../settings/SettingsFormTest.kt`, `ui/.../settings/SettingsCategoryTest.kt`,
`M5ScreensUiTest.anInvalidFieldBlocksSavingThenAValidFormIsSaved`, `SettingsCategoriesUiTest`,
`SettingsLayoutUiTest`._

État : **jalon M5** ; rangement par catégories implémenté le 2026-10-22. Kairos n'a aucun secret à stocker (pas de trousseau) ni
de réglage de chemin de base, de proxy ou de niveau de log : tout ce qui se
règle l'est depuis ce formulaire.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos démarre sans rien configurer ; tout ce qui se règle se règle depuis
l'application, jamais dans un fichier. Un réglage faux (heure 25, texte dans
un nombre) ne doit jamais être enregistré, ni à moitié.

### Comportement attendu (utilisateur)

- Écran secondaire « Réglages » (roue crantée, `navigation-theme.md`), **rangé par catégories** (2026-10-22, sur le
  modèle des réglages de Rethink : une liste de catégories, chacune avec son icône, son titre et un résumé de ce
  qu'elle contient). Toucher une catégorie ouvre son écran ; ses cartes s'y suivent, chacune avec son icône à côté du
  titre. Les catégories, dans cet ordre, et leurs cartes :
  1. **Général** (« Langue, apparence », « …, mises à jour » sur le bureau) : **Langue** (Système, Français ou
     English ; `i18n.md` § Choix de la langue), **Apparence** (thème sombre et couleur du thème, `apparence.md`), puis sur le
     bureau **Mises à jour** (`mises-a-jour.md`) et sur une copie portable **Raccourci** (`raccourci-portable.md`).
  2. **Journée et horaires** : **Journée de travail** (durée par défaut d'une tâche, marge après un créneau occupé,
     début et fin de journée), **Horaires par jour de la semaine** (3.4, lot B1 : pour chaque jour, « Comme
     d'habitude », « Personnalisé » avec « De » et « À » en heures, ou « Jour libre » ; champs `schedule.dN.mode`,
     `.start`, `.end`, N = 0 lundi … 6 dimanche ; `ScheduleCard` ; 3.5, lot B2 : un jour personnalisé a une à
     quatre **plages** (`schedule.dN.count`, `.ctx`, `schedule.dN.wK.start|end|ctx`), chacune réservable à un
     contexte, « Ajouter une plage » ; une plage commence après la fin de la précédente (« Au moins … » sur son
     début)), **Contextes** (`ContextsCard` : jusqu'à quatre, nom unique et types de tâche en pastilles, un type
     n'est que dans un contexte ; un type sorti de « Types de tâche » reste, signalé « type inconnu »),
     **Jours fériés** (français, dates supplémentaires).
  3. **Planification** : **Types de tâches**, **Noms des niveaux** (3.9.0, lot D de `planning-personnalise.md` :
     un champ par priorité `levelNames.p0`…`p2` et par palier de points `levelNames.pts1`…`pts21`, libellé = nom par
     défaut, vide = nom par défaut, 16 caractères au plus, nom unique dans son échelle ; `LevelNamesCard` ;
     `Settings.levelNames` reste nul tant qu'aucun nom n'est choisi), **Meilleures heures** (3.6, étape C2 de `planning-personnalise.md` :
     interrupteur `schedule.bestHours.enabled` désactivé par défaut, force `schedule.bestHours.strength` en trois
     niveaux Faible / Moyenne / Forte (0 à 2, Moyenne par défaut), et une heure préférée facultative (0 à 23) par
     type de tâche dans `schedule.bestHours.preferred`, un seul champ texte `type=heure,…`, et (3.9.0, v2) « Mesurer par »
     `schedule.bestHours.measure` (Fréquence par défaut, Efficacité, Les deux ; boutons segmentés, valeur inconnue = fréquence) ; `BestHoursCard`, qui
     n'affiche la force et les heures que si l'interrupteur est actif ; une heure hors des bornes est refusée sur
     ce champ ; `schedule` reste nul tant que les trois valent leur défaut), **Durées apprises** (3.4,
     `durees-apprises.md` : interrupteur `learning.enabled`, occurrences considérées `learning.history` ≥ 3,
     occurrences nécessaires `learning.minSamples` ≥ 2 et au plus `history`, sinon `TOO_LARGE` sur
     `learning.minSamples` ; `Settings.learning` reste nul tant que les trois valent leur défaut), **Garde-fous**
     (seuils « traîne » en retard et sans date, seuil de surcharge P0), puis le groupe replié **« Avancé : comment
     les tâches sont ordonnées »** (`planning-personnalise.md` lot A, ouvert d'office quand un de ses champs est en
     erreur) : **Score de priorité** (base de valeur, horizon d'urgence, poids maximal de l'urgence, points par
     défaut) et **Creux de l'après-midi** (activé, début, creux le plus profond, fin, force de la pénalité).
  4. **Chrono et statistiques** : **Alertes du chrono** (chrono oublié, pause suggérée, son de secours),
     **Rappels** (3.8.0 : « Rappeler les tâches à heure fixe », « Combien de minutes avant » 0-120, 10 par défaut,
     « Signaler aussi la tâche suivante du plan » ; désactivés par défaut, `rappels.md`),
     **Statistiques** (fenêtre en semaines).
  5. **Équipe** : la carte **Équipe** (interrupteur « Gestion d'équipe », puis, mode activé, nom de l'équipe et du
     manager, seuils « sans avancement », « ballottée » et limite d'en-cours, horizon, taux de focus, heures par
     point, seuil d'alerte de charge et tolérance d'affinité (`equipe-charge.md`), tirages par simulation,
     historique, échantillon minimum et seuil d'échéance en danger (`equipe-simulation.md`), « Supprimer les
     données d'équipe… » avec sauvegarde préalable ; désactivation confirmée, `equipe.md`,
     `equipe-backlog-suivi.md`).
  6. **Données** : **Données** (export, import, `export-import.md`), **Calendriers** (3.10.0, import d'un fichier
     `.ics`, réimport, retrait, `connexions-externes.md`), puis **Réglages par défaut** avec
     **« Restaurer les valeurs par défaut »**.
  7. **À propos** : **« Découvrir Kairos »** puis **« À propos et guide »**, deux lignes qui ouvrent chacune leur
     écran, puis **« Revoir le guide depuis le début »** (3.7.0, issue #94, `visite-guidee.md` § Persistance) : texte et
     bouton « Recommencer », inactif tant que le guide n'a rien retenu ; il efface aussitôt ce qu'il a retenu (pas
     besoin d'« Enregistrer ») et un message « Le guide repart du début. » propose « Annuler ».
- **Téléphone et fenêtre étroite** (moins de 840 dp de large pour l'écran) : la liste des catégories, sous la phrase
  d'introduction, puis une catégorie à la fois ; la barre du haut prend le titre de la catégorie, et la flèche
  retour (ou le retour système, ou Échap) revient à la liste, puis ferme les Réglages. Rouvrir les Réglages
  (roue crantée) montre toujours la liste ; revenir de « À propos et guide » ramène à la catégorie quittée.
- **Fenêtre large** (840 dp ou plus) : la liste à gauche (320 dp), la catégorie choisie à droite (« Général »
  d'office), avec son titre en tête ; la ligne choisie est en conteneur de sélection. La flèche retour ferme les
  Réglages.
- Une catégorie qui contient un champ refusé le dit sur sa ligne (icône d'avertissement, « Un champ à corriger »).
- Chaque champ a un libellé court et, dessous, une phrase qui dit à quoi il
  sert ; un booléen est un interrupteur dont
  toute la ligne se touche.
- Une barre fixée en bas porte « Enregistrer » (actif dès qu'une valeur
  change), « Annuler les modifications » et « Modifications non
  enregistrées ». Un seul « Enregistrer » pour tout le formulaire, **toutes catégories confondues** : la barre
  reste sous la liste comme sous chaque catégorie, et une modification faite dans une catégorie survit au passage
  dans une autre jusqu'à « Enregistrer » ou « Annuler les modifications ».
- À l'enregistrement : chaque champ invalide affiche son erreur à la place de
  son aide (« Au plus 23. », « Un nombre entier est attendu. », « Date
  illisible : « 25/12 » (format AAAA-MM-JJ). »…), un message dit
  « Réglages non enregistrés », et **rien** n'est enregistré. Corriger un
  champ efface son erreur. Les règles entre champs (début de journée avant
  la fin ; début ≤ creux le plus profond ≤ fin) s'affichent dans un bandeau
  en tête de leur catégorie (« Journée et horaires », « Planification »), une fois chaque champ valide. Un
  enregistrement refusé **ouvre la catégorie** du premier champ fautif (dans l'ordre des catégories ; sinon celle
  du bandeau) et y **défile** jusqu'au champ (sinon jusqu'au bandeau) : à 360 dp, le champ
  est trois cartes plus bas et le message « Réglages non enregistrés » ne disait
  pas où regarder (`ErrorReveal` : un `BringIntoViewRequester` par clé, fourni par
  `LocalErrorReveal`).
- Une décimale s'écrit avec une virgule ou un point ; en français, le
  formulaire l'affiche à la virgule (« 0,8 », comme son aide).
- Un jour « Personnalisé » doit finir après avoir commencé : sinon l'erreur « Au moins H+1. » est sur son champ « À »
  (`SettingsForm.validate`). Repasser tous les jours à « Comme d'habitude » ramène `Settings.schedule` à nul : sauver le
  formulaire sans y toucher n'en crée jamais (export inchangé). « Restaurer les valeurs par défaut » remet aussi les jours.
- Les réglages valent aussitôt partout (planning, score, alertes, stats).
- « Restaurer les valeurs par défaut » remplit le formulaire des valeurs par défaut (`SettingsForm.restoreDefaults`), **sauf** les
  champs `team.*`, jamais touchés ; rien n'est écrit avant « Enregistrer » et « Annuler les modifications » revient en arrière.
  Il remet aussi le guide à zéro **à l'enregistrement** (3.7.0, issue #94) : « Enregistrer » devient actif si le guide a
  retenu quelque chose, même quand tous les champs valent déjà leur défaut. Il **garde** en revanche la mémoire des avis
  de mois sautés ignorés (`Settings.recurrence`, 3.13.0, `recurrence.md` § Avis des mois sautés) : une décision sur des
  données, pas une préférence ; le formulaire ne l'affiche pas et `validate` garde la valeur courante.
- « WSJF » n'est plus dans le titre de la section du score ni dans l'aide de l'agenda (jargon ; il reste expliqué dans « À propos
  et guide »).

### Critères de succès

- Valeurs par défaut relues puis revalidées à l'identique ; un champ absent
  garde sa valeur (`SettingsFormTest`).
- Hors bornes, vide, non entier, non nombre, date illisible : une erreur par
  champ, aucun enregistrement ; bornes propres de Kairos (figées par `SettingsFormTest`), borne basse exclue pour
  la base de valeur (`SettingsFormTest`).
- Un jour personnalisé et un jour libre s'enregistrent d'un coup ; une fin avant le début est refusée sur son champ ; `schedule`
  reste nul tant qu'aucun jour n'est propre (`M5ScreensUiTest.aCustomDayAndADayOffAreSavedAsTheSchedule`,
  `anEndBeforeTheStartIsRefusedOnItsField`, `SettingsFormTest.theScheduleStaysNullUntilADayIsCustomised`).
- La carte **« Découvrir Kairos »** (juste avant « À propos et guide ») ouvre le hub des chapitres (`visite-guidee.md`,
  `M6GuideUiTest.theDiscoverHubIsInTheSettingsAndOnHome…`).
- L'interrupteur « Apprendre les durées… » se coupe et s'enregistre sans toucher aux autres bornes
  (`LearnedDurationsUiTest.switchingTheFeatureOffHidesTheBadgeAndTheSettingIsSaved`).
- Heure 25 refusée, début après la fin refusé, puis 8 h, base 2,5 et creux
  désactivé (dans le groupe « Avancé », ouvert à la main) enregistrés d'un coup (`M5ScreensUiTest`).
- « Restaurer les valeurs par défaut » remplit le formulaire sans rien enregistrer ; « Enregistrer » les pose
  (`M5ScreensUiTest.restoringTheDefaultsFillsTheFormAndStillNeedsASave`) ; les champs `team.*` ne bougent pas
  (`SettingsFormTest.restoring_the_defaults_resets_every_field_but_the_team_ones`).
- Chaque champ du formulaire appartient à une catégorie, et chaque règle entre champs aussi
  (`SettingsCategoryTest`).
- Sur un téléphone, la liste des catégories s'affiche d'abord ; une catégorie s'ouvre avec son titre dans la barre,
  la flèche retour ramène à la liste puis ferme les Réglages
  (`SettingsCategoriesUiTest.onAPhoneACategoryOpensUnderItsTitleAndBackReturnsToTheList`).
- En fenêtre large, la liste et « Général » sont côte à côte, et choisir une catégorie la montre à droite
  (`SettingsCategoriesUiTest.aWideWindowShowsTheListAndTheChosenCategorySideBySide`).
- Une valeur changée dans une catégorie survit au passage dans une autre et s'enregistre avec le reste
  (`SettingsCategoriesUiTest.anEditSurvivesSwitchingCategoryAndIsSavedWithTheRest`).
- Un enregistrement refusé ouvre la catégorie du champ fautif, y défile jusqu'à lui, et la ligne de la catégorie
  le signale (`SettingsLayoutUiTest.aRefusedSaveOpensTheCategoryOfTheFlaggedFieldAndScrollsToIt`).

### Hors périmètre / différé

- Thème sombre (`navigation-theme.md`, `apparence.md` : décision rouverte pour `3.7.0`, issue #72).
- Recherche parmi les réglages : sept catégories se parcourent d'un coup d'œil ; à rouvrir si elles grossissent.
- Proxy saisi dans Kairos : écarté, le bureau suit le proxy du système ; le dossier des données et le journal de
  diagnostic du bureau sont dans `bureau-avance.md` (3.9.0, issue #115), hors du formulaire (propres à l'appareil).

## 2. Solution technique

### Champs (`SettingsForm`, `core`)

- `FIELDS` : un `SettingField` par réglage éditable, clé = nom du champ de
  `Settings` (préfixée `team.` pour ceux de `Settings.team` : `team.enabled`,
  `team.name`, `team.managerName` ; tant que `team` est nul, y écrire la
  valeur par défaut le laisse nul, pour qu'enregistrer le formulaire d'un
  solo ne crée jamais d'objet d'équipe), nature (`INT`, `DECIMAL`, `BOOL`, `TEXT`, `DATES`, `COLOR` ; `language` est un
  `TEXT` normalisé par `UiLanguage.normalize`, jamais refusé), bornes
  incluses, borne basse exclue (`minExclusive`), lecture et écriture.
  Bornes de `app/config.py` : durée par défaut ≥ 1 ; marge, horizon, poids
  de l'urgence, seuils « traîne » et de surcharge, pénalité du creux, seuils
  du chrono ≥ 0 ; heures 0-23 ; base de valeur > 0 ; points par défaut ≥ 1 ;
  fenêtre des stats ≥ 1.
- `values(settings)` : texte de chaque champ (« 4 » plutôt que « 4.0 »,
  booléens « true » / « false »).
- `validate(textes, base)` : chaque champ présent est vérifié ; une clé
  absente garde la valeur de `base`. Erreurs (`FieldError`) : `REQUIRED`,
  `NOT_INTEGER`, `NOT_NUMBER`, `TOO_SMALL` / `TOO_LARGE` (avec la borne),
  `INVALID_DATE`, `INVALID_COLOR` (avec le texte fautif). Puis, seulement sans erreur de
  champ, `GeneralError.WORKDAY_ORDER` (début ≥ fin) ou `DIP_ORDER` (creux
  activé seulement : désactivé, ses heures ne servent pas et ne bloquent
  rien). Un entier trop long pour être lu donne `TOO_LARGE` / `TOO_SMALL`
  avec la borne (ou la limite d'un entier), pas `NOT_INTEGER`. Rend les
  réglages complets, ou `null` à la moindre erreur. `ExportCodec.decode`
  s'en sert aussi pour refuser un export aux réglages hors bornes.
- Nettoyage à l'écriture : textes sans espaces de bord ; dates
  supplémentaires et types proposés sans vides **ni doublons** (le premier
  reste), séparés par « , » (`normalizeList`) ; couleur normalisée en
  `#RRGGBB` majuscules (`apparence.md`).

### Écran (`SettingsScreen`, `ui`)

- État : textes du formulaire, erreurs et règle violée, réinitialisés quand
  les réglages enregistrés changent (import d'un export) ; `dirty` = textes ≠
  valeurs enregistrées.
- `SettingsFormCards` : carte « filled » par section (`SettingsCard`, sans contour, `densite.md` ; titre marqué
  `heading`), `SettingInput` : `OutlinedTextField` (largeur bornée à 320 dp pour un nombre, pleine largeur pour un texte), clavier
  numérique ou décimal, `supportingText` = erreur sinon aide ; `SwitchRow` :
  `Switch` dans une ligne `toggleable` (rôle interrupteur, 48 dp).
- **Catégories** (`SettingsCategory.kt`) : `SettingsCategory` (`GENERAL`, `DAY`, `PLANNING`, `TRACKING`, `TEAM`,
  `DATA`, `ABOUT`, dans l'ordre de la liste), chacune avec son titre, son résumé et son icône (`Tune`, `Schedule`,
  `Sort`, `Timer`, `Groups`, `Database`, `Info`). `SettingsCategory.of(clé)` range chaque champ de `SettingsForm` :
  `language`, `themeColor`, `updateCheckEnabled` → Général ; journée de travail, `schedule.dN.*`, `schedule.cN.*`
  (contextes), `holidaysFr`, `extraHolidays` → Journée et horaires ; `taskTypes`, `levelNames.*`, `schedule.bestHours.*`,
  `learning.*`, garde-fous, score, creux → Planification ; chrono, `reminders.*` et `statsWindowWeeks` → Chrono et statistiques ;
  `team.*` → Équipe. Une clé inconnue lève une erreur (aucune ne tombe en silence hors de l'écran) ;
  `SettingsCategory.of(GeneralError)` : `WORKDAY_ORDER` → Journée et horaires, `DIP_ORDER` → Planification.
- **Navigation interne** (`SettingsNav`, porté par `NavState.settings` pour que la coquille le lise) : `category`
  (catégorie ouverte, `null` = la liste) et `singlePane` (posé par l'écran selon sa largeur). `AppShell` en tire
  `inCategory` = `singlePane` et une catégorie ouverte : titre de la barre = titre de la catégorie, flèche retour,
  `BackHandler` (retour système, Échap) → `category = null` ; sinon ils ferment l'écran secondaire. `onOpenSettings`
  remet `category` à `null` ; ouvrir « À propos » puis revenir ne la touche pas.
- **Mise en page** (`SettingsScreen`) : `BoxWithConstraints` ; deux volets si la largeur offerte à l'écran est
  d'au moins 840 dp (`SETTINGS_TWO_PANE_MIN`) : liste de 320 dp (`SettingsCategoryList`), séparée de la catégorie
  par un `VerticalDivider`, catégorie bornée à 720 dp et centrée ; chaque volet défile seul. Un seul volet sinon :
  la liste, ou la catégorie ouverte, colonne bornée à 720 dp. Deux volets sans catégorie choisie affichent
  `GENERAL`. `SaveBar` sous les volets dans les deux cas.
- **Liste** (`SettingsCategoryList`) : phrase d'introduction, puis une `ListItem` par catégorie dans une carte
  « filled » (`surfaceContainerLow`, sans ombre, marge intérieure `KairosSpacing.s`, `densite.md`), coins de ligne
  12 dp (`shapes.medium`) : icône en tête (`onSurfaceVariant`), titre,
  résumé, puis en fin de ligne l'icône `Warning` (teinte `error`, description « Un champ à corriger ») si la
  catégorie contient une erreur et, sur un seul volet, le chevron. Deux volets : la ligne choisie est en
  `secondaryContainer` (sélection, charte) et `selectable` (rôle onglet) ; un seul volet : `clickable`
  (rôle bouton). Chaque ligne porte la balise de test `settings-category-<NOM>`.
- **Contenu d'une catégorie** (`categoryContent` de `SettingsScreen`) : sur deux volets, le titre de la catégorie en
  `titleLarge` (`heading`) ; le bandeau de la règle entre champs si elle relève de cette catégorie ; puis ses
  cartes. `SettingsFormCards(category, …)` rend les sections du formulaire de la catégorie (`SECTIONS`, chacune
  rangée par sa catégorie) et y insère `ScheduleCard` et `ContextsCard` après « Journée de travail »,
  `BestHoursCard` après « Types de tâches », le groupe « Avancé » en fin de Planification.
- **Icônes de section** : `SettingsCardHeader(titre, aide, icône)` met l'icône (24 dp, `onSurfaceVariant`, décorative)
  devant le titre, l'aide dessous sur toute la largeur. Langue `Translate`, Apparence `Palette`, Mises à jour
  `Update`, Raccourci `InstallDesktop`, Journée de travail `Work`, Horaires par jour `CalendarViewWeek`, Contextes
  `Workspaces`, Jours fériés `EventBusy`, Types de tâches `Label`, Meilleures heures `LightMode`, Durées apprises
  `History`, Garde-fous `Shield`, Score de priorité `Leaderboard`, Creux de l'après-midi `Bedtime`, Alertes du chrono
  `NotificationsActive`, Rappels `Alarm`, Statistiques `BarChart`, Équipe `Groups`, Données `Database`, Réglages par défaut
  `SettingsBackupRestore` ; « Découvrir Kairos » `PlayArrow` et « À propos et guide » `Info` gardent leur
  `ListItem` dans une carte cliquable (`SettingsLinkCard`).
- Enregistrement refusé : catégorie à ouvrir = la première, dans l'ordre de `SettingsCategory`, qui contient une
  erreur de champ (sinon celle de la règle violée) ; `nav.category` y passe, deux images plus tard
  (`withFrameNanos`, la catégorie doit être composée et mesurée), `ErrorReveal.reveal` sur son premier champ fautif
  (sinon le bandeau).
- Bandeau de règle inter-champs : conteneur d'erreur et icône `Warning`
  (un vrai échec, charte). `SaveBar` : `Surface` `surfaceContainer` sous la
  colonne défilante, `Button` « Enregistrer », `TextButton` « Annuler les
  modifications ».
- Enregistrer : `SettingsForm.validate` puis `updateSettings`, message
  « Réglages enregistrés. » ou « Réglages non enregistrés… ».
- **Guide** (3.7.0, issue #94, `visite-guidee.md` § Persistance) : l'état du formulaire est indexé sur
  `current.copy(guide = null)` (une écriture du guide ne remet pas à zéro des modifications en cours) ; « Restaurer les
  valeurs par défaut » pose `restored`, qui rend le formulaire modifié si `current.guide` n'est pas nul et fait
  enregistrer `guide = null` ; « Annuler les modifications » l'efface. `GuideResetCard` (catégorie « À propos »,
  icône `Refresh`) : `KairosRepository.resetGuide()` puis le message à annulation (`LocalUndoMessages`), qui réécrit
  l'état d'avant par `updateGuide`.

### Décisions et pièges tracés

- **Un seul formulaire, un seul « Enregistrer »** : la carte
  du chrono du jalon M3, qui avait son propre bouton, y est fondue
  (`ChronoSettingsCard` retiré). Décision **gardée** au passage aux catégories (2026-10-22) : l'état du
  formulaire reste dans `SettingsScreen`, au-dessus des catégories, et les règles entre champs (journée, creux)
  restent vérifiées d'un coup. Écartés : un « Enregistrer » par catégorie (confirmer chaque sortie de catégorie,
  règles entre champs coupées en deux) et l'application immédiate à la Rethink (un champ texte à moitié tapé
  serait refusé ou appliqué à chaque frappe, « Annuler les modifications » disparaîtrait).
- **Catégories plutôt qu'une page** (2026-10-22) : vingt cartes à la suite rendaient l'écran brouillon, surtout à
  360 dp. Liste de catégories puis sous-écrans (Rethink, MD3 « list-detail ») ; écartés : une seule page à
  en-têtes (toujours aussi longue) et des onglets (sept onglets ne tiennent pas à 360 dp). Deux volets dès 840 dp
  (classe de fenêtre « étendue » MD3), mesurés sur la largeur offerte à l'écran et non sur la fenêtre : le rail
  en prend déjà 80. Pas de dépendance `material3-adaptive` pour si peu : `BoxWithConstraints`.
- **Icônes par catégorie et par carte, pas par champ** : une icône devant chaque champ doublait la hauteur visuelle
  des cartes et demandait une cinquantaine d'icônes de plus ; les champs gardent libellé et aide.
- **Icônes neutres** (`onSurfaceVariant`), pas de pastille colorée comme chez Rethink : la charte réserve la couleur
  (« Tout le reste est neutre »).
- **Restaurer les valeurs par défaut dans « Données »** : c'est un geste sur l'ensemble des réglages, pas sur une
  catégorie ; il reste soumis à « Enregistrer ».
- **Validation à l'enregistrement, pas à la frappe** : on ne crie pas
  « Obligatoire » pendant qu'on efface pour retaper.
- **Dates supplémentaires validées** : une date illisible est signalée
  plutôt qu'ignorée en silence (même format, rien d'autre ne change).
- **Virgule décimale acceptée** : « 2,5 » est la façon française de
  l'écrire.
- **Plus de carte « En construction »** : toutes les parties des Réglages
  sont portées ; `UnderConstruction`, ses textes et l'icône `Construction`
  sont retirés.
