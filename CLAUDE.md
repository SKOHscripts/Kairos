# Design system Kairos — sobre & professionnel

Cette charte remplace l'ancienne (« appli perso chaleureuse », ivoire/terracotta).
Toute nouvelle vue ou composant Kairos doit s'y conformer. Source des jetons et détail
complet : `docs/DESIGN_SYSTEM.md` (mêmes noms de classes/variables que
`static/style.css`).

## Palette
- Neutres ardoise : fond `#F3F5F8`, surface `#FFFFFF`, bordure `#E2E6EB` /
  `#C9D2DC` (forte), texte `#16202B` / `#55606D` (secondaire) / `#8A94A0`
  (tertiaire).
- **Un seul accent transverse**, bleu `#2F6FED` (hover `#2557C0`, fond doux
  `#E4ECFB`). Réservé aux boutons primaires/CTA et aux deux seuls badges
  « clés » : score WSJF et priorité. Tout le reste des badges reste neutre ou
  sémantique bas-chroma (vert `#1F7A46` ok, rouge `#B3261E` critique, ambre
  `#92660A` avertissement, gris `#55606D` neutre/info).
- Surface sombre `#16202B` (texte `#F5F6F8`) : réservée à la pilule de nav
  active et à la carte « En ce moment ». Seul endroit sombre de l'UI.
- Un seul thème clair — pas de mode sombre.
- **Exception assumée** : la carte « Progression du jour » (`.mj-progress`,
  élément principal de la vue Jour) garde un aplat terracotta doux (`#F7DDC8` /
  bordure `#EFCDA9`, même famille que le logo) pour se distinguer — décision
  produit explicite, à ne pas « corriger » vers le neutre/bleu. Son texte
  (`.mj-next`) utilise `#B85A2A` (pas le `#D9713C` du logo, trop clair : ~2.6:1
  de contraste sur ce fond, sous le seuil AA) pour rester lisible. Ne pas
  étendre cette teinte à d'autres cartes ou badges.

## Typographie
- IBM Plex Sans (400/500/600/700) partout, y compris les nombres
  (`font-variant-numeric: tabular-nums`).
- Aucun registre décoratif/italique séparé : les titres d'accroche restent en Plex
  Sans, poids 600-700, jamais italique — sauf `.mj-next` (« À faire
  maintenant : ... » dans `.mj-progress`), volontairement en italique 19px/600
  pour peser sur cet élément principal ; toujours IBM Plex Sans (italique
  chargé via Google Fonts, `ital,wght@1,600`), pas une police séparée.
- Corps 13.5px, labels majuscules 10-11px, KPI 18-26px en 800. Densité
  d'information inchangée par rapport à l'existant — ne pas l'augmenter ni la
  réduire lors de futurs ajouts.

## Forme & effets
- Rayons réduits : cartes 10px, cartes imbriquées 8px, contrôles 6px, pilules
  999px.
- Pas d'ombre portée nulle part, sauf le panneau de modification de tâche
  ouvert (`.mj-edit-body`), seule vraie modale flottante de l'app.
- Pas de dégradé (`linear-gradient`) — tout est en aplat, sauf le fondu de
  scroll du bandeau d'actions sticky de la page Réglages
  (`.mj-settings-actions`) et la carte « Progression du jour »
  (`.mj-progress`, voir Palette ci-dessus).
- Puce de priorité/urgence sur une ligne de tâche : `border-left: 3px solid`
  (jamais de remplissage).

## Identité
- Logo (mire/cadran solaire) et nom **Kairos** inchangés, y compris ses
  couleurs terracotta d'origine — c'est volontairement le seul point de
  couleur chaude au milieu d'une interface sinon neutre.

## Navigation & mobile
- Barre horizontale sticky en haut (`.topnav` + `.topbar`), pas de sidebar, pas
  de barre de navigation basse même sur mobile/APK Android — seulement
  redimensionnée (sous-titre masqué, pilules resserrées sous 720px).
- Cibles tactiles ≥ 44px sur mobile ; vérifier qu'aucun composant (grille
  semaine, panneau d'édition) ne déborde horizontalement sur ~375px de large.
- L'app tourne aussi en exécutable de bureau (PyInstaller, Windows/Linux) :
  rester en HTML/CSS pur, sans dépendance de build.

## Références
- Charte complète, table des jetons : `docs/DESIGN_SYSTEM.md`.
- Feuille de style : `static/style.css`.
