package com.skohscripts.kairos.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.provider.Settings
import com.skohscripts.kairos.ui.app.NetworkAccess

/**
 * Réseau bloqué pour Kairos (docs/spec/connexions-en-ligne.md § Interface) : `INTERNET` refusée (l'autorisation « Réseau » de GrapheneOS
 * la rend révocable), ou réseau actif `BLOCKED` pour l'app (règle du système). Aucune permission ajoutée : `ACCESS_NETWORK_STATE` est déjà
 * déclarée. Un pare-feu ou un VPN ne se voit pas d'ici : le texte « peut-être bloqué » couvre ce cas.
 */
class AndroidNetworkAccess(private val context: Context) : NetworkAccess {
    override fun blocked(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        // NetworkInfo is deprecated but is the only synchronous view of a per-app block (DetailedState.BLOCKED).
        @Suppress("DEPRECATION")
        return runCatching { connectivity.activeNetworkInfo?.detailedState == NetworkInfo.DetailedState.BLOCKED }.getOrDefault(false)
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
