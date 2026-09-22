"""Noms de jours et de mois en français, sans dépendre de la locale système.

`strftime('%A %d %B %Y')` affichait « Tuesday 22 September 2026 » : la locale
d'un processus Python vaut « C » par défaut, et celle du poste n'est ni
garantie (exécutable PyInstaller, APK Android, service systemd) ni souhaitable
à modifier — `locale.setlocale` est global au processus et non sûr entre
threads (Uvicorn sert les routes synchrones dans un pool de threads). Kairos
étant une application francophone, deux tables fixes suffisent.

Exposées aux gabarits comme filtres Jinja (`app/main.py`) : `date_longue` et
`jour_court`.
"""

from __future__ import annotations

from datetime import date

_JOURS = ("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
_JOURS_COURTS = ("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
_MOIS = (
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)


def date_longue(d: date) -> str:
    """« mardi 22 septembre 2026 » — le 1er du mois s'écrit « 1er », usage
    typographique français."""
    quantieme = "1er" if d.day == 1 else str(d.day)
    return f"{_JOURS[d.weekday()]} {quantieme} {_MOIS[d.month - 1]} {d.year}"


def jour_court(d: date) -> str:
    """« mar. 22/09 » — en-tête de colonne de la vue semaine."""
    return f"{_JOURS_COURTS[d.weekday()]} {d.strftime('%d/%m')}"
