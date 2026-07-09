"""Interface (seam) vers TimeTree — calendrier personnel de l'utilisateur.

Isolé du reste de l'app derrière :func:`fetch_busy_slots` : c'est le **seul** point
d'entrée que la route « Kairos » consomme (voir ``app/main.py``). L'intégration
réelle appelle directement l'API Python interne du paquet PyPI
``timetree-exporter`` — **non-officiel, reverse-engineeré**, qui prévient
explicitement d'un risque de panne sans préavis et de rate-limiting en cas
d'appels trop fréquents.

Appel **en-process** (pas de `subprocess` vers le CLI du paquet) : nécessaire
pour fonctionner dans un exécutable packagé (PyInstaller), où il n'existe plus
de binaire `timetree-exporter` installé à côté de l'interpréteur.

**Contrat** : ``fetch_busy_slots`` ne lève **jamais** d'exception. Toute erreur
(identifiants absents, TimeTree indisponible, calendrier introuvable) se
traduit par ``TimeTreeFetchResult(ok=False, ...)`` avec un ``detail`` lisible,
affiché comme avertissement par le dashboard — jamais une page en erreur 500.

**Cache** : un cache en mémoire (par processus), à la clé (identifiants + plage de
dates), TTL ``settings.timetree_cache_ttl_minutes``. Évite d'invoquer l'API à
chaque chargement de « Kairos » et respecte ainsi le risque de rate-limiting
signalé par le mainteneur du paquet.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from timetree_exporter.api.auth import login
from timetree_exporter.api.calendar import TimeTreeCalendar
from timetree_exporter.calendar import Calendar
from timetree_exporter.event import TimeTreeEvent, TimeTreeEventCategory, TimeTreeEventType
from timetree_exporter.utils import convert_timestamp_to_datetime

from ..config import Settings


@dataclass
class BusySlot:
    """Créneau occupé importé de TimeTree.

    ``all_day`` distingue les événements « journée entière » : ils n'occupent
    **pas** d'heures — ils ne doivent ni bloquer l'ordonnancement ni remplir la
    timeline, juste être affichés en puce sur le jour.
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


def _fetch_from_timetree(start: date, end: date, settings: Settings) -> TimeTreeFetchResult:
    # Capture large, délibérée : au-delà des exceptions documentées du paquet
    # (échec de connexion, jeton de session absent), `TimeTreeCalendar.get_events`
    # ne lève pas toujours sur une réponse HTTP en échec (elle logue puis tente
    # `response.json()["events"]`, qui peut lever `KeyError`/`ValueError` sur une
    # réponse malformée) — cette frontière avec un paquet non-officiel, non
    # versionné strictement, ne doit jamais faire remonter une page en erreur.
    try:
        session_id = login(settings.timetree_email, settings.timetree_password)
        api = TimeTreeCalendar(session_id, capture_raw_responses=False)
        metadatas = [m for m in api.get_metadata() if m.get("deactivated_at") is None]
        matches = [m for m in metadatas if m.get("alias_code") == settings.timetree_calendar_code]
        if not matches:
            return TimeTreeFetchResult(
                ok=False, blocks=[],
                detail="Calendrier TimeTree introuvable (code invalide ou calendrier désactivé).",
            )
        calendar = Calendar(api, matches[0])
        raw_events = calendar.get_events(include_comments=False)
        blocks = [
            slot for event_data in raw_events
            if (slot := _to_busy_slot(event_data, start, end)) is not None
        ]
    except Exception as exc:
        return TimeTreeFetchResult(ok=False, blocks=[], detail=f"Échec de l'export TimeTree : {exc}")
    return TimeTreeFetchResult(ok=True, blocks=blocks, detail="")


def _to_busy_slot(event_data: dict, range_start: date, range_end: date) -> BusySlot | None:
    event = TimeTreeEvent.from_dict(event_data)
    # Réplique le filtre silencieux qu'appliquait l'ancien export iCal
    # (`ICalEventFormatter.to_ical`) : anniversaires et mémos n'ont jamais été
    # des créneaux occupés, ne pas les faire apparaître soudainement comme tels.
    if event.event_type == TimeTreeEventType.BIRTHDAY or event.category == TimeTreeEventCategory.MEMO:
        return None

    start_at = _event_datetime(event.start_at, event.start_timezone)
    end_at = _event_datetime(event.end_at, event.end_timezone)
    if event.all_day:
        # DTEND exclusif au sens RFC 5545 : le paquet l'appliquait via son
        # formatter iCal (+1 jour), qu'on ne traverse plus ici.
        start_at = datetime.combine(start_at.date(), datetime.min.time())
        end_at = datetime.combine(end_at.date() + timedelta(days=1), datetime.min.time())

    if end_at.date() < range_start or start_at.date() > range_end:
        return None  # événement hors de la plage demandée
    return BusySlot(title=event.title or "", start=start_at, end=end_at, all_day=bool(event.all_day))


def _event_datetime(epoch_ms: int | None, tz_name: str | None) -> datetime:
    """Convertit un timestamp TimeTree (millisecondes epoch) en heure murale
    naïve dans son propre fuseau — même repli que l'ancien `_to_datetime` (issu
    du parsing iCal d'un DTSTART/DTEND qualifié TZID)."""
    aware = convert_timestamp_to_datetime((epoch_ms or 0) / 1000, ZoneInfo(tz_name or "UTC"))
    return aware.replace(tzinfo=None)
