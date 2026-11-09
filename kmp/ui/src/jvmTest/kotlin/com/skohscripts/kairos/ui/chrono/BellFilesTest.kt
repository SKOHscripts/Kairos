package com.skohscripts.kairos.ui.chrono

import com.skohscripts.kairos.core.alerts.Bell
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Cloches (docs/spec/temps-reel-chrono.md § Cloches) : générées par `kmp/tools/make_bells.py`, mêmes octets partout. */
class BellFilesTest {
    @Test
    fun theCommonAndAndroidCopiesAreTheSameWav() {
        Bell.entries.forEach { bell ->
            val common = File("src/commonMain/composeResources/${bellFile(bell)}")
            val android = File("../androidApp/src/main/res/raw/${common.name}")
            assertContentEquals(common.readBytes(), android.readBytes(), "relancer kmp/tools/make_bells.py : ${common.name}")
            val format = AudioSystem.getAudioFileFormat(common).format
            assertEquals(1, format.channels)
            assertEquals(16, format.sampleSizeInBits)
            assertEquals(22_050f, format.sampleRate)
        }
    }
}
