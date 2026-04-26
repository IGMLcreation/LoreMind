#!/usr/bin/env bash
# ==========================================================================
# Installeur LoreMindMJ pour Linux (Debian/Ubuntu/Fedora/Arch)
# Usage :
#   curl -fsSL https://raw.githubusercontent.com/IGMLcreation/LoreMind/main/installers/install.sh | bash
# ==========================================================================
set -euo pipefail

INSTALL_DIR="${INSTALL_DIR:-$HOME/.local/share/loremind}"
COMPOSE_URL="${COMPOSE_URL:-https://raw.githubusercontent.com/IGMLcreation/LoreMind/main/docker-compose.yml}"
WEB_PORT="${WEB_PORT:-8081}"
NON_INTERACTIVE="${NON_INTERACTIVE:-0}"

c_cyan='\033[1;36m'; c_green='\033[1;32m'; c_yellow='\033[1;33m'; c_red='\033[1;31m'; c_off='\033[0m'
step() { echo -e "${c_cyan}==> $*${c_off}"; }
ok()   { echo -e "  ${c_green}OK${c_off} $*"; }
warn() { echo -e "  ${c_yellow}!!${c_off} $*"; }
err()  { echo -e "  ${c_red}XX${c_off} $*" >&2; }

rand_hex() {
    # $1 = nb de caracteres hex
    local n="${1:-32}"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex $((n / 2))
    else
        head -c $((n * 2)) /dev/urandom | od -An -tx1 | tr -d ' \n' | head -c "$n"
    fi
}

ask() {
    # ask "prompt" "default"
    local prompt="$1" def="${2:-}" reply
    if [ "$NON_INTERACTIVE" = "1" ]; then
        echo "$def"; return
    fi
    if [ -n "$def" ]; then
        read -r -p "  $prompt [$def] " reply </dev/tty || true
    else
        read -r -p "  $prompt " reply </dev/tty || true
    fi
    echo "${reply:-$def}"
}

detect_pkg() {
    if   command -v apt-get >/dev/null 2>&1; then echo apt
    elif command -v dnf     >/dev/null 2>&1; then echo dnf
    elif command -v pacman  >/dev/null 2>&1; then echo pacman
    else echo unknown
    fi
}

install_docker() {
    step "Installation de Docker..."
    local pm; pm="$(detect_pkg)"
    case "$pm" in
        apt|dnf|pacman)
            # Script officiel Docker (gere apt/dnf/pacman)
            curl -fsSL https://get.docker.com | sh
            ;;
        *)
            err "Gestionnaire de paquets non reconnu. Installez Docker manuellement : https://docs.docker.com/engine/install/"
            exit 1
            ;;
    esac
    if ! getent group docker >/dev/null; then sudo groupadd docker || true; fi
    sudo usermod -aG docker "$USER" || true
    sudo systemctl enable --now docker || true
    warn "Vous avez ete ajoute au groupe 'docker'. Si docker echoue ensuite, deconnectez-vous puis reconnectez-vous (ou 'newgrp docker')."
}

# ---------------------------------------------------------------------------
echo
echo "============================================================"
echo -e " ${c_cyan}LoreMindMJ - Installeur Linux${c_off}"
echo "============================================================"
echo

# 1. Docker
step "Verification de Docker..."
if ! command -v docker >/dev/null 2>&1; then
    install_docker
elif ! docker info >/dev/null 2>&1; then
    warn "Docker installe mais inaccessible (daemon arrete ou groupe docker manquant)"
    sudo systemctl start docker || true
    if ! docker info >/dev/null 2>&1; then
        sudo usermod -aG docker "$USER" || true
        err "Re-essayez apres 'newgrp docker' ou une nouvelle session."
        exit 1
    fi
fi
ok "Docker fonctionnel"

# 2. docker compose v2
step "Verification de docker compose..."
if ! docker compose version >/dev/null 2>&1; then
    err "Plugin 'docker compose' manquant. Sur Debian/Ubuntu : sudo apt install docker-compose-plugin"
    exit 1
fi
ok "docker compose disponible"

# 3. Dossier + compose
step "Preparation du dossier $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"
step "Telechargement de docker-compose.yml"
curl -fsSL "$COMPOSE_URL" -o docker-compose.yml
ok "docker-compose.yml recupere"

# 4. .env
if [ -f .env ]; then
    warn ".env existant -> sauvegarde en .env.bak"
    cp .env .env.bak
fi

step "Configuration"
ADMIN_USERNAME="$(ask "Nom d'utilisateur admin" "admin")"
ADMIN_PASSWORD="$(ask "Mot de passe admin (vide = genere)" "")"
[ -z "$ADMIN_PASSWORD" ] && ADMIN_PASSWORD="$(rand_hex 16)"

LLM_PROVIDER="$(ask "Provider LLM (ollama / onemin)" "ollama")"
ONEMIN_API_KEY=""
if [ "$LLM_PROVIDER" = "onemin" ] && [ "$NON_INTERACTIVE" != "1" ]; then
    ONEMIN_API_KEY="$(ask "Cle API 1min.ai" "")"
fi

# --- Mode Ollama : 3 options possibles -------------------------------------
# 1. host     : Ollama deja installe sur la machine -> helper de securisation
# 2. embedded : service 'ollama' du compose (profile local-ollama)
# 3. none     : aucune installation, configuration ulterieure via l'app
OLLAMA_MODE="embedded"
OLLAMA_BASE_URL_VAL="http://ollama:11434"
LLM_MODEL_VAL="gemma4:e4b"
if [ "$LLM_PROVIDER" = "ollama" ]; then
    HOST_OLLAMA_REPLY="$(ask "Avez-vous deja Ollama installe sur cette machine ? [o/N]" "N")"
    case "$HOST_OLLAMA_REPLY" in
        o|O|y|Y|oui|yes|Oui|Yes)
            OLLAMA_MODE="host"
            ;;
        *)
            # Pas d'Ollama present : proposer l'installation Docker.
            INSTALL_DOCKER_REPLY="$(ask "Voulez-vous installer Ollama via Docker maintenant ? [O/n]" "O")"
            case "$INSTALL_DOCKER_REPLY" in
                n|N|no|non|No|Non) OLLAMA_MODE="none" ;;
                *)                 OLLAMA_MODE="embedded" ;;
            esac
            ;;
    esac

    case "$OLLAMA_MODE" in
        host)
            OLLAMA_BASE_URL_VAL="http://host.docker.internal:11434"
            # Delegue la configuration securisee au helper dedie : il fait
            # ecouter Ollama uniquement sur l'IP du bridge Docker (jamais
            # exposee au LAN ni a Internet) plutot que sur 0.0.0.0.
            SECURE_HELPER="$(dirname -- "$0")/secure-host-ollama.sh"
            if [ -f "$SECURE_HELPER" ]; then
                step "Configuration securisee d'Ollama hote..."
                bash "$SECURE_HELPER" || warn "Le helper secure-host-ollama.sh a echoue. Configurez Ollama manuellement."
            else
                warn "secure-host-ollama.sh introuvable a cote de install.sh."
                warn "Telechargez-le depuis le depot et relancez : bash secure-host-ollama.sh"
            fi
            ;;
        embedded)
            ok "Ollama sera lance dans Docker (modeles dans un volume Docker)"
            ;;
        none)
            # On cible host.docker.internal par defaut en supposant qu'Ollama
            # sera installe plus tard sur l'hote. L'utilisateur peut aussi
            # changer l'URL via la page Parametres pour un Ollama distant.
            OLLAMA_BASE_URL_VAL="http://host.docker.internal:11434"
            warn "Aucun Ollama ne sera installe pour le moment. Configurez-le plus tard via la page Parametres de LoreMind."
            ;;
    esac
fi

AUTO_UPDATE_REPLY="$(ask "Activer les mises a jour auto (chaque nuit a 4h) ? [O/n]" "O")"
case "$AUTO_UPDATE_REPLY" in
    n|N|no|non|No|Non) AUTO_UPDATE=0 ;;
    *)                 AUTO_UPDATE=1 ;;
esac

# Combinaison de profiles : autoupdate et/ou local-ollama (separes par virgule).
PROFILES_ARR=()
[ "$AUTO_UPDATE" = "1" ] && PROFILES_ARR+=("autoupdate")
if [ "$LLM_PROVIDER" = "ollama" ] && [ "$OLLAMA_MODE" = "embedded" ]; then
    PROFILES_ARR+=("local-ollama")
fi
COMPOSE_PROFILES="$(IFS=,; echo "${PROFILES_ARR[*]}")"

cat > .env <<EOF
# Genere par install.sh le $(date '+%Y-%m-%d %H:%M')
REGISTRY=ghcr.io
IMAGE_NAMESPACE=igmlcreation/loremind-
TAG=latest

WEB_PORT=${WEB_PORT}

POSTGRES_DB=loremind
POSTGRES_USER=loremind
POSTGRES_PASSWORD=$(rand_hex 24)

ADMIN_USERNAME=${ADMIN_USERNAME}
ADMIN_PASSWORD=${ADMIN_PASSWORD}

BRAIN_INTERNAL_SECRET=$(rand_hex 32)

MINIO_USER=minioadmin
MINIO_PASSWORD=$(rand_hex 24)

LLM_PROVIDER=${LLM_PROVIDER}
OLLAMA_BASE_URL=${OLLAMA_BASE_URL_VAL}
LLM_MODEL=${LLM_MODEL_VAL}
ONEMIN_API_KEY=${ONEMIN_API_KEY}
ONEMIN_MODEL=gpt-4o-mini

COMPOSE_PROFILES=${COMPOSE_PROFILES}
WATCHTOWER_TOKEN=$(rand_hex 32)
WATCHTOWER_MONITOR_ONLY=false
WATCHTOWER_SCHEDULE=0 0 4 * * *
TZ=$(timedatectl show -p Timezone --value 2>/dev/null || echo Europe/Paris)
EOF
chmod 600 .env
ok ".env genere ($INSTALL_DIR/.env)"

# 5. Pull + up
step "Telechargement des images (peut prendre quelques minutes)"
docker compose pull
step "Demarrage de la stack"
docker compose up -d

# 5b. Telechargement du modele Ollama (mode embarque uniquement)
# ----------------------------------------------------------------------------
# Le conteneur Ollama est pret mais sans modele. On propose le pull tout de
# suite pour que l'utilisateur ait quelque chose a utiliser au premier lancement.
if [ "$LLM_PROVIDER" = "ollama" ] && [ "$OLLAMA_MODE" = "embedded" ]; then
    PULL_REPLY="$(ask "Telecharger le modele '${LLM_MODEL_VAL}' maintenant ? (peut prendre plusieurs minutes) [O/n]" "O")"
    case "$PULL_REPLY" in
        n|N|no|non|No|Non)
            echo "  Pour le telecharger plus tard : docker exec -it loremind-ollama ollama pull ${LLM_MODEL_VAL}"
            ;;
        *)
            step "Attente de la disponibilite du conteneur Ollama..."
            OLLAMA_READY=0
            for i in $(seq 1 30); do
                if docker exec loremind-ollama ollama list >/dev/null 2>&1; then
                    OLLAMA_READY=1
                    break
                fi
                sleep 2
            done
            if [ "$OLLAMA_READY" = "0" ]; then
                warn "Le conteneur Ollama ne repond pas encore. Vous pourrez pull plus tard :"
                warn "  docker exec -it loremind-ollama ollama pull ${LLM_MODEL_VAL}"
            else
                step "Telechargement du modele ${LLM_MODEL_VAL} (peut prendre plusieurs minutes selon votre connexion)..."
                if docker exec loremind-ollama ollama pull "${LLM_MODEL_VAL}"; then
                    ok "Modele ${LLM_MODEL_VAL} pret a l'emploi"
                else
                    warn "Echec du pull. Reessayez : docker exec -it loremind-ollama ollama pull ${LLM_MODEL_VAL}"
                fi
            fi
            ;;
    esac
fi

# 6. Recap
URL="http://localhost:${WEB_PORT}"
echo
echo -e "${c_green}============================================================${c_off}"
echo -e "${c_green} LoreMindMJ est lance !${c_off}"
echo -e "${c_green}============================================================${c_off}"
echo " URL          : $URL"
echo " Identifiant  : $ADMIN_USERNAME"
echo " Mot de passe : $ADMIN_PASSWORD"
echo " Dossier      : $INSTALL_DIR"
if [ "$AUTO_UPDATE" = "1" ]; then
    echo -e " Auto-update  : ${c_green}active${c_off} (chaque nuit a 4h via Watchtower)"
else
    echo " Auto-update  : desactive (mise a jour manuelle uniquement)"
fi
if [ "$LLM_PROVIDER" = "ollama" ]; then
    case "$OLLAMA_MODE" in
        embedded)
            echo -e " Ollama       : ${c_green}embarque${c_off} (service Docker 'ollama')"
            echo
            echo " IMPORTANT : telechargez un modele avant utilisation :"
            echo "   docker exec -it loremind-ollama ollama pull ${LLM_MODEL_VAL}"
            ;;
        host)
            echo " Ollama       : hote (configure via secure-host-ollama.sh)"
            ;;
        none)
            echo -e " Ollama       : ${c_yellow}non configure${c_off} - a faire via Parametres dans l'app"
            ;;
    esac
fi
echo
echo " Commandes utiles (depuis $INSTALL_DIR) :"
echo "   docker compose ps         # etat"
echo "   docker compose logs -f    # logs"
echo "   docker compose down       # arret"
echo "   docker compose pull && docker compose up -d   # mise a jour"
echo

if command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1 || true; fi
