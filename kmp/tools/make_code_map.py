"""Carte du code de Kairos (docs/spec/carte-du-code.md) : paquets, fichiers, déclarations, dépendances et specs.

Une seule source, deux sorties :

    python3 kmp/tools/make_code_map.py --index [filtre]      # Markdown compact pour Claude Code (sortie standard)
    python3 kmp/tools/make_code_map.py --html site/code/index.html --stamp "$GITHUB_SHA"   # page publique
    python3 kmp/tools/make_code_map.py --json carte.json     # données brutes

Bibliothèque standard seulement ; lecture par expressions régulières, sans compilateur (§ Décisions de la spec).
"""
from __future__ import annotations

import argparse
import html
import json
import pathlib
import re
import sys
from dataclasses import dataclass, field

ROOT_PACKAGE = "com.skohscripts.kairos."
# Ordre des colonnes du graphe : celui des dépendances (docs/spec/architecture.md § Modules).
MODULES = ["core", "data", "ui", "androidApp", "desktopApp", "webApp"]
GITHUB = "https://github.com/SKOHscripts/Kairos/blob/main/"

PACKAGE_RE = re.compile(r"^package\s+([\w.]+)")
IMPORT_RE = re.compile(r"^import\s+([\w.]+)")
# Déclaration de premier niveau (colonne 0) : modificateurs, puis le genre et le nom. Les annotations sur leur propre ligne
# ne gênent pas ; une déclaration `private` n'est pas retenue.
DECL_RE = re.compile(
    r"^((?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|value|inline|expect|actual|const|annotation|"
    r"operator|infix|suspend|tailrec|external|lateinit|override|fun)\s+)*)"
    r"(class|interface|object|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?([A-Za-z_]\w*)"
)
SPEC_TOKEN_RE = re.compile(r"`([^`\s]*?\.kt)`")


@dataclass
class SourceFile:
    path: str  # relatif au dépôt, en « / »
    module: str
    source_set: str
    package: str  # nom court, sans ROOT_PACKAGE
    lines: int
    decls: list[str] = field(default_factory=list)  # « class Tips », « fun due »…
    imports: set[str] = field(default_factory=set)  # paquets importés (noms courts, connus)
    specs: list[str] = field(default_factory=list)  # noms de fichiers de docs/spec/

    @property
    def name(self) -> str:
        return self.path.rsplit("/", 1)[-1]


@dataclass
class Package:
    name: str
    module: str
    source_sets: list[str]
    files: list[SourceFile]
    deps: list[str]  # paquets dont il dépend (hors lui-même)
    specs: list[str]


def short(package: str) -> str:
    return package[len(ROOT_PACKAGE):] if package.startswith(ROOT_PACKAGE) else package


def read_sources(root: pathlib.Path) -> list[SourceFile]:
    """Les fichiers Kotlin de production des modules de `kmp/`, triés par chemin."""
    files: list[SourceFile] = []
    for module in MODULES:
        src = root / "kmp" / module / "src"
        if not src.is_dir():
            continue
        for set_dir in sorted(p for p in src.iterdir() if p.is_dir() and not p.name.endswith("Test")):
            for kt in sorted((set_dir / "kotlin").rglob("*.kt")):
                text = kt.read_text(encoding="utf-8")
                package, decls, imports = "", [], set()
                for line in text.splitlines():
                    if not package and (m := PACKAGE_RE.match(line)):
                        package = m.group(1)
                    elif m := IMPORT_RE.match(line):
                        if m.group(1).startswith(ROOT_PACKAGE):
                            imports.add(m.group(1))
                    elif m := DECL_RE.match(line):
                        modifiers, kind, name = m.group(1).split(), m.group(2), m.group(3)
                        if "private" in modifiers:
                            continue
                        label = " ".join([w for w in modifiers if w in ("data", "sealed", "enum", "abstract", "value")] + [kind, name])
                        if label not in decls:
                            decls.append(label)
                files.append(SourceFile(
                    path=kt.relative_to(root).as_posix(), module=module, source_set=set_dir.name,
                    package=short(package), lines=text.count("\n") + (0 if text.endswith("\n") or not text else 1),
                    decls=decls, imports=imports,
                ))
    known = sorted({f.package for f in files}, key=len, reverse=True)
    for f in files:
        resolved = set()
        for imp in f.imports:
            target = short(imp)
            match = next((p for p in known if target == p or target.startswith(p + ".")), None)
            if match is not None:
                resolved.add(match)
        f.imports = resolved
    return files


def read_specs(root: pathlib.Path) -> dict[str, tuple[str, list[str]]]:
    """docs/spec/*.md → (titre, jetons `….kt` cités, dans l'ordre, sans doublon)."""
    specs = {}
    spec_dir = root / "docs" / "spec"
    for md in sorted(spec_dir.glob("*.md")) if spec_dir.is_dir() else []:
        text = md.read_text(encoding="utf-8")
        title = next((line[2:].strip() for line in text.splitlines() if line.startswith("# ")), md.stem)
        tokens = []
        for token in SPEC_TOKEN_RE.findall(text):
            token = re.split(r"\.\.\.|…", token)[-1].lstrip("/")
            if token and token not in tokens:
                tokens.append(token)
        specs[md.name] = (title, tokens)
    return specs


def link_specs(files: list[SourceFile], specs: dict[str, tuple[str, list[str]]]) -> None:
    """Relie chaque fichier aux specs qui le citent : chemin qui se termine par le jeton, ou même nom de fichier."""
    for f in files:
        f.specs = [name for name, (_, tokens) in specs.items()
                   if any(f.path.endswith("/" + t) or f.path == t for t in tokens)]


def build(root: pathlib.Path) -> tuple[list[Package], dict[str, tuple[str, list[str]]]]:
    files = read_sources(root)
    specs = read_specs(root)
    link_specs(files, specs)
    packages: dict[tuple[str, str], list[SourceFile]] = {}
    for f in files:
        packages.setdefault((f.module, f.package), []).append(f)
    result = []
    for (module, name), members in packages.items():
        deps = sorted({d for f in members for d in f.imports} - {name})
        pkg_specs = sorted({s for f in members for s in f.specs})
        sets = sorted({f.source_set for f in members})
        result.append(Package(name, module, sets, sorted(members, key=lambda f: f.path), deps, pkg_specs))
    result.sort(key=lambda p: (MODULES.index(p.module), p.name))
    return result, specs


# ---- Index pour Claude Code -------------------------------------------------------------------------------------------

def index(packages: list[Package], query: str | None = None) -> str:
    """Markdown compact, un paquet par section ; [query] filtre sur le nom du paquet ou le chemin d'un fichier."""
    out = []
    shown = [p for p in packages if not query or query in p.name or any(query in f.path for f in p.files)]
    total = sum(len(p.files) for p in shown)
    out.append(f"# Carte du code ({len(shown)} paquets, {total} fichiers)" + (f" · filtre « {query} »" if query else ""))
    for p in shown:
        lines = sum(f.lines for f in p.files)
        out.append("")
        out.append(f"## {p.name} ({p.module} · {', '.join(p.source_sets)}) · {len(p.files)} fichiers, {lines} lignes")
        if p.deps:
            out.append(f"Dépend de : {', '.join(p.deps)}")
        out.append(f"Specs : {', '.join(p.specs) if p.specs else '(aucune)'}")
        for f in p.files:
            if query and query not in p.name and query not in f.path:
                continue
            decls = ", ".join(f.decls[:8]) + (" …" if len(f.decls) > 8 else "")
            spec = f" · specs {', '.join(f.specs)}" if f.specs else " · sans spec"
            out.append(f"- `{f.path}` ({f.lines}) {decls}{spec}")
    return "\n".join(out) + "\n"


# ---- Données et page -------------------------------------------------------------------------------------------------

def data(packages: list[Package], specs: dict[str, tuple[str, list[str]]], stamp: str | None) -> dict:
    return {
        "stamp": stamp,
        "modules": [m for m in MODULES if any(p.module == m for p in packages)],
        "specs": {name: title for name, (title, _) in specs.items()},
        "packages": [
            {
                "id": f"{p.module}:{p.name}", "name": p.name, "module": p.module, "sets": p.source_sets,
                "deps": [f"{q.module}:{q.name}" for d in p.deps for q in packages if q.name == d],
                "specs": p.specs,
                "files": [{"path": f.path, "lines": f.lines, "decls": f.decls, "specs": f.specs} for f in p.files],
            }
            for p in packages
        ],
    }


BOX_W, BOX_H, GAP_Y, COL_W, PAD = 200, 26, 8, 260, 20


def layout(model: dict) -> tuple[dict[str, tuple[int, int]], int, int]:
    """Position (x, y) de chaque paquet : une colonne par module, les paquets triés par nom."""
    pos, height = {}, 0
    for c, module in enumerate(model["modules"]):
        column = [p for p in model["packages"] if p["module"] == module]
        for r, p in enumerate(column):
            pos[p["id"]] = (PAD + c * COL_W, PAD + 30 + r * (BOX_H + GAP_Y))
        height = max(height, PAD + 30 + len(column) * (BOX_H + GAP_Y))
    width = PAD * 2 + (len(model["modules"]) - 1) * COL_W + BOX_W
    return pos, width, height + PAD


def svg(model: dict) -> str:
    pos, width, height = layout(model)
    parts = [f'<svg id="graph" viewBox="0 0 {width} {height}" width="{width}" height="{height}" role="img" '
             f'aria-label="Package dependency graph">']
    for c, module in enumerate(model["modules"]):
        parts.append(f'<text class="col" x="{PAD + c * COL_W}" y="{PAD + 12}">{html.escape(module)}</text>')
    for p in model["packages"]:
        x1, y1 = pos[p["id"]]
        for d in p["deps"]:
            x2, y2 = pos[d]
            a, b = y1 + BOX_H / 2, y2 + BOX_H / 2
            if x2 < x1:  # vers un module à gauche : du bord gauche de la source au bord droit de la cible
                sx, ex = x1, x2 + BOX_W
                mid = (sx + ex) / 2
                path = f"M{sx},{a} C{mid},{a} {mid},{b} {ex},{b}"
            else:  # même colonne (ou à droite) : un arc par la droite
                sx, ex = x1 + BOX_W, x2 + BOX_W
                # Borné à l'écart entre deux colonnes : l'arc ne déborde pas sur la colonne suivante.
                bulge = max(sx, ex) + min(COL_W - BOX_W - 8, 16 + abs(b - a) / 12)
                path = f"M{sx},{a} C{bulge},{a} {bulge},{b} {ex},{b}"
            parts.append(f'<path class="edge" data-from="{p["id"]}" data-to="{d}" d="{path}"/>')
    for p in model["packages"]:
        x, y = pos[p["id"]]
        label = p["name"] if len(p["name"]) <= 26 else "…" + p["name"][-25:]
        parts.append(
            f'<g class="node" data-id="{p["id"]}" tabindex="0" role="button" aria-label="{html.escape(p["name"])}">'
            f'<rect x="{x}" y="{y}" width="{BOX_W}" height="{BOX_H}" rx="8"/>'
            f'<text x="{x + 10}" y="{y + 17}">{html.escape(label)}</text></g>'
        )
    parts.append("</svg>")
    return "".join(parts)


def package_list(model: dict) -> str:
    out = []
    for p in model["packages"]:
        lines = sum(f["lines"] for f in p["files"])
        specs = " ".join(f'<a href="{GITHUB}docs/spec/{s}">{html.escape(model["specs"].get(s, s))}</a>' for s in p["specs"])
        deps = ", ".join(html.escape(d.split(":", 1)[1]) for d in p["deps"])
        files = []
        for f in p["files"]:
            fspecs = ", ".join(f'<a href="{GITHUB}docs/spec/{s}">{html.escape(s)}</a>' for s in f["specs"]) or '<span class="none">no spec</span>'
            decls = html.escape(", ".join(f["decls"]))
            files.append(
                f'<li data-text="{html.escape((f["path"] + " " + " ".join(f["decls"])).lower())}">'
                f'<a href="{GITHUB}{f["path"]}">{html.escape(f["path"].rsplit("/", 1)[-1])}</a> '
                f'<span class="meta">{f["lines"]} lines</span><div class="decls">{decls}</div><div class="fspecs">{fspecs}</div></li>'
            )
        out.append(
            f'<details class="pkg" id="{html.escape(p["id"])}" data-id="{html.escape(p["id"])}" '
            f'data-text="{html.escape(p["name"].lower())}"><summary><strong>{html.escape(p["name"])}</strong> '
            f'<span class="meta">{html.escape(p["module"])} · {len(p["files"])} files · {lines} lines</span></summary>'
            + (f'<p class="row"><span class="k">Depends on</span> {deps}</p>' if deps else "")
            + f'<p class="row"><span class="k">Specs</span> {specs or "<span class=none>none</span>"}</p>'
            + f'<ul>{"".join(files)}</ul></details>'
        )
    return "".join(out)


PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Kairos code map</title>
<meta name="description" content="How the Kairos code is organised: modules, packages, files, dependencies and the specs that describe them.">
<style>
@font-face { font-family: Roboto; src: url("../fonts/roboto_regular.ttf") format("truetype"); font-weight: 400; font-display: swap; }
@font-face { font-family: Roboto; src: url("../fonts/roboto_medium.ttf") format("truetype"); font-weight: 500; font-display: swap; }
@font-face { font-family: Roboto; src: url("../fonts/roboto_bold.ttf") format("truetype"); font-weight: 700; font-display: swap; }
:root { --bg: #FAF8FF; --surface: #FFFFFF; --text: #1A1B21; --muted: #44464F; --line: #C5C6D0; --edge: #C5C6D0;
  --primary: #485D92; --on-primary: #FFFFFF; --soft: #DAE2FF; color-scheme: light dark; }
@media (prefers-color-scheme: dark) { :root { --bg: #121318; --surface: #1A1B21; --text: #E3E2E9; --muted: #C5C6D0;
  --line: #44464F; --edge: #44464F; --primary: #B1C5FF; --on-primary: #182E60; --soft: #304578; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.5 Roboto, system-ui, sans-serif; }
main { max-width: 1200px; margin: 0 auto; padding: 24px 16px 48px; }
h1 { font-size: 28px; font-weight: 500; margin: 0 0 8px; }
p.lead { color: var(--muted); margin: 0 0 16px; max-width: 760px; }
a { color: var(--primary); }
input[type=search] { width: 100%; max-width: 480px; font: inherit; padding: 10px 12px; border: 1px solid var(--line);
  border-radius: 4px; background: var(--surface); color: var(--text); min-height: 48px; }
.scroll { overflow-x: auto; border: 1px solid var(--line); border-radius: 12px; background: var(--surface); margin: 16px 0 24px; }
svg text { font: 12px Roboto, system-ui, sans-serif; fill: var(--text); }
svg text.col { font-weight: 700; fill: var(--muted); }
.node { cursor: pointer; }
.node rect { fill: var(--surface); stroke: var(--line); }
.node:focus { outline: none; }
.node:focus rect, .node:hover rect { stroke: var(--primary); }
.edge { fill: none; stroke: var(--edge); stroke-width: 1; }
svg.active .node, svg.active .edge { opacity: 0.18; }
svg.active .node.on, svg.active .edge.on { opacity: 1; }
svg.active .edge.on { stroke: var(--primary); stroke-width: 2; }
svg.active .node.self rect { fill: var(--primary); stroke: var(--primary); }
svg.active .node.self text { fill: var(--on-primary); }
svg .node.hidden { opacity: 0.18; }
.pkg { border: 1px solid var(--line); border-radius: 12px; background: var(--surface); margin: 8px 0; padding: 0 12px; }
.pkg[open] { padding-bottom: 8px; }
.pkg.target { border-color: var(--primary); }
summary { cursor: pointer; padding: 12px 0; min-height: 48px; }
.meta, .none { color: var(--muted); }
.row { margin: 4px 0; }
.k { font-weight: 500; margin-right: 6px; }
ul { list-style: none; padding: 0; margin: 8px 0 0; }
li { padding: 6px 0; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
.decls { color: var(--muted); font-size: 13px; }
.fspecs { font-size: 13px; }
footer { color: var(--muted); margin-top: 32px; font-size: 13px; }
/* Menu du site (docs/spec/publication.md § Menu du site), en anglais : rangée de liens à 720 px et plus, bouton en dessous. */
header { max-width: 1200px; margin: 0 auto; padding: 24px 16px 0; display: flex; align-items: center; gap: 12px; }
header img { width: 48px; height: 48px; }
header .name { font-size: 28px; font-weight: 500; }
.menu { display: flex; flex-wrap: wrap; column-gap: 20px; margin-left: 16px; font-weight: 500; }
.menu a, .menu-narrow nav a { display: inline-flex; align-items: center; min-height: 48px; color: var(--muted); text-decoration: none; }
.menu a:hover, .menu-narrow nav a:hover { color: var(--primary); }
.menu a[aria-current="page"] { color: var(--text); box-shadow: inset 0 -2px 0 var(--primary); }
.menu-narrow { display: none; position: relative; margin-left: auto; }
.menu-narrow summary { display: inline-flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 16px 0 12px;
  border: 1px solid var(--line); border-radius: 24px; cursor: pointer; font-weight: 500; list-style: none; }
.menu-narrow summary::-webkit-details-marker { display: none; }
.menu-narrow summary svg { fill: currentColor; }
.menu-narrow nav { position: absolute; right: 0; top: 56px; z-index: 10; min-width: 240px; padding: 8px 0; display: flex;
  flex-direction: column; background: var(--surface); border-radius: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3); }
.menu-narrow nav a { padding: 0 16px; }
.menu-narrow nav a[aria-current="page"] { color: var(--text); background: var(--soft); }
@media (max-width: 719px) { .menu { display: none; } .menu-narrow { display: block; } }
</style>
</head>
<body>
<header>
<img src="../icon.png" alt="">
<span class="name">Kairos</span>
@@MENU@@
</header>
<main>
<h1>Kairos code map</h1>
<p class="lead">How the code is organised: one column per module, one box per package, a line for each package that
imports another. Select a package to see what it depends on and what depends on it; the list below gives its files,
their top-level declarations and the specifications (in French) that describe them. Generated from <code>main</code>.</p>
<input type="search" id="q" placeholder="Search a package, a file or a declaration" aria-label="Search">
<div class="scroll">@@SVG@@</div>
<section id="list">@@LIST@@</section>
<footer>@@STAMP@@ Generated by <a href="https://github.com/SKOHscripts/Kairos/blob/main/kmp/tools/make_code_map.py">kmp/tools/make_code_map.py</a>
(<a href="https://github.com/SKOHscripts/Kairos/blob/main/docs/spec/carte-du-code.md">spec</a>).</footer>
</main>
<script type="application/json" id="data">@@DATA@@</script>
<script>
(() => {
  const model = JSON.parse(document.getElementById("data").textContent);
  const graph = document.getElementById("graph");
  const deps = new Map(model.packages.map(p => [p.id, new Set(p.deps)]));
  const users = new Map(model.packages.map(p => [p.id, new Set()]));
  model.packages.forEach(p => p.deps.forEach(d => users.get(d).add(p.id)));
  let current = null;
  function select(id) {
    current = current === id ? null : id;
    graph.classList.toggle("active", current !== null);
    graph.querySelectorAll(".node").forEach(n => {
      const nid = n.dataset.id;
      n.classList.toggle("self", nid === current);
      n.classList.toggle("on", current !== null && (nid === current || deps.get(current).has(nid) || users.get(current).has(nid)));
    });
    graph.querySelectorAll(".edge").forEach(e => {
      e.classList.toggle("on", current !== null && (e.dataset.from === current || e.dataset.to === current));
    });
    document.querySelectorAll(".pkg").forEach(d => d.classList.toggle("target", d.dataset.id === current));
    if (current) { const d = document.getElementById(current); d.open = true; }
  }
  graph.querySelectorAll(".node").forEach(n => {
    n.addEventListener("click", () => select(n.dataset.id));
    n.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); select(n.dataset.id); } });
  });
  document.getElementById("q").addEventListener("input", e => {
    const q = e.target.value.trim().toLowerCase();
    document.querySelectorAll(".pkg").forEach(d => {
      const pkgHit = !q || d.dataset.text.includes(q);
      let any = pkgHit;
      d.querySelectorAll("li").forEach(li => { const hit = pkgHit || li.dataset.text.includes(q); li.hidden = !hit; if (hit) any = true; });
      d.hidden = !any;
      if (q && any) d.open = true;
      const node = graph.querySelector(`.node[data-id="${CSS.escape(d.dataset.id)}"]`);
      if (node) node.classList.toggle("hidden", !any);
    });
  });
})();
</script>
</body>
</html>
"""


MENU_ICON = ('<svg aria-hidden="true" width="24" height="24" viewBox="0 -960 960 960">'
             '<path d="M120-240v-60h720v60H120Zm0-210v-60h720v60H120Zm0-210v-60h720v60H120Z"/></svg>')


def menu(web: str) -> str:
    """Le menu du site (docs/spec/publication.md § Menu du site), en anglais, « Code map » courant ; [web] : `app/` ou `preview/`."""
    entries = [("Download", "../en/"), ("Story", "../en/story/"), ("Web version", "../" + web), ("Code map", "./"),
               ("Source code", "https://github.com/SKOHscripts/Kairos"), ("Support", "https://skohscripts.github.io/donate.github.io/")]
    links = "".join(f'<a href="{html.escape(href)}"' + (' aria-current="page"' if label == "Code map" else "") + f">{label}</a>"
                    for label, href in entries)
    return (f'<nav class="menu" aria-label="Menu">{links}</nav>\n'
            f'<details class="menu-narrow"><summary>{MENU_ICON}Menu</summary><nav aria-label="Menu">{links}</nav></details>')


def page(model: dict, web: str = "preview/") -> str:
    stamp = f"Commit {html.escape(model['stamp'][:12])}." if model.get("stamp") else ""
    # Le script de la page n'a besoin que des liens entre paquets ; le reste est déjà dans le HTML. « </ » dans le JSON
    # fermerait la balise script : on l'échappe.
    links = {"packages": [{"id": p["id"], "deps": p["deps"]} for p in model["packages"]]}
    payload = json.dumps(links, ensure_ascii=False, sort_keys=True).replace("</", "<\\/")
    return (PAGE.replace("@@MENU@@", menu(web)).replace("@@SVG@@", svg(model)).replace("@@LIST@@", package_list(model))
            .replace("@@STAMP@@", stamp).replace("@@DATA@@", payload))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    parser.add_argument("--index", nargs="?", const="", metavar="FILTRE", help="index Markdown, filtré par paquet ou chemin")
    parser.add_argument("--html", type=pathlib.Path, help="écrit la page publique")
    parser.add_argument("--json", type=pathlib.Path, help="écrit les données")
    parser.add_argument("--stamp", help="commit affiché sur la page")
    parser.add_argument("--web", default="preview/", help="entrée « Web version » du menu : app/ ou preview/ (pages.yml)")
    args = parser.parse_args(argv)
    packages, specs = build(args.root)
    if args.index is not None:
        sys.stdout.write(index(packages, args.index or None))
    model = data(packages, specs, args.stamp)
    if args.html:
        args.html.parent.mkdir(parents=True, exist_ok=True)
        args.html.write_text(page(model, args.web), encoding="utf-8")
    if args.json:
        args.json.write_text(json.dumps(model, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    if args.index is None and not args.html and not args.json:
        parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
