# Images dans les notes

_Rôle : une note peut porter jusqu'à quatre images (capture d'écran, photo d'un tableau blanc ou d'un ticket, image partagée),
réduites à l'ajout, visibles et synchronisées sur toutes les plateformes. Fichiers couverts :
`kmp/core/src/commonMain/.../core/model/NoteImage.kt` et `KairosSnapshot.kt`, `core/notes/ImageBudget.kt` (`EncodedImage`,
taille cible, pur), `core/sync/SyncScope.kt`, `SyncRecords.kt`, `SnapshotMerge.kt`, `SyncReconcile.kt`, `SyncEdit.kt` (genre
`noteImage`), `kmp/data/.../ExportCodec.kt` (`noteImages`), `17.sqm`, `Kairos.sq`, `KairosStore.kt` (marqueur du schéma 18),
`Mappers.kt` et `KairosRepository.kt` (`createNote`, `editNote`, `deleteNote`), `kmp/ui/.../app/AppServices.kt` (`images`),
`kmp/ui/.../notes/ImageService.kt`, `NoteImages.kt` (vignettes, menu, visionneuse), `NotesScreen.kt`, `ShareTexts.kt`,
`ui/day/EditTaskDialog.kt` et `DayScreen.kt` (lien de la fiche), `kmp/androidApp/.../AndroidImages.kt`, `MainActivity.kt`,
`ShareNoteActivity.kt`, `AndroidServices.kt`, `AndroidManifest.xml` et `res/xml/file_paths.xml`,
`kmp/desktopApp/.../DesktopImages.kt` et `DesktopServices.kt`, `kmp/webApp/.../WebImages.kt`, `WebInterop.kt`, `WebServices.kt`
et `kairos-web.js`, `make_icons.py` et `KairosIcons`, `values*/strings.xml`, `FeatureCatalog.kt`, `README.md` et `README.fr.md`.
Tests : `ImageBudgetTest` (core), `NoteImageRepositoryTest` (data : dépôt, migration, export, fichier lié), `DesktopImagesTest`
et `NoteImagesUiTest` (desktopApp), `ShareTargetManifestTest`, `StringsParityTest` et `FeatureCatalogTest` (ui)._

État : issue #295, spécifiée et **implémentée le 2026-11-27**, pour la **4.6.0-beta.2**, avec le partage vers Kairos (propriétaire,
2026-11-27) ; presse-papiers sur toutes les plateformes ajouté le même jour. Vérifications manuelles sur Android et dans
Chromium à faire avant la finale (voir Critères de succès).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Une note ne contient que du texte. Or beaucoup de ce qu'on veut capturer est une image : une capture d'écran, la photo d'un
tableau blanc après une réunion, d'un ticket de caisse, d'une étiquette, une image trouvée en lisant un article. Aujourd'hui,
il faut la garder ailleurs (galerie, dossier) et écrire dans Kairos une note qui y renvoie : la capture est coupée en deux, et
l'image se perd. La boîte de réception GTD doit tout recevoir au même endroit (`methode-gtd.md`).

### Comportement attendu (utilisateur)

- **Ajouter des images** : dans la carte de capture de Notes et dans le dialogue « Modifier », un bouton « Ajouter une image »
  (icône d'image), à côté de « Capturer ». Il ouvre un menu, selon la plateforme :
  - **Android** : « Choisir dans la galerie » (sélecteur de photos du système, sans autorisation à donner), « Prendre une
    photo » (l'appareil photo du système) et « Coller depuis le presse-papiers » ;
  - **bureau et web** : « Choisir un fichier » (PNG, JPEG, WebP, GIF, BMP) et « Coller depuis le presse-papiers ».
  On peut choisir plusieurs fichiers ou photos d'un coup.
- **Coller une image**, sur toutes les plateformes (propriétaire, 2026-11-27) :
  - l'entrée « Coller depuis le presse-papiers » du menu ajoute l'image du presse-papiers ; s'il n'y en a pas, le message dit
    « Pas d’image dans le presse-papiers. » ;
  - **bureau et web** : Ctrl+V (Cmd+V sur Mac) dans le champ de la note ajoute l'image du presse-papiers (sur le bureau, aussi
    les images copiées comme fichiers dans le gestionnaire de fichiers) ; un texte se colle comme avant, sans message s'il n'y
    a pas d'image ;
  - **Android** : « Coller » du champ de la note, et les images que le clavier envoie (presse-papiers du clavier, GIF,
    autocollants), ajoutent l'image ; un texte se colle comme avant.
  Sur le web, le navigateur peut demander d'autoriser la lecture du presse-papiers à la première utilisation de l'entrée du
  menu ; Ctrl+V n'a pas besoin de cette autorisation.
- **Avant de capturer**, les images choisies s'affichent en vignettes sous le champ, chacune avec « Retirer » (✕). « Capturer »
  enregistre le texte et les images ensemble.
- **Quatre images au plus par note.** Au-delà, le bouton est inactif, et le message dit « 4 images au plus par note. » ; d'un
  choix multiple, seules les premières entrent, et le même message le dit.
- **Une note peut n'avoir que des images**, sans texte. Elle s'affiche alors avec ses seules vignettes. « → Tâche » est inactif
  tant qu'elle n'a pas de texte, avec l'aide « Ajoute un texte pour en faire une tâche » : le titre de la tâche vient du texte
  (`notes-capture.md`).
- **Réduction à l'ajout** : chaque image est réduite (1 600 px au plus sur son grand côté, JPEG), tournée selon la prise de vue,
  et ses métadonnées sont retirées (lieu, appareil, date). Une image avec transparence (capture PNG) est posée sur un fond
  blanc. Une image illisible ou d'un type non pris en charge est refusée : « Cette image ne peut pas être lue. »
- **Voir** : dans la liste, les vignettes de la note (carrées, 72 dp, sur une ligne, sous le texte). Un toucher ouvre la
  **visionneuse** plein écran : l'image entière, « Image 2 sur 3 », image précédente et suivante (balayage sur Android,
  flèches et ←/→ sur le bureau et le web), zoom de 1 à 5 (pincer, double toucher, molette), « Fermer » et Échap. Les images
  en attente de la capture et du dialogue s'ouvrent de même.
- **Modifier** : le dialogue montre les vignettes de la note, avec « Retirer » sur chacune et « Ajouter une image ».
  « Enregistrer » est actif si la note garde un texte ou au moins une image.
- **Archiver** garde les images. **Supprimer** une note supprime ses images.
- **« → Tâche »** : la tâche reçoit le titre et la description comme avant ; les images restent sur la note, archivée et liée à
  la tâche. La fiche de la tâche montre alors, sous la description, « Images de la note d’origine (2) », qui ouvre ces images
  dans la visionneuse. Le lien n'apparaît pas si la note d'origine n'a pas d'image, ou n'existe plus.
- **Partager une image vers Kairos (Android)** : la cible de partage (`partage-android.md`) accepte aussi les images. Partager
  une ou plusieurs images (avec ou sans texte) crée une note qui les porte, sans ouvrir Kairos ; le texte suit les règles du
  partage de texte. Au-delà de quatre images, les quatre premières sont gardées et le message le dit : « Note ajoutée à Kairos
  (4 images au plus). » Rien de lisible (ni texte ni image) : « Rien à ajouter : le partage ne contenait ni texte ni image
  lisible. »
- **Les images voyagent avec les données** : l'export, l'import, les sauvegardes et la synchronisation par fichier lié les
  portent. Les autres appareils montrent les mêmes notes, images comprises.
- L'espace Équipe n'en voit rien : les notes sont personnelles, et les fichiers d'échange d'équipe ne portent pas d'image.

### Critères de succès

- Taille cible : une image de 4 000 × 3 000 px devient 1 600 × 1 200 ; une image de 800 × 600 garde sa taille ; une image très
  étirée (1 000 × 20 000) garde son rapport, et son grand côté passe à 1 600 (`ImageBudgetTest`).
- Une photo JPEG tournée par son EXIF (et portant un lieu GPS et un fabricant) est remise droite, sans EXIF ; une image de
  4 000 × 3 000 devient un JPEG de 1 600 × 1 200 ; une capture PNG transparente devient un JPEG sur fond blanc ; ce qui n'est
  pas une image est refusé (`DesktopImagesTest`). Android et le web, qui ont leur propre décodeur, sont vérifiés à la main
  (voir plus bas).
- Le dépôt refuse une cinquième image, et une note sans texte ni image ; une note sans texte avec une image est acceptée ;
  l'ordre d'ajout est gardé ; « Modifier » garde, retire et ajoute dans le plafond. Supprimer une note supprime ses images ;
  l'archiver ou la convertir les garde ; une note sans texte ne se convertit pas ; une base du schéma 17 gagne une table vide
  (`NoteImageRepositoryTest`).
- L'export porte `noteImages` seulement s'il y en a, et l'import les rend à l'identique ; une image orpheline, illisible ou de
  trop (cinquième) est ignorée, le reste s'importe (`NoteImageRepositoryTest.theExportCarriesThemAndIgnoresUnreadableOnes`).
- Par le fichier lié, les images arrivent sur l'autre appareil ; une image retirée d'un côté (qui l'avait vue) disparaît
  partout ; une note supprimée emporte ses images partout (`NoteImageRepositoryTest.theyTravelThroughTheLinkedFileAndLeaveWithTheirNote`).
- Écran (bureau, hors écran) : le menu propose « Choisir un fichier » et « Coller depuis le presse-papiers » ; une image en
  attente se retire avant « Capturer » ; une note faite d'une image seule montre « Ajoute un texte… » et « → Tâche » inactif ;
  sa vignette ouvre la visionneuse (« Image 1 sur 1 »), « Fermer » la ferme ; la fiche d'une tâche née d'une note à deux images
  montre « Images de la note d’origine (2) », qui ouvre « Image 1 sur 2 » (`NoteImagesUiTest`). À 360 dp, la carte de capture
  et quatre vignettes tiennent sans débordement (captures `--self-test`).
- Le manifeste déclare la cible de partage pour `text/plain` et pour les images (`SEND`, `SEND_MULTIPLE`), sans permission
  ajoutée (`ShareTargetManifestTest`).
- Vérification manuelle, notée dans la PR : sur Android, galerie, photo, partage d'une et de plusieurs images ; sur le web
  (Chromium), sélecteur et collage ; sur le bureau, collage. Une note à images ajoutée sur un appareil apparaît sur l'autre par
  le fichier lié.

### Hors périmètre / différé

- Images sur les **tâches** (fiche, vue Jour, espace Équipe) : les images restent sur la note d'origine (décision du
  propriétaire, 2026-11-27).
- Autres fichiers (PDF, documents, sons), dessin ou annotation, recadrage, légende d'image, reconnaissance du texte (OCR).
- Glisser-déposer une image sur le bureau et le web.
- Coller une image sur le web dans un navigateur sans `OffscreenCanvas` ni `createImageBitmap` (anciens Safari) : l'image est
  alors comptée illisible.
- Réordonner les images d'une note : elles gardent l'ordre d'ajout.
- Garder l'image d'origine en pleine résolution, ou la ré-enregistrer hors de Kairos (« Enregistrer l'image »).
- Limite globale de l'espace occupé par les images, ou écran « Espace utilisé » : à reprendre si les fichiers liés deviennent
  trop lourds en pratique (voir Décisions).

## 2. Solution technique

### Modèle et taille cible (`core`, pur)

- `NoteImage(id, noteId, createdAt, mime, width, height, data: String)` (`core/model/NoteImage.kt`) : `data` est le JPEG en
  **Base64 standard**, tel qu'il est stocké, exporté et synchronisé. `mime` vaut toujours `image/jpeg` à l'écriture ; il est
  stocké pour qu'un autre format puisse venir sans migration. Une image est **immuable**. Ordre d'une note : `createdAt`, puis
  `id`.
- `KairosSnapshot.noteImages: List<NoteImage>` ; `Note` ne change pas.
- `core/notes/ImageBudget.kt` :
  - `EncodedImage(mime, width, height, data)` : une image réduite, produite par la plateforme, écrite telle quelle par le dépôt ;
  - `ImageBudget.MAX_IMAGES` = 4, `MAX_SIDE` = 1 600, `MAX_BYTES` = 400 000, `QUALITIES` = 85, 75, 65, `JPEG` = `image/jpeg` ;
  - `target(width, height): Pair<Int, Int>` : grand côté au plus `MAX_SIDE`, rapport gardé, arrondi au plus proche, jamais
    d'agrandissement, jamais de côté nul ;
  - `sampleSize(width, height)` : la plus grande puissance de 2 qui garde l'image décodée au moins aussi grande que la cible
    (`BitmapFactory.inSampleSize`, Android).
  On encode en JPEG à 85 et on descend tant que le résultat dépasse `MAX_BYTES` ; à 65, le résultat est gardé tel quel.

### Stockage (`data`)

- Migration `17.sqm` (schéma 17 → 18) : table `note_image` (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `note_id INTEGER NOT NULL`,
  `created_at TEXT`, `mime TEXT`, `width INTEGER`, `height INTEGER`, `data TEXT`, tous `NOT NULL`), index `note_image_note`.
  Référence **molle** vers la note, sans clé étrangère, comme partout dans Kairos. `KairosStore.MARKERS` reconnaît le schéma 18
  à cette table ; les tests de migration vers un schéma plus ancien la retirent d'abord.
- `Kairos.sq` : `allNoteImages` (par `id`), `insertNoteImage`, `insertNoteImageWithId`, `deleteNoteImage`, `deleteImagesOfNote`,
  `deleteAllNoteImages`.
- Dépôt :
  - `readAll` trie les images par `createdAt` puis `id` **en Kotlin** : trié par SQL, le texte ISO d'un instant sans
    millisecondes (« …00Z ») passerait après « …00.001Z ».
  - `createNote(body, images = emptyList())` : rien sans texte **et** sans image, ni au-delà de `MAX_IMAGES` ; note et images
    dans une seule transaction (`lastInsertedId`). `insertImages` donne à chaque image une milliseconde de plus que la
    précédente : l'ordre d'ajout reste le même partout.
  - `editNote(noteId, body, keep: List<Long>? = null, add = emptyList())` : `keep` nul garde toutes les images ; refuse un
    résultat de plus de 4 images ou sans texte ni image (la note garde son état) ; `updated_at` n'avance que si le texte ou
    les images changent. Les images ajoutées datent d'après la plus récente des gardées, même si l'horloge n'a pas avancé.
  - `deleteNote` retire les images de la note dans la même transaction ; `replaceRows` (import, fusion, web) vide la table et
    n'écrit pas une image dont la note manque.
  - `convertNote` : inchangé ; une note sans texte n'a pas de titre et rend `null`.

### Services des plateformes

- `ui/notes/ImageService.kt` : `ImageSource` (`GALLERY`, `CAMERA`, `FILE`, `CLIPBOARD`, l'ordre du menu), `ImagePick(images,
  unreadable)`, et l'interface `ImageService` :
  - `sources` : les entrées du menu ; `pick(source, max)` ; `paste(max)` (entrée du menu) ;
  - `receivesContent` et `receive(content, max)` : le récepteur de contenu du champ, **Android seulement** (ailleurs il
    prendrait aussi un collage de texte, qui se perdrait) ;
  - `pastesOnShortcut` et `pasteFromShortcut(max)` (par défaut `paste`) : Ctrl/Cmd+V, bureau et web.
  `AppServices.images` est `null` dans les tests d'un écran seul : pas de bouton, les images existantes s'affichent quand même.
- **Android** (`AndroidImages.kt`, sources galerie, photo, presse-papiers) :
  - galerie : `MainActivity.pickImages()`, `ActivityResultContracts.PickMultipleVisualMedia(4)` avec `ImageOnly` (repli de
    lui-même sur `ACTION_OPEN_DOCUMENT` là où le sélecteur de photos manque) ; aucune autorisation ;
  - photo : `MainActivity.takePhoto()`, `TakePicture` vers `cache/camera/photo-<ms>.jpg`, exposé par
    `androidx.core.content.FileProvider` (autorité `com.skohscripts.kairos.files`, `res/xml/file_paths.xml` : `cache/camera/`
    seulement, non exporté) ; aucune permission `CAMERA` ; `clearPhotos()` efface le dossier après la lecture ;
  - presse-papiers : `ClipboardManager.primaryClip` ; champ : `TransferableContent.clipEntry.clipData` ; dans les deux cas,
    les éléments qui désignent une `Uri` d'image ;
  - `fromUris` lit tout de suite (l'accès temporaire ne dure pas), puis `encode` : `BitmapFactory` (dimensions, puis
    `inSampleSize` = `ImageBudget.sampleSize`), orientation par `android.media.ExifInterface(InputStream)` (API 24, sous
    `minSdk` 26) et une `Matrix` par cas EXIF, dessin filtré sur un `Bitmap` blanc à la taille cible, `Bitmap.compress(JPEG)`.
- **Bureau** (`DesktopImages.kt`, sources fichier et presse-papiers, Ctrl/Cmd+V) :
  - fichiers : `FileDialog` d'AWT, choix multiple, filtre par extension (png, jpg, jpeg, webp, gif, bmp) ;
  - presse-papiers : `javaFileListFlavor` (fichiers copiés, ceux qui sont des images) ou `imageFlavor` (image passée en PNG
    par ImageIO avant la réduction commune) ;
  - `encode` par Skia, déjà embarqué par Compose : `Codec.makeFromData`, `readPixels`, `encodedOrigin` ; surface à la taille
    cible effacée en blanc, puis `scale` et `concat(origin.toMatrix(largeur, hauteur))` où largeur et hauteur sont celles de
    l'image **tournée** (`SkEncodedOriginToMatrix`), `drawImageRect` en `FilterMipmap(LINEAR, LINEAR)`, `encodeToData(JPEG, q)`.
- **Web** (`WebImages.kt`, `kairos-web.js`, sources fichier et presse-papiers, Ctrl/Cmd+V) : le navigateur fait tout et rend
  un JSON `{images, unreadable}` ; `MAX_SIDE`, `MAX_BYTES` et `QUALITIES` lui sont passés par Kotlin (une seule source).
  - `imagesPick` : `<input type="file" multiple>` sur les mêmes types ;
  - `imagesPaste` (entrée du menu) : `navigator.clipboard.read()`, que le navigateur peut faire autoriser ; refus → rien ;
  - `imagesTakePasted` (Ctrl/Cmd+V) : les images du dernier événement `paste` (écouté sur `document`, en capture, sans
    autorisation), attendu 400 ms au plus après l'appui de la touche ;
  - `encodeImage` : `createImageBitmap(blob, { imageOrientation: "from-image" })`, `OffscreenCanvas` blanc à la taille cible,
    `convertToBlob({ type: "image/jpeg", quality })`, puis Base64 par `FileReader`.

### Interface (`ui`)

- `NoteImages.kt` :
  - `rememberImage(data)` : `Base64.decode` puis `decodeToImageBitmap()` (Compose Resources), gardé tant que `data` ne change
    pas ; illisible → vignette vide ;
  - `NoteThumbnails(images, onOpen, onRemove?)` : `FlowRow` (passe à la ligne à 360 dp) de vignettes de 72 dp, forme
    `shapes.small` (8 dp), fond `surfaceContainerHigh`, contour `outlineVariant`, `ContentScale.Crop`, description « Ouvrir
    l’image N » ; « Retirer l’image » en `KairosRowIconButton` (48 dp) sur une pastille `surface` à 90 % ;
  - `AddImageButton(sources, enabled, onPick)` : `KairosRowIconButton` « Ajouter une image » (`AddPhotoAlternate`) et un
    `DropdownMenu`, une entrée par source (`PhotoLibrary`, `PhotoCamera`, `Image`, `ContentPaste`) ;
  - `ImageViewer(images, start, onClose)` : `Dialog` (`usePlatformDefaultWidth = false`) sur `scrim`, `HorizontalPager`,
    « Image N sur M » et « Fermer » en `inverseOnSurface`, flèches hors Android (`LocalPlatform`), ←/→ et Échap par
    `onKeyEvent` ; `ZoomableImage` : `detectTapGestures` (double toucher ×2,5 ou retour), `detectTransformGestures`
    (pincement, déplacement seulement une fois zoomé), molette (`PointerEventType.Scroll`), `graphicsLayer`.
- `NotesScreen.kt` :
  - les champs de la capture et de « Modifier » passent à `TextFieldState` (`OutlinedTextField(state = …)`) : le récepteur
    de contenu (`Modifier.contentReceiver`) n'agit que sur ce champ-là ;
  - capture : images en attente (`mutableStateListOf`, en mémoire), vignettes avec « Retirer », `AddImageButton` après
    « Capturer », « Capturer » actif avec un texte ou une image ; `createNote(texte, images)` ;
  - `addImages` demande une place de plus que la place libre, pour savoir s'il a fallu en laisser : alors « 4 images au plus
    par note. » ; une image illisible : « Cette image ne peut pas être lue. » ; presse-papiers sans image (menu) : « Pas
    d’image dans le presse-papiers. » ;
  - `Modifier.imageInput` : `contentReceiver` (Android) qui prend tout contenu portant une image et le confie à `receive` ;
    `onPreviewKeyEvent` Ctrl/Cmd+V (bureau, web) qui lit `pasteFromShortcut` sans consommer l'événement : le texte se colle
    comme avant, et rien n'est dit s'il n'y avait pas d'image ;
  - liste : `NoteRow` montre le texte s'il y en a, puis les vignettes ; « → Tâche » inactif et l'aide `notes_needs_text`
    quand `NoteConversion.fields(corps)` n'a pas de titre ; « Modifier » garde les images (`kept`), en ajoute (`added`),
    `editNote(id, texte, keep, add)` ; « Enregistrer » actif avec un texte ou une image.
- Fiche : `DayScreen` passe à `EditTaskDialog(originImages = …)` les images de la note dont `convertedTaskId` est la tâche ;
  sous la description, un `KairosTextButton` (icône `Image`) « Images de la note d’origine (N) » ouvre `ImageViewer`.
- Textes (six langues, apostrophe `’`) : `note_image_add`, `note_image_gallery`, `note_image_camera`, `note_image_file`,
  `note_image_paste`, `note_image_remove`, `note_image_open`, `note_image_position`, `note_image_previous`,
  `note_image_next`, `note_image_close`, `note_image_limit`, `note_image_unreadable`, `note_image_none`, `notes_needs_text`,
  `task_origin_note`, `share_note_added_limit`, et `share_note_empty` élargi aux images ; carte `card_note_images_*`.
- Icônes `add_photo_alternate`, `photo_library`, `photo_camera`, `content_paste` (et `share`) ajoutées à `make_icons.py`.
- Carte `note-images` (« Premiers pas », après `share`, `since = "4.6.0"`, toutes plateformes) ; puce « Images in notes » /
  « Images dans les notes » dans « Notes (capture) » des deux README, et sa ligne dans le tableau.

### Partage d'images (Android)

- `ShareNoteActivity` gagne deux filtres : `SEND` et `SEND_MULTIPLE` sur `image/*`. `EXTRA_STREAM` est lu par
  `IntentCompat.getParcelableExtra` / `getParcelableArrayListExtra` ; les quatre premières `Uri` passent par
  `AndroidImages.fromUris` **avant** `finish()` (l'accès accordé par l'application d'origine cesse avec l'activité) ; le texte,
  s'il y en a, par `SharedNote.body`. Note créée par `createNote(corps, images)` si elle a un texte ou une image lisible ;
  sinon `share_note_empty`. Plus de quatre images partagées → `ShareTexts.addedLimit()`.

### Export, import et synchronisation

- `ExportCodec` : tableau `noteImages` (`id`, `noteId`, `createdAt`, `mime`, `width`, `height`, `data`, et les champs `uid`,
  `born`, `base`, `stamps` de la synchronisation), nul donc omis s'il n'y en a aucune ; `formatVersion` inchangée : un champ de
  plus que les anciens lecteurs ignorent, comme `interruptions` en 4.5.0. À la lecture (`readableImages`), une image dont la
  note manque, au Base64 illisible ou aux dimensions nulles est ignorée, et une note garde ses quatre premières ; seules les
  images gardées prennent une identité de synchronisation. Identifiants en double refusés, comme ailleurs.
- Synchronisation : `RecordKind.NOTE_IMAGE("noteImage")`, **dernier** genre (le rang ordonne les `RecordRef` ; sa note est
  construite avant lui), toujours dans la portée (`SyncScope.Scoped.noteImages`). `uid` aléatoire. Champs levés : `noteId`
  (référence en `uid`), `mime`, `width`, `height`, `data` ; aucun ne change après la naissance. `buildNoteImage` rend `null`
  pour une image sans sa note (orpheline), sans contenu ou sans dimensions : elle n'est pas écrite, et ses marques tombent.
  `SyncEdit.remove` d'une note (« Supprimer à nouveau ») emporte ses images. La réconciliation compare les images comme les
  autres genres (`same`, référence à la note).
- Le web garde ses données dans l'instantané JSON de l'OPFS (`export-import.md` § Version web) : les images y sont aussi.

### Décisions et pièges tracés

- **Toutes les plateformes** (propriétaire, 2026-11-27) : une note doit être la même partout. Écartés : l'ajout sur Android
  seulement (le bureau, où l'on fait le plus de captures d'écran, en serait privé) ; Android seul (les notes divergeraient).
- **Presse-papiers partout, par le menu et par les gestes de chaque plateforme** (propriétaire, 2026-11-27). Écartés : les
  gestes seuls (rien à toucher sur un téléphone sans clavier qui affiche « Coller ») ; le menu seul (Ctrl+V ne collerait pas).
- **Images réduites, dans la base et dans le fichier lié** (propriétaire, 2026-11-27). Rien ne se perd d'un appareil à
  l'autre, et les sauvegardes sont complètes. Écartés :
  - des images locales non synchronisées : on perdrait ses images en changeant de téléphone ;
  - un dossier d'images à côté de `kairos.json` : il faudrait un dossier lié au lieu d'un fichier, ce que le Storage Access
    Framework et l'API File System Access rendent lourd, et une sauvegarde en plusieurs fichiers.
  Contrepartie : le fichier lié grossit d'environ 270 Ko par image (Base64 compris). Il est réécrit 2 s après chaque
  changement (`export-import.md`). À 50 images, il pèse environ 14 Mo. Le plafond par note et la réduction le contiennent ;
  une limite globale est différée (Hors périmètre). Chaque écriture relit aussi toute la base, images comprises : coût accepté
  pour la beta, à mesurer avant d'optimiser.
- **Base64 dans le modèle et en base** (`TEXT` plutôt que `BLOB`) : l'égalité des instantanés (`data class`) compare alors le
  contenu (un `ByteArray` se compare par référence), et l'export comme la synchronisation, faits à chaque changement, n'ont
  rien à réencoder. Un tiers de place en plus en base, sans effet sur le fichier lié, déjà en Base64.
- **Pas de clé étrangère** : Kairos n'en pose nulle part (références molles) ; `deleteNote` et `replaceRows` font le ménage.
  Écarté : `ON DELETE CASCADE`, qui suppose `PRAGMA foreign_keys` partout, ce que la base ne fait pas.
- **1 600 px et JPEG** : assez pour lire une capture d'écran de téléphone ou une page photographiée, et quatre à dix fois plus
  léger que l'original. Écartés : 1 080 px (texte d'une capture d'écran de bureau illisible) ; 2 048 px (presque deux fois
  plus lourd) ; WebP (encodage inégal selon les plateformes, alors que JPEG s'encode partout sans dépendance) ; garder le PNG
  des captures (souvent plus lourd que le JPEG à 1 600 px).
- **Métadonnées retirées** : une photo porte souvent le lieu de prise de vue, et le fichier lié peut se trouver dans un nuage.
  Réencoder l'image, ce que la réduction impose de toute façon, les supprime sans code de plus.
- **Images immuables** : rien à fusionner champ par champ, donc pas de conflit possible sur une image. Modifier une image
  revient à la retirer puis à en ajouter une autre.
- **Images sur la note, pas sur la tâche** (propriétaire, 2026-11-27) : les tâches n'ont pas de pièce jointe, et en ajouter
  toucherait la fiche, la vue Jour, l'export des tâches et l'espace Équipe. Le lien de la fiche suffit à les retrouver ; il
  ouvre directement la visionneuse plutôt qu'une lecture de la note (le texte de la note est déjà le titre et la
  description de la tâche).
- **« Images de la note d’origine (N) »** plutôt que « Note d’origine · N images » : pas de forme plurielle à tenir dans six
  langues (le russe en a trois), et Kairos n'a aucune ressource `plurals`.
- **Note sans texte autorisée** : une capture d'écran ou un partage d'image n'a souvent pas de texte. Obliger à en écrire un
  freinerait la capture, ce que les Notes doivent éviter (`notes-capture.md`). La conversion, elle, attend un texte : le
  titre d'une tâche ne s'invente pas.
- **`android.media.ExifInterface` plutôt qu'`androidx.exifinterface`** : la version du système lit l'orientation depuis un flux
  dès l'API 24, sous `minSdk` 26 ; la bibliothèque serait une dépendance de plus pour un seul attribut. Android Lint le
  signale (`ExifInterface`, avertissement) : accepté.
- **Sélecteur de photos plutôt qu'une autorisation de stockage** (Android) : aucune permission, et on ne voit que les photos
  choisies. **Appareil photo du système** plutôt que CameraX : aucune permission `CAMERA`, aucune dépendance. L'APK reste
  acceptable par F-Droid (`publication.md`).
- **Skia sur le bureau, le navigateur sur le web** : les deux sont déjà là. Écartés : ImageIO seul (pas de WebP, EXIF à lire
  à la main) ; une bibliothèque d'images en wasm (des centaines de Ko de plus pour ce que le navigateur sait faire). Piège :
  `EncodedOrigin.toMatrix` attend les dimensions de l'image tournée ; celles du fichier la font sortir du cadre (image
  blanche).
- **Récepteur de contenu sur Android seulement** : il prend tout contenu qui porte une image et doit répondre tout de suite ;
  sur le bureau et le web, où la plateforme ne lit pas ce contenu, il avalerait un collage de texte. Ctrl/Cmd+V y lit le
  presse-papiers à côté du collage normal, sans le consommer.
- **Ctrl+V sur le web par l'événement `paste`** plutôt que l'API Clipboard : l'événement ne demande aucune autorisation ;
  l'API, elle, sert l'entrée du menu, qui n'a pas d'événement. L'événement suit l'appui de la touche : on l'attend 400 ms.
- **Pas d'ajout d'image dans la démonstration** (`AppServices.forDemo`, `images = null`) : elle ne prend rien de la plateforme
  (`visite-guidee.md` § Visite interactive) ; une image de démonstration n'aurait de toute façon nulle part où aller.
- **Images en attente non gardées** : si on quitte l'écran Notes avant « Capturer », les images choisies sont perdues, comme
  un texte non capturé.

- **Impacts reportés** (2026-11-27) dans `notes-capture.md`, `modele-donnees.md`, `export-import.md`, `synchronisation.md`,
  `partage-android.md`, `fiche-tache.md`, `distribution.md` § Android, `architecture.md` et l'index.
