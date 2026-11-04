package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.sync.Canonical
import com.skohscripts.kairos.core.sync.MergeOutcome
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.sync.SyncData
import com.skohscripts.kairos.core.sync.SyncSummary
import com.skohscripts.kairos.core.sync.Synced
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.MergeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Une passe de synchronisation sur le texte du fichier lié (docs/spec/synchronisation.md § Fichier lié : cycle), commune au
 * bureau, à Android ([LinkedFileSync]) et au web : décoder, demander au premier rapprochement, fusionner, dire s'il faut
 * réécrire. Ne lit ni n'écrit le fichier lui-même : l'appelant le fait, sous son verrou.
 */
class LinkedMerge(
    private val repository: KairosRepository,
    private val clock: Clock,
    /** Nom de la plateforme (« Windows », « Android », « Web ») : le nom de l'appareil sera « Android · 3f9c ». */
    private val platformLabel: String,
    private val backups: BackupStore?,
) {
    /** Lectures illisibles d'affilée : au bout de [MAX_FAILURES], [Step.Unreadable]. */
    private var failures = 0
    private var lastBackup: Instant? = null

    sealed interface Step {
        /**
         * Passe terminée : [write], le texte à écrire (`null` : rien) ; [seenHash], l'empreinte du texte lu, à retenir si rien
         * n'est écrit ; [outcome], la fusion faite (`null` : le fichier n'avait pas changé).
         */
        class Done(val write: String?, val seenHash: String, val outcome: MergeOutcome?) : Step

        /** Premier rapprochement : rien n'est fusionné ni écrit avant la réponse de l'utilisateur. */
        class Confirm(val summary: SyncSummary) : Step

        /** Illisible une fois ou deux : on réessaiera à la passe suivante, sans rien écrire. */
        data object Retry : Step

        class Unreadable(val reason: UnreadableReason) : Step

        /** La sauvegarde exigée avant une fusion qui modifie ou supprime a échoué : rien n'est fusionné. */
        data object BackupFailed : Step
    }

    /** La synchronisation de la base, activée au besoin (premier lien) avec le nom de l'appareil. */
    suspend fun ensureSync(): SyncData {
        repository.sync.value?.let { return it }
        val d = repository.enableSync(platformLabel)
        repository.renameDevice("$platformLabel · ${d.device.take(LABEL_SUFFIX)}")
        return repository.sync.value ?: d
    }

    /** Le texte à écrire : l'export complet, avec l'état de synchronisation (format 3). */
    suspend fun encode(): String {
        ensureSync()
        return ExportCodec.encode(repository.snapshot.value, KairosBuild.VERSION_NAME, clock.now(), repository.sync.value)
    }

    /** Remet le compteur d'illisibilité à zéro (« Réessayer »). */
    fun resetFailures() {
        failures = 0
    }

    /**
     * Une passe sur [text], le contenu actuel du fichier. [knownHash] : l'empreinte du dernier texte écrit ou vu ; [dirty] : des
     * changements locaux attendent ; [confirmed] : l'utilisateur a accepté le premier rapprochement (ou a choisi « Fusionner »
     * à l'ouverture du fichier).
     */
    suspend fun pass(text: String, knownHash: String?, dirty: Boolean, confirmed: Boolean): Step {
        val hash = LinkHash.of(text)
        val d = ensureSync()
        if (hash == knownHash) {
            failures = 0
            return Step.Done(if (dirty) encode() else null, hash, null)
        }
        val theirs = try {
            withContext(Dispatchers.Default) { ExportCodec.decodeSynced(text) }
        } catch (e: ImportException) {
            if (e.reason == ImportException.Reason.TOO_NEW) return Step.Unreadable(UnreadableReason.TOO_NEW)
            failures++
            return if (failures >= MAX_FAILURES) Step.Unreadable(UnreadableReason.NOT_READABLE) else Step.Retry
        }
        failures = 0
        if (!confirmed && firstContact(d, theirs.data)) {
            val ours = Synced(repository.snapshot.value, d)
            val preview = try {
                withContext(Dispatchers.Default) { com.skohscripts.kairos.core.sync.SnapshotMerge.merge(ours, theirs, clock.now().toEpochMilliseconds()) }
            } catch (e: IllegalArgumentException) {
                return Step.Unreadable(UnreadableReason.CLOCK)
            }
            if (preview.writeNeeded || preview.conflicts.isNotEmpty()) {
                return Step.Confirm(withContext(Dispatchers.Default) { SyncSummary.of(ours, theirs) })
            }
        }
        val result = try {
            repository.merge(theirs, ::backup)
        } catch (e: IllegalArgumentException) {
            // `SnapshotMerge.merge` refuse un fichier dont une marque est à plus de 30 jours dans le futur.
            return Step.Unreadable(UnreadableReason.CLOCK)
        }
        val outcome = when (result) {
            is MergeResult.Applied -> result.outcome
            MergeResult.BackupFailed -> return Step.BackupFailed
            MergeResult.NotSynced -> return Step.Retry
        }
        // Réécrire si l'état d'ici (réparations comprises) diffère de celui du fichier : jamais sinon, pas de ping-pong.
        val after = repository.sync.value ?: return Step.Retry
        val differs = withContext(Dispatchers.Default) {
            Canonical.of(repository.snapshot.value, after) != Canonical.of(theirs.snapshot, theirs.data)
        }
        return Step.Done(if (differs || dirty) encode() else null, hash, outcome)
    }

    /**
     * Le contexte de cet appareil ne connaît encore aucun autre appareil (premier rapprochement) et le fichier vient d'ailleurs.
     * Les identités réservées (origine des exemples, fichiers d'avant la synchronisation) ne comptent pas.
     */
    private fun firstContact(d: SyncData, theirs: SyncData): Boolean =
        theirs.device != d.device && d.seen.keys.none { it != d.device && it != Stamp.ORIGIN_DEVICE && it != Stamp.LEGACY_DEVICE }

    /**
     * Sauvegarde avant une fusion qui modifie ou supprime (`avant-fusion-<date>.json`), une par heure au plus : la rotation des
     * sauvegardes (10 fichiers) garderait sinon les seules sauvegardes de fusions rapprochées. Sans magasin (tests), rien.
     */
    private suspend fun backup(before: KairosSnapshot): Boolean {
        val store = backups ?: return true
        val now = clock.now()
        lastBackup?.let { if (now - it < BACKUP_EVERY) return true }
        val ok = runCatching { store.save("avant-fusion-${stamp(now)}.json", ExportCodec.encode(before, KairosBuild.VERSION_NAME, now)) }.isSuccess
        if (ok) lastBackup = now
        return ok
    }

    private fun stamp(now: Instant): String {
        val t = now.toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${t.year}${two(t.month.number)}${two(t.day)}-${two(t.hour)}${two(t.minute)}"
    }

    companion object {
        const val MAX_FAILURES = 3
        val BACKUP_EVERY = 1.hours

        /** Caractères de l'identité ajoutés au nom de la plateforme (« Android · 3f9c »). */
        const val LABEL_SUFFIX = 4
    }
}

/** Pourquoi le fichier lié est tenu pour illisible : texte non décodable, version plus récente, horloge trop en avance. */
enum class UnreadableReason { NOT_READABLE, TOO_NEW, CLOCK }
