# Design system Kairos

Charte visuelle de l'application : sobre et professionnelle, à la manière d'un tableau
de bord de pilotage — pas une appli perso chaleureuse. Tout nouveau gabarit ou
composant doit réutiliser ces jetons plutôt que d'en réinventer. Implémentés dans
`static/style.css` (variables `:root`) et `templates/base.html` (police, logo,
navigation).

## Couleurs

| Rôle | Variable CSS | Valeur |
|---|---|---|
| Fond de page | `--bg` | `#F3F5F8` (ardoise très clair) |
| Surface (cartes) | `--surface` | `#FFFFFF` |
| Surface teintée (hover, bandeaux) | `--surface-tint` | `#EEF2FA` |
| Bordure | `--border` | `#E2E6EB` |
| Bordure forte / pointillés | `--border-strong` | `#C9D2DC` |
| Texte principal | `--text` | `#16202B` |
| Texte secondaire | `--text-2` | `#55606D` |
| Texte tertiaire / muted | `--text-3` | `#8A94A0` |
| Texte inversé (surfaces sombres) | `--text-inverse` | `#F5F6F8` |
| Accent primaire | `--accent` | `#2F6FED` (bleu) |
| Accent primaire, hover/texte foncé | `--accent-700` | `#2557C0` |
| Accent primaire, fond doux | `--accent-soft` | `#E4ECFB` |
| Accent primaire, ligne | `--accent-line` | `#C7D8F7` |
| Lien / focus | `--link` | `#2557C0` |
| Surface sombre (carte focus, pilule nav active) | `--dark-surface` | `#16202B` (texte `--dark-text` `#F5F6F8`, muted `--dark-muted` `#97A1AE`, accent `--dark-accent` `#7FA8FF`) |

Il n'y a qu'**un seul accent transverse** (bleu), réservé aux boutons primaires/CTA et
aux deux seuls badges « clés » : score WSJF (`.badge.mj-score`) et priorité
(`.badge.prio`). Tout le reste des badges reste neutre ou sémantique bas-chroma.

**Exception assumée, scopée à une seule carte** : `.mj-progress` (« Progression du
jour », élément principal de la vue Jour) garde un dégradé terracotta —
`linear-gradient(135deg, var(--surface-tint), #F7DDC8 130%)`, bordure `#EFCDA9` —
même famille de couleur que le logo, pour se démarquer visuellement. Décision produit
délibérée, à ne pas généraliser à d'autres cartes/badges ni « corriger » vers
bleu/neutre.

### Badges sémantiques (pilules, `border-radius: 999px`)

| Rôle | Classe | Fond | Texte |
|---|---|---|---|
| Priorité (accent) | `.badge.prio` | `#E4ECFB` | `#2557C0` |
| Deep work / OK / fait | `.badge.ok` | `#E1F3E7` | `#1F7A46` (vert) |
| Urgent / critique / erreur | `.badge.bad` | `#FBE4E1` | `#B3261E` (rouge) |
| Neutre / tag projet | `.badge.neutral` | `#EEF1F4` | `#55606D` |
| Info | `.badge.info` | `#EEF1F4` | `#55606D` |
| Avertissement | `.badge.warn` | `#FDF0D8` | `#92660A` (ambre) |

## Typographie

- **IBM Plex Sans** (400/500/600/700) — tout le texte d'interface, labels, chiffres
  (nombres en `font-variant-numeric: tabular-nums`, plus besoin de police mono dédiée
  aux identifiants/chiffres).
- Aucun registre décoratif/italique séparé : les titres d'accroche restent en Plex
  Sans, poids 600-700, jamais italique (classe `.editorial` neutralisée, conservée
  pour compatibilité de nommage uniquement) — **sauf** `.mj-next` (la ligne « À faire
  maintenant : ... » dans `.mj-progress`), en italique 19px/600 pour peser sur cet
  élément principal ; toujours IBM Plex Sans (italique chargé via Google Fonts,
  `ital,wght@1,600` dans `templates/base.html`), jamais une police séparée.
- Échelle : corps ~13.5px · labels majuscules 10–11.5px (`letter-spacing` ~0.04em) ·
  chiffres clés (KPI) 18–26px en 800. Densité d'information inchangée par rapport à
  l'existant.

## Forme

- Rayon des cartes/panneaux : `--r` 10px (défaut), `--r-md` 8px (cartes imbriquées :
  ligne de tâche, jour de semaine, agenda), `--r-xl` 12px (bandeaux d'accueil, carte
  « en ce moment »).
- Rayon des contrôles (boutons, champs, icônes) : `--r-sm` 6px.
- Pilules (badges, nav, filtres) : `--r-pill` 999px.
- Puce de priorité/catégorie sur une ligne de tâche : `border-left: 3px solid
  <couleur>` (jamais de remplissage `box-shadow: inset`).
- Pas de dégradé (`linear-gradient`) nulle part, sauf le fondu de scroll du bandeau
  d'actions sticky de la page Réglages (`.mj-settings-actions`) et la carte
  « Progression du jour » (`.mj-progress`, voir Couleurs ci-dessus).
- Ombres : supprimées presque partout. Le seul `box-shadow` conservé est celui du
  panneau de modification de tâche ouvert (`.mj-edit-body`), qui se comporte comme un
  popup flottant.

## Logo

Mark « cadran solaire » (cercle + un seul secteur terracotta + point pivot),
`static/favicon.svg` et repris inline dans `templates/base.html` et
`templates/home.html` : c'est volontairement le **seul** élément qui garde ses
couleurs d'origine, au milieu d'une interface sinon neutre.

```html
<svg width="34" height="34" viewBox="0 0 40 40">
  <circle cx="20" cy="20" r="18.5" fill="#FBEEDF"/>
  <circle cx="20" cy="20" r="18.5" fill="none" stroke="#E7C4A2" stroke-width="1.6"/>
  <path d="M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z" fill="#D9713C"/>
  <circle cx="20" cy="20" r="2.6" fill="#2B241E"/>
</svg>
```

## Navigation

Barre horizontale sticky (`.topnav`) : logo + titre à gauche, items en pilules
(`.tn-item`, actif = fond `--dark-surface` / texte `--dark-text`) — pas de sidebar, pas
de barre de navigation basse même sur mobile/APK Android. Sous 720px : sous-titre
masqué (`.tn-sub`), pilules resserrées pour tenir sur un écran de téléphone. Une
sous-barre (`.topbar`) porte le titre de page et les actions contextuelles (bascule
Jour/Semaine, retour, etc.).

## Panneau de modification d'une tâche

`edit_panel(task)` (`.mj-edit` / `.mj-edit-body` dans `templates/kairos.html`) se
présente comme une **modale centrée avec fond assombri**, en CSS pur, sans
JavaScript. Mécanique :
- `.mj-edit-body` (la carte visible) passe en `position: fixed`, centrée,
  quand le `<details>` porte l'attribut `[open]`.
- Le `<summary>` (le crayon) devient, lui, un calque plein écran
  (`position: fixed; inset: 0`) semi-transparent dès l'ouverture : c'est un
  vrai `<summary>`, donc cliquer n'importe où en dehors de la carte referme
  nativement le panneau (pas de JS, pas de `pointer-events` factice).
- Un glyphe ✕ (`::after` de ce même `<summary>`) est positionné juste à côté
  du coin haut-droit de la carte — **jamais par-dessus** : un pseudo-élément
  ne peut pas peindre au-dessus d'une boîte empilée plus haut (ici
  `.mj-edit-body`, qui doit rester au-dessus pour que ses propres champs/
  boutons restent cliquables), donc le faire chevaucher la carte le rendrait
  invisible malgré un z-index élevé sur le pseudo-élément lui-même — l'ordre
  d'empilement se décide au niveau du contexte parent (`<summary>`, plus bas),
  pas du descendant.
- Piège évité : ne **jamais** mettre de règle `:hover` sur ce glyphe — une
  fois ouvert, le `<summary>` couvre tout l'écran, donc il serait « survolé »
  en permanence et resterait bloqué dans son état hover.

## Case à cocher

Chaque ligne de tâche (`.kairos-item`) porte un rond `.mj-check` en tête de ligne
(avant l'heure/le titre) qui bascule le statut fait/à faire — même formulaire
`POST .../done` qu'avant, en premier enfant du `<li>`, sorti du groupe d'actions de
droite (`.mj-actions`, qui ne garde que chrono et décaler).

## Carte « En ce moment »

Le chrono en cours est repris en évidence dans une carte sombre dédiée
(`.mj-now-card`, colonne latérale de la vue jour, à côté de l'agenda) — en plus du
badge `.mj-timer` déjà présent sur la ligne de la tâche (conservé tel quel, y compris
son minuteur vivant en JS). C'est, avec la pilule de navigation active, le seul
endroit sombre de l'interface.

## Écarts assumés par rapport aux maquettes `.dc.html`

Les maquettes du dossier `design_handoff_kairos_redesign/` (non versionné) montrent
des pilules cliquables pour la priorité/les points Fibonacci et des chips à bascule
pour les tâches bloquantes, dans le panneau de modification. L'implémentation réelle
garde des `<select>`/`<select multiple>` HTML natifs (mêmes noms de champs, mêmes
tests) — le style pilule n'est pas repris pour ces champs précis, pour ne pas ajouter
de JavaScript ni changer la sémantique des formulaires.
