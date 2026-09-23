# Design system Kairos — Material Design 3, thème « miel »

Charte visuelle de l'application, construite sur **Material Design 3** (MD3) : un
design system complet et documenté (https://m3.material.io), natif sur Android et
lisible sur un bureau Windows/Linux, à suivre pour toute évolution future. Tout
nouveau gabarit ou composant réutilise ces rôles et composants plutôt que d'en
réinventer. Implémentés dans `static/style.css` (variables `:root`, `@font-face`),
`templates/_icons.html` (icônes) et `templates/base.html` (logo, navigation).

## Historique de la décision

- **Charte précédente** (« sobre & professionnelle », ardoise + accent bleu
  `#2F6FED`, IBM Plex Sans, topnav horizontale) : remplacée en 2026-09 à la
  demande de l'utilisateur, qui la jugeait trop marquée par un style « généré »
  et voulait un design system complet, pérenne, cohérent sur Windows, Linux et
  Android.
- **Trois pistes comparées** (Carbon d'IBM, MD3, Fluent 2), puis maquettes
  Carbon/MD3 : **MD3 retenu** (bottom nav, FAB, chips natifs sur Android ;
  composants tous documentés ; palette dérivée d'une seule couleur, donc un
  thème sombre générable plus tard).
- **Graine de couleur** : le terracotta du logo (`#D9713C`) jugé trop orange ;
  **miel `#C28417`** retenu (lumière de fin de journée sur le cadran solaire :
  chaud sans être orange, et 50° de teinte d'écart avec le rouge d'erreur — le
  terracotta n'en avait que 18°, P0 et primaire se confondaient). Le logo suit la
  graine (§ Logo).
- **Schéma « Tonal spot »** (défaut d'Android) préféré à « Fidelity » : moins
  saturé, conteneurs pêche/miel clair plutôt que le terracotta plein.
- **Rail de navigation vertical sur bureau** : décision « pas de sidebar »
  explicitement **rouverte** et tranchée par l'utilisateur en faveur du rail MD3
  (§ Navigation).
- **Exceptions de l'ancienne charte absorbées** (décision utilisateur) : la
  carte « Progression du jour » crème/ambre, la boîte de réception crème/ambre et
  la ligne « À faire maintenant » en Newsreader italique disparaissent ; « Maintenant »
  devient le bloc `primary-container`, la ligne passe en Roboto *title-large*.

## Architecture de l'information : vue Jour (flux GTD)

La vue Jour (`templates/kairos.html` + `templates/_kairos_day.html`) est organisée
autour du flux GTD **capturer → traiter la boîte de réception → faire**, de haut en
bas :

1. **Barre de capture** (`.mj-capture`) : toujours visible, jamais dans un `<details>`
   replié : la capture ne doit jamais coûter un clic de plus. Deux volets par onglet
   (radios `mj-add-mode`) : « Tâche » (titre seul ; capture GTD volontairement sans
   friction, la clarification vient après) et « Créneau / deep work » (avec la liste
   d'édition des créneaux du jour). Le CTA « Ajouter » (`.btn.primary`) est le seul
   bouton plein de la zone.
2. **Boîte de réception** (`.mj-to-process`, id `#mj-inbox`) : juste sous la capture,
   pour qu'une tâche capturée y apparaisse immédiatement. Jamais masquée (pas de
   `<details>`) : affiche un état vide discret (`.mj-inbox-empty`) plutôt que de
   disparaître, pour toujours rappeler où regarder en premier. Qualification **en
   ligne** (priorité + points Fibonacci, voir « Composants » plus bas) : une tâche
   qualifiée quitte la boîte de réception et entre dans l'agenda ordonné, sans ouvrir
   l'édition complète.
3. **« Maintenant »** (`.mj-progress`) : poste de pilotage, prochaine tâche
   actionnable sur place, toujours dépliée. Ses trois actions sont des boutons
   nommés (« Fait », « Démarrer le chrono », « Décaler »), les seuls de l'app à
   porter texte et icône hors formulaires : ailleurs, les icônes suffisent. Seul bloc
   teinté de l'écran (`primary-container`), voir « Où va la couleur » plus bas.
4. **Bannières d'alerte** (TimeTree, GitLab, surcharge de priorité) : sous
   « Maintenant », pas en tout premier : ce ne sont que des avertissements de
   dégradation, pas le point d'entrée du flux (bannières neutres, jamais rouges).
5. **Agenda ordonné** (« Aujourd'hui, dans l'ordre », `<details open>`) : la liste
   centrale, triée par score WSJF, toujours dépliée, juste sous « Maintenant ».
6. **Sections secondaires condensées** (Sans créneau / Bloquées / Plus tard / Mères /
   Fait) : `<details>` repliés sauf « Sans créneau », chaque `<summary>` porte un
   compte et une courte phrase de rôle (`.hint`).
7. **Filtres compacts** (`.mj-filter-compact`) et **Backlog** : utilitaires
   secondaires, repliés, en bas de colonne. Un filtre actif remonte en tête.
8. **Colonne latérale** (`.mj-day-grid` → `.mj-side-col`) : inchangée : carte
   « En ce moment » (chrono) + Agenda (timeline verticale).

La vue Semaine reste un gabarit simple, non concernée par cette réorganisation.

### Partiels et mise à jour AJAX

- `templates/_kairos_macros.html` porte les macros partagées (`done_toggle`,
  `task_actions`, `time_spent`, `fibo_help`, `edit_panel`, `task_key_badges`,
  `task_tags`, `task_description`), importées
  `with context` par `kairos.html` et `_kairos_day.html` : évite un cycle d'import
  entre les deux gabarits.
- `templates/_kairos_day.html` est le partiel de la vue Jour : rendu à l'intérieur de
  `<div id="mj-day-content">` par `kairos.html` (page pleine), **et** rendu
  directement (sans cette enveloppe) par `app.main.render_kairos_response(fragment=True)`
  pour les réponses AJAX. Un seul `id="mj-day-content"` dans toute l'app.
- **Amélioration progressive, jamais de JS obligatoire** : un `<form data-ajax>`
  se soumet en `fetch` (en-tête `X-Requested-With: fetch`), remplace
  `#mj-day-content` par le fragment renvoyé, puis réinitialise le chrono vivant
  (`initDayScripts`, réappelable). Sans JS (ou en cas d'échec réseau), le même
  formulaire se soumet normalement → POST → redirection 303 côté serveur,
  identique au comportement historique : indispensable pour la WebView Android et
  l'accessibilité. Le bouton cliqué est ajouté à la requête (`ev.submitter`) :
  `new FormData(form)` l'ignore, et les pastilles de qualification portent leur
  valeur dans leur `name`/`value`.

## Couleurs

### Rôles MD3

Générés par l'algorithme officiel (material-color-utilities, portage Python
`materialyoucolor`, `SchemeTonalSpot`, spec 2021, contraste standard) depuis la
graine **`#C28417`**. Variables `--md-*` de `static/style.css` ; un seul thème
clair (pas de mode sombre pour l'instant, mais les mêmes outils le génèrent
depuis la même graine).

| Rôle | Variable CSS | Valeur | Usage principal |
|---|---|---|---|
| Primaire | `--md-primary` / `--md-on-primary` | `#7F5610` / `#FFFFFF` | bouton plein, score WSJF, liens, focus |
| Conteneur primaire | `--md-primary-container` / `--md-on-primary-container` | `#FFDDB3` / `#624000` | carte « Maintenant », chrono en cours |
| Secondaire | `--md-secondary` | `#6F5B40` | (réservé) |
| Conteneur secondaire | `--md-secondary-container` / `--md-on-secondary-container` | `#FADEBC` / `#56442A` | sélection : destination active, chips/pastilles choisies, créneaux de travail de l'agenda |
| Conteneur tertiaire | `--md-tertiary-container` / `--md-on-tertiary-container` (trait `--md-tertiary`) | `#D4EABC` / `#3A4C2A` (`#516440`) | deep work, uniquement |
| Erreur | `--md-error` / `--md-on-error` | `#BA1A1A` / `#FFFFFF` | texte d'erreur, liseré critique |
| Conteneur d'erreur | `--md-error-container` / `--md-on-error-container` | `#FFDAD6` / `#93000A` | P0, badges `.bad`, bannière d'erreur |
| Surface | `--md-surface` / `--md-on-surface` | `#FFF8F4` / `#201B13` | fond de page, cartes « outlined », texte |
| Conteneurs de surface | `--md-surface-container-lowest` … `-highest` | `#FFFFFF`, `#FEF1E5`, `#F9ECDF`, `#F3E6DA`, `#EDE0D4` | cartes « filled », lignes de tâche, badges neutres, dialogue |
| Texte secondaire | `--md-on-surface-variant` | `#4F4539` | libellés, aides, métadonnées |
| Contours | `--md-outline` / `--md-outline-variant` | `#817567` / `#D3C4B4` | champs, boutons « outlined » / cartes, séparateurs |
| Surface inverse | `--md-inverse-surface` / `--md-inverse-on-surface` / `--md-inverse-primary` | `#362F27` / `#FCEFE2` / `#F4BD6F` | carte « En ce moment », snackbar d'alerte |
| Voile | `--md-scrim` | `rgba(0,0,0,.32)` | derrière le dialogue d'édition |

Contrastes texte/fond (WCAG 2.1) : 6,4:1 et plus pour tous les couples ci-dessus,
au-delà du minimum AA (4,5:1).

### Couleur personnalisée « fait / ok »

`--kx-ok` `#36693D`, `--kx-ok-container` `#B7F1B8`, `--kx-on-ok-container`
`#1D5127` (palette tonale teinte 150°, chroma 36, mêmes tons que les rôles
d'erreur). **Non harmonisée** vers la graine : harmonisée, elle virait à
l'olive, trop proche de la tertiaire.

### Pas d'ambre d'avertissement (décision)

Un ambre d'avertissement (`#755B00`/`#FFDF90`) serait **indiscernable du miel
primaire** ; harmonisé vers la graine, il devenait même identique au conteneur
primaire. Les états « à surveiller » sont donc rendus **par la forme** : badge
`.warn`/`.at_risk` à contour (`--md-outline`) + icône, tuile `.stat.tone-amber`
à contour, bannière de dégradation neutre. Le nom historique `tone-amber` est
conservé côté gabarit.

### Où va la couleur (et nulle part ailleurs)

1. **Un seul bloc teinté par écran** : la carte « Maintenant »
   (`.mj-progress`, `primary-container`).
2. **Le primaire plein** est réservé à l'action principale de chaque zone :
   « Ajouter », « Fait » (dans « Maintenant »), « Enregistrer », « Mettre à jour »,
   « Ouvrir Aujourd'hui ».
3. **Le score WSJF** (`.badge.mj-score`) est un chiffre en couleur primaire, sans
   pastille pleine (couche d'état au survol : il ouvre « Pourquoi à cette place ? »).
4. **Le rouge** ne sert qu'à P0 (`.badge.prio.is-p0`), aux erreurs et aux
   dépassements (`.badge.bad`, `.stat.tone-red`, liseré `.mj-bucket-0`), toujours
   accompagné d'un texte ou d'une icône. P1/P2 restent neutres.
5. **La tertiaire** (vert sauge) ne sert qu'au deep work (badge
   `.badge.ok.mj-deepwork`, blocs de la timeline).
6. **Le vert « ok »** marque le fait (`.badge.ok`, coche `.mj-check.is-done`,
   barres « done »).
7. **Les bannières de dégradation** (TimeTree, GitLab, surcharge de priorité)
   sont neutres (`.banner`, `surface-container-high` + icône) : un service
   dégradé n'est pas un danger. `.banner.warning` = conteneur d'erreur, pour les
   vrais échecs (réglages refusés, trousseau indisponible, échec de mise à jour).
8. **La sélection** (destination active, onglets de capture, pastilles
   choisies, créneaux de travail) utilise `secondary-container`.
9. **Le sombre** (surface inverse) est réservé à la carte « En ce moment » et à
   la snackbar d'alerte de chrono.

### Badges (pilules, `--md-shape-full`)

| Rôle | Classe | Fond | Texte |
|---|---|---|---|
| Neutre / info / tag / projet | `.badge`, `.badge.neutral`, `.badge.info`, `.badge.mj-tag` | `surface-container-highest` | `on-surface-variant` |
| Priorité P1, P2 | `.badge.prio` | `surface-container-highest` | `on-surface`, gras |
| Priorité P0 | `.badge.prio.is-p0` | `error-container` | `on-error-container` |
| Score WSJF | `.badge.mj-score` | transparent | `primary`, gras |
| Chrono en cours | `.badge.mj-timer` | `primary-container` | `on-primary-container` |
| Fait / ok | `.badge.ok` | `--kx-ok-container` | `--kx-on-ok-container` |
| Deep work | `.badge.ok.mj-deepwork` | `tertiary-container` | `on-tertiary-container` |
| Critique / erreur | `.badge.bad` | `error-container` | `on-error-container` |
| À surveiller | `.badge.warn`, `.badge.at_risk` | transparent, contour `outline` | `on-surface` |

## Typographie

- **Roboto** (400/500/700), la police de MD3, **servie par l'app** : fichiers WOFF2
  des sous-ensembles latin et latin-ext dans `static/fonts/` (paquet npm
  `@fontsource/roboto` 5.3.0, licence OFL `static/fonts/Roboto-OFL.txt`),
  déclarés par `@font-face` en tête de `style.css`. **Aucune police distante** :
  l'exécutable et l'APK rendent la charte hors ligne (l'ancienne charte chargeait
  IBM Plex et Newsreader depuis Google Fonts, dégradées sans réseau). Test :
  `test_fonts_are_served_locally_never_from_google`.
- Échelle MD3 appliquée : *title-large* 22/28 400 (titre de page, « À faire
  maintenant »), *title-medium* 16/24 500 (titres de carte/section),
  *title-small / label-large* 14/20 500 (boutons, résumés repliables),
  *body-medium* 14/20 (corps), *body-small* 12–13 px (aides, étiquettes),
  *label-small* 11–12 px 500 en majuscules (sur-titres), *headline-small*
  24/32 400 (KPI), *display-small* 36/44 400 (minuteur « En ce moment »).
- Chiffres en `font-variant-numeric: tabular-nums` (heures, scores, KPI).
- Aucun italique décoratif, aucune seconde police.

## Icônes

**Material Symbols** (style *Outlined*, graisse 400), en **SVG inline**
(`fill="currentColor"`) générés dans `templates/_icons.html` par
`packaging/make_icons.py` depuis le paquet npm `@material-symbols/svg-400`
(Apache 2.0) : ni police d'icônes, ni requête réseau. La macro garde les noms
historiques de Kairos (`icon('pencil')` → *edit*, `icon('clock')` → *schedule*,
`icon('skip_forward')` → *redo*…) : aucun appel existant n'a changé. Ajouts :
`today`, `date_range`, `bar_chart` (navigation), `play`/`stop` (chrono),
`repeat` (récurrence), `logout` (Quitter). Paramètre `fill=true` : variante
pleine, portée par la destination active de la navigation (convention MD3).
Chaque SVG porte `class="ico ico-<nom>"` (ex. seuls les chevrons
`.ico-chevron_right` pivotent à l'ouverture d'un `<details>`, jamais la loupe de
la recherche). `gitlab` (marque absente de Material Symbols) reste dessinée à la
main.

## Forme & élévation

- Formes MD3 : `--md-shape-xs` 4px (champs, snackbar, menu « Pourquoi »),
  `--md-shape-sm` 8px (chips/pastilles, entrées de l'agenda), `--md-shape-md`
  12px (cartes, lignes de tâche, bannières), `--md-shape-lg` 16px (bandeau
  d'accueil, carte « En ce moment »), `--md-shape-xl` 28px (dialogue d'édition),
  `--md-shape-full` (boutons, badges, indicateurs de navigation).
- Cartes : « outlined » par défaut (`.card`, `.panel` : surface + contour
  `outline-variant`) ; « filled » pour la capture (`surface-container-low`), les
  lignes de tâche et de note, les tuiles `.stat`.
- **Élévation** : les cartes ne portent jamais d'ombre, leur plan se lit par la
  surface tonale. Seuls les éléments qui **flottent** au-dessus du contenu en
  ont une : dialogue d'édition et son bouton ✕ (`--md-elevation-3`), snackbar
  d'alerte (`--md-elevation-3`), menu « Pourquoi à cette place ? »
  (`--md-elevation-2`), survol du bouton plein (niveau 1).
- **Couches d'état** : survol 8 %, appui 12 % de la couleur du contenu
  (`color-mix`), sur boutons, boutons-icônes, chips, destinations.
- Liseré critique d'une ligne de tâche : `border-left: 3px` `--md-error`
  (`.mj-bucket-0`), transparent sur les autres lignes pour garder le même retrait.
- Dégradés : uniquement le fondu du bandeau d'actions sticky des Réglages
  (`.mj-settings-actions`) et les hachures des fenêtres deep work réservées de la
  timeline (`.mj-tl-entry.deepwork`, `repeating-linear-gradient`).

## Boutons & champs

- `.btn` = bouton MD3 **outlined** (36px, pilule, texte primaire) ; `.btn.primary`
  = **filled** ; `.btn.sm` 32px ; `.btn.danger` texte d'erreur ; désactivé =
  on-surface 12 % / 38 %.
- `.icbtn` = **bouton-icône standard** rond, sans contour, 32px (36px sous
  720px) : actions de ligne (chrono, décaler, crayon).
- Champs = **outlined** (contour `outline`, rayon 4px, focus primaire 2px) ; le
  libellé reste au-dessus du champ (porté par le `<label>` existant, pas de
  libellé flottant : même HTML, mêmes tests).

## Logo

Mark « cadran solaire » inchangé dans son dessin (cercle + un secteur + point
pivot), recoloré depuis la graine : quatre tons de la même palette, pour qu'il
s'accorde à l'interface au lieu d'y faire exception.

| Pièce | Ton | Valeur |
|---|---|---|
| Cadran | graine, ton 95 | `#FFEEDC` |
| Anneau | graine, ton 85 | `#FFCC85` |
| Secteur | la graine (ton 60) | `#C28417` |
| Axe | neutre, ton 15 | `#2B251C` |

```html
<svg width="34" height="34" viewBox="0 0 40 40">
  <circle cx="20" cy="20" r="18.5" fill="#FFEEDC"/>
  <circle cx="20" cy="20" r="18.5" fill="none" stroke="#FFCC85" stroke-width="1.6"/>
  <path d="M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z" fill="#C28417"/>
  <circle cx="20" cy="20" r="2.6" fill="#2B251C"/>
</svg>
```

Repris dans `static/favicon.svg`, `templates/base.html`, `templates/home.html`,
les icônes PNG/ICO et `packaging/splash.png` (générés par
`packaging/make_icon.py`) et les ressources Android (`colors.xml`,
`mipmap/ic_launcher.xml`, `drawable/ic_launcher_foreground.xml`,
`drawable/kairos_splash_logo.xml`, `drawable/kairos_splash_icon_base.xml`).

## Navigation

Une seule navigation (`.topnav`, six destinations `.tn-item`, icône dans un
indicateur `.tn-ind` + libellé), trois formes selon le contexte :

- **Bureau / navigateur, fenêtre > 720px** : **rail de navigation MD3** vertical,
  collé à gauche (88px, logo + « Kairos » en tête, destinations empilées,
  « Quitter » en pied pour l'exécutable). Destination active : indicateur
  `secondary-container` 56×32 + icône pleine + libellé gras.
- **Fenêtre ≤ 720px (navigateur rétréci)** : barre horizontale compacte en haut
  (pilules texte, l'active en `secondary-container`), **jamais** de barre basse :
  un navigateur de bureau simplement rétréci n'en affiche pas.
- **APK Android** (`is_android`) : petite barre de marque en haut + **barre de
  navigation MD3** basse (`.bn-nav`, `surface-container`, indicateurs 56×32,
  libellés 12px), quelle que soit la largeur. Seule dérogation au principe
  « aucune détection de plateforme côté serveur » (`docs/spec/accueil-navigation.md`).

Sous la navigation, la **barre d'application supérieure** (`.topbar`, sticky,
64px, 56px sous 720px) porte le titre de page en *title-large*.

## Composants de la vue Jour

### Barre de capture

`.mj-capture` : carte « filled » (`surface-container-low`) non repliable, toujours
en tête de la vue Jour. Les deux volets (`[data-mj-add-pane="task"]` /
`[data-mj-add-pane="slot"]`) se basculent par les radios `mj-add-mode`, rendues
en **chips de filtre** : le `<label>` entier est la chip (sélection
`secondary-container` + coche via `:has(input:checked)`), la radio native reste
dans le DOM (clavier, bascule JS inchangée).

### Boîte de réception + qualification en ligne

`.mj-to-process` : carte « outlined » standard (fusionnée avec `.card` sur le même
élément). `.mj-section-head` porte le titre + le compte (`.count`) et une aide
repliable (`.mj-help`). État vide : `.mj-inbox-empty` (padding réduit, pas de
liste), présent plutôt que la section entière disparaissant du DOM.

Chaque ligne de la boîte de réception porte deux rangées de **pastilles**
(`.mj-pill`) : priorité (« P0 Critique », « P1 Important », « P2 Utile ») et
points (« 1 trivial » … « 21 énorme »), un bouton submit par valeur, dans la zone
`qualify` de la grille. Un clic enregistre ; une fois les deux champs posés, la
tâche quitte la boîte de réception (fragment AJAX, sans rechargement).

### Pastilles de choix (`.mj-pill`) = chips de filtre MD3

Même composant pour les boutons de la boîte de réception et les radios du
panneau d'édition (`.mj-radio input:checked + .mj-pill`). Contour
`outline-variant`, rayon 8px, code en gras puis libellé. Valeur choisie :
`secondary-container` + coche « ✓ » (priorité comme points). 32px de haut sur
grand écran, 44px sous 720px (cible tactile).

### Carte « Maintenant » (`.mj-progress`)

Seul bloc teinté de l'écran (`primary-container` / `on-primary-container`).
« À faire maintenant : … » (`.mj-next`) en *title-large* (22/28, 18/24 sous
720px). Actions en hiérarchie MD3 : « Fait » bouton plein, « Démarrer le chrono »
contour, « Décaler » bouton texte ; les trois tiennent sur une ligne à 390px.

### Explication du score (`.mj-why`)

Le score WSJF est le `<summary>` d'un `<details>` : un clic ou un toucher ouvre,
sous la colonne priorité/points, un **menu** MD3 (`surface-container`, rayon 4px,
élévation 2) qui détaille le calcul ; la ligne « Score » en primaire.

### Ligne de tâche (`.kairos-item`)

Carte « filled » (`surface-container-low`, rayon 12px, sans contour). Grille de
quatre colonnes, invisible à l'œil mais stable d'une ligne à l'autre (issue
#33) : `[coche] [corps] [priorité/points] [actions]`, zones nommées `check main
key actions`. Seul le corps est élastique (`minmax(0, 1fr)`) : il empile titre,
étiquettes (`.mj-item-tags`, le seul conteneur qui s'enroule) et extrait de
description **vers le bas**. Les trois autres colonnes se dimensionnent sur leur
contenu (hauteur de référence 32px, 36px sous 720px), si bien que priorité et
actions tombent à la même abscisse sur toutes les lignes.

Règles à respecter pour tout ajout à une ligne de tâche :

- un nouvel élément s'ajoute **dans** une cellule, jamais comme cinquième
  enfant direct du `<li>` (il tomberait dans une piste implicite et casserait
  l'alignement général) ;
- un badge de longueur imprévisible (phrase, note explicative) va dans les
  étiquettes du corps, jamais dans la colonne priorité/points, réservée aux
  signaux de tri courts (score WSJF, `P0`-`P2`, `N pts`) ;
- sous 720px la colonne priorité/points passe sous le corps ; actions et coche
  ne bougent pas.

Coche « fait » (`.mj-check`) : rond de 22px contour `on-surface-variant`, rempli
du conteneur « ok » une fois la tâche faite. Tâche bloquée (`.mj-blocked`) :
fond `surface` + contour pointillé, titre atténué — **jamais d'`opacity`**
(piège tracé dans `docs/spec/vue-jour-gtd.md`).

### Contrôle de filtrage compact

`.mj-filter-compact` : la recherche + les 4 filtres à facettes tiennent dans un
`<details class="card mj-filter-compact">`, replié par défaut. Un seul marqueur
visible (`<span class="badge info">filtre actif</span>`) quand un filtre est posé.

### Carte « En ce moment »

`.mj-now-card` : surface inverse (`inverse-surface`, rayon 16px), sur-titre en
`inverse-primary`, minuteur *display-small* (36/44), bouton « Arrêter le chrono »
plein `inverse-primary` (icône `stop`). En plus du badge `.mj-timer` de la ligne
(`primary-container`, minuteur vivant en JS inchangé).

### Timeline (« Agenda »)

Entrées en rayon 8px : occupé `surface-container-highest`, travail
`secondary-container`, épinglé `secondary-container` + contour pointillé,
conflit `error-container`, deep work réservé en hachures tertiaires + tâche
deep work en `tertiary-container`, rail du temps chronométré (`.mj-tl-session`)
en vert « ok ».

### Vue Semaine

Cartes de jour « outlined » ; aujourd'hui : `surface-container-low` + contour
primaire, titre primaire. Les créneaux et événements du jour (`.mj-week-day >
.badge`) **passent à la ligne** (`white-space: normal`) au lieu de déborder de
la carte (défaut antérieur, aggravé par la largeur du rail, corrigé avec la
migration).

## Panneau de modification d'une tâche (dialogue MD3)

`edit_panel(task)` (`.mj-edit` / `.mj-edit-body` dans `templates/_kairos_macros.html`)
se présente comme un **dialogue** MD3 (`surface-container-high`, rayon 28px,
élévation 3) centré sur un voile (`--md-scrim`), en CSS + JS minimal (un seul
écouteur `click` délégué sur `document`, voir `templates/kairos.html`) :
- Un bouton `.mj-edit-toggle` (le crayon) bascule `hidden` sur le `.mj-edit-body`
  associé (`aria-expanded` reflète l'état). Quand il est ouvert
  (`[aria-expanded="true"]`), ce même bouton devient le voile plein écran
  (`position: fixed; inset: 0`) : cliquer n'importe où en dehors du dialogue le
  referme (même écouteur `click`). **Échap** referme aussi le panneau ouvert
  (écouteur `keydown` délégué).
- Un glyphe ✕ (`::after` de ce même bouton, seulement quand ouvert, rond 36px
  `surface-container-high`) est positionné juste à côté du coin haut-droit du
  dialogue, **jamais par-dessus** : un pseudo-élément ne peut pas peindre
  au-dessus d'une boîte empilée plus haut (ici `.mj-edit-body`, qui doit rester
  au-dessus pour que ses champs restent cliquables).
- Piège évité : le survol du voile ouvert garde la couleur du voile (règle
  `:hover` explicite, sinon la couche d'état du bouton-icône s'appliquerait à
  tout l'écran) ; aucune règle `:hover` sur le glyphe ✕ (le bouton couvre tout
  l'écran, il serait « survolé » en permanence).

**Divulgation progressive** : deux niveaux, mêmes `name=` de champs (donc
`edit_task`, `app/main.py`, inchangé) :
- **Essentiels**, toujours visibles : Titre, Description, Priorité, Points Fibo,
  Échéance, Durée.
- **Options avancées** (`<details class="mj-edit-advanced">`, repliées) :
  programmation, projet, temps passé manuel, récurrence, type, heure fixe, fiche
  liée, sous-tâches, bloqueurs.

**Bloqueurs en cases à cocher** (`.mj-blocker-checks`, `<input type="checkbox"
name="blocker_ids">`). Piège de spécificité CSS : la règle générique `.mj-edit-form
label` (`display:flex; flex-direction:column`) est plus spécifique
qu'`.mj-check-label` seul et empilait la case au-dessus du texte ; la règle
`.mj-edit-form .mj-check-label { flex-direction: row; }` lui rend la priorité.

## Bannières et alertes

- `.banner` : `surface-container-high`, rayon 12px, icône `on-surface-variant` —
  informations et dégradations (sources externes, surcharge, « Réglages
  enregistrés »… ; `.banner.success` en conteneur « ok »).
- `.banner.warning` : conteneur d'erreur — vrais échecs uniquement.
- **Bandeau de mise à jour** (`.mj-update`, `templates/_update_banner.html`,
  `docs/spec/mises-a-jour.md`) : `.banner` neutre, icône `download`,
  `.banner.warning` en cas d'échec ; « Mettre à jour » seul bouton plein.
- **Alertes de chrono flottantes** (`.mj-alert-toast`, issue #34) : **snackbar**
  MD3 (surface inverse, rayon 4px, élévation 3, ✕ en `inverse-primary`), en bas à
  droite, au-dessus de la barre basse sur Android.

## Contraintes transverses (inchangées)

- HTML/CSS pur, sans dépendance de build ; le JavaScript reste une amélioration
  progressive (aucune bibliothèque de composants MD3 en JS, type Material Web :
  les composants sont rendus par le CSS sur le HTML existant).
- Cibles tactiles ≥ 44px sur mobile pour les contrôles à un tap (pastilles),
  36px minimum pour les boutons-icônes de ligne ; aucun défilement horizontal à
  390px (vérifié sur toutes les pages, navigateur étroit et APK).
- Densité d'information : proche de l'existant (corps 14px au lieu de 13.5px) ;
  ne pas l'augmenter ni la réduire lors de futurs ajouts.
