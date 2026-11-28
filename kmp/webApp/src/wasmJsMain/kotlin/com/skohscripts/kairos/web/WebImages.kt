package com.skohscripts.kairos.web

import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.core.notes.ImageBudget
import com.skohscripts.kairos.ui.notes.ImagePick
import com.skohscripts.kairos.ui.notes.ImageService
import com.skohscripts.kairos.ui.notes.ImageSource
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.Promise

/**
 * Images des notes dans le navigateur (docs/spec/notes-images.md § Web) : sélecteur de fichiers, API Clipboard pour l'entrée du
 * menu, événement « paste » pour Ctrl/Cmd+V ; décodage, orientation et réduction par le navigateur (`kairos-web.js`).
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal object WebImages : ImageService {
    override val sources: List<ImageSource> = listOf(ImageSource.FILE, ImageSource.CLIPBOARD)

    override val pastesOnShortcut: Boolean = true

    private val QUALITIES = ImageBudget.QUALITIES.joinToString(",")

    override suspend fun pick(source: ImageSource, max: Int): ImagePick =
        if (source != ImageSource.FILE) ImagePick.NONE else read { KairosWeb.imagesPick(max, ImageBudget.MAX_SIDE, ImageBudget.MAX_BYTES, QUALITIES) }

    override suspend fun paste(max: Int): ImagePick = read { KairosWeb.imagesPaste(max, ImageBudget.MAX_SIDE, ImageBudget.MAX_BYTES, QUALITIES) }

    override suspend fun pasteFromShortcut(max: Int): ImagePick =
        read { KairosWeb.imagesTakePasted(max, ImageBudget.MAX_SIDE, ImageBudget.MAX_BYTES, QUALITIES) }

    private suspend fun read(call: () -> Promise<JsString>): ImagePick = runCatching {
        val root = Json.parseToJsonElement(call().await<JsString>().toString()).jsonObject
        val images = root.getValue("images").jsonArray.map { it.jsonObject.toImage() }
        ImagePick(images, unreadable = root["unreadable"]?.jsonPrimitive?.int ?: 0)
    }.getOrDefault(ImagePick.NONE)

    private fun JsonObject.toImage() = EncodedImage(
        mime = getValue("mime").jsonPrimitive.content,
        width = getValue("width").jsonPrimitive.int,
        height = getValue("height").jsonPrimitive.int,
        data = getValue("data").jsonPrimitive.content,
    )
}
