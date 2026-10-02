# Soutien : lien vers la page de don

Le cœur de la barre du haut et le bouton « Soutenir Kairos » de « À propos et
guide » ouvrent la page de don de l'auteur. Fichiers couverts :
`ui/.../screens/AboutScreen.kt` (`DONATE_URL`), `ui/.../navigation/AppShell.kt`,
`tools/make_icons.py` (icône `Favorite`), chaînes `donate_action`.

## Besoin métier

### Problème

Kairos est gratuit, libre et sans publicité. Un utilisateur qui l'apprécie doit
pouvoir soutenir son auteur en un geste, sans que l'application insiste.
MeteoCompare, autre application de l'auteur, propose déjà un cœur de ce type ;
Kairos reprend le principe, mais ouvre directement la page de don commune
(dépôt `donate.github.io`) au lieu d'un dialogue interne.

### Comportement attendu

- Une icône **cœur** (« Soutenir Kairos ») à droite de la barre du haut, à côté
  du « ? », visible sur tous les écrans, y compris « Accueil » et « À propos et
  guide » (là où le « ? » disparaît).
- Un bouton **« Soutenir Kairos »** (icône cœur) dans « À propos et guide », sous
  « Code source ».
- Les deux ouvrent `https://skohscripts.github.io/donate.github.io/` dans le
  navigateur du système (Android, bureau, web).
- Aucun privilège pour les donateurs, aucune relance, aucun compteur.

### Critères de succès

- Un clic sur le cœur ouvre la page de don ; le lecteur d'écran annonce
  « Soutenir Kairos ».
- Aucune permission réseau ajoutée à l'APK : c'est le navigateur qui charge la
  page, l'application n'émet aucune requête.

### Hors périmètre

- Aucune modification des dépôts `donate.github.io` et `meteocompare`.
- Pas de dialogue interne, pas de liste de plateformes dans l'application, pas
  de masquage du lien par réglage.

## Solution technique

- `DONATE_URL` (`AboutScreen.kt`, à côté de `SOURCE_URL`) : constante unique de
  l'adresse ; l'appel est `LocalUriHandler.openUri`, enveloppé de `runCatching`
  (un environnement sans navigateur ne doit pas faire planter l'application).
- `AppShell` : `IconButton` avec `KairosIcons.Favorite` dans `actions` de la
  `TopAppBar`, **avant** le « ? » et hors de la condition `secondary == null`.
- `AboutScreen` : `KairosOutlinedButton` icône + texte, comme « Code source ».
- Icône générée par `make_icons.py` (`Favorite`, contour, sans variante pleine).
- Chaînes `donate_action` en français (« Soutenir Kairos ») et en anglais
  (« Support Kairos »), servant de libellé visible et de description d'accessibilité.

### Décisions

- **Racine de la page GitHub Pages**, pas `donate/redirect.html` : la racine
  redirige déjà, et le chemin interne reste libre d'évoluer.
- **Aussi sur Android** : un lien ouvert dans le navigateur n'est pas une
  dépendance ni un traitement de paiement ; l'APK reste sans permission réseau.
- **Pas de dialogue** (contrairement à MeteoCompare) : la page de don porte déjà
  les options de paiement.
