package com.skohscripts.kairos.ui.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NetworkIsolationTest {
    private val root = File("..").canonicalFile
    private val commonNetworkFiles = setOf("OnlineGate.kt", "ConnectionSync.kt", "DefaultOnlineConnections.kt")
    private val network = Regex("""\b(java\.net|HttpURLConnection|HttpClient)\b""")
    private val fetchCall = Regex("""\b(?:http|fetcher)\.fetch\s*\(""")

    @Test fun calendarNetworkStaysAtThePlatformBoundary() {
        val historical = "desktopApp/src/jvmMain/kotlin/com/skohscripts/kairos/desktop/DesktopUpdates.kt"
        assertTrue(File(root, historical).isFile, "Historical desktop update path must remain explicit")
        val violations = mutableListOf<String>()
        for (module in listOf("core", "data", "ui", "androidApp", "desktopApp", "webApp")) {
            val sources = File(root, "$module/src")
            assertTrue(sources.isDirectory, "Missing source module: $module")
            for (file in sources.walkTopDown().filter { it.isFile && it.extension == "kt" && (it.path.contains("Main/") || it.path.contains("Main\\")) }) {
                val path = file.relativeTo(root).invariantSeparatorsPath
                val commonAllowed = path.startsWith("ui/src/commonMain/kotlin/com/skohscripts/kairos/ui/app/") && file.name in commonNetworkFiles
                val platformAllowed = module in setOf("androidApp", "desktopApp", "webApp") && file.name.endsWith("Http.kt")
                if (path == historical || commonAllowed || platformAllowed) continue
                for ((index, raw) in file.readLines().withIndex()) {
                    val line = raw.trimStart()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue
                    if (network.containsMatchIn(line) || fetchCall.containsMatchIn(line)) violations += "$path:${index + 1}"
                }
            }
        }
        assertTrue(violations.isEmpty(), "Network outside its boundary: ${violations.joinToString()}")
    }
}
