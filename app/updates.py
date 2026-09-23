"""Vérification et installation des mises à jour de Kairos.

Voir `docs/spec/mises-a-jour.md` pour le besoin et les décisions. En bref :

- **Source** : une forge GitHub (``github.com``) ou GitLab (toute autre URL),
  un projet, un jeton optionnel (dépôt privé). Par défaut, la forge d'où vient
  la version installée (`app/build_info.py`), surchargeable dans les réglages.
- **Vérification** : dernière release dont le tag est ``vX.Y.Z`` (les
  préversions sont ignorées), au plus toutes les 6 heures, dans un thread de
  fond déclenché par l'affichage d'une page ; jamais bloquante, jamais une
  cause d'erreur pour le reste de l'application.
- **Installation** (sur clic uniquement) : téléchargement du fichier de la
  plateforme, vérifié contre le ``SHA256SUMS`` publié avec la release, puis
  remplacement de l'exécutable et redémarrage (bureau) ou remise à l'installeur
  Android (APK). Une installation depuis les sources n'est jamais modifiée.
- **Jeton** : envoyé seulement aux hôtes de la forge configurée. Les
  redirections sont suivies à la main pour le retirer dès que l'hôte change
  (un téléchargement GitHub est redirigé vers un stockage tiers).
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, urljoin, urlparse

import httpx

from . import build_info
from .settings_store import data_dir
from .subprocess_env import external_process_env

logger = logging.getLogger("kairos.updates")

CHECK_INTERVAL = timedelta(hours=6)
CHECKSUMS_ASSET = "SHA256SUMS"
# Noms des fichiers publiés par les deux CI (.github/workflows/release.yml,
# .gitlab-ci.yml) : identiques d'une forge à l'autre.
DESKTOP_ASSETS = {"win32": "kairos-windows-x86_64.exe", "linux": "kairos-linux-x86_64"}
ANDROID_ASSET = "kairos-android-arm64.apk"
# Chemin relatif au dossier de données, relu tel quel par UpdateApkProvider.java
# (`<filesDir>/kairos-data/updates/kairos-update.apk`, voir android_launcher).
ANDROID_APK_PATH = Path("updates") / "kairos-update.apk"
SOURCE_COMMAND = "git pull && pip install -e ."

_STATE_FILENAME = "update-state.json"
_TIMEOUT = httpx.Timeout(15.0, read=60.0)
_MAX_REDIRECTS = 5
# Le temps que la réponse « redémarrage » parte vers la page avant l'arrêt.
_RESTART_DELAY = 1.0
_GITHUB_HOSTS = ("github.com", "www.github.com", "api.github.com")
_LOOPBACK_HOSTS = ("127.0.0.1", "localhost", "::1")
_VERSION_RE = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")


class UpdateError(Exception):
    """Échec attendu (réseau, droits, release incomplète) : message affichable."""


def is_github(url: str) -> bool:
    return (urlparse(url).hostname or "").lower() in _GITHUB_HOSTS


def parse_version(text: str | None) -> tuple[int, int, int] | None:
    """``v2.6.0``/``2.6.0`` → ``(2, 6, 0)`` ; préversion ou autre → None."""
    match = _VERSION_RE.match((text or "").strip())
    return tuple(int(part) for part in match.groups()) if match else None


def auto_check_allowed() -> bool:
    """``KAIROS_UPDATE_CHECK=0`` coupe la vérification automatique (tests,
    smoke test de la CI), comme ``KAIROS_NO_BROWSER`` pour le navigateur.
    « Vérifier maintenant » reste possible."""
    return os.environ.get("KAIROS_UPDATE_CHECK", "1") != "0"


@dataclass(frozen=True)
class Source:
    server_url: str
    project: str
    token: str = ""

    @property
    def provider(self) -> str:
        return "github" if is_github(self.server_url) else "gitlab"

    @property
    def api_base(self) -> str:
        if self.provider == "github":
            return "https://api.github.com"
        return f"{self.server_url}/api/v4"

    @property
    def web_url(self) -> str:
        return f"{self.server_url}/{self.project}"

    @property
    def releases_url(self) -> str:
        suffix = "releases" if self.provider == "github" else "-/releases"
        return f"{self.web_url}/{suffix}"

    def trusts(self, url: str) -> bool:
        """Vrai si ``url`` appartient à la forge : seuls ces hôtes reçoivent le jeton."""
        parsed = urlparse(url)
        if self.provider == "github":
            return (parsed.hostname or "").lower() in _GITHUB_HOSTS
        server = urlparse(self.server_url)
        return (parsed.hostname, parsed.port) == (server.hostname, server.port)

    def auth_headers(self) -> dict[str, str]:
        if not self.token:
            return {}
        if self.provider == "github":
            return {"Authorization": f"Bearer {self.token}"}
        return {"PRIVATE-TOKEN": self.token}


@dataclass(frozen=True)
class Release:
    tag: str
    page_url: str
    assets: dict[str, str]  # nom du fichier → URL de téléchargement

    @property
    def version(self) -> tuple[int, int, int] | None:
        return parse_version(self.tag)

    def to_dict(self) -> dict:
        return {"tag": self.tag, "page_url": self.page_url, "assets": dict(self.assets)}

    @classmethod
    def from_dict(cls, data: dict) -> Release:
        return cls(str(data["tag"]), str(data.get("page_url", "")), dict(data.get("assets", {})))


def resolve_source(settings) -> Source | None:
    server_url, project = settings.update_source
    if not server_url or not project:
        return None
    return Source(server_url, project, settings.update_token_effective)


# --- HTTP ---------------------------------------------------------------------


def _check_scheme(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme == "https" or (
        parsed.scheme == "http" and parsed.hostname in _LOOPBACK_HOSTS
    ):
        return
    raise UpdateError(f"Adresse refusée (HTTPS obligatoire) : {url}")


def _send(client: httpx.Client, source: Source, url: str, *, accept: str,
          stream: bool = False) -> httpx.Response:
    """GET avec redirections suivies à la main : le jeton n'accompagne que les
    requêtes vers la forge, jamais vers l'hôte tiers d'une redirection."""
    for _ in range(_MAX_REDIRECTS + 1):
        _check_scheme(url)
        headers = {"Accept": accept, "User-Agent": f"Kairos/{build_info.version()}"}
        if source.trusts(url):
            headers.update(source.auth_headers())
        try:
            response = client.send(client.build_request("GET", url, headers=headers),
                                   stream=stream, follow_redirects=False)
        except httpx.HTTPError as exc:
            raise UpdateError(f"Forge injoignable ({exc.__class__.__name__}).") from exc
        if response.is_redirect and "location" in response.headers:
            location = urljoin(url, response.headers["location"])
            response.close()
            url = location
            continue
        _raise_for_status(response)
        return response
    raise UpdateError("Trop de redirections.")


def _raise_for_status(response: httpx.Response) -> None:
    code = response.status_code
    if code < 400:
        return
    response.close()
    if code in (401, 403):
        raise UpdateError("Accès refusé par la forge : jeton absent, invalide ou sans droit de lecture.")
    if code == 404:
        raise UpdateError(
            "Projet ou release introuvable : vérifier le projet, ou renseigner un "
            "jeton si le dépôt est privé."
        )
    raise UpdateError(f"Réponse inattendue de la forge (HTTP {code}).")


def _get_json(client: httpx.Client, source: Source, url: str):
    response = _send(client, source, url, accept="application/json")
    try:
        return response.json()
    except ValueError as exc:
        raise UpdateError("Réponse illisible de la forge (JSON attendu).") from exc


def _github_latest(client: httpx.Client, source: Source) -> Release:
    data = _get_json(client, source, f"{source.api_base}/repos/{source.project}/releases/latest")
    assets = {}
    for asset in data.get("assets", []):
        # Dépôt privé : seule l'URL d'API accepte le jeton (avec
        # `Accept: application/octet-stream`) ; public : lien direct.
        url = asset.get("url") if source.token else asset.get("browser_download_url")
        if asset.get("name") and url:
            assets[asset["name"]] = url
    return Release(str(data.get("tag_name", "")), str(data.get("html_url", source.releases_url)), assets)


def _gitlab_latest(client: httpx.Client, source: Source) -> Release:
    project = quote(source.project, safe="")
    releases = _get_json(client, source, f"{source.api_base}/projects/{project}/releases?per_page=20")
    for data in releases if isinstance(releases, list) else []:
        # Liste triée par date de publication décroissante ; les releases à
        # venir et les préversions sont ignorées.
        if data.get("upcoming_release") or parse_version(data.get("tag_name")) is None:
            continue
        assets = {}
        for link in (data.get("assets") or {}).get("links", []):
            url = link.get("direct_asset_url") or link.get("url")
            if link.get("name") and url:
                assets[link["name"]] = url
        page = (data.get("_links") or {}).get("self") or source.releases_url
        return Release(str(data["tag_name"]), str(page), assets)
    raise UpdateError("Aucune release publiée (tag vX.Y.Z) sur ce projet.")


def fetch_latest_release(source: Source, client: httpx.Client | None = None) -> Release:
    own_client = client is None
    client = client or httpx.Client(timeout=_TIMEOUT)
    try:
        if source.provider == "github":
            release = _github_latest(client, source)
        else:
            release = _gitlab_latest(client, source)
    finally:
        if own_client:
            client.close()
    if release.version is None:
        raise UpdateError(f"Tag de release non reconnu : {release.tag or '(vide)'}.")
    return release


def parse_checksums(text: str) -> dict[str, str]:
    """Format `sha256sum` : ``<empreinte>  <nom>`` (``*nom`` en mode binaire)."""
    sums = {}
    for line in text.splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and re.fullmatch(r"[0-9a-fA-F]{64}", parts[0]):
            sums[parts[1].strip().lstrip("*")] = parts[0].lower()
    return sums


# --- État (mémoire + fichier) -------------------------------------------------


@dataclass
class _Install:
    phase: str = "idle"  # idle, downloading, verifying, installing, restarting, ready, error
    progress: float | None = None
    message: str = ""


@dataclass
class _State:
    loaded: bool = False
    checking: bool = False
    source_key: str = ""
    checked_at: datetime | None = None
    latest: Release | None = None
    error: str | None = None
    dismissed: str = ""
    notified: str = ""
    install: _Install = field(default_factory=_Install)


_lock = threading.Lock()
_state = _State()


def _state_path() -> Path:
    return data_dir() / _STATE_FILENAME


def _load_locked() -> None:
    if _state.loaded:
        return
    _state.loaded = True
    try:
        data = json.loads(_state_path().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return
    try:
        _state.source_key = str(data.get("source_key", ""))
        checked = data.get("checked_at")
        _state.checked_at = datetime.fromisoformat(checked) if checked else None
        _state.latest = Release.from_dict(data["latest"]) if data.get("latest") else None
        _state.error = data.get("error")
        _state.dismissed = str(data.get("dismissed", ""))
        _state.notified = str(data.get("notified", ""))
    except (KeyError, TypeError, ValueError):
        pass  # fichier d'état abîmé : on repart de zéro, sans erreur


def _save_locked() -> None:
    data = {
        "source_key": _state.source_key,
        "checked_at": _state.checked_at.isoformat() if _state.checked_at else None,
        "latest": _state.latest.to_dict() if _state.latest else None,
        "error": _state.error,
        "dismissed": _state.dismissed,
        "notified": _state.notified,
    }
    try:
        path = _state_path()
        tmp = path.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
        os.replace(tmp, path)
    except OSError:
        logger.warning("État des mises à jour non enregistré", exc_info=True)


def reset_state() -> None:
    """Oublie l'état en mémoire (tests)."""
    global _state
    with _lock:
        _state = _State()


def _source_key(source: Source | None) -> str:
    return f"{source.server_url}|{source.project}" if source else ""


def _asset_for_kind(kind: str) -> str | None:
    if kind == "android":
        return ANDROID_ASSET
    if kind == "desktop":
        return DESKTOP_ASSETS.get("win32" if sys.platform == "win32" else sys.platform)
    return None


def _install_blocker(kind: str, release: Release) -> str | None:
    """Pourquoi la release ne peut pas s'installer toute seule ici (ou None)."""
    if kind == "source":
        return "Installation depuis les sources : mise à jour à faire à la main."
    asset = _asset_for_kind(kind)
    if not asset or asset not in release.assets:
        return "Cette release ne contient pas de fichier pour cette plateforme."
    if CHECKSUMS_ASSET not in release.assets:
        return (
            "Cette release ne publie pas de somme de contrôle (SHA256SUMS) : "
            "installation automatique impossible."
        )
    return None


def _run_check(source: Source) -> None:
    try:
        release = fetch_latest_release(source)
        error = None
    except UpdateError as exc:
        release, error = None, str(exc)
    except Exception as exc:  # jamais d'exception hors du thread de fond
        logger.exception("Vérification des mises à jour en échec")
        release, error = None, f"Erreur inattendue ({exc.__class__.__name__})."
    with _lock:
        key = _source_key(source)
        if release is not None:
            _state.latest = release
        elif _state.source_key != key:
            _state.latest = None
        # Sinon (même source, forge momentanément injoignable) : la dernière
        # version connue reste proposée, l'erreur s'affiche dans les réglages.
        _state.checking = False
        _state.checked_at = datetime.now(timezone.utc)
        _state.source_key = key
        _state.error = error
        _save_locked()


def check_now(settings) -> None:
    """Vérification synchrone (« Vérifier maintenant »)."""
    source = resolve_source(settings)
    if source is None:
        return
    with _lock:
        _load_locked()
        _state.checking = True
    _run_check(source)


def _maybe_check_async(settings, source: Source | None) -> None:
    if source is None or not settings.update_check_enabled or not auto_check_allowed():
        return
    with _lock:
        _load_locked()
        if _state.checking:
            return
        fresh = (
            _state.checked_at is not None
            and _state.source_key == _source_key(source)
            and datetime.now(timezone.utc) - _state.checked_at < CHECK_INTERVAL
        )
        if fresh:
            return
        _state.checking = True
    threading.Thread(target=_run_check, args=(source,), name="kairos-update-check",
                     daemon=True).start()


def snapshot(settings, *, trigger_check: bool = True) -> dict:
    """État sérialisable (JSON, gabarits). Déclenche au besoin une vérification
    de fond, sans jamais attendre le réseau."""
    source = resolve_source(settings)
    if trigger_check:
        _maybe_check_async(settings, source)
    kind = build_info.install_kind()
    current = build_info.version()
    current_version = parse_version(current)
    with _lock:
        _load_locked()
        same_source = _state.source_key == _source_key(source)
        latest = _state.latest if same_source else None
        available = bool(
            settings.update_check_enabled and latest and current_version
            and latest.version and latest.version > current_version
        )
        blocker = _install_blocker(kind, latest) if latest else None
        install = _state.install
        return {
            "current": current,
            "kind": kind,
            "enabled": settings.update_check_enabled,
            "source": source.web_url if source else "",
            "releases_url": source.releases_url if source else "",
            "checking": _state.checking,
            "checked_at": _state.checked_at.isoformat() if _state.checked_at and same_source else None,
            "error": _state.error if same_source else None,
            "available": available,
            "latest": ".".join(map(str, latest.version)) if latest and latest.version else None,
            "tag": latest.tag if latest else None,
            "page_url": latest.page_url if latest else None,
            "dismissed": bool(latest and _state.dismissed == latest.tag),
            "notified": bool(latest and _state.notified == latest.tag),
            "can_install": available and blocker is None,
            "install_blocker": blocker,
            "command": SOURCE_COMMAND if kind == "source" else None,
            "install": {"phase": install.phase, "progress": install.progress, "message": install.message},
        }


def dismiss(tag: str) -> None:
    with _lock:
        _load_locked()
        _state.dismissed = tag
        _save_locked()


def claim_notification(tag: str) -> bool:
    """Vrai pour le premier appelant seulement, par version : une seule
    notification système par version, même avec plusieurs pages ouvertes."""
    with _lock:
        _load_locked()
        if not _state.latest or _state.latest.tag != tag or _state.notified == tag:
            return False
        _state.notified = tag
        _save_locked()
        return True


# --- Installation -------------------------------------------------------------


def _set_install(phase: str, *, progress: float | None = None, message: str = "") -> None:
    with _lock:
        _state.install = _Install(phase, progress, message)


def start_install(settings, *, port: int) -> None:
    """Lance l'installation de la dernière release dans un thread de fond.
    Lève `UpdateError` si elle n'est pas possible ici (déjà en cours, rien à
    installer, release incomplète, installation depuis les sources)."""
    kind = build_info.install_kind()
    source = resolve_source(settings)
    with _lock:
        _load_locked()
        if _state.install.phase in ("downloading", "verifying", "installing", "restarting"):
            raise UpdateError("Une mise à jour est déjà en cours.")
        release = _state.latest
        if source is None or release is None or _state.source_key != _source_key(source):
            raise UpdateError("Aucune nouvelle version connue : lancer une vérification.")
        blocker = _install_blocker(kind, release)
        if blocker:
            raise UpdateError(blocker)
        _state.install = _Install("downloading", 0.0)
    threading.Thread(target=_run_install, args=(source, release, kind, port),
                     name="kairos-update-install", daemon=True).start()


def _download(client: httpx.Client, source: Source, url: str, target: Path,
              expected_sha256: str) -> None:
    """Télécharge vers ``target`` en calculant l'empreinte au fil de l'eau ;
    le fichier n'existe sous son nom final que si l'empreinte correspond."""
    part = target.with_name(target.name + ".part")
    digest = hashlib.sha256()
    response = _send(client, source, url, accept="application/octet-stream", stream=True)
    try:
        total = int(response.headers.get("content-length") or 0)
        done = 0
        with part.open("wb") as out:
            for chunk in response.iter_bytes(64 * 1024):
                out.write(chunk)
                digest.update(chunk)
                done += len(chunk)
                if total:
                    _set_install("downloading", progress=min(done / total, 1.0))
    except httpx.HTTPError as exc:
        part.unlink(missing_ok=True)
        raise UpdateError(f"Téléchargement interrompu ({exc.__class__.__name__}).") from exc
    finally:
        response.close()
    _set_install("verifying")
    if digest.hexdigest() != expected_sha256:
        part.unlink(missing_ok=True)
        raise UpdateError(
            "Somme de contrôle incorrecte : fichier corrompu ou modifié, rien n'a été installé."
        )
    os.replace(part, target)


def _run_install(source: Source, release: Release, kind: str, port: int) -> None:
    try:
        asset = _asset_for_kind(kind)
        updates_dir = data_dir() / "updates"
        updates_dir.mkdir(parents=True, exist_ok=True)
        with httpx.Client(timeout=_TIMEOUT) as client:
            sums = _send(client, source, release.assets[CHECKSUMS_ASSET],
                         accept="application/octet-stream").text
            expected = parse_checksums(sums).get(asset)
            if not expected:
                raise UpdateError(f"{asset} absent de SHA256SUMS : rien n'a été installé.")
            target = data_dir() / ANDROID_APK_PATH if kind == "android" else updates_dir / asset
            _download(client, source, release.assets[asset], target, expected)
        if kind == "android":
            # La suite est côté Java (window.KairosAndroid.installUpdate) : la
            # page ouvre l'installeur Android sur ce fichier vérifié.
            _set_install("ready")
            return
        _set_install("installing")
        exe = Path(sys.executable).resolve()
        replace_executable(target, exe)
        _set_install("restarting")
        restart_desktop(exe, port)
    except UpdateError as exc:
        _set_install("error", message=str(exc))
    except Exception as exc:
        logger.exception("Installation de la mise à jour en échec")
        _set_install("error", message=f"Erreur inattendue ({exc.__class__.__name__}).")


def replace_executable(new_file: Path, exe: Path) -> None:
    """Remplace l'exécutable en cours d'exécution par ``new_file``.

    Copie d'abord à côté de l'exécutable (même système de fichiers : le
    renommage final est atomique). Linux : renommer par-dessus un exécutable
    lancé est permis (l'ancien inode reste ouvert par le process). Windows :
    l'écraser est refusé, mais le renommer est permis ; l'ancien devient
    ``<nom>.old``, supprimé au lancement suivant (`app/launcher.py`).
    """
    staged = exe.with_name(exe.name + ".new")
    try:
        shutil.copyfile(new_file, staged)
        if os.name != "nt":
            staged.chmod(0o755)
        if os.name == "nt":
            old = exe.with_name(exe.name + ".old")
            old.unlink(missing_ok=True)
            os.replace(exe, old)
            try:
                os.replace(staged, exe)
            except OSError:
                os.replace(old, exe)
                raise
        else:
            os.replace(staged, exe)
    except OSError as exc:
        staged.unlink(missing_ok=True)
        raise UpdateError(
            f"Impossible de remplacer {exe} ({exc.strerror or exc}). Le fichier "
            f"vérifié est ici : {new_file}"
        ) from exc


def restart_desktop(exe: Path, port: int) -> None:
    """Lance la nouvelle version puis arrête celle-ci, comme le bouton Quitter.

    ``KAIROS_RESTART_PORT`` dit au nouveau process d'attendre que ce port se
    libère, de le reprendre et de ne pas ouvrir de seconde fenêtre (la page
    ouverte se recharge d'elle-même). ``PYINSTALLER_RESET_ENVIRONMENT`` : le
    nouvel exécutable est un process PyInstaller indépendant, pas un enfant de
    celui-ci (il ne doit pas réutiliser son dossier d'extraction).
    """
    env = external_process_env()
    env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"
    env["KAIROS_RESTART_PORT"] = str(port)
    kwargs: dict = {
        "cwd": str(exe.parent), "env": env, "close_fds": True,
        "stdin": subprocess.DEVNULL, "stdout": subprocess.DEVNULL, "stderr": subprocess.DEVNULL,
    }
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        kwargs["start_new_session"] = True
    subprocess.Popen([str(exe)], **kwargs)
    threading.Timer(_RESTART_DELAY, lambda: os.kill(os.getpid(), signal.SIGINT)).start()
