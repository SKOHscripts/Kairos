"""Interface (seam) vers TimeTree — calendrier personnel de l'utilisateur.

Isolé du reste de l'app derrière :func:`fetch_busy_slots` : c'est le **seul** point
d'entrée que la route « Kairos » consomme (voir ``app/main.py``). L'intégration
réelle invoque le paquet PyPI ``timetree-exporter`` — **non-officiel, reverse-
engineeré**, qui prévient explicitement d'un risque de panne sans préavis et de
rate-limiting en cas d'appels trop fréquents.

**Contrat** : ``fetch_busy_slots`` ne lève **jamais** d'exception. Toute erreur
(identifiants absents, TimeTree indisponible, subprocess en échec, `.ics`
imparsable) se traduit par ``TimeTreeFetchResult(ok=False, ...)`` avec un
``detail`` lisible, affiché comme avertissement par le dashboard — jamais une
page en erreur 500.

**Cache** : un cache en mémoire (par processus), à la clé (identifiants + plage de
dates), TTL ``settings.timetree_cache_ttl_minutes``. Évite d'invoquer le binaire à
chaque chargement de « Kairos » et respecte ainsi le risque de rate-limiting
signalé par le mainteneur du paquet. Volontairement simple (pas de persistance en
base) : le seam reste sans dépendance à une session SQLAlchemy.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from ..config import Settings


@dataclass
class BusySlot:
    """Créneau occupé importé de TimeTree (avant conversion en `TimeBlock` local).

    ``all_day`` distingue les événements « journée entière » (DTSTART de type date
    dans le `.ics`) : ils n'occupent **pas** d'heures — ils ne doivent ni bloquer
    l'ordonnancement ni remplir la timeline, juste être affichés en puce sur le jour.
    """

    title: str
    start: datetime
    end: datetime
    all_day: bool = False

    def covers(self, day: date) -> bool:
        """Vrai si l'événement couvre ``day`` (DTEND exclusif au sens iCal ; un
        DTEND absent/égal au DTSTART couvre au moins le jour de début)."""
        last = max(self.end.date(), self.start.date() + timedelta(days=1))
        return self.start.date() <= day < last


@dataclass
class TimeTreeFetchResult:
    ok: bool
    blocks: list[BusySlot] = field(default_factory=list)
    detail: str = ""


_cache: dict[tuple, tuple[datetime, TimeTreeFetchResult]] = {}


def fetch_busy_slots(start: date, end: date, *, settings: Settings) -> TimeTreeFetchResult:
    """Récupère les créneaux occupés TimeTree sur ``[start, end]`` (bornes incluses)."""
    if not settings.timetree_configured:
        return TimeTreeFetchResult(
            ok=False, blocks=[], detail="TimeTree non configuré (identifiants absents)."
        )

    cache_key = (settings.timetree_email, settings.timetree_calendar_code, start, end)
    cached = _cache.get(cache_key)
    now = datetime.now(timezone.utc)
    if cached is not None:
        cached_at, cached_result = cached
        if (now - cached_at).total_seconds() < settings.timetree_cache_ttl_minutes * 60:
            return cached_result

    result = _fetch_from_timetree(start, end, settings)
    _cache[cache_key] = (now, result)
    return result


def _resolve_timetree_binary() -> str:
    """Chemin du binaire ``timetree-exporter``, résolu dans le même venv que ce process.

    Le service systemd invoque directement ``.venv/bin/uvicorn`` sans « activer »
    le venv (pas de ``source .venv/bin/activate``) : ``PATH`` ne contient donc pas
    ``.venv/bin``, et une recherche par simple nom échoue avec
    ``FileNotFoundError: [Errno 2] No such file or directory``. On résout donc le
    binaire à côté de l'interpréteur Python courant (même venv que ce process, où
    `pip install -e ".[dev]"` l'a installé), avec un repli sur ``PATH`` pour les
    environnements où le venv est bien activé (ex. lancement manuel en dev).
    """
    sibling = Path(sys.executable).with_name("timetree-exporter")
    if sibling.exists():
        return str(sibling)
    return shutil.which("timetree-exporter") or "timetree-exporter"


def _fetch_from_timetree(start: date, end: date, settings: Settings) -> TimeTreeFetchResult:
    try:
        with tempfile.TemporaryDirectory() as tmp_dir:
            output_path = Path(tmp_dir) / "timetree.ics"
            subprocess.run(
                [
                    _resolve_timetree_binary(),
                    "-o", str(output_path),
                    "-e", settings.timetree_email,
                    "-c", settings.timetree_calendar_code,
                ],
                env={
                    **os.environ,
                    "TIMETREE_EMAIL": settings.timetree_email,
                    "TIMETREE_PASSWORD": settings.timetree_password,
                },
                capture_output=True,
                timeout=30,
                check=True,
            )
            blocks = _parse_ics(output_path, start, end)
    except (subprocess.SubprocessError, OSError, ValueError) as exc:
        return TimeTreeFetchResult(
            ok=False, blocks=[], detail=f"Échec de l'export TimeTree : {exc}"
        )
    return TimeTreeFetchResult(ok=True, blocks=blocks, detail="")


def _parse_ics(path: Path, start: date, end: date) -> list[BusySlot]:
    from icalendar import Calendar

    with open(path, "rb") as f:
        calendar = Calendar.from_ical(f.read())

    blocks: list[BusySlot] = []
    for component in calendar.walk("VEVENT"):
        dtstart = component.get("dtstart")
        dtend = component.get("dtend")
        if dtstart is None or dtend is None:
            continue
        # Événement « journée entière » : DTSTART est une date (pas un datetime).
        all_day = not isinstance(dtstart.dt, datetime)
        start_at = _to_datetime(dtstart.dt)
        end_at = _to_datetime(dtend.dt)
        if end_at.date() < start or start_at.date() > end:
            continue  # événement hors de la plage demandée
        blocks.append(
            BusySlot(title=str(component.get("summary", "")), start=start_at,
                     end=end_at, all_day=all_day)
        )
    return blocks


def _to_datetime(value: date | datetime) -> datetime:
    if isinstance(value, datetime):
        return value.replace(tzinfo=None)
    # Événement « journée entière » (date seule côté .ics) : minuit à minuit.
    return datetime.combine(value, datetime.min.time())
