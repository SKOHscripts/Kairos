package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Instant

class ConnectionWatcher(
    private val sync: ConnectionSync,
    private val scope: CoroutineScope,
    private val now: () -> Instant,
) {
    private var job: Job? = null
    fun start(): Job {
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            while (isActive) {
                sync.runDue(now())
                delay(30 * 60 * 1_000L)
            }
        }.also { job = it }
    }
}
