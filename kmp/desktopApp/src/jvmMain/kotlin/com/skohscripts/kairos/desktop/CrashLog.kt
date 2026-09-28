package com.skohscripts.kairos.desktop

import java.io.File
import java.io.PrintWriter
import java.time.LocalDateTime

/**
 * Journal de crash (docs/spec-v3/distribution.md § Bureau) : une exception non
 * rattrapée est ajoutée à `crash.log` du dossier de données, exploitable sans
 * terminal (l'exécutable n'en a pas).
 */
object CrashLog {
    const val FILE_NAME = "crash.log"

    fun install(directory: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(directory, thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(directory: File, threadName: String, error: Throwable) {
        runCatching {
            directory.mkdirs()
            File(directory, FILE_NAME).appendText(
                buildString {
                    append("=== ${LocalDateTime.now()} [$threadName] ")
                    append("Kairos ${com.skohscripts.kairos.core.KairosBuild.VERSION_NAME}, ")
                    append("${System.getProperty("os.name")} ${System.getProperty("os.arch")}, ")
                    append("Java ${System.getProperty("java.version")}\n")
                    val sw = java.io.StringWriter()
                    error.printStackTrace(PrintWriter(sw))
                    append(sw)
                    append('\n')
                },
            )
        }
    }
}
