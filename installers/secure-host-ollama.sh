#!/usr/bin/env bash
# ============================================================================
#  LoreMindMJ - Configuration securisee d'Ollama hote (Linux)
# ----------------------------------------------------------------------------
#  But : permettre au conteneur Docker de LoreMind d'atteindre l'Ollama
#        installe sur l'hote, SANS l'exposer sur le LAN ni Internet.
#
#  Strategie : faire ecouter Ollama uniquement sur l'IP de la passerelle du
#              bridge Docker (typiquement 172.17.0.1). Cette IP n'est jamais
#              routee en dehors de la machine — seuls les conteneurs Docker
#              peuvent l'atteindre.
#
#  Ce script peut etre lance independamment de install.sh, par ex. si vous
#  avez initialement choisi le mode "Ollama embarque" et changez d'avis.
#
#  Usage : bash secure-host-ollama.sh
# ============================================================================
set -euo pipefail

c_cyan='\033[1;36m'; c_green='\033[1;32m'; c_yellow='\033[1;33m'; c_red='\033[1;31m'; c_off='\033[0m'
step() { echo -e "${c_cyan}==> $*${c_off}"; }
ok()   { echo -e "  ${c_green}OK${c_off} $*"; }
warn() { echo -e "  ${c_yellow}!!${c_off} $*"; }
err()  { echo -e "  ${c_red}XX${c_off} $*" >&2; }

# --- 1. Verifications prealables -------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
    err "Docker introuvable. Installez Docker avant de lancer ce script."
    exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
    err "systemctl introuvable. Ce script suppose un systeme avec systemd."
    err "Configurez OLLAMA_HOST manuellement selon votre init system."
    exit 1
fi

if ! systemctl list-unit-files 2>/dev/null | grep -q '^ollama\.service'; then
    err "Service systemd 'ollama' introuvable."
    err "Installez Ollama via le script officiel : curl -fsSL https://ollama.com/install.sh | sh"
    exit 1
fi

# --- 2. Detection de l'IP de la passerelle Docker --------------------------
step "Detection de l'IP du bridge Docker..."
BRIDGE_IP=""

# Methode 1 : docker network inspect (la plus fiable)
if BRIDGE_IP="$(docker network inspect bridge -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}' 2>/dev/null)"; then
    if [ -n "$BRIDGE_IP" ]; then
        ok "IP du bridge Docker detectee via docker network inspect : $BRIDGE_IP"
    fi
fi

# Methode 2 : interface docker0 (si docker network inspect echoue)
if [ -z "$BRIDGE_IP" ] && command -v ip >/dev/null 2>&1; then
    BRIDGE_IP="$(ip -4 addr show docker0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1 | head -1)"
    if [ -n "$BRIDGE_IP" ]; then
        ok "IP du bridge Docker detectee via interface docker0 : $BRIDGE_IP"
    fi
fi

# Methode 3 : valeur par defaut (compatible avec 99% des installations)
if [ -z "$BRIDGE_IP" ]; then
    BRIDGE_IP="172.17.0.1"
    warn "Detection automatique echouee, utilisation de la valeur par defaut : $BRIDGE_IP"
    warn "Si Docker n'a jamais ete demarre sur cette machine, lancez 'docker info' une fois pour creer le bridge."
fi

# --- 3. Ecriture de l'override systemd -------------------------------------
step "Configuration du service systemd Ollama..."
OVERRIDE_DIR="/etc/systemd/system/ollama.service.d"
OVERRIDE_FILE="$OVERRIDE_DIR/loremind-host.conf"

sudo mkdir -p "$OVERRIDE_DIR"
sudo tee "$OVERRIDE_FILE" >/dev/null <<EOF
# Genere par LoreMind secure-host-ollama.sh
# Lie Ollama exclusivement a l'IP de la passerelle Docker.
# Consequence : Ollama est joignable depuis les conteneurs Docker
# (via host.docker.internal) mais PAS depuis le LAN ni Internet.
# Pour revenir a la configuration par defaut : sudo rm $OVERRIDE_FILE && sudo systemctl daemon-reload && sudo systemctl restart ollama
[Service]
Environment="OLLAMA_HOST=$BRIDGE_IP:11434"
EOF
ok "Override ecrit : $OVERRIDE_FILE"

# --- 4. Rechargement et redemarrage ----------------------------------------
step "Rechargement de la configuration systemd..."
sudo systemctl daemon-reload
ok "daemon-reload effectue"

step "Redemarrage du service Ollama..."
sudo systemctl restart ollama
sleep 2
if sudo systemctl is-active --quiet ollama; then
    ok "Ollama redemarre et actif"
else
    err "Ollama n'a pas redemarre correctement. Verifiez : sudo journalctl -u ollama -n 50"
    exit 1
fi

# --- 5. Verification du binding --------------------------------------------
step "Verification : Ollama doit ecouter sur $BRIDGE_IP:11434..."
sleep 1
if command -v ss >/dev/null 2>&1; then
    if ss -tln 2>/dev/null | grep -q "$BRIDGE_IP:11434"; then
        ok "Ollama ecoute bien sur $BRIDGE_IP:11434"
    else
        warn "Verification impossible (ss n'a pas trouve le binding). Cela peut etre normal si le service vient juste de demarrer."
    fi
fi

# --- 6. Recap --------------------------------------------------------------
echo
echo -e "${c_green}============================================================${c_off}"
echo -e "${c_green} Ollama hote configure de maniere securisee${c_off}"
echo -e "${c_green}============================================================${c_off}"
echo " Adresse d'ecoute : $BRIDGE_IP:11434"
echo " Accessible depuis : conteneurs Docker uniquement (via host.docker.internal)"
echo " Inaccessible depuis : LAN, WiFi public, Internet"
echo
echo " Pour LoreMind, definissez dans le fichier .env :"
echo "   OLLAMA_BASE_URL=http://host.docker.internal:11434"
echo
echo " Pour annuler cette configuration :"
echo "   sudo rm $OVERRIDE_FILE"
echo "   sudo systemctl daemon-reload && sudo systemctl restart ollama"
echo
