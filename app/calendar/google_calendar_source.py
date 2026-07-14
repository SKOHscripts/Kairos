"""Interface (seam) vers l'API Google Calendar — un ou plusieurs calendriers
d'un même compte Google, connecté via le flux OAuth de ``google_oauth.py``.

Isolé du reste de l'app derrière :func:`fetch_busy_slots` : même contrat que
``calendar/timetree_source.py::fetch_busy_slots`` (seul point d'entrée
consommé par ``app/main.py``), même dataclass ``BusySlot`` réutilisée telle
quelle (pas de duplication).

**Contrat** : ``fetch_busy_slots`` ne lève **jamais** d'exception. Toute
erreur (non configuré, jeton expiré/révoqué, calendrier introuvable, échec
réseau) se traduit par ``GoogleCalendarFetchResult(ok=False, ...)`` avec un
``detail`` lisible, affiché comme avertissement par le dashboard.

**Cache** : cache en mémoire (par processus), à la clé (jeton + calendriers +
plage de dates), TTL ``settings.google_cache_ttl_minutes`` — même patron que
``timetree_source.py``, anti rate-limiting.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone

import httpx

from ..config import Settings
from .google_oauth import refresh_access_token
from .timetree_source import BusySlot

__all__ = ["GoogleCalendarFetchResult", "fetch_busy_slots"]

_EVENTS_URL_TMPL = "https://www.googleapis.com/calendar/v3/calendars/{calendar_id}/events"


@dataclass
class GoogleCalendarFetchResult:
    ok: bool
    blocks: list[BusySlot] = field(default_factory=list)
    detail: str = ""


_cache: dict[tuple, tuple[datetime, GoogleCalendarFetchResult]] = {}


def fetch_busy_slots(start: date, end: date, *, settings: Settings) -> GoogleCalendarFetchResult:
    """Récupère les créneaux occupés Google Calendar sur ``[start, end]`` (bornes incluses),
    tous calendriers de ``settings.google_calendar_id_list`` confondus."""
    if not settings.google_calendar_configured:
        return GoogleCalendarFetchResult(
            ok=False, blocks=[], detail="Google Calendar non configuré.",
        )

    cache_key = (settings.google_refresh_token, tuple(settings.google_calendar_id_list), start, end)
    cached = _cache.get(cache_key)
    now = datetime.now(timezone.utc)
    if cached is not None:
        cached_at, cached_result = cached
        if (now - cached_at).total_seconds() < settings.google_cache_ttl_minutes * 60:
            return cached_result

    result = _fetch_from_google(start, end, settings)
    _cache[cache_key] = (now, result)
    return result


def _fetch_from_google(
    start: date, end: date, settings: Settings, *, client: httpx.Client | None = None
) -> GoogleCalendarFetchResult:
    token_result = refresh_access_token(settings, client=client)
    if not token_result.ok:
        return GoogleCalendarFetchResult(ok=False, blocks=[], detail=token_result.detail)

    # Marge d'un jour de part et d'autre pour couvrir tous les fuseaux horaires
    # possibles d'un événement proche des bornes ; les événements hors plage
    # réelle sont retirés par `_to_busy_slot` ci-dessous.
    time_min = datetime.combine(start - timedelta(days=1), datetime.min.time(), tzinfo=timezone.utc)
    time_max = datetime.combine(end + timedelta(days=1), datetime.min.time(), tzinfo=timezone.utc)
    headers = {"Authorization": f"Bearer {token_result.access_token}"}
    http_client = client or httpx.Client(timeout=10.0)

    blocks: list[BusySlot] = []
    try:
        for calendar_id in settings.google_calendar_id_list:
            page_token: str | None = None
            while True:
                params = {
                    "timeMin": time_min.isoformat(),
                    "timeMax": time_max.isoformat(),
                    "singleEvents": "true",
                    "orderBy": "startTime",
                    "maxResults": 250,
                }
                if page_token:
                    params["pageToken"] = page_token
                response = http_client.get(
                    _EVENTS_URL_TMPL.format(calendar_id=calendar_id), params=params, headers=headers,
                )
                response.raise_for_status()
                payload = response.json()
                blocks.extend(
                    slot for item in payload.get("items", [])
                    if (slot := _to_busy_slot(item, start, end)) is not None
                )
                page_token = payload.get("nextPageToken")
                if not page_token:
                    break
    except httpx.HTTPError as exc:
        return GoogleCalendarFetchResult(ok=False, blocks=[], detail=f"Échec de l'appel à Google Calendar : {exc}")
    except (KeyError, ValueError) as exc:
        return GoogleCalendarFetchResult(ok=False, blocks=[], detail=f"Réponse Google Calendar invalide : {exc}")
    return GoogleCalendarFetchResult(ok=True, blocks=blocks, detail="")


def _to_busy_slot(event: dict, range_start: date, range_end: date) -> BusySlot | None:
    # Un événement annulé (occurrence supprimée d'une série récurrente) ou marqué
    # "disponible" (transparency="transparent") ne doit pas compter comme occupé.
    if event.get("status") == "cancelled" or event.get("transparency") == "transparent":
        return None

    start_data = event.get("start") or {}
    end_data = event.get("end") or {}
    all_day = "date" in start_data
    if all_day:
        # `end.date` est déjà exclusif au sens RFC 5545 côté API Google, comme
        # pour TimeTree — aucun ajustement supplémentaire nécessaire.
        start_at = datetime.combine(date.fromisoformat(start_data["date"]), datetime.min.time())
        end_at = datetime.combine(date.fromisoformat(end_data["date"]), datetime.min.time())
    else:
        start_at = datetime.fromisoformat(start_data["dateTime"]).replace(tzinfo=None)
        end_at = datetime.fromisoformat(end_data["dateTime"]).replace(tzinfo=None)

    if end_at.date() < range_start or start_at.date() > range_end:
        return None  # événement hors de la plage demandée (marge de padding ci-dessus)
    return BusySlot(title=event.get("summary") or "", start=start_at, end=end_at, all_day=all_day)
