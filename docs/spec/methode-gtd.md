# La méthode GTD expliquée dans Kairos

_Rôle : dire où l'on est dans le cycle GTD, sur chaque écran où une étape a lieu, et expliquer la méthode en un dialogue,
pour qui ne la connaît pas. Fichiers couverts : `kmp/ui/.../guide/Gtd.kt` (`GtdStep`, `GtdStepButton`, `GtdMethodButton`,
`GtdDialog`), `kmp/ui/.../notes/NotesScreen.kt` (objectif de l'écran), `kmp/ui/.../day/DayScreen.kt` (« À traiter »),
`kmp/ui/.../day/NowCard.kt` (« Maintenant »), `kmp/ui/.../week/WeekScreen.kt`, `kmp/ui/.../stats/StatsScreen.kt`,
`kmp/ui/.../guide/HelpDialog.kt` et `kmp/ui/.../navigation/AppShell.kt` (entrée du « ? »), `kmp/ui/.../guide/FeatureCatalog.kt`
(carte `gtd`). Tests : `GtdUiTest` (desktopApp), `FeatureCatalogTest`, `StringsParityTest`, `ComponentDensityTest`._

État : issue #235 (4.2.0), spécifiée et implémentée le 2026-11-10.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

GTD (Getting Things Done, David Allen) est au cœur de Kairos : l'ordre des destinations suit son cycle (`navigation-theme.md`)
et la vue Jour parle de capturer, traiter, faire (`vue-jour.md`). C'est clair pour qui pratique GTD, pas pour les autres :
l'écran Notes ne dit pas qu'il est l'étape « capturer », et la méthode n'est expliquée nulle part dans l'application.

### Comportement attendu (utilisateur)

- **Les cinq étapes de GTD, et où elles ont lieu dans Kairos** :
  1. **Capturer** : l'écran Notes (et la capture de la vue Jour) ;
  2. **Clarifier** : « → Tâche » ou « Archiver » sur une note, puis « À traiter » dans la vue Jour ;
  3. **Organiser** : priorité, points et échéance dans « À traiter » ; Kairos range ensuite la liste lui-même ;
  4. **Faire le point** : les vues Semaine et Stats ;
  5. **Faire** : « Maintenant » dans la vue Jour, avec son chrono.
- **Écran Notes**, en tête, avant le champ de capture : « Étape 1 sur 5 de GTD · Capturer », une phrase d'objectif (« Sors
  de ta tête tout ce qui t'occupe, sans trier ni décider. Une note peut attendre ici aussi longtemps qu'il faut. ») et un
  bouton **« La méthode GTD »** qui ouvre le dialogue.
- **Ligne d'étape** sur les autres écrans, cliquable (elle ouvre le même dialogue), une ligne courte et discrète :
  - vue Jour, sous le titre « À traiter » : « Étapes 2 et 3 de GTD · Clarifier, organiser » ;
  - vue Semaine et Stats, en tête : « Étape 4 de GTD · Faire le point » ;
  - carte « Maintenant » : « Étape 5 de GTD · Faire », en texte simple, **non cliquable** (la carte est le bloc teinté de
    l'écran ; un bouton texte s'y perd, `temps-reel-chrono.md` § Interface commune).
- **Dialogue « La méthode GTD »** : une phrase sur le principe (la tête sert à avoir des idées, pas à les retenir : tout va
  dans un système fiable, vidé et revu régulièrement), puis les cinq étapes, chacune avec son principe et « Dans Kairos : … »,
  puis **« Ce que Kairos fait autrement »**, une ligne par écart :
  - **l'ordre** : GTD laisse choisir dans ses listes ; Kairos calcule l'ordre (priorité, taille, échéance) et dit par quoi
    commencer ;
  - **« Un jour peut-être »** : pas de liste à part ; Notes en tient lieu : une note peut y rester aussi longtemps qu'il
    faut avant de devenir une tâche (précision du propriétaire, 2026-11-10), « Archiver » la classe sans suite ;
  - **la revue hebdomadaire** : Kairos ne la guide pas pas à pas ; la vue Semaine et les Stats en donnent la matière.
  Le dialogue défile sur un petit écran ; « Fermer » le ferme.
- **Aide « ? »** de l'espace Perso : une entrée « La méthode GTD » (« Les cinq étapes et où elles sont dans Kairos »), qui
  ferme l'aide et ouvre le dialogue. Pas dans l'espace Équipe, qui ne suit pas le cycle GTD personnel.
- Catalogue : une carte « La méthode GTD » (chapitre « Premiers pas », 4.2.0), qui ouvre l'écran Notes.

### Critères de succès

- Sur Notes, l'étape, l'objectif et le bouton sont visibles sans défiler ; le bouton ouvre le dialogue, qui liste les cinq
  étapes et les trois écarts (`GtdUiTest`).
- La ligne d'étape de « À traiter », de Semaine et de Stats ouvre le même dialogue ; celle de « Maintenant » n'est pas
  cliquable (`GtdUiTest`).
- L'entrée du « ? » existe dans l'espace Perso et pas dans l'espace Équipe (`GtdUiTest`).
- À 360 dp, dans les six langues, aucune ligne d'étape ne déborde : elle passe à la ligne (captures `--self-test`) ; la ligne
  cliquable garde une cible de 48 dp (enveloppe `KairosTextButton`, `ComponentDensityTest`).
- Textes dans les six langues (`StringsParityTest`) ; carte, puce et lignes du README (`FeatureCatalogTest`).
- Les captures des magasins qui montrent Notes, Jour, Semaine ou Stats sont régénérées (`--store-screenshots`).

### Hors périmètre

- Une revue hebdomadaire guidée, des listes par contexte au sens de GTD, une liste « Un jour peut-être » distincte.
- Un changement du comportement de Notes ou de « À traiter » : seuls des textes et le dialogue s'ajoutent.
- L'espace Équipe.

## 2. Solution technique

- `ui/guide/Gtd.kt` :
  - `enum class GtdStep(label)` : `CAPTURE` (étape 1), `PROCESS` (étapes 2 et 3), `REFLECT` (étape 4), `ENGAGE`
    (étape 5) ; chaque libellé est une chaîne entière (`gtd_label_capture`, `gtd_label_process`, `gtd_label_reflect`,
    `gtd_label_engage`), sans formatage : l'ordre des mots change d'une langue à l'autre.
  - `GtdStepButton(step)` : `KairosTextButton` (icône `Info` 18 dp, libellé `labelMedium`, passe à la ligne) qui ouvre
    `GtdDialog` ; l'état du dialogue est local au bouton.
  - `GtdStepText(step, color)` : la même ligne, non cliquable (« Maintenant »).
  - `GtdMethodButton()` : `KairosTonalButton` « La méthode GTD » (icône `Help`), qui ouvre `GtdDialog`.
  - `GtdDialog(onDismiss)` : `AlertDialog` (icône `Help`, titre `gtd_title` marqué titre pour les lecteurs d'écran),
    colonne défilante : `gtd_intro`, puis pour chaque étape son nom en `titleSmall` (`gtd_step_*_name`) et son texte
    (`gtd_step_*_body`), puis `gtd_diff_title` et les trois écarts (`gtd_diff_order`, `gtd_diff_someday`,
    `gtd_diff_review`) ; bouton « Fermer » (`help_close`).
- `NotesScreen` : premier élément de la liste, avant la capture : `GtdStepText(CAPTURE)` en `labelLarge` couleur primaire
  sans fond, `gtd_notes_goal` (`bodyMedium`), puis `GtdMethodButton`.
- `DayScreen` : dans l'élément « inbox-help », sous le titre « À traiter », `GtdStepButton(PROCESS)` avant l'aide existante.
- `NowCard` : `GtdStepText(ENGAGE)` en `labelMedium`, couleur `onPrimaryContainer`, en tête de la carte.
- `WeekScreen`, `StatsScreen` : `GtdStepButton(REFLECT)` en tête de la colonne.
- `HelpDialog(…, onGtd)` : entrée `HelpEntry(Help, gtd_open, gtd_open_hint)` après « Découvrir Kairos », seulement si
  `!team` ; `AppShell` ferme l'aide puis ouvre `GtdDialog`.
- Catalogue : `FeatureCard("gtd", FIRST_STEPS, Help, card_gtd_title, card_gtd_body, GuideTarget.Personal(Destination.NOTES),
  since = "4.2.0")`, placée après `capture` ; puce « GTD explained » du README, lignes des tableaux, en anglais et en
  français. Notes n'a pas de `GuideSection` : la carte ouvre l'écran sans repère.

### Décisions

- **Un fichier de spec pour la méthode, pas un ajout à chaque spec d'écran** : l'explication traverse Notes, Jour, Semaine,
  Stats et l'aide ; `notes-capture.md`, `vue-jour.md` et `accueil.md` y renvoient.
- **Bouton dédié sur Notes, ligne cliquable ailleurs** (choix du propriétaire, 2026-11-10) : Notes porte l'objectif et le
  bouton demandés par l'issue ; ailleurs, une ligne suffit à situer l'étape sans alourdir l'écran. Écartés : tout mettre dans
  le « ? » (introuvable pour qui ne sait pas qu'il existe) ; le même texte dans chaque aide.
- **Dire les écarts avec GTD** (choix du propriétaire) : qui connaît GTD voit où Kairos s'en écarte ; qui le découvre n'est
  pas surpris ensuite. **Notes tient lieu de « Un jour peut-être »** (précision du propriétaire) : une note peut attendre
  indéfiniment, il n'y a donc pas de liste à part à créer.
- **« Maintenant » sans lien** : la carte teintée ne reçoit ni bouton texte ni seconde couleur ; le dialogue reste à un
  geste par le « ? » ou par la ligne de « À traiter ».
- **Libellés entiers, pas de format « Étape %d »** : « Étapes 2 et 3 » et l'ordre des mots varient selon la langue.
- **Pas dans l'espace Équipe** : le cycle y est celui du manager (backlog, assignation), pas GTD personnel.
