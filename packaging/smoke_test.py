"""Vérifie qu'un exécutable empaqueté (PyInstaller, voir `kairos.spec`) démarre
réellement et sert Kairos, avant de le publier en release.

Lancé par `.github/workflows/release.yml` juste après le build PyInstaller,
sur Linux et Windows — garde-fou direct contre les régressions de packaging
(bibliothèque/donnée manquante, import caché oublié, exécutable qui plante
silencieusement au démarrage...). Utilisable aussi à la main :

    pyinstaller packaging/kairos.spec --distpath dist --noconfirm
    python packaging/smoke_test.py dist/kairos        # Linux/macOS
    python packaging/smoke_test.py dist/kairos.exe    # Windows

Isole `KAIROS_DATA_DIR` dans un dossier temporaire (verrou, réglages) pour ne
jamais toucher à une installation réelle de Kairos sur la machine qui exécute
ce script, et pour trouver de façon déterministe le port choisi par
`app/launcher.py` (fichier `kairos.lock`).
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

_STARTUP_TIMEOUT = 30.0
_POLL_INTERVAL = 0.25


def _read_port(data_dir: Path) -> int | None:
    try:
        return json.loads((data_dir / "kairos.lock").read_text(encoding="utf-8"))["port"]
    except (OSError, ValueError, KeyError):
        return None


def _probe_favicon(port: int) -> bool:
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/favicon.ico", timeout=1) as response:
            return response.status == 200
    except (OSError, urllib.error.URLError):
        return False


def _wait_until_serving(process: subprocess.Popen, data_dir: Path, timeout: float) -> tuple[bool, str]:
    """(succès, message) — sonde le verrou puis `/favicon.ico`, en abandonnant
    tôt si le process est déjà mort plutôt que d'attendre le timeout entier."""
    deadline = time.monotonic() + timeout
    port: int | None = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            return False, f"l'exécutable s'est arrêté tout seul (code {process.returncode})"
        if port is None:
            port = _read_port(data_dir)
        elif _probe_favicon(port):
            return True, f"répond sur le port {port}"
        time.sleep(_POLL_INTERVAL)
    if port is None:
        return False, f"aucun verrou (port) écrit sous {timeout:.0f}s"
    return False, f"aucune réponse sur le port {port} sous {timeout:.0f}s"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: smoke_test.py <chemin-vers-executable>", file=sys.stderr)
        return 2
    exe_path = Path(sys.argv[1]).resolve()
    if not exe_path.exists():
        print(f"introuvable : {exe_path}", file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(prefix="kairos-smoke-") as tmp:
        data_dir = Path(tmp)
        process = subprocess.Popen(
            [str(exe_path)],
            env={**os.environ, "KAIROS_DATA_DIR": str(data_dir)},
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            success, message = _wait_until_serving(process, data_dir, _STARTUP_TIMEOUT)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            output = process.stdout.read() if process.stdout else ""

        if not success:
            print(f"ÉCHEC : {exe_path.name} — {message}", file=sys.stderr)
            if output:
                print("--- sortie de l'exécutable ---", file=sys.stderr)
                print(output, file=sys.stderr)
            return 1

        print(f"OK : {exe_path.name} — {message}.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
