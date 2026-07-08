"""Calculs en **jours ouvrés** (lundi–vendredi) pour la capacité de travail.

La capacité de l'équipe ne s'accumule que les jours ouvrés : les projections de fin de
jalon, la capacité d'une fenêtre et le décalage « exploitation » comptent donc
**uniquement les jours ouvrés** (week-ends exclus, et **jours fériés** exclus quand un
calendrier est fourni).

Les jours fériés sont **optionnels** : sans ensemble ``holidays`` fourni, seuls les
week-ends sont sautés (comportement historique, arithmétique inchangée). L'application
peut activer le calendrier **français** (et des dates supplémentaires) via la config -
voir :func:`build_holidays`.
"""

from __future__ import annotations

from datetime import date, timedelta

# Jours ouvrés par semaine : sert à convertir une cadence hebdomadaire (fiches/semaine)
# en cadence par jour ouvré (fiches / jour ouvré).
WORKDAYS_PER_WEEK = 5


def is_workday(day: date, holidays: frozenset[date] | None = None) -> bool:
    """Vrai si le jour est ouvré (lundi=0 … vendredi=4 ; samedi/dimanche et fériés exclus).

    ``holidays`` (optionnel) : ensemble de dates fériées à exclure en plus des week-ends.
    """
    if day.weekday() >= 5:
        return False
    return holidays is None or day not in holidays


def add_business_days(
    start: date, days: int, holidays: frozenset[date] | None = None
) -> date:
    """Date obtenue en ajoutant ``days`` jours ouvrés à ``start`` (week-ends/fériés sautés).

    ``days <= 0`` renvoie ``start`` inchangé. Le résultat tombe toujours un jour ouvré.
    """
    if days <= 0:
        return start
    current = start
    remaining = days
    while remaining > 0:
        current += timedelta(days=1)
        if is_workday(current, holidays):
            remaining -= 1
    return current


def previous_business_day(start: date, holidays: frozenset[date] | None = None) -> date:
    """Jour ouvré précédent ``start`` (recule d'un jour tant que ce n'est pas ouvré).

    Symétrique de :func:`add_business_days` mais en arrière. Utilisé pour les
    échéances calées sur une date fixe (ex. « le 23 du mois ») qui doivent reculer,
    et non avancer, quand elles tombent un jour non ouvré.
    """
    current = start - timedelta(days=1)
    while not is_workday(current, holidays):
        current -= timedelta(days=1)
    return current


def on_or_before_business_day(day: date, holidays: frozenset[date] | None = None) -> date:
    """``day`` s'il est ouvré, sinon le jour ouvré précédent (jamais en avant).

    C'est la règle du décalage « arrière » : une échéance du 23 tombant un dimanche
    (ou un férié) recule au vendredi précédent, elle n'avance jamais au lundi suivant
    (contrairement au snooze, qui lui avance — voir :func:`add_business_days`).
    """
    if is_workday(day, holidays):
        return day
    return previous_business_day(day, holidays)


def business_days_between(
    start: date, end: date, holidays: frozenset[date] | None = None
) -> int:
    """Nombre de jours ouvrés dans l'intervalle ``]start, end]`` (0 si ``end <= start``)."""
    if end <= start:
        return 0
    days = 0
    current = start
    while current < end:
        current += timedelta(days=1)
        if is_workday(current, holidays):
            days += 1
    return days


# --------------------------------------------------------------------------- #
# Jours fériés (calendrier français métropolitain, optionnel)
# --------------------------------------------------------------------------- #

def easter_sunday(year: int) -> date:
    """Dimanche de Pâques (algorithme de Meeus/Butcher, calendrier grégorien)."""
    a = year % 19
    b, c = divmod(year, 100)
    d, e = divmod(b, 4)
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    ll = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * ll) // 451
    month = (h + ll - 7 * m + 114) // 31
    day = ((h + ll - 7 * m + 114) % 31) + 1
    return date(year, month, day)


def french_public_holidays(year: int) -> set[date]:
    """Jours fériés légaux en France **métropolitaine** pour une année donnée.

    Fixes : Jour de l'an, Fête du Travail, Victoire 1945, Fête nationale, Assomption,
    Toussaint, Armistice, Noël. Mobiles (calés sur Pâques) : Lundi de Pâques, Ascension,
    Lundi de Pentecôte. (Les particularités Alsace-Moselle / outre-mer ne sont pas
    incluses ; à ajouter au besoin via ``extra`` de :func:`build_holidays`.)
    """
    easter = easter_sunday(year)
    fixed = {
        date(year, 1, 1),    # Jour de l'an
        date(year, 5, 1),    # Fête du Travail
        date(year, 5, 8),    # Victoire 1945
        date(year, 7, 14),   # Fête nationale
        date(year, 8, 15),   # Assomption
        date(year, 11, 1),   # Toussaint
        date(year, 11, 11),  # Armistice 1918
        date(year, 12, 25),  # Noël
    }
    movable = {
        easter + timedelta(days=1),   # Lundi de Pâques
        easter + timedelta(days=39),  # Ascension
        easter + timedelta(days=50),  # Lundi de Pentecôte
    }
    return fixed | movable


def build_holidays(
    years: range | list[int],
    *,
    france: bool = False,
    extra: list[str | date] | tuple[str | date, ...] = (),
) -> frozenset[date]:
    """Construit l'ensemble des jours fériés à exclure des projections.

    ``years`` : années à couvrir (ex. ``range(2025, 2028)``). ``france`` : ajoute le
    calendrier français de chaque année. ``extra`` : dates supplémentaires (objets
    ``date`` ou chaînes ISO ``AAAA-MM-JJ``), pour des ponts d'équipe ou des fériés locaux.
    Ensemble **vide par défaut** : sans activation, l'arithmétique ne saute que les
    week-ends (comportement historique).
    """
    out: set[date] = set()
    if france:
        for y in years:
            out |= french_public_holidays(y)
    for item in extra:
        if isinstance(item, date):
            out.add(item)
            continue
        text = str(item).strip()
        if not text:
            continue
        try:
            out.add(date.fromisoformat(text[:10]))
        except ValueError:
            pass
    return frozenset(out)
