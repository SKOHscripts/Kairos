# Partager vers Kairos (Android)

_Rôle : faire de Kairos une cible de la feuille de partage d'Android. Un lien ou un texte partagé devient tout de suite une
note, sans ouvrir Kairos. Fichiers couverts (prévus) : `kmp/core/src/commonMain/.../core/notes/SharedNote.kt` (corps de la
note, pur), `kmp/androidApp/.../android/ShareNoteActivity.kt` (réception du partage), `kmp/androidApp/src/main/AndroidManifest.xml`
(activité et filtre d'intention), `androidApp/src/main/res/values/themes.xml` (thème transparent `Theme.Kairos.Share`),
`kmp/ui/.../notes/ShareTexts.kt` et `composeResources/values*/strings.xml` (textes `share_note_*`, carte du catalogue),
`kmp/ui/.../guide/FeatureCatalog.kt` (carte `share`), `kmp/tools/make_icons.py` et `KairosIcons` (icône `Share`), `README.md` et
`README.fr.md`. Le modèle `Note` et `createNote` sont décrits par `notes-capture.md`. Tests :
`core/.../notes/SharedNoteTest.kt`, `FeatureCatalogTest`, `StringsParityTest`, `ShareTargetManifestTest` (module `ui`, qui lit
le manifeste sans SDK Android, comme `AndroidStringsParityTest`)._

État : issue #293, spécifiée et **implémentée le 2026-11-27**, pour la **4.6.0-beta.2** (milestone 4.6.0). Vérification manuelle
sur appareil à faire avant la finale (voir Critères de succès).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Sur Android, on tombe sur un article, une vidéo ou un fil qu'on veut lire ou traiter plus tard. Aujourd'hui, pour le garder
dans Kairos, il faut copier le lien, ouvrir Kairos, aller sur Notes, coller, puis toucher « Capturer ». Ce détour fait
renoncer, ou finir dans une autre application. Or la capture GTD doit coûter le moins de gestes possible : tout ce qui
retient l'attention doit pouvoir aller en un geste dans la boîte de réception, qui est Notes dans Kairos (`methode-gtd.md`).

### Comportement attendu (utilisateur)

- Dans la feuille de partage d'Android (« Partager » d'un navigateur, d'un lecteur RSS, de YouTube, d'un texte sélectionné…),
  **Kairos** apparaît, avec son icône et son nom, pour le **texte et les liens** seulement. Pour une image ou un fichier, il
  n'apparaît pas.
- Toucher Kairos **crée la note aussitôt**. Rien ne s'ouvre et rien n'est à confirmer. Un message bref du système confirme
  « Note ajoutée à Kairos », et on reste dans l'application d'où l'on a partagé.
- **Contenu de la note** :
  - En première ligne, le titre que l'application d'origine fournit avec le partage, en général celui de l'article ou de la
    page. Dessous, le texte partagé, en général le lien.
  - Sans titre fourni, la note est le texte partagé seul.
  - Si le texte partagé contient déjà ce titre, il n'est pas répété.
  - Exemple : partager depuis le navigateur la page « Comment réparer une fuite » donne une note de deux lignes, « Comment
    réparer une fuite » puis « https://exemple.org/fuite ». Avec « → Tâche », la tâche s'appelle « Comment réparer une fuite »
    et le lien passe dans sa description (`notes-capture.md`).
- La note est **une note ordinaire** : elle arrive en tête de « Notes », la plus récente, dans l'espace Perso, et se modifie,
  se convertit, s'archive ou se supprime comme les autres. Si Kairos est déjà ouvert sur Notes, elle y apparaît tout de suite.
- **Hors ligne** : Kairos ne télécharge rien. Si l'application d'origine ne donne qu'un lien, la note est le lien.
- **Cas limites** :
  - Un partage sans texte, ou fait seulement d'espaces, ne crée rien. Le message dit « Rien à ajouter : le partage ne
    contenait pas de texte. ».
  - Un texte très long (une sélection de page entière) est gardé jusqu'à 10 000 caractères, puis coupé et terminé par « … ».
  - Si la base ne peut pas s'ouvrir (Kairos a planté au lancement précédent, ou échoue à démarrer), rien n'est écrit. Le
    message dit « Kairos n’a pas pu ajouter la note. Ouvre Kairos pour en savoir plus. ».
- Les messages suivent la langue choisie dans Kairos (Réglages → Langue), dans les six langues.
- Découverte : une carte « Partager vers Kairos » du catalogue (chapitre « Premiers pas », Android seulement, « depuis 4.6.0 »)
  ouvre Notes. Elle apparaît donc dans « Nouveau dans cette version » et dans la liste des fonctionnalités.

### Critères de succès

- Le corps de la note suit les règles ci-dessus : titre puis texte ; texte seul sans titre ; pas de titre répété ; titre sur
  une ligne ; espaces de bord retirés ; partage vide → rien ; coupe à 10 000 caractères avec « … » (`SharedNoteTest`).
- Le corps d'un partage « titre + lien », converti par « → Tâche », donne une tâche titrée par le titre, avec le lien en
  description (`SharedNoteTest`, qui enchaîne `SharedNote.body` et `NoteConversion.fields`).
- Le manifeste déclare l'activité de partage avec un seul filtre `SEND` sur `text/plain`. Elle est exportée, sans permission
  ajoutée, et `MainActivity` n'a pas de filtre de partage (`ShareTargetManifestTest`).
- Les textes `share_note_*` et la carte existent dans les six langues (`StringsParityTest`). La carte `share` a sa puce et sa
  ligne dans les deux README (`FeatureCatalogTest`).
- Vérification manuelle sur un appareil ou un émulateur Android, notée dans la PR :
  - Partager une page depuis Chrome et depuis Firefox, puis un texte sélectionné : la note est créée, le message s'affiche, on
    reste dans l'application d'origine.
  - Kairos fermé (processus tué) puis Kairos ouvert sur Notes : la note apparaît dans les deux cas.
  - La liste des applications récentes ne montre aucune fenêtre Kairos de plus.

### Hors périmètre / différé

- Images et partage de plusieurs images (`SEND_MULTIPLE`) : spécifiés à part, avec les images dans les notes
  (`notes-images.md`, #295). Autres fichiers : une note n'a pas de pièce jointe.
- Récupérer en ligne le titre d'une page quand l'application ne le fournit pas : ce serait une connexion réseau implicite,
  contraire à « hors ligne par défaut » (`connexions-en-ligne.md`).
- Partager directement comme **tâche**, ou choisir entre note et tâche dans la feuille de partage : la note est l'étape de
  capture, et la qualification vient après (GTD).
- Partage direct vers une conversation (raccourcis de partage, « Direct Share »), modification de la note avant création.
- Repérer un doublon (le même lien déjà dans une note) : deux partages font deux notes.
- Bureau et web. Le web aurait besoin d'une application web installable (Web Share Target), que Kairos n'est pas.
- L'écran Notes ne change pas : aucune mention du partage dans l'aide de capture.

## 2. Solution technique

### Corps de la note (`core`, pur)

- `SharedNote.body(subject: String?, text: String?): String`, dans `core/notes/SharedNote.kt` :
  - `s` = `subject` sans ses espaces de bord, chaque suite de blancs et de retours à la ligne réduite à une espace : le titre
    tient sur une ligne, pour rester le titre de la tâche à la conversion.
  - `t` = `text` sans ses espaces de bord. Les lignes intérieures sont gardées telles quelles.
  - Les deux vides → `""`. `s` vide → `t`. `t` vide → `s`. `t` contient `s` (sans tenir compte de la casse) → `t`.
    Sinon → `s + "\n" + t`.
  - Résultat de plus de `MAX_BODY` (10 000) caractères : les 9 999 premiers, puis `…`. La coupe ne sépare jamais une paire de
    substitution UTF-16 : un émoji n'est jamais coupé en deux.
- Le titre long n'est pas traité ici : à la conversion, `NoteConversion.fields` tronque déjà la première ligne à 200
  caractères et la reprend en description (`notes-capture.md`).

### Réception (`androidApp`)

- **Manifeste** : une activité `ShareNoteActivity` avec :
  - `exported="true"` et un `intent-filter` : action `android.intent.action.SEND`, catégorie `DEFAULT`, `mimeType` `text/plain` ;
  - `theme="@style/Theme.Kairos.Share"`, `excludeFromRecents="true"`, `noHistory="true"`, `taskAffinity=""` : l'activité ne
    prend pas la place de la tâche Kairos, n'apparaît pas dans les récentes et ne se rouvre pas au retour ;
  - pas de `label` ni d'`icon` propres : elle hérite de ceux de l'application, « Kairos » et le logo.
  Aucune permission n'est ajoutée.
- **Thème** `Theme.Kairos.Share` (`values/themes.xml`) : parent explicite `android:Theme.Translucent.NoTitleBar` (fenêtre
  transparente, sans titre), `windowAnimationStyle` à `@null` : rien n'est visible. Rien du thème de l'application n'y sert, et
  un seul fichier suffit : pas de variante sombre ni `v31`.
- **`ShareNoteActivity.onCreate`** (une `Activity` simple, sans Compose) : aucun `setContent`.
  1. `KairosCrash.pending(this) != null` → message d'échec, `finish()`. La base reste fermée, comme pour `MainActivity`
     (`demarrage.md`).
  2. `body = SharedNote.body(getCharSequenceExtra(EXTRA_SUBJECT)?.toString(), getCharSequenceExtra(EXTRA_TEXT)?.toString())`.
     On lit des `CharSequence` parce que certaines applications envoient du texte mis en forme (`Spanned`), et
     `getStringExtra` le rendrait `null`. Corps vide → message « rien à ajouter », `finish()`.
  3. `KairosProcess.scope.launch` :
     - `KairosProcess.services(app).await()`, dans un `runCatching` (échec → message d'échec) ;
     - `applyLanguage(UiLanguage.resolve(settings.language, …))`, comme `WidgetSync`, pour un processus ouvert sans
       activité ;
     - `repository.createNote(body)` ; une exception ou un refus donne le message d'échec ;
     - puis, sur `Dispatchers.Main`, le message de succès et `finish()`.
     L'activité attend la fin de l'écriture avant de se fermer : le processus reste au premier plan le temps de l'écriture,
     et le système ne peut pas le tuer avant que la note soit enregistrée.
- **Message** : `Toast.makeText(applicationContext, texte, LENGTH_SHORT)`, avec un texte lu hors composition par
  `ShareTexts` (`ui/notes`, comme `WidgetTexts`) dans les ressources Compose (`share_note_added`, `share_note_empty`,
  `share_note_failed`). Le contexte de l'application
  garde le message affiché même si l'activité est déjà fermée.
- La note passe par `createNote` et son flux d'état. `MainActivity` ouverte, l'écran Notes et la synchronisation du fichier
  lié la voient sans rien de plus. Les notes n'ont pas d'espace Équipe : elle est dans la vue Perso.

### Textes et catalogue (`ui`)

- `share_note_added` : « Note ajoutée à Kairos » ; `share_note_empty` : « Rien à ajouter : le partage ne contenait pas de
  texte. » ; `share_note_failed` : « Kairos n’a pas pu ajouter la note. Ouvre Kairos pour en savoir plus. ». Les trois dans les
  six langues `values*/`, avec l'apostrophe `’`.
- Carte `FeatureCard("share", GuideChapter.FIRST_STEPS, { KairosIcons.Share }, card_share_title, card_share_body,
  GuideTarget.Personal(Destination.NOTES), since = "4.6.0", platforms = setOf(Platform.ANDROID))`, placée juste après `capture`.
  Titre « Partager vers Kairos ». Phrase : « Partage un article ou un texte depuis n’importe quelle appli : il devient une
  note, sans ouvrir Kairos. ». Six langues.
- Icône `share` (Material Symbols Outlined 400), ajoutée à `make_icons.py`, puis `KairosIcons` regénéré.
- README : la puce **« Share to Kairos »** / **« Partager vers Kairos »** dans « Notes (capture) » des deux README, et sa ligne
  dans le tableau « Feature list » (Premiers pas, 4.6.0, Android). `README_FEATURES` fait le lien.

### Version

- `main` est en `4.6.0-beta.1`, déjà taguée (`v4.6.0-beta.1`). L'implémentation passe `main` en `4.6.0-beta.2`
  (`python3 kmp/tools/set_version.py 4.6.0-beta.2`). Une beta n'a pas de note de version Fastlane (`distribution.md`). La
  carte porte `since = "4.6.0"` : une carte porte une finale, jamais une beta (`FeatureCatalogTest.aFeatureArrivesInAMinorOrMajorVersion`).

### Décisions et pièges tracés

- **Création sans confirmation** (propriétaire, 2026-11-27). On veut sauvegarder en un geste. Écartés :
  - ouvrir Kairos sur Notes avec le champ prérempli : un toucher de plus, et on quitte l'application d'origine ;
  - créer la note puis ouvrir Kairos dessus : même rupture.
  Une note de trop s'archive ou se supprime en un toucher.
- **Titre de l'application en première ligne** (propriétaire, 2026-11-27). La première ligne d'une note devient le titre de
  sa tâche : y mettre le titre de l'article donne une tâche lisible. Avec le lien en première ligne, la tâche s'appellerait
  « https://… ». Écartés : le texte seul, et le titre récupéré en ligne (voir Hors périmètre).
- **Pas de titre répété** : beaucoup d'applications mettent déjà le titre dans le texte (« Titre https://… »). Le test « contient,
  sans la casse » évite la note « Titre / Titre https://… ». Écartés : une égalité stricte, qui laissait passer ces doublons ;
  une ressemblance approchée, imprévisible.
- **Texte et liens seulement** (propriétaire, 2026-11-27) : une note n'a pas de pièce jointe. Garder seulement le nom d'un
  fichier ou d'une image n'apporterait rien. Kairos ne se propose donc pas pour ces types : pas de cible qui ne fait rien.
- **Activité dédiée plutôt que `MainActivity`** : `MainActivity` est `singleTop` et monte toute l'interface. Recevoir le
  partage dans cette activité ouvrirait Kairos, ou poserait une copie de l'application dans la tâche de l'application
  d'origine. Une activité transparente, sans historique et sans affinité, ne montre rien et ne touche pas à la tâche Kairos.
  Écartés :
  - un `Theme.NoDisplay`, qui exige `finish()` avant la fin d'`onResume`, alors que l'ouverture de la base est asynchrone ;
  - un service lancé par l'activité : plus de code, pour rien, puisque l'écriture dure quelques centaines de millisecondes.
- **Attendre l'écriture avant `finish()`** : fermer l'activité tout de suite et écrire en arrière-plan laisserait le système
  tuer le processus mis en cache avant l'écriture. La note serait alors perdue sans le dire, et le message de succès
  mentirait.
- **Plafond de 10 000 caractères** : une note n'en a pas à la saisie. Mais un partage peut porter une page entière, qui
  alourdirait la liste des notes et le fichier lié pour une capture. 10 000 caractères suffisent largement pour un lien, un
  extrait ou un fil. Écartés : refuser le partage (on perdrait tout) ; aucun plafond.
- **Pas de détection de doublon** : comparer aux notes existantes demanderait une règle de normalisation des liens (paramètres
  de suivi, fragments). Pour l'instant, deux partages font deux notes, simples à repérer dans la liste.
- **Libellé et icône hérités** : la cible s'appelle « Kairos », comme l'application, pour qu'on la reconnaisse. Le message
  dit que c'est une note. Une chaîne Android de plus, à tenir dans six langues, n'apporterait rien.
- **Impacts reportés** (2026-11-27) dans `notes-capture.md`, `distribution.md` § Android, `demarrage.md` et l'index.
