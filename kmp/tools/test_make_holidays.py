"""Tests du générateur des fériés (docs/spec/jours-feries.md), sans python-holidays installé."""
import datetime
import unittest

import make_holidays as mh


class MakeHolidaysTest(unittest.TestCase):
    def test_a_year_line_is_sorted_unique_and_of_that_year(self):
        dates = [datetime.date(2026, 12, 25), datetime.date(2026, 1, 1), datetime.date(2026, 1, 1), datetime.date(2027, 1, 1)]
        self.assertEqual("2026 0101 1225", mh.year_line(2026, dates))
        self.assertEqual("2025", mh.year_line(2025, dates))

    def test_render_lists_every_country_in_order(self):
        table = {code: ["2026 0101"] for code in mh.COUNTRIES}
        text = mh.render(table, "python-holidays 9.9")
        self.assertIn('const val SOURCE = "python-holidays 9.9"', text)
        positions = [text.index('"%s" to """' % code) for code in mh.COUNTRIES]
        self.assertEqual(sorted(positions), positions)

    def test_an_unknown_country_is_refused(self):
        table = {code: [] for code in mh.COUNTRIES}
        table["XX"] = []
        with self.assertRaises(ValueError):
            mh.render(table, "python-holidays 1")

    def test_check_ignores_the_source_version_only(self):
        table = {code: ["2026 0101"] for code in mh.COUNTRIES}
        old = mh.render(table, "python-holidays 1.0")
        self.assertEqual(mh.dates_only(old), mh.dates_only(mh.render(table, "python-holidays 2.0")))
        table["FR"] = ["2026 0101 0714"]
        self.assertNotEqual(mh.dates_only(old), mh.dates_only(mh.render(table, "python-holidays 1.0")))


if __name__ == "__main__":
    unittest.main()
