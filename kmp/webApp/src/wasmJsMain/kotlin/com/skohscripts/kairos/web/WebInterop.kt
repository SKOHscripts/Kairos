package com.skohscripts.kairos.web

import kotlin.js.Promise

/**
 * Fonctions navigateur de `kairos-web.js` (chargé par index.html avant
 * kairos.js), exposées sous `globalThis.KairosWeb`.
 */
@OptIn(ExperimentalWasmJsInterop::class)
external object KairosWeb : JsAny {
    fun opfsRead(name: String): Promise<JsString?>
    fun opfsWrite(name: String, text: String): Promise<JsAny?>
    fun opfsBackup(name: String, text: String, keep: Int): Promise<JsAny?>
    fun persist(): Promise<JsBoolean>
    fun download(name: String, text: String)
    fun pickText(): Promise<JsString?>
    fun linkSupported(): Boolean
    fun linkState(): Promise<JsString>
    fun linkCreate(suggestedName: String, text: String): Promise<JsString?>
    fun linkOpen(): Promise<JsString?>
    fun linkAdopt(): Promise<JsBoolean>
    fun linkAuthorize(): Promise<JsBoolean>
    fun linkRead(): Promise<JsString?>
    fun linkWrite(text: String): Promise<JsBoolean>
    fun notifyState(): String
    fun notifyRequest(): Promise<JsString>
    fun notify(title: String, body: String, tag: String): Boolean
    fun setTitle(prefix: String?)
    fun beep()
}
