# Images dans les notes

_Rôle : une note peut porter jusqu'à quatre images (capture d'écran, photo d'un tableau blanc ou d'un ticket, image partagée),
réduites à l'ajout, visibles et synchronisées sur toutes les plateformes. Fichiers couverts (prévus) :
`kmp/core/src/commonMain/.../core/model/TimeBlock.kt` (`NoteImage`), `core/notes/ImageBudget.kt` (taille cible, pur),
`core/sync/SyncScope.kt` et `SyncRecords.kt` (genre `noteImage`), `kmp/data/.../ExportCodec.kt` (`noteImages`), `17.sqm`,
`Kairos.sq` et `KairosRepository.kt` (`createNote`, `editNote`, `deleteNote`), `kmp/ui/.../app/AppServices.kt` (`ImageService`),
`kmp/ui/.../notes/NotesScreen.kt`, `NoteImages.kt` (vignettes, visionneuse) et la fiche de tâche (`fiche-tache.md`),
`kmp/androidApp/.../AndroidImages.kt`, `ShareNoteActivity.kt` et `AndroidManifest.xml`, `kmp/desktopApp/.../DesktopImages.kt`,
`kmp/webApp/.../WebImages.kt` et `kairos-web.js`, `make_icons.py` et `KairosIcons`, `values*/strings.xml`, `FeatureCatalog.kt`,
`README.md` et `README.fr.md`. Tests (prévus) : `ImageBudgetTest` (core), `NoteImageRepositoryTest` et `SyncRepositoryTest`
(data), `ExportCodecTest` (data), `SnapshotMergeTest` (core), `M4ScreensUiTest` (desktopApp), `DesktopImagesTest` (desktopApp)._

État : issue #295, **spécifiée le 2026-11-27, non implémentée**. Prévue pour la **4.6.0-beta.2**, avec le partage vers Kairos
(propriétaire, 2026-11-27) ; presse-papiers sur toutes les plateformes ajouté le même jour.

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
  - **bureau et web** : Ctrl+V (Cmd+V sur Mac) dans le champ de la note ajoute l'image du presse-papiers ; un texte se colle
    comme avant ;
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
  flèches et ←/→ sur le bureau et le web), zoom (pincer, double toucher ; Ctrl+molette sur le bureau et le web), « Fermer » et
  Échap.
- **Modifier** : le dialogue montre les vignettes de la note, avec « Retirer » sur chacune et « Ajouter une image ».
  « Enregistrer » est actif si la note garde un texte ou au moins une image.
- **Archiver** garde les images. **Supprimer** une note supprime ses images.
- **« → Tâche »** : la tâche reçoit le titre et la description comme avant ; les images restent sur la note, archivée et liée à
  la tâche. La fiche de la tâche montre alors « Note d’origine · 2 images ». Ce lien ouvre la note en lecture (texte et
  vignettes, visionneuse au toucher). Il n'apparaît pas si la note d'origine n'a pas d'image, ou n'existe plus.
- **Partager une image vers Kairos (Android)** : la cible de partage (`partage-android.md`) accepte aussi les images. Partager
  une ou plusieurs images (avec ou sans texte) crée une note qui les porte, sans ouvrir Kairos ; le texte suit les règles du
  partage de texte. Au-delà de quatre images, les quatre premières sont gardées et le message le dit : « Note ajoutée à Kairos
  (4 images au plus). »
- **Les images voyagent avec les données** : l'export, l'import, les sauvegardes et la synchronisation par fichier lié les
  portent. Les autres appareils montrent les mêmes notes, images comprises.
- L'espace Équipe n'en voit rien : les notes sont personnelles, et les fichiers d'échange d'équipe ne portent pas d'image.

### Critères de succès

- Taille cible : une image de 4 000 × 3 000 px devient 1 600 × 1 200 ; une image de 800 × 600 garde sa taille ; une image très
  étirée (1 000 × 20 000) garde son rapport, et son grand côté passe à 1 600 (`ImageBudgetTest`).
- Une photo JPEG d'appareil, tournée par son EXIF, est réduite, remise droite, sans EXIF ; une capture PNG transparente devient
  un JPEG sur fond blanc ; un fichier qui n'est pas une image est refusé (`DesktopImagesTest`). Android et le web, qui ont leur
  propre décodeur, sont vérifiés à la main (voir plus bas).
- Le dépôt refuse une cinquième image, et une note sans texte ni image. Supprimer une note supprime ses images ; l'archiver ou
  la convertir les garde (`NoteImageRepositoryTest`).
- L'export porte `noteImages` et l'import les rend à l'identique ; un ancien export sans `noteImages` se lit ; une image dont
  la note manque est ignorée à l'import (`ExportCodecTest`).
- La fusion de deux fichiers liés réunit les images ajoutées de part et d'autre ; une image retirée d'un côté (qui l'avait vue)
  disparaît partout ; une image dont la note a disparu de la fusion n'est pas gardée (`SnapshotMergeTest`,
  `SyncRepositoryTest`).
- Écran (bureau, hors écran) : capturer une note avec deux images montre deux vignettes ; la visionneuse s'ouvre sur la
  deuxième ; « → Tâche » est inactif sur une note sans texte ; la fiche de la tâche née d'une note à images montre « Note
  d’origine · 2 images » (`M4ScreensUiTest`). À 360 dp, quatre vignettes et la carte de capture tiennent sans débordement
  (captures `--self-test`).
- Vérification manuelle, notée dans la PR : sur Android, galerie, photo, partage d'une et de plusieurs images ; sur le web
  (Chromium), sélecteur et collage ; sur le bureau, collage. Une note à images ajoutée sur un appareil apparaît sur l'autre par
  le fichier lié.

### Hors périmètre / différé

- Images sur les **tâches** (fiche, vue Jour, espace Équipe) : les images restent sur la note d'origine (décision du
  propriétaire, 2026-11-27).
- Autres fichiers (PDF, documents, sons), dessin ou annotation, recadrage, légende d'image, reconnaissance du texte (OCR).
- Glisser-déposer une image sur le bureau et le web.
- Réordonner les images d'une note : elles gardent l'ordre d'ajout.
- Garder l'image d'origine en pleine résolution, ou la ré-enregistrer hors de Kairos (« Enregistrer l'image »).
- Limite globale de l'espace occupé par les images, ou écran « Espace utilisé » : à reprendre si les fichiers liés deviennent
  trop lourds en pratique (voir Décisions).

## 2. Solution technique

### Modèle et taille cible (`core`, pur)

- `NoteImage(id: Long, noteId: Long, createdAt: Instant, mime: String, width: Int, height: Int, data: ByteArray)`. `mime` vaut
  toujours `image/jpeg` à l'écriture ; il est stocké pour qu'un format plus compact (WebP) puisse venir plus tard sans
  migration. Une image est **immuable** : on l'ajoute ou on la retire, jamais on ne la modifie. Ordre = `createdAt`, puis `id`.
- `KairosSnapshot.noteImages: List<NoteImage>`. `Note` ne change pas.
- `NoteLimits.MAX_IMAGES` = 4.
- `ImageBudget.target(width, height): Size` : `MAX_SIDE` = 1 600 ; grand côté au plus `MAX_SIDE`, rapport gardé, arrondi à
  l'entier le plus proche, jamais d'agrandissement, jamais de côté nul. `ImageBudget.QUALITIES` = 85, 75, 65 : on encode en
  JPEG à 85 et on descend tant que le résultat dépasse `MAX_BYTES` = 400 000 octets. À 65, si le résultat dépasse encore, on
  le garde tel quel (une image de 1 600 px à 65 dépasse rarement 400 Ko).

### Stockage (`data`)

- Migration `17.sqm` (schéma 17 → 18) : table `note_image` (`id INTEGER PRIMARY KEY AUTOINCREMENT`, `note_id INTEGER NOT NULL
  REFERENCES note(id) ON DELETE CASCADE`, `created_at TEXT NOT NULL`, `mime TEXT NOT NULL`, `width INTEGER NOT NULL`,
  `height INTEGER NOT NULL`, `data BLOB NOT NULL`), index sur `note_id`. La table est vide après la migration.
- Dépôt :
  - `createNote(body, images: List<EncodedImage> = emptyList())` : refuse un corps vide **et** sans image, et plus de
    `MAX_IMAGES` images ; note et images dans **une** transaction. `EncodedImage` = l'image déjà réduite (`mime`, `width`,
    `height`, `data`), produite par `ImageService`.
  - `editNote(noteId, body, keep: List<Long>, add: List<EncodedImage>)` : garde les images de `keep`, retire les autres,
    ajoute `add`. Refuse le résultat s'il dépasse 4 images, ou s'il n'a ni texte ni image : dans ce cas, la note garde son état.
    Le cas « corps vide garde l'ancien » de `notes-capture.md` devient « ni texte ni image : rien ne change ».
  - `deleteNote` : les images partent par la cascade, dans la même transaction.
  - `convertNote` : inchangé, sauf qu'une note sans titre (sans texte) rend `null`, comme aujourd'hui.

### Réduction des images (plateformes)

- `ImageService` (dans `AppServices`, `ui`) :
  - `suspend fun pick(max: Int): List<ByteArray>` : ouvre le sélecteur de la plateforme ; liste vide si on annule ;
  - `suspend fun encode(bytes: ByteArray): EncodedImage?` : décode, tourne selon l'orientation, réduit à
    `ImageBudget.target`, pose sur fond blanc, encode en JPEG selon `QUALITIES` ; `null` si l'image est illisible. L'encodage
    réécrit l'image : aucune métadonnée ne survit ;
  - `suspend fun paste(): ByteArray?` : l'image du presse-papiers, `null` s'il n'y en a pas (toutes les plateformes) ;
  - `suspend fun fromClip(entry: ClipEntry): List<ByteArray>` : les images d'un contenu reçu par le champ (Android : clavier
    et « Coller » ; liste vide ailleurs).
  `encode` tourne hors du fil principal (`Dispatchers.Default`).
- **Android** (`AndroidImages.kt`) :
  - galerie : `ActivityResultContracts.PickMultipleVisualMedia(4)` avec `ImageOnly`. Le contrat se rabat de lui-même sur
    `ACTION_OPEN_DOCUMENT` là où le sélecteur de photos manque ; aucune autorisation de stockage ;
  - photo : `ActivityResultContracts.TakePicture` vers un fichier de `cacheDir/camera/`, exposé par un `FileProvider`
    (`androidx.core.content.FileProvider`, autorité `com.skohscripts.kairos.files`, chemins `res/xml/file_paths.xml`). Aucune
    permission `CAMERA` : l'application photo du système prend la photo. Le fichier est effacé après l'encodage ;
  - décodage : `BitmapFactory` avec `inSampleSize`, pour ne jamais charger 12 Mpx en mémoire ; orientation lue par
    `android.media.ExifInterface(InputStream)` (API 24, sous `minSdk` 26) ; encodage `Bitmap.compress(JPEG)`.
- **Bureau** (`DesktopImages.kt`) : décodage et encodage par Skia (`org.jetbrains.skia.Codec`, `encodedOrigin` pour
  l'orientation ; `Image.encodeToData(EncodedImageFormat.JPEG, q)`). Skia est déjà embarqué par Compose Desktop : aucune
  dépendance de plus. Sélecteur : `FileDialog` d'AWT avec choix multiple, comme l'import ; presse-papiers :
  `Toolkit.getSystemClipboard()` et `DataFlavor.imageFlavor`.
- **Web** (`WebImages.kt`, `kairos-web.js`) : `<input type="file" accept="image/*" multiple>` ; collage par l'événement
  `paste` (`clipboardData.items` de type `image/*`) ; décodage par `createImageBitmap(blob, { imageOrientation: "from-image" })`,
  réduction et fond blanc sur un `OffscreenCanvas`, `convertToBlob({ type: "image/jpeg", quality })`. Le navigateur sait
  déjà décoder et tourner l'image : rien n'est ajouté au paquet wasm.

### Interface (`ui`)

- `NotesScreen` : état des images en attente dans la carte de capture (`EncodedImage`, en mémoire, perdues si on quitte
  l'écran) ; bouton `IconButton` « Ajouter une image » (`KairosIcons.AddPhotoAlternate`), et sur Android un `DropdownMenu`
  à deux entrées. Sur le bureau et le web, `onPreviewKeyEvent` intercepte Ctrl/Cmd+V : `paste()` donne-t-il une image, elle
  s'ajoute et l'événement est consommé ; sinon, le collage de texte suit son cours.
- `NoteImages.kt` :
  - `NoteThumbnails(images, onOpen, onRemove?)` : `Row` de vignettes 72 dp, forme 8 dp, `ContentScale.Crop`, cible de 48 dp
    au moins pour « Retirer » ;
  - `ImageViewer(images, start, onClose)` : `Dialog` plein écran (`usePlatformDefaultWidth = false`), `HorizontalPager`,
    zoom par `transformable` et double toucher, raccourcis clavier hors Android (`LocalPlatform`) ;
  - décodage par `ByteArray.decodeToImageBitmap()` (Compose Resources), avec un cache des vignettes par identifiant
    (`remember`), pour ne pas redécoder à chaque recomposition.
- Fiche de tâche (`fiche-tache.md`) : une note `convertedTaskId == tâche.id` qui a des images → ligne « Note d’origine ·
  N images » (`TextButton`) dans le groupe de la description. Elle ouvre `NoteReadDialog` (texte et vignettes).
- Couleurs : surfaces et contours neutres seulement (`surfaceContainerHigh` sous les vignettes, `outlineVariant`) ; le fond
  de la visionneuse est `scrim`, la seule surface sombre en thème clair (`DESIGN_SYSTEM.md`). Aucune ombre sur les vignettes.
- Textes (six langues, apostrophe `’`) : `note_image_add`, `note_image_gallery`, `note_image_camera`, `note_image_remove`,
  `note_image_limit` (« 4 images au plus par note. »), `note_image_unreadable`, `note_image_position` (« Image %1$d sur
  %2$d »), `note_image_close`, `note_needs_text` (« Ajoute un texte pour en faire une tâche »), `task_origin_note` (« Note
  d’origine · %1$d images », pluriel par `pluralStringResource`), `share_note_added_limit`.
- Icônes `add_photo_alternate` et `photo_camera` ajoutées à `make_icons.py` ; `ChevronLeft` et `ChevronRight` existent déjà.
- Carte du catalogue `note-images` (chapitre « Premiers pas », après `capture`, `since` = la version retenue, toutes
  plateformes) ; puce « Images in notes » / « Images dans les notes » dans « Notes (capture) » des deux README, et sa ligne
  dans le tableau.

### Partage d'images (Android)

- Le manifeste de `ShareNoteActivity` (`partage-android.md`) gagne deux filtres : `SEND` sur `image/*`, et `SEND_MULTIPLE`
  sur `image/*`.
- `EXTRA_STREAM` (une `Uri`, ou une liste avec `SEND_MULTIPLE`), lu par `contentResolver.openInputStream` **avant**
  `finish()` : l'accès temporaire accordé par l'application d'origine cesse avec l'activité. Les images passent par
  `AndroidImages.encode`, les quatre premières seulement ; le texte, s'il y en a un, par `SharedNote.body`. Note créée si
  elle a un texte ou au moins une image lisible ; sinon le message « rien à ajouter ».
- Message `share_note_added_limit` si des images ont été laissées de côté.

### Export, import et synchronisation

- `ExportCodec` : tableau `noteImages` (`[ { "id", "noteId", "createdAt", "mime", "width", "height", "data" } ]`, `data` en
  Base64 standard) ; tableau absent → aucune image. `formatVersion` reste 2 : c'est un champ de plus que les anciens lecteurs
  ignorent, comme `interruptions` en 4.5.0 (`export-import.md`). Une image dont `noteId` ne désigne aucune note de l'export
  est ignorée, ainsi qu'une image au `data` illisible ; une note avec plus de 4 images garde les 4 premières.
- Synchronisation : nouveau genre `RecordKind.NOTE_IMAGE("noteImage")`, ajouté **en dernier** (le rang du genre ordonne les
  `RecordRef`), toujours dans la portée. `uid` aléatoire (`Uuid.random()` dans `data`) ; la note est désignée par son `uid`.
  Champs fusionnés : aucun en pratique, l'image étant immuable ; l'ajout et le retrait suivent les règles d'existence
  communes (`synchronisation.md` : une suppression qui a vu l'élément se propage).
- **Image orpheline** : après la fusion, une image dont la note n'existe pas dans le résultat n'est pas écrite en base. Si la
  note revient (suppression qui n'avait pas vu une modification, « À revoir »), ses images reviennent avec elle, puisqu'elles
  sont dans le même fichier.
- Le web garde ses données dans l'instantané JSON de l'OPFS (`export-import.md` § Version web) : les images y sont donc aussi,
  en Base64.

### Décisions et pièges tracés

- **Toutes les plateformes** (propriétaire, 2026-11-27) : une note doit être la même partout. Écartés : l'ajout sur Android
  seulement (le bureau, où l'on fait le plus de captures d'écran, en serait privé) ; Android seul (les notes divergeraient).
- **Images réduites, dans la base et dans le fichier lié** (propriétaire, 2026-11-27). Rien ne se perd d'un appareil à
  l'autre, et les sauvegardes sont complètes. Écartés :
  - des images locales non synchronisées : on perdrait ses images en changeant de téléphone ;
  - un dossier d'images à côté de `kairos.json` : il faudrait un dossier lié au lieu d'un fichier, ce que le Storage Access
    Framework et l'API File System Access rendent lourd, et une sauvegarde en plusieurs fichiers.
  Contrepartie : le fichier lié grossit d'environ 270 Ko par image (Base64 compris). Il est réécrit 2 s après chaque
  changement (`export-import.md`). À 50 images, il pèse environ 14 Mo. Le plafond par note et la réduction le contiennent ;
  une limite globale est différée (Hors périmètre).
- **1 600 px et JPEG** : assez pour lire une capture d'écran de téléphone ou une page photographiée, et quatre à dix fois plus
  léger que l'original. Écartés : 1 080 px (texte d'une capture d'écran de bureau illisible) ; 2 048 px (presque deux fois
  plus lourd) ; WebP (encodage inégal selon les plateformes, alors que JPEG s'encode partout sans dépendance) ; garder le PNG
  des captures (souvent plus lourd que le JPEG à 1 600 px).
- **Métadonnées retirées** : une photo porte souvent le lieu de prise de vue, et le fichier lié peut se trouver dans un nuage.
  Réencoder l'image, ce que la réduction impose de toute façon, les supprime sans code de plus.
- **Images immuables** : rien à fusionner champ par champ, donc pas de conflit possible sur une image. Modifier une image
  revient à la retirer puis à en ajouter une autre.
- **Images sur la note, pas sur la tâche** (propriétaire, 2026-11-27) : les tâches n'ont pas de pièce jointe, et en ajouter
  toucherait la fiche, la vue Jour, l'export des tâches et l'espace Équipe. Le lien « Note d’origine » de la fiche suffit à
  les retrouver.
- **Note sans texte autorisée** : une capture d'écran ou un partage d'image n'a souvent pas de texte. Obliger à en écrire un
  freinerait la capture, ce que les Notes doivent éviter (`notes-capture.md`). La conversion, elle, attend un texte : le
  titre d'une tâche ne s'invente pas.
- **Sélecteur de photos plutôt qu'une autorisation de stockage** (Android) : aucune permission, et on ne voit que les photos
  choisies. **Appareil photo du système** plutôt que CameraX : aucune permission `CAMERA`, aucune dépendance. L'APK reste
  acceptable par F-Droid (`publication.md`).
- **Skia sur le bureau, le navigateur sur le web** : les deux sont déjà là. Écartés : ImageIO (pas de WebP, EXIF à lire à la
  main) ; une bibliothèque d'images en wasm (des centaines de Ko de plus pour ce que le navigateur sait faire).
- **Images en attente non gardées** : si on quitte l'écran Notes avant « Capturer », les images choisies sont perdues, comme
  un texte non capturé aujourd'hui.

### Impacts sur les specs existantes (à reporter à l'implémentation)

- `notes-capture.md` : bouton « Ajouter une image », vignettes, visionneuse, note sans texte, « → Tâche » inactif sans texte,
  « Modifier » avec images ; le « corps vide garde l'ancien » devient « ni texte ni image : rien ne change ».
- `modele-donnees.md` : `NoteImage`, `KairosSnapshot.noteImages`, schéma 18 (`17.sqm`), méthodes du dépôt.
- `export-import.md` : `noteImages` dans le format, ignorées si orphelines, `formatVersion` inchangée.
- `synchronisation.md` : genre `noteImage` dans le tableau de portée, images orphelines.
- `partage-android.md` : filtres `image/*` et `SEND_MULTIPLE`, lecture d'`EXTRA_STREAM` ; son « Hors périmètre » (images,
  `SEND_MULTIPLE`) renvoie à cette spec.
- `fiche-tache.md` : la ligne « Note d’origine · N images ».
- `distribution.md` § Android : le `FileProvider`, et toujours aucune permission ajoutée.
- `architecture.md` : `ImageService`, parmi les services fournis par les plateformes.
- `docs/spec/README.md` : la ligne de cette spec, avec sa version.
