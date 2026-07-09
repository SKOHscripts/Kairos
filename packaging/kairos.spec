# -*- mode: python ; coding: utf-8 -*-
"""Spec PyInstaller pour « Kairos » (exécutable de bureau, mode onefile).

Un seul spec partagé pour Linux et Windows : PyInstaller ne fait pas de
cross-compile, seule la MACHINE de build change (voir
`.github/workflows/release.yml`) — le contenu du spec, lui, est identique.

Construction locale :
    pip install -e ".[dev]" pyinstaller pyinstaller-hooks-contrib
    pyinstaller packaging/kairos.spec --distpath dist --noconfirm

Voir `packaging/README.md` pour plus de détails.
"""

from pathlib import Path

from PyInstaller.utils.hooks import collect_submodules, copy_metadata

# Chemin du dépôt (ce spec vit dans packaging/, la racine est son parent).
ROOT = Path(SPECPATH).resolve().parent

hiddenimports = []
# uvicorn choisit sa boucle d'événements et son implémentation de protocole HTTP
# dynamiquement selon les paquets optionnels installés (uvicorn.loops.auto,
# uvicorn.protocols.http.auto, ...) : invisible à l'analyse statique de PyInstaller.
hiddenimports += collect_submodules("uvicorn")
# keyring sélectionne son back-end (Windows Credential Manager, SecretService,
# Keychain...) via les entry-points de son propre paquet — également invisible
# à l'analyse statique. C'est le point du packaging le plus incertain : à
# vérifier empiriquement en lançant l'exécutable construit (voir le README de
# ce dossier), le repli fichier local (`app/secret_store.py`) reste sûr sinon.
hiddenimports += collect_submodules("keyring")

datas = [
    (str(ROOT / "templates"), "templates"),
    (str(ROOT / "static"), "static"),
    (str(ROOT / "README.md"), "."),
    (str(ROOT / "SPEC_KAIROS.md"), "."),
]
datas += copy_metadata("keyring")

a = Analysis(
    [str(ROOT / "app" / "launcher.py")],
    pathex=[str(ROOT)],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="kairos",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    # Pas de console visible (ressenti « application de bureau ») : tout échec
    # de démarrage est journalisé dans un fichier par `app/launcher.py` plutôt
    # que perdu derrière une fenêtre qui se ferme aussitôt.
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
