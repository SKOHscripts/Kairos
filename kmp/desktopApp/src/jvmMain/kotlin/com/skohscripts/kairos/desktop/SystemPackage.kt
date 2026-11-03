package com.skohscripts.kairos.desktop

import java.io.File

/**
 * Installation tenue par un gestionnaire de paquets (docs/spec/distribution.md § Paquet Arch Linux) : le `PKGBUILD` du
 * paquet AUR `kairos-bin` pose [MARKER] à côté des JAR de l'image. Kairos ne cherche alors pas lui-même de nouvelle
 * version (docs/spec/mises-a-jour.md) : pacman s'en charge. La release n'écrit jamais ce marqueur : le même `.deb`
 * installé à la main par `dpkg` garde la vérification.
 */
object SystemPackage {
    const val MARKER = "kairos-managed"

    /**
     * Vrai si le lanceur jpackage ([appPath], qu'il résout lui-même par `/proc/self/exe` : `/opt/kairos/bin/Kairos`
     * même lancé par le lien `/usr/bin/kairos`) appartient à une image marquée.
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
