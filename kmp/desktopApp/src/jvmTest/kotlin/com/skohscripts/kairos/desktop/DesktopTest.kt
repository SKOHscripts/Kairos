package com.skohscripts.kairos.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataDirectoryTest {
    @Test
    fun followsPlatformdirsOfKairos2PlusV3() {
        assertEquals(
            File("C:\\Users\\a\\AppData\\Local", "Kairos/v3"),
            DataDirectory.resolve(mapOf("LOCALAPPDATA" to "C:\\Users\\a\\AppData\\Local"), "Windows 11", "C:\\Users\\a"),
        )
        assertEquals(
            File("/Users/a/Library/Application Support/Kairos/v3"),
            DataDirectory.resolve(emptyMap(), "Mac OS X", "/Users/a"),
        )
        assertEquals(File("/home/a/.local/share/Kairos/v3"), DataDirectory.resolve(emptyMap(), "Linux", "/home/a"))
        assertEquals(
            File("/data/Kairos/v3"),
            DataDirectory.resolve(mapOf("XDG_DATA_HOME" to "/data"), "Linux", "/home/a"),
        )
    }

    @Test
    fun overrideWins() {
        assertEquals(File("/portable"), DataDirectory.resolve(mapOf("KAIROS_DATA_DIR" to "/portable"), "Linux", "/home/a"))
    }
}

class SingleInstanceTest {
    @Test
    fun secondInstanceIsRefusedAndSignalsTheFirst() {
        val dir = Files.createTempDirectory("kairos-lock").toFile()
        val first = assertNotNull(SingleInstance.acquire(dir))
        try {
            // Même process : FileChannel.tryLock lève OverlappingFileLockException,
            // traitée comme « déjà verrouillé » ; entre deux process, tryLock rend null.
            assertNull(SingleInstance.acquire(dir))
            assertTrue(File(dir, SingleInstance.ACTIVATE_FILE).exists())
        } finally {
            first.release()
        }
        val again = assertNotNull(SingleInstance.acquire(dir))
        again.release()
    }
}

class CrashLogTest {
    @Test
    fun appendsATraceableEntry() {
        val dir = Files.createTempDirectory("kairos-crash").toFile()
        CrashLog.write(dir, "main", IllegalStateException("boom"))
        CrashLog.write(dir, "main", IllegalStateException("encore"))
        val text = File(dir, CrashLog.FILE_NAME).readText()
        assertTrue("boom" in text && "encore" in text)
        assertTrue("Kairos " in text)
    }
}
