package com.skohscripts.kairos.android

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.skohscripts.kairos.core.connections.CalendarConnection

class ConnectionScheduler(context: Context) {
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private val service = ComponentName(context, SyncJobService::class.java)

    fun apply(connections: List<CalendarConnection>) {
        val minutes = connections.asSequence().filter { it.syncEnabled }
            .mapNotNull { it.interval.minutes }.minOrNull()
        if (minutes == null) {
            scheduler.cancel(JOB_ID)
            return
        }
        val period = minutes.coerceAtLeast(60).toLong() * 60_000
        // Sync metadata changes must not postpone an already scheduled job.
        if (scheduler.getPendingJob(JOB_ID)?.intervalMillis == period) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, service)
                .setPeriodic(period)
                .setPersisted(true)
                // Seulement avec un réseau : hors ligne, un passage compterait comme un échec et repousserait la suivante jusqu'à
                // 6 h (docs/spec/connexions-en-ligne.md § Synchronisation ; ACCESS_NETWORK_STATE, décision du 2026-11-19).
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .build(),
        )
    }

    private companion object {
        const val JOB_ID = 139
    }
}
