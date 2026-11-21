"""Génère `core/.../engine/HolidayTable.kt` : les jours fériés nationaux des pays de Kairos (docs/spec/jours-feries.md).

Source : python-holidays (licence MIT), lue ici et dans la CI, jamais par l'application. Une ligne par année,
`AAAA MMJJ MMJJ …`, de FIRST_YEAR à LAST_YEAR. La table est recalculée à chaque version : le workflow « Kairos tag »
lance `--check` et propose la mise à jour si les dates ont changé.

    pip install holidays
    python3 kmp/tools/make_holidays.py            # écrit la table
    python3 kmp/tools/make_holidays.py --check    # 0 : à jour ; 1 : périmée
"""
import pathlib
import sys

# Les pays des six langues de Kairos (issue #264), codes ISO 3166-1 alpha-2, dans l'ordre du fichier.
COUNTRIES = ["AT", "BE", "CA", "CH", "DE", "ES", "FR", "GB", "IE", "IT", "LU", "RU", "US"]
FIRST_YEAR = 2000
LAST_YEAR = 2050
OUT = (pathlib.Path(__file__).resolve().parent.parent
       / "core/src/commonMain/kotlin/com/skohscripts/kairos/core/engine/HolidayTable.kt")


def year_line(year, dates):
    """`2026 0101 0406` : l'année puis ses dates `MMJJ`, triées et sans doublon."""
    days = sorted({(d.month, d.day) for d in dates if d.year == year})
    return " ".join([str(year)] + ["%02d%02d" % day for day in days])


def render(table, source):
    """Le fichier Kotlin : [table] associe chaque pays à ses lignes d'années ; [source] nomme la version de python-holidays."""
    unknown = sorted(set(table) - set(COUNTRIES))
    if unknown:
        raise ValueError("pays inconnus : %s" % ", ".join(unknown))
    out = [
        "// GÉNÉRÉ par kmp/tools/make_holidays.py (%s, années %d-%d) : ne pas éditer." % (source, FIRST_YEAR, LAST_YEAR),
        "package com.skohscripts.kairos.core.engine",
        "",
        "/** Fériés nationaux par pays (docs/spec/jours-feries.md) : une ligne par année, « AAAA MMJJ MMJJ … ». */",
        "internal object HolidayTable {",
        '    const val SOURCE = "%s"' % source,
        "    const val FIRST_YEAR = %d" % FIRST_YEAR,
        "    const val LAST_YEAR = %d" % LAST_YEAR,
        "",
        "    val DATA: Map<String, String> = mapOf(",
    ]
    for code in COUNTRIES:
        out.append('        "%s" to """' % code)
        out.extend(table[code])
        out.append('""",')
    out += ["    )", "}", ""]
    return "\n".join(out)


def dates_only(text):
    """Le contenu comparé par `--check` : tout sauf la ligne d'en-tête et `SOURCE`, qui nomment la version."""
    return [line for line in text.splitlines()
            if not line.startswith("// GÉNÉRÉ") and "const val SOURCE" not in line]


def build():
    import holidays  # au développement et dans la CI seulement

    table = {}
    for code in COUNTRIES:
        found = holidays.country_holidays(code, years=range(FIRST_YEAR, LAST_YEAR + 1))
        table[code] = [year_line(year, found.keys()) for year in range(FIRST_YEAR, LAST_YEAR + 1)]
    return render(table, "python-holidays %s" % holidays.__version__)


def main(argv):
    text = build()
    if "--check" in argv:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if dates_only(current) == dates_only(text):
            print("Table des fériés à jour.")
            return 0
        print("Table des fériés périmée : lancer python3 kmp/tools/make_holidays.py.")
        return 1
    OUT.write_text(text, encoding="utf-8")
    print("Écrit %s" % OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
