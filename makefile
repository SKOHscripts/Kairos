.PHONY: all install test dev run service service-uninstall build-exe clean help

# Venv du projet
VENV := .venv

# Service systemd « utilisateur » (démarrage automatique, port 8001)
SYSTEMD_USER_DIR := $(HOME)/.config/systemd/user
SERVICE_NAME := kairos.service
SERVICE_TEMPLATE := deploy/$(SERVICE_NAME)
# Chemin ABSOLU du dépôt (requis par l'unité : WorkingDirectory + ExecStart)
PROJECT_ABS := $(CURDIR)

all: install

# Installation dans un environnement virtuel dédié.
# Recette robuste : certains systèmes (Debian/Ubuntu sans python3-venv complet) créent
# un venv SANS pip → bootstrap via ensurepip, sinon message d'action clair.
install:
	@echo "=== Installation de Kairos (venv) ==="
	@set -e; \
	python3 -m venv $(VENV) || { echo "ERREUR : venv impossible. Debian/Ubuntu : sudo apt install python3-venv (ou python3-full)"; exit 1; }; \
	if [ ! -x "$(VENV)/bin/pip" ]; then \
		echo "pip absent du venv - bootstrap via ensurepip..."; \
		$(VENV)/bin/python -m ensurepip --upgrade || { echo "ERREUR : ensurepip indisponible. Installez python3-venv (sudo apt install python3-venv) puis relancez."; exit 1; }; \
	fi; \
	$(VENV)/bin/python -m pip install --upgrade pip; \
	$(VENV)/bin/python -m pip install -e ".[dev]"
	@echo "Kairos installé dans $(VENV)"
	@echo "Lancement : make run"

# Lancement des tests (dans le venv)
test: install
	@echo "=== Tests de Kairos ==="
	$(VENV)/bin/pytest

# Lancement en développement (rechargement automatique)
dev: install
	$(VENV)/bin/uvicorn app.main:app --reload --port 8001

# Lancement en mode normal
run: install
	$(VENV)/bin/uvicorn app.main:app --port 8001

# Installation et activation du service systemd « utilisateur » (démarrage auto).
# Génère l'unité depuis le template (chemin absolu injecté), l'active, et tente le
# linger SANS sudo. Best-effort : aucune étape ne fait échouer make.
service: install
	@echo "=== Installation du service systemd utilisateur ($(SERVICE_NAME)) ==="
	@if ! command -v systemctl >/dev/null 2>&1; then \
		echo "systemctl introuvable : service non installé."; \
	else \
		mkdir -p $(SYSTEMD_USER_DIR); \
		sed "s#__PROJECT_DIR__#$(PROJECT_ABS)#g" $(SERVICE_TEMPLATE) > $(SYSTEMD_USER_DIR)/$(SERVICE_NAME); \
		echo "Unité écrite : $(SYSTEMD_USER_DIR)/$(SERVICE_NAME)"; \
		if ! systemctl --user show-environment >/dev/null 2>&1; then \
			echo "Session systemd utilisateur indisponible ici : unité copiée mais non activée."; \
			echo "À activer depuis une vraie session : systemctl --user enable --now $(SERVICE_NAME)"; \
		else \
			systemctl --user daemon-reload; \
			systemctl --user enable --now $(SERVICE_NAME); \
			if loginctl enable-linger "$$(id -un)" 2>/dev/null; then \
				echo "Linger activé : démarrage dès le boot, sans session ouverte."; \
			else \
				echo "Linger non activé (droits insuffisants) : le service démarrera à"; \
				echo "l'ouverture de session. Pour le boot complet, l'IT peut lancer :"; \
				echo "  loginctl enable-linger $$(id -un)"; \
			fi; \
			echo "Service actif. État : systemctl --user status $(SERVICE_NAME)"; \
			echo "Accès local : http://127.0.0.1:8001"; \
		fi; \
	fi

# Désinstallation du service systemd utilisateur
service-uninstall:
	@echo "=== Désinstallation du service systemd utilisateur ($(SERVICE_NAME)) ==="
	@if command -v systemctl >/dev/null 2>&1; then \
		systemctl --user disable --now $(SERVICE_NAME) 2>/dev/null || true; \
		rm -f $(SYSTEMD_USER_DIR)/$(SERVICE_NAME); \
		systemctl --user daemon-reload; \
	else \
		rm -f $(SYSTEMD_USER_DIR)/$(SERVICE_NAME); \
	fi
	@echo "Service désinstallé (linger éventuel conservé : loginctl disable-linger $$(id -un) pour le retirer)."

# Exécutable de bureau (onefile PyInstaller) pour l'OS courant — voir packaging/README.md.
# Les builds Windows/Linux publiés en release GitHub sont construits en CI
# (.github/workflows/release.yml), pas avec cette cible (PyInstaller ne fait pas
# de cross-compile : un build local produit l'exécutable de la machine courante).
build-exe: install
	@echo "=== Construction de l'exécutable Kairos (PyInstaller) ==="
	$(VENV)/bin/pip install pyinstaller pyinstaller-hooks-contrib
	$(VENV)/bin/pyinstaller packaging/kairos.spec --distpath dist --noconfirm
	@echo "Exécutable : dist/kairos"

# Suppression du venv (repart de zéro)
clean:
	rm -rf $(VENV)

# Affichage de l'aide
help:
	@echo "=================================="
	@echo "  Commandes disponibles — Kairos"
	@echo "=================================="
	@echo ""
	@echo "  make install          # Crée le venv + installe les dépendances"
	@echo "  make test             # Installe le venv puis lance les tests"
	@echo "  make dev              # Lance en développement (rechargement auto), port 8001"
	@echo "  make run              # Lance en mode normal, port 8001"
	@echo "  make service          # Installe/active le service systemd (démarrage auto)"
	@echo "  make service-uninstall # Désinstalle le service systemd"
	@echo "  make build-exe        # Construit l'exécutable de bureau (dist/kairos)"
	@echo "  make clean            # Supprime le venv"
	@echo "  make help             # Affiche cette aide"
	@echo ""
