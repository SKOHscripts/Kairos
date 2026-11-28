package com.skohscripts.kairos.core.model

import kotlin.time.Instant

/**
 * Une image d'une note (docs/spec/notes-images.md, issue #295), déjà réduite à l'ajout. **Immuable** : on l'ajoute ou on la
 * retire, jamais on ne la modifie (rien à fusionner champ par champ). [data] : le JPEG en Base64 standard, tel qu'il est stocké,
 * exporté et synchronisé ; une chaîne plutôt que des octets, pour que l'égalité des instantanés compare le contenu et que
 * l'export, écrit à chaque changement, ne réencode rien. [mime] vaut toujours `image/jpeg` à l'écriture ; il est stocké pour
 * qu'un autre format puisse venir sans migration. Ordre d'une note : [createdAt], puis [id].
 */
data class NoteImage(
    val id: Long,
    val noteId: Long,
    val createdAt: Instant,
    val mime: String,
    val width: Int,
    val height: Int,
    val data: String,
)
