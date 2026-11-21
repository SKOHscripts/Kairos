package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * `runDue` au démarrage puis toutes les 30 minutes (docs/spec/connexions-en-ligne.md § Synchronisation). [active] : faux, le tour est
 * sauté (le web : onglet caché, aucun calcul, comme le fichier lié).
 */
class ConnectionWatcher(
    private val sync: ConnectionSync,
    private val scope: CoroutineScope,
    private val now: () -> Instant,
    private val active: () -> Boolean = { true },
) {
    private var job: Job? = null
    fun start(): Job {
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            while (isActive) {
                if (active()) sync.runDue(now())
                delay(30 * 60 * 1_000L)
            }
        }.also { job = it }
    }
}
