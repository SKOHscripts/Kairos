package com.skohscripts.kairos.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import kotlin.concurrent.thread

/**
 * Instance unique sur le bureau (docs/spec/distribution.md § Bureau) : un
 * verrou de fichier dans le dossier de données. Une seconde instance ne
 * s'ouvre pas : elle réécrit le fichier `activate`, que l'instance principale
 * surveille pour ramener sa fenêtre au premier plan. Aucun port réseau.
 */
class SingleInstance private constructor(
    private val directory: File,
    private val channel: FileChannel,
    private val lock: FileLock,
) {
    /** Appelle [onActivate] (sur un thread de fond) à chaque relance d'une seconde instance. */
    fun watchActivations(onActivate: () -> Unit) {
        thread(name = "kairos-activation", isDaemon = true) {
            val watcher = FileSystems.getDefault().newWatchService()
            directory.toPath().register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            while (true) {
                val key = watcher.take()
                val touched = key.pollEvents().any { it.context()?.toString() == ACTIVATE_FILE }
                if (touched) onActivate()
                if (!key.reset()) break
            }
        }
    }

    fun release() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        private const val LOCK_FILE = "instance.lock"
        const val ACTIVATE_FILE = "activate"

        /**
         * Prend le verrou, ou signale l'instance déjà ouverte et rend `null`.
         * Le verrou est libéré par le système si le process meurt : jamais de
         * verrou fantôme après un crash.
         */
        fun acquire(directory: File): SingleInstance? {
            directory.mkdirs()
            val channel = RandomAccessFile(File(directory, LOCK_FILE), "rw").channel
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                File(directory, ACTIVATE_FILE).writeText(System.currentTimeMillis().toString())
                return null
            }
            return SingleInstance(directory, channel, lock)
        }
    }
}
