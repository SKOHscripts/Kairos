package com.skohscripts.kairos.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Propriétés de la fusion (docs/spec/synchronisation.md § Critères de succès) sur des histoires aléatoires à graine fixe :
 * trois appareils, créations, modifications, suppressions, récurrences terminées, chronos, dépendances, notes, réglages,
 * échangés dans un ordre aléatoire.
 */
class SnapshotMergePropertiesTest {
    private val seeds = 0 until 120

    @Test
    fun convergence() {
        for (seed in seeds) {
            val sim = SyncSim(seed)
            sim.run(60)
            sim.converge()
            val forms = sim.devices.map { it.canonical() }
            if (forms.toSet().size != 1) fail("graine $seed : les appareils n'ont pas convergé")
            // Même contenu visible, appareil par appareil (au-delà de la forme canonique) : mêmes titres de tâches.
            val titles = sim.devices.map { d -> d.inScopeTasks().map { it.title }.sorted() }
            assertEquals(1, titles.toSet().size, "graine $seed")
        }
    }

    @Test
    fun commutativeAssociativeIdempotent() {
        var associativityChecked = 0
        for (seed in seeds) {
            val sim = SyncSim(seed)
            sim.run(45)
            val (a, b, c) = sim.devices.map { it.synced }
            val now = sim.now + 1
            val kept = ArrayList<KeptAfterDeletion>()
            fun m(x: Synced, y: Synced) = SnapshotMerge.merge(x, y, now).also { kept += it.kept }.merged
            fun can(x: Synced) = Canonical.of(x.snapshot, x.data)
            assertEquals(can(m(a, b)), can(m(b, a)), "graine $seed : commutativité")
            // Associativité : vraie tant qu'aucun élément supprimé d'un côté n'est conservé grâce à une modification non vue de
            // l'autre. Dans ce cas, la copie conservée dépend de l'ordre (ses autres champs peuvent être ceux d'avant la
            // suppression) ; la convergence, elle, reste garantie (test convergence).
            val left = can(m(m(a, b), c))
            val right = can(m(a, m(b, c)))
            if (kept.isEmpty()) {
                assertEquals(left, right, "graine $seed : associativité")
                associativityChecked++
            }
            assertEquals(can(a), can(m(a, a)), "graine $seed : idempotence")
            assertEquals(can(m(a, b)), can(m(m(a, b), b)), "graine $seed : réabsorber le même fichier ne change rien")
            val seen = m(a, b).data.seen
            for ((d, h) in a.data.seen + b.data.seen) assertTrue(seen.getValue(d) >= h, "graine $seed : monotonie du contexte")
        }
        assertTrue(associativityChecked >= seeds.count() / 2, "associativité vérifiée sur $associativityChecked graines seulement")
    }

    @Test
    fun mergingTheSameFileTwiceNeedsNoWrite() {
        for (seed in seeds) {
            val sim = SyncSim(seed)
            sim.run(40)
            val (a, b) = sim.devices
            sim.converge()
            val out = SnapshotMerge.merge(a.synced, b.synced, sim.now + 1)
            assertTrue(!out.writeNeeded, "graine $seed : deux appareils convergés n'écrivent plus rien")
            assertTrue(out.changes.isEmpty, "graine $seed")
        }
    }

    @Test
    fun noSilentLoss() {
        for (seed in seeds) {
            val sim = SyncSim(seed)
            sim.run(60)
            sim.converge()
            val final = sim.devices.first().canonical()
            // Aucun élément jamais supprimé ne disparaît (tâches et notes : leur existence ne dépend d'aucun autre).
            for (ref in sim.created) {
                if (ref.kind != RecordKind.TASK && ref.kind != RecordKind.NOTE) continue
                if (ref !in sim.deleted && ref !in final.records) fail("graine $seed : $ref a disparu sans avoir été supprimé")
            }
            // Toute écriture non dépassée par une écriture qui l'a vue est appliquée, ou listée dans un conflit.
            for (w in sim.writes) {
                val rec = final.records[w.ref] ?: continue
                val (value, stamp) = rec.fields[w.field] ?: continue
                if (value == w.value || stamp == w.stamp) continue
                // Dépassée par une écriture qui l'a vue, elle ou une écriture de même valeur (deux valeurs égales fusionnées ne
                // gardent que la plus grande marque : rien n'est perdu, la valeur est la même).
                val sameValue = sim.writes.filter { it.ref == w.ref && it.field == w.field && it.value == w.value }.map { it.stamp }
                val superseder = sim.writes.filter { o -> o.ref == w.ref && o.field == w.field && o.value != w.value && sameValue.any { Context.covers(o.seenAtWrite, it) } }
                if (superseder.isNotEmpty()) continue
                // Supprimé par un appareil qui l'avait vue, puis conservé grâce à une modification que la suppression n'avait
                // pas vue : la copie conservée est celle de l'autre appareil (docs/spec/synchronisation.md § Fusion, conservés).
                if (sim.deletions.any { (ref, seen) -> ref == w.ref && Context.covers(seen, w.stamp) }) continue
                if (w.ref.kind == RecordKind.SESSION && w.field == "endedAt") continue // registre « plus petite fin » : jamais un conflit
                val listed = sim.conflicts.any { it.ref == w.ref && it.field == w.field && it.loser == w.stamp }
                if (!listed) fail("graine $seed : l'écriture ${w.field} de ${w.ref} (${w.value}) a été perdue sans conflit")
            }
        }
    }

    @Test
    fun atMostOneOpenSessionAfterConvergence() {
        for (seed in seeds) {
            val sim = SyncSim(seed)
            sim.run(60)
            sim.converge()
            for (d in sim.devices) assertTrue(d.snapshot.workSessions.count { it.endedAt == null } <= 1, "graine $seed")
        }
    }
}
