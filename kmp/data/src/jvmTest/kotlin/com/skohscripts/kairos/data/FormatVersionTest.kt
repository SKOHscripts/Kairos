package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Règle de numérotation (docs/spec/distribution.md § Quel numéro) : changer le format du fichier d'export ou de
 * synchronisation est une version **majeure**, parce qu'une version plus ancienne refuse ce fichier (`TOO_NEW`) et
 * cesse de le fusionner. Ce test lie chaque version majeure au format qu'elle écrit : monter `SYNC_FORMAT_VERSION` sans
 * monter la majeure (ou l'inverse sans l'ajouter ici) le fait échouer.
 */
class FormatVersionTest {
    /** Version majeure → format de fichier le plus récent qu'elle écrit. */
    private val formatOfMajor = mapOf(3 to ExportCodec.FORMAT_VERSION, 4 to ExportCodec.SYNC_FORMAT_VERSION)

    @Test
    fun theFileFormatChangesOnlyWithTheMajorVersion() {
        val major = AppVersion.current.major
        val expected = formatOfMajor[major] ?: error("version majeure $major : ajoute son format de fichier à formatOfMajor")
        assertEquals(expected, ExportCodec.SYNC_FORMAT_VERSION, "format de fichier changé sans version majeure ($major.x écrit le format $expected)")
    }
}
