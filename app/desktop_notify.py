"""Notification système émise par Kairos lui-même, sans passer par le navigateur.

Motivation (issue #34) : les alertes de chrono (`docs/spec/temps-reel-chrono.md`)
reposaient uniquement sur les notifications du navigateur, qui échouent
définitivement dans deux cas courants — permission refusée pour l'origine
(`Notification.permission === "denied"`, aucune redemande possible) et origine
non sécurisée (l'unité systemd écoute sur toutes les interfaces : un accès en
``http://ip-du-poste:8001`` n'expose même pas l'API `Notification`). Or Kairos
tourne le plus souvent **sur la machine de l'utilisateur** : le serveur peut
donc émettre la notification lui-même, en s'adressant directement au système,
sans aucune permission navigateur.

Volontairement **sans dépendance** (ni `plyer`, ni `win10toast`, ni D-Bus en
Python) : uniquement des outils déjà présents sur le système cible, invoqués en
sous-processus — cohérent avec le reste du dépôt (HTML/CSS pur, PyInstaller sans
extension native, voir `docs/spec/packaging-lancement.md`).

- **Linux** : ``notify-send`` (paquet `libnotify`), présent sur la quasi-totalité
  des environnements de bureau. Arguments passés en `argv`, jamais via un shell.
- **Windows** : bulle de notification `System.Windows.Forms.NotifyIcon` via
  PowerShell — Windows 10/11 la rend comme un toast natif. Le titre et le corps
  transitent par des **variables d'environnement**, pas par le texte du script :
  aucun échappement PowerShell à faire, donc aucune injection possible.
- **macOS et tout autre système** : hors périmètre du dépôt
  (`docs/spec/packaging-lancement.md` § Hors périmètre) — `is_available()`
  retourne alors `False` et l'appelant retombe proprement sur son repli in-page.

Android n'appelle **jamais** ce module : l'APK a son pont natif
(`KairosNotificationBridge`), qui reste prioritaire côté client.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys

from .subprocess_env import external_process_env

# Préfixe imposé au titre, côté serveur : une notification émise par Kairos
# s'annonce toujours comme telle et ne peut donc pas être fabriquée pour
# ressembler à un autre logiciel — la route qui appelle ce module est ouverte à
# tout ce qui tourne sur la machine (voir `docs/spec/temps-reel-chrono.md`).
_TITLE_PREFIX = "Kairos · "
# Bornes de longueur : une notification système n'affiche de toute façon que
# quelques lignes, et une chaîne démesurée n'a rien à faire dans un argv.
_MAX_TITLE_CHARS = 120
_MAX_BODY_CHARS = 400

_WINDOWS_TOAST_SCRIPT = (
    "Add-Type -AssemblyName System.Windows.Forms;"
    "$n = New-Object System.Windows.Forms.NotifyIcon;"
    "$n.Icon = [System.Drawing.SystemIcons]::Information;"
    "$n.Visible = $true;"
    "$n.ShowBalloonTip(10000, $env:KAIROS_NOTIFY_TITLE, $env:KAIROS_NOTIFY_BODY,"
    " [System.Windows.Forms.ToolTipIcon]::Info);"
    "Start-Sleep -Seconds 10;"
    "$n.Dispose()"
)


def _powershell_path() -> str | None:
    return shutil.which("powershell") or shutil.which("powershell.exe")


def is_available() -> bool:
    """Un outil de notification utilisable existe-t-il sur ce système ?

    Consulté au rendu de la page (pour annoncer honnêtement l'état des alertes)
    autant qu'avant l'envoi. Ne lance rien : une simple recherche de binaire.
    """
    if sys.platform == "linux":
        return shutil.which("notify-send") is not None
    if sys.platform == "win32":
        return _powershell_path() is not None
    return False


def _clean(text: str, limit: int) -> str:
    """Texte prêt pour un argv : sur une seule ligne, borné, espaces normalisés.

    Les retours à la ligne sont remplacés par des espaces plutôt que supprimés
    (sinon deux mots se colleraient), et non conservés : `notify-send` les
    interpréterait selon le serveur de notifications, la bulle Windows pas du
    tout — un seul comportement, prévisible partout.
    """
    return " ".join(text.split())[:limit]


def send(title: str, body: str) -> bool:
    """Émet une notification système. Retourne `True` si elle a été confiée au
    système, `False` sur tout échec ou système non couvert.

    Best-effort de bout en bout : aucune exception ne remonte à l'appelant —
    une alerte de chrono qui ne sort pas ne doit jamais faire échouer une
    requête ni casser une page (même principe que `launch_app_window`).
    """
    title = _TITLE_PREFIX + _clean(title, _MAX_TITLE_CHARS)
    body = _clean(body, _MAX_BODY_CHARS)

    try:
        if sys.platform == "linux":
            # `--` ferme l'analyse des options : un titre ou un corps commençant
            # par un tiret reste un argument, jamais une option de notify-send.
            subprocess.run(
                ["notify-send", "--app-name=Kairos", "--", title, body],
                env=external_process_env(),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=5,
                check=True,
            )
            return True

        if sys.platform == "win32":
            powershell = _powershell_path()
            if powershell is None:
                return False
            # Le script PowerShell attend 10 secondes que la bulle s'affiche :
            # `Popen` et non `run`, sinon la requête HTTP resterait bloquée
            # pendant tout ce temps. Le texte passe par l'environnement — le
            # script lui-même est une constante, aucun échappement à faire.
            env = external_process_env()
            env["KAIROS_NOTIFY_TITLE"] = title
            env["KAIROS_NOTIFY_BODY"] = body
            subprocess.Popen(
                [powershell, "-NoProfile", "-NonInteractive", "-WindowStyle",
                 "Hidden", "-Command", _WINDOWS_TOAST_SCRIPT],
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            return True
    except Exception:
        # Binaire disparu entre la détection et l'appel, droit refusé, serveur
        # de notifications absent (session sans D-Bus), délai dépassé...
        return False

    return False


def loopback_client(client_host: str | None) -> bool:
    """`client_host` désigne-t-il la machine qui héberge Kairos elle-même ?

    C'est le garde-fou central de la fonctionnalité : une notification système
    s'affiche sur l'écran du **serveur**. Si la page est ouverte depuis un autre
    appareil (téléphone sur le réseau local, poste voisin), l'alerte sortirait
    sur le mauvais écran — donc on refuse, et le client retombe sur son repli
    dans la page.

    Volontairement restreint aux adresses de bouclage littérales, sans
    résolution de nom ni comparaison avec les adresses locales de la machine :
    en cas de doute, on ne notifie pas (le coût d'un faux négatif est un repli
    visuel, celui d'un faux positif une notification sur l'écran de quelqu'un
    d'autre).
    """
    if not client_host:
        return False
    return client_host in {"127.0.0.1", "::1", "localhost", "::ffff:127.0.0.1"}
