package com.skohscripts.kairos.desktop

import java.io.File

/**
 * Installation tenue par un gestionnaire de paquets ou un magasin (docs/spec/distribution.md § Paquet Arch Linux,
 * § Microsoft Store) : le `PKGBUILD` du paquet AUR `kairos-bin` et le paquet MSIX du Microsoft Store (`make_msix.py`)
 * posent [MARKER] à côté des JAR de l'image. Kairos ne cherche alors pas lui-même de nouvelle version
 * (docs/spec/mises-a-jour.md) : pacman ou le Store s'en charge. Les installeurs de la release ne l'écrivent jamais : le
 * même `.deb` installé à la main par `dpkg`, le `.msi` et les copies portables gardent la vérification.
 */
object SystemPackage {
    const val MARKER = "kairos-managed"

    /**
     * Vrai si le lanceur jpackage ([appPath], qu'il résout lui-même par `/proc/self/exe` : `/opt/kairos/bin/Kairos`
     * même lancé par le lien `/usr/bin/kairos` ; sous Windows `…\Kairos.exe`, marqueur dans `app\`) appartient à une
     * image marquée.
     */
    fun detect(
        appPath: String? = System.getProperty("jpackage.app-path"),
        osName: String = System.getProperty("os.name"),
    ): Boolean {
        val launcher = appPath?.takeIf { it.isNotBlank() }?.let(::File) ?: return false
        val (_, jars) = PortableCopy.imageDirs(launcher, osName) ?: return false
        return File(jars, MARKER).isFile
    }
}
