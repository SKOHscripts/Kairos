# Réglages

_Rôle : tout ce qui se règle dans Kairos, depuis un seul écran, avec une
validation par champ. Fichiers couverts :
`kmp/core/src/commonMain/.../core/settings/SettingsForm.kt` (champs, bornes,
validation, pur), `kmp/core/.../model/Settings.kt` (modèle, décrit par
`modele-donnees.md`), `kmp/ui/.../settings/` (`SettingsScreen.kt`,
`SettingsFormCards.kt`). La carte Données est décrite par `export-import.md`
et `migration-2x.md`, la carte Mises à jour par `mises-a-jour.md`. Tests :
`core/.../settings/SettingsFormTest.kt`,
`M5ScreensUiTest.anInvalidFieldBlocksSavingThenAValidFormIsSaved`._

État : **jalon M5**. Reprend le besoin de `docs/spec/reglages-secrets.md`
(Kairos 2, tag `v2.6.0`), moins le périmètre retiré : chemin de la base, import GitLab,
base pilotage, TimeTree, proxy, niveau de log, source et jeton des mises à
jour. Kairos 3 n'a plus aucun secret à stocker (pas de trousseau).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos démarre sans rien configurer ; tout ce qui se règle se règle depuis
l'application, jamais dans un fichier. Un réglage faux (heure 25, texte dans
un nombre) ne doit jamais être enregistré, ni à moitié.

### Comportement attendu (utilisateur)

- Destination « Réglages », une carte par section, dans l'ordre de Kairos 2 :
  **Journée de travail** (durée par défaut d'une tâche, marge après un
  créneau occupé, début et fin de journée), **Score de priorité (WSJF)**
  (base de valeur, horizon d'urgence, poids maximal de l'urgence, points par
  défaut), **Creux de l'après-midi** (activé, début, creux le plus profond,
  fin, force de la pénalité), **Garde-fous** (seuils « traîne » en retard et
  sans date, seuil de surcharge P0), **Types de tâches**, **Statistiques**
  (fenêtre en semaines), **Alertes du chrono** (chrono oublié, pause
  suggérée, son de secours), **Jours fériés** (français, dates
  supplémentaires), puis sur le bureau **Mises à jour**, puis **Données** et
  « À propos et guide ».
- Chaque champ a un libellé court et, dessous, une phrase qui dit à quoi il
  sert (les descriptions de Kairos 2) ; un booléen est un interrupteur dont
  toute la ligne se touche.
- Une barre fixée en bas porte « Enregistrer » (actif dès qu'une valeur
  change), « Annuler les modifications » et « Modifications non
  enregistrées ». Un seul « Enregistrer » pour tout le formulaire.
- À l'enregistrement : chaque champ invalide affiche son erreur à la place de
  son aide (« Au plus 23. », « Un nombre entier est attendu. », « Date
  illisible : « 25/12 » (format AAAA-MM-JJ). »…), un message dit
  « Réglages non enregistrés », et **rien** n'est enregistré. Corriger un
  champ efface son erreur. Les règles entre champs (début de journée avant
  la fin ; début ≤ creux le plus profond ≤ fin) s'affichent dans un bandeau
  en tête, une fois chaque champ valide.
- Une décimale s'écrit avec une virgule ou un point.
- Les réglages valent aussitôt partout (planning, score, alertes, stats).

### Critères de succès

- Valeurs par défaut relues puis revalidées à l'identique ; un champ absent
  garde sa valeur (`SettingsFormTest`).
- Hors bornes, vide, non entier, non nombre, date illisible : une erreur par
  champ, aucun enregistrement ; bornes de Kairos 2, borne basse exclue pour
  la base de valeur (`SettingsFormTest`).
- Heure 25 refusée, début après la fin refusé, puis 8 h, base 2,5 et creux
  désactivé enregistrés d'un coup (`M5ScreensUiTest`).

### Hors périmètre / différé

- Choix de la langue (on suit le système, `i18n.md`), thème sombre
  (`navigation-theme.md`).
- Réglages retirés avec leurs intégrations (voir l'en-tête).

## 2. Solution technique

### Champs (`SettingsForm`, `core`)

- `FIELDS` : un `SettingField` par réglage éditable, clé = nom du champ de
  `Settings`, nature (`INT`, `DECIMAL`, `BOOL`, `TEXT`, `DATES`), bornes
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
  `INVALID_DATE` (avec le texte fautif). Puis, seulement sans erreur de
  champ, `GeneralError.WORKDAY_ORDER` (début ≥ fin) ou `DIP_ORDER`. Rend les
  réglages complets, ou `null` à la moindre erreur.
- Nettoyage à l'écriture : textes sans espaces de bord ; dates
  supplémentaires sans vides, séparées par « , ».

### Écran (`SettingsScreen`, `ui`)

- État : textes du formulaire, erreurs et règle violée, réinitialisés quand
  les réglages enregistrés changent (import, migration) ; `dirty` = textes ≠
  valeurs enregistrées.
- `SettingsFormCards` : `OutlinedCard` par section (titre marqué
  `heading`), `SettingInput` : `OutlinedTextField` pleine largeur, clavier
  numérique ou décimal, `supportingText` = erreur sinon aide ; `SwitchRow` :
  `Switch` dans une ligne `toggleable` (rôle interrupteur, 48 dp).
- Bandeau de règle inter-champs : conteneur d'erreur et icône `Warning`
  (un vrai échec, charte). `SaveBar` : `Surface` `surfaceContainer` sous la
  colonne défilante, `Button` « Enregistrer », `TextButton` « Annuler les
  modifications ».
- Enregistrer : `SettingsForm.validate` puis `updateSettings`, message
  « Réglages enregistrés. » ou « Réglages non enregistrés… ».

### Décisions et pièges tracés

- **Un seul formulaire, un seul « Enregistrer »** (comme Kairos 2) : la carte
  du chrono du jalon M3, qui avait son propre bouton, y est fondue
  (`ChronoSettingsCard` retiré).
- **Validation à l'enregistrement, pas à la frappe** : on ne crie pas
  « Obligatoire » pendant qu'on efface pour retaper.
- **Dates supplémentaires validées** : Kairos 2 ignorait en silence une date
  illisible ; Kairos 3 la signale (même format, rien d'autre ne change).
- **Virgule décimale acceptée** : « 2,5 » est la façon française de
  l'écrire.
- **Plus de carte « En construction »** : toutes les parties des Réglages
  sont portées ; `UnderConstruction`, ses textes et l'icône `Construction`
  sont retirés.
