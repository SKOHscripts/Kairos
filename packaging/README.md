# Empaquetage de Kairos (exécutables Windows/Linux)

`kairos.spec` construit un exécutable **onefile** (un seul fichier à
double-cliquer) via [PyInstaller](https://pyinstaller.org/). Un seul spec
partagé pour Linux et Windows : PyInstaller ne fait pas de cross-compile,
seule la **machine** de build change (Linux → build Linux, Windows → build
Windows) — voir `.github/workflows/release.yml` pour le build automatisé.

## Construction locale

```bash
pip install -e ".[dev]" pyinstaller pyinstaller-hooks-contrib
pyinstaller packaging/kairos.spec --distpath dist --noconfirm
```

L'exécutable généré : `dist/kairos` (Linux) ou `dist/kairos.exe` (Windows).
Au lancement, il choisit un port libre à partir de 8001, ouvre le navigateur
par défaut automatiquement, et sert Kairos comme en développement.

## Vérifier qu'il démarre (smoke test)

`packaging/smoke_test.py` lance l'exécutable construit et vérifie qu'il répond
bien en HTTP, avant de le publier — `.github/workflows/release.yml` l'exécute
automatiquement pour chaque OS juste après le build PyInstaller (échec du
smoke test = pas de publication) :

```bash
python packaging/smoke_test.py dist/kairos        # Linux/macOS
python packaging/smoke_test.py dist/kairos.exe    # Windows
```

## Icône

Le logo de l'application (`packaging/kairos.ico`, même dessin que
`static/favicon.svg`) est embarqué dans le `.exe` Windows (barre des tâches,
explorateur). Il est ignoré sans erreur pour le binaire Linux, qui ne porte pas
d'icône (côté bureau, elle viendrait d'un fichier `.desktop`, pas du binaire).

Pour régénérer l'`.ico` après une évolution du logo :

```bash
pip install Pillow
python packaging/make_icon.py
```

## Points d'attention

- Les données (réglages, base de tâches) vivent dans le dossier utilisateur de
  l'OS (`platformdirs`), jamais à côté de l'exécutable — voir
  `app/settings_store.py::data_dir`.
- Le trousseau système (`keyring`, pour le jeton GitLab et le mot de passe
  TimeTree) dépend de ce qui est disponible sur le poste (Windows Credential
  Manager, GNOME Keyring/SecretService, Keychain macOS). Sans back-end
  utilisable (ex. Linux headless), Kairos dégrade proprement vers un stockage
  en fichier local — vérifier ce comportement après un build sur chaque OS
  cible (pas d'erreur, juste un bandeau dans la page Réglages).
- En cas d'échec au démarrage (`console=False`, pas de terminal visible), une
  trace est écrite dans `<dossier de données>/kairos-crash.log` — voir
  `app/launcher.py`.
