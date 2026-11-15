"""Vérifie le menu du site (docs/spec/publication.md § Menu du site) : chaque page versionnée porte les six entrées, dans
l'ordre, avec la bonne cible et la bonne entrée courante, dans ses deux formes (rangée `nav.menu` et bouton
`details.menu-narrow`). La carte du code, générée, est vérifiée par test_make_code_map.py.

    python3 kmp/tools/validate_menu.py
"""
import html.parser
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2] / "site"
SOURCE = "https://github.com/SKOHscripts/Kairos"
DONATE = "https://skohscripts.github.io/donate.github.io/"
LABELS = {
    "fr": ["Télécharger", "Histoire", "Version web", "Carte du code", "Code source", "Soutenir"],
    "en": ["Download", "Story", "Web version", "Code map", "Source code", "Support"],
    "es": ["Descargar", "Historia (en inglés)", "Versión web", "Mapa del código", "Código fuente", "Apoyar"],
    "de": ["Herunterladen", "Geschichte (auf Englisch)", "Webversion", "Code-Karte", "Quellcode", "Unterstützen"],
    "ru": ["Скачать", "История (на английском)", "Веб-версия", "Карта кода", "Исходный код", "Поддержать"],
    "it": ["Scarica", "Storia (in inglese)", "Versione web", "Mappa del codice", "Codice sorgente", "Sostieni"],
}
# Page → (langue, chemin vers la racine du site, page « histoire », index de l'entrée courante).
PAGES = {
    "index.html": ("fr", "", "histoire/", 0),
    "en/index.html": ("en", "../", "story/", 0),
    "es/index.html": ("es", "../", "../en/story/", 0),
    "de/index.html": ("de", "../", "../en/story/", 0),
    "ru/index.html": ("ru", "../", "../en/story/", 0),
    "it/index.html": ("it", "../", "../en/story/", 0),
    "histoire/index.html": ("fr", "../", "./", 1),
    "en/story/index.html": ("en", "../../", "./", 1),
}


def expected(page):
    lang, up, story, current = PAGES[page]
    download = "./" if current == 0 else "../"
    hrefs = [download, story, up + "@@WEB@@", up + "code/", SOURCE, DONATE]
    return [(label, href, i == current) for i, (label, href) in enumerate(zip(LABELS[lang], hrefs))]


class Menus(html.parser.HTMLParser):
    """Les liens de chaque menu (`nav.menu`, `nav` d'un `details.menu-narrow`) : (libellé, cible, courante)."""

    def __init__(self):
        super().__init__()
        self.menus, self.depth, self.link, self.in_details = [], 0, None, False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "details" and "menu-narrow" in (a.get("class") or "").split():
            self.in_details = True
        if tag == "nav" and ("menu" in (a.get("class") or "").split() or self.in_details):
            self.menus.append([])
            self.depth = 1
        elif tag == "a" and self.depth:
            self.link = [a.get("href"), a.get("aria-current") == "page", ""]

    def handle_data(self, data):
        if self.link is not None:
            self.link[2] += data

    def handle_endtag(self, tag):
        if tag == "a" and self.link is not None:
            href, current, label = self.link
            self.menus[-1].append((label.strip(), href, current))
            self.link = None
        elif tag == "nav" and self.depth:
            self.depth = 0
        elif tag == "details":
            self.in_details = False


def check(root=ROOT):
    errors = []
    for page in PAGES:
        parser = Menus()
        parser.feed((root / page).read_text(encoding="utf-8"))
        if len(parser.menus) != 2:
            errors.append(f"{page} : {len(parser.menus)} menu(s) au lieu de 2 (rangée et bouton)")
            continue
        for menu in parser.menus:
            if menu != expected(page):
                errors.append(f"{page} : menu {menu} au lieu de {expected(page)}")
    return errors


if __name__ == "__main__":
    problems = check()
    for p in problems:
        print(p)
    print("menus : ok" if not problems else f"menus : {len(problems)} erreur(s)")
    sys.exit(1 if problems else 0)
