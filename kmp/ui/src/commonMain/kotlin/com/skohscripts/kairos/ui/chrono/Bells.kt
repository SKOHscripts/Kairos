package com.skohscripts.kairos.ui.chrono

import com.skohscripts.kairos.core.alerts.Bell
import com.skohscripts.kairos.ui.generated.resources.Res

/** Le WAV de [bell], généré par `kmp/tools/make_bells.py` (docs/spec/temps-reel-chrono.md § Cloches), pour le bureau et le web. */
suspend fun bellBytes(bell: Bell): ByteArray = Res.readBytes(bellFile(bell))

/** Chemin de [bell] dans les ressources communes ; Android lit les mêmes octets dans `res/raw`. */
fun bellFile(bell: Bell): String = if (bell == Bell.DOUBLE) "files/bell_double.wav" else "files/bell_single.wav"
