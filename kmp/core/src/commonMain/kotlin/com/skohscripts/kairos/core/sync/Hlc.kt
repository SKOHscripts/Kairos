package com.skohscripts.kairos.core.sync

/**
 * Horloge hybride (docs/spec/synchronisation.md § Horloge hybride et marques) : [wall] en millisecondes depuis 1970,
 * [counter] pour départager les écritures d'une même milliseconde. Ordre : [wall] puis [counter]. Pure : l'heure est
 * toujours un paramètre. Une horloge locale en retard ne produit jamais une marque inférieure à une marque déjà vue.
 */
data class Hlc(val wall: Long, val counter: Int) : Comparable<Hlc> {
    init {
        require(wall >= 0) { "horloge négative" }
        require(counter in 0..MAX_COUNTER) { "compteur hors bornes" }
    }

    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, { it.wall }, { it.counter })

    /** Texte `"<wall>.<counter>"`. */
    override fun toString(): String = "$wall.$counter"

    companion object {
        val ZERO = Hlc(0, 0)

        /** Au-delà, l'horloge avance d'une milliseconde plutôt que de déborder. */
        const val MAX_COUNTER = 65_535

        /** Écriture locale : `wall' = max(last.wall, now)`, compteur remis à 0 si `wall'` a avancé, sinon incrémenté. */
        fun tick(last: Hlc, nowMillis: Long): Hlc = advance(maxOf(last.wall, nowMillis), last)

        /** Lecture d'une marque distante : même règle avec `max(last.wall, remote.wall, now)`. */
        fun receive(last: Hlc, remote: Hlc, nowMillis: Long): Hlc {
            val top = if (remote > last) remote else last
            return advance(maxOf(top.wall, nowMillis), top)
        }

        private fun advance(wall: Long, last: Hlc): Hlc = when {
            wall > last.wall -> Hlc(wall, 0)
            last.counter >= MAX_COUNTER -> Hlc(last.wall + 1, 0)
            else -> Hlc(last.wall, last.counter + 1)
        }

        /** Lit `"<wall>.<counter>"` ; `null` si le texte est mal formé. */
        fun parse(text: String): Hlc? {
            val dot = text.indexOf('.')
            if (dot <= 0 || dot == text.lastIndex) return null
            val wall = text.substring(0, dot).toLongStrict() ?: return null
            val counter = text.substring(dot + 1).toLongStrict()?.takeIf { it <= MAX_COUNTER }?.toInt() ?: return null
            return if (wall >= 0) Hlc(wall, counter) else null
        }
    }
}

/**
 * Marque d'une écriture : une horloge et l'appareil qui l'a posée. Ordre total `(wall, counter, device)`. Texte
 * `"<wall>.<counter>@<device>"`. Deux identités réservées : [ORIGIN_DEVICE] (`""`), la marque d'origine des exemples, couverte
 * par tout contexte, et [LEGACY_DEVICE], celle des fichiers d'avant la synchronisation, couverte par aucun.
 */
data class Stamp(val hlc: Hlc, val device: String) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int = compareValuesBy(this, other, { it.hlc }, { it.device })

    override fun toString(): String = "$hlc@$device"

    companion object {
        const val ORIGIN_DEVICE = ""
        const val LEGACY_DEVICE = "legacy"

        /** La marque d'origine (exemples d'une base neuve) : couverte par tout le monde. */
        val ORIGIN = Stamp(Hlc.ZERO, ORIGIN_DEVICE)

        /** Lit `"<wall>.<counter>@<device>"` ; `null` si le texte est mal formé ou l'appareil trop long. */
        fun parse(text: String): Stamp? {
            val at = text.indexOf('@')
            if (at < 0) return null
            val device = text.substring(at + 1)
            if (device.length > MAX_DEVICE) return null
            return Hlc.parse(text.substring(0, at))?.let { Stamp(it, device) }
        }

        /** Longueur maximale d'une identité d'appareil (12 caractères hexadécimaux tirés à l'activation, `legacy`). */
        const val MAX_DEVICE = 32
    }
}

/**
 * Contexte : « j'ai incorporé toutes les écritures de cet appareil jusqu'à cette horloge ». Valide parce que les appareils
 * échangent des **états complets**.
 */
object Context {
    /** Au plus ce nombre d'appareils dans un contexte (fichier refusé au-delà, `ExportCodec`). */
    const val MAX_DEVICES = 32

    /** La marque est couverte par le contexte : marque d'origine, ou appareil connu jusqu'à cette horloge (jamais `legacy`). */
    fun covers(seen: Map<String, Hlc>, stamp: Stamp): Boolean = when (stamp.device) {
        Stamp.ORIGIN_DEVICE -> true
        Stamp.LEGACY_DEVICE -> false
        else -> seen[stamp.device]?.let { it >= stamp.hlc } == true
    }

    /** Maximum point à point de deux contextes. */
    fun join(a: Map<String, Hlc>, b: Map<String, Hlc>): Map<String, Hlc> {
        val out = HashMap(a)
        for ((device, hlc) in b) out[device] = out[device]?.let { if (it >= hlc) it else hlc } ?: hlc
        return out
    }
}

/** Dérive d'horloge entre appareils (docs/spec/synchronisation.md § Horloge hybride). */
object Drift {
    /** Une marque à plus de 24 h dans le futur de l'horloge locale : fusionnée, avec une note dans la carte Données. */
    const val WARN_MILLIS: Long = 24L * 3_600_000

    /** À plus de 30 jours : le fichier est refusé comme illisible (marque corrompue ou horloge absurde). */
    const val MAX_MILLIS: Long = 30L * 86_400_000
}

/** Entier décimal sans signe ni espace (un `+` ou des zéros de tête restent permis, comme `toLong`). */
private fun String.toLongStrict(): Long? = if (isNotEmpty() && all { it in '0'..'9' }) toLongOrNull() else null
